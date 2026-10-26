using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using ReliableOrder.Api.Data;
using ReliableOrder.Api.Domain;

namespace ReliableOrder.Api.Services;

/// <summary>Kết quả trả về từ OrderService: mã HTTP + nội dung JSON.</summary>
public class OrderResult
{
    public int StatusCode { get; set; }
    public string Json { get; set; } = "";
    public bool Replayed { get; set; }

    public OrderResult(int statusCode, string json, bool replayed = false)
    {
        StatusCode = statusCode;
        Json = json;
        Replayed = replayed;
    }
}

public class OrderService
{
    private readonly AppDbContext _db;
    private readonly ILogger<OrderService> _logger;

    // Controller trả JSON theo camelCase, nên service cũng serialize giống vậy
    private static readonly JsonSerializerOptions JsonOptions = new JsonSerializerOptions(JsonSerializerDefaults.Web);

    public OrderService(AppDbContext db, ILogger<OrderService> logger)
    {
        _db = db;
        _logger = logger;
    }

    /// <summary>
    /// Tạo đơn an toàn khi client retry:
    /// 1) Idempotency-Key: cùng key + cùng body => trả lại kết quả cũ; cùng key khác body => 422.
    /// 2) Trừ kho bằng UPDATE có điều kiện (Stock >= qty) => không bao giờ âm kho dù nhiều request song song.
    /// 3) Đơn + sự kiện outbox + idempotency record nằm trong CÙNG 1 transaction.
    /// </summary>
    public async Task<OrderResult> CreateAsync(string key, CreateOrderRequest request, CancellationToken ct)
    {
        string hash = ComputeHash(request);

        // Bước 1: key này đã từng xử lý chưa?
        var existing = await _db.IdempotencyRecords.AsNoTracking().FirstOrDefaultAsync(r => r.Key == key, ct);
        if (existing != null)
        {
            return Replay(existing, hash);
        }

        // Gộp các dòng trùng biến thể thành 1 dòng
        var lines = request.Items
            .GroupBy(i => i.VariantId)
            .Select(g => new OrderLineRequest { VariantId = g.Key, Quantity = g.Sum(x => x.Quantity) })
            .ToList();

        await using var tx = await _db.Database.BeginTransactionAsync(ct);
        try
        {
            var order = new Order
            {
                OrderNumber = "ORD-" + DateTime.UtcNow.ToString("yyyyMMdd") + "-" + Guid.NewGuid().ToString("N").Substring(0, 6).ToUpper(),
                CustomerName = request.CustomerName.Trim(),
                CustomerEmail = request.CustomerEmail.Trim()
            };

            foreach (var line in lines)
            {
                var variant = await _db.Variants.AsNoTracking()
                    .Include(v => v.Product)
                    .FirstOrDefaultAsync(v => v.Id == line.VariantId, ct);

                if (variant == null || !variant.IsActive || !variant.Product.IsActive)
                {
                    await tx.RollbackAsync(ct);
                    return Error(400, "Biến thể " + line.VariantId + " không tồn tại hoặc đã ngừng bán.");
                }

                // Bước 2: trừ kho nguyên tử. Chỉ trừ được khi Stock >= số lượng.
                int quantity = line.Quantity;
                int affectedRows = await _db.Variants
                    .Where(v => v.Id == variant.Id && v.Stock >= quantity)
                    .ExecuteUpdateAsync(s => s.SetProperty(v => v.Stock, v => v.Stock - quantity), ct);

                if (affectedRows == 0)
                {
                    await tx.RollbackAsync(ct);
                    return Error(409, "Không đủ tồn kho cho " + variant.Product.Name + " (" + variant.Sku + ").");
                }

                order.Items.Add(new OrderItem
                {
                    OrderId = order.Id,
                    VariantId = variant.Id,
                    ProductName = variant.Product.Name,
                    Sku = variant.Sku,
                    OptionValues = variant.OptionValues,
                    UnitPrice = variant.Price,
                    Quantity = quantity
                });
            }

            order.Total = order.Items.Sum(i => i.UnitPrice * i.Quantity);
            string json = JsonSerializer.Serialize(Mapper.ToOrderDto(order), JsonOptions);

            // Bước 3: ghi đơn + outbox + idempotency trong cùng transaction
            _db.Orders.Add(order);
            _db.Outbox.Add(new OutboxMessage { Type = "OrderCreated", Payload = json });
            _db.IdempotencyRecords.Add(new IdempotencyRecord
            {
                Key = key,
                RequestHash = hash,
                StatusCode = 201,
                ResponseJson = json
            });

            await _db.SaveChangesAsync(ct);
            await tx.CommitAsync(ct);
            return new OrderResult(201, json);
        }
        catch (DbUpdateException ex)
        {
            // Hai request cùng key chạy song song: request đến sau vi phạm khoá chính của IdempotencyRecord.
            await tx.RollbackAsync(ct);
            _db.ChangeTracker.Clear();

            var winner = await _db.IdempotencyRecords.AsNoTracking().FirstOrDefaultAsync(r => r.Key == key, ct);
            if (winner != null)
            {
                return Replay(winner, hash);
            }

            _logger.LogError(ex, "Tạo đơn thất bại");
            throw;
        }
    }

    public async Task<OrderResult> ConfirmAsync(Guid id, CancellationToken ct)
    {
        var order = await _db.Orders.Include(o => o.Items).FirstOrDefaultAsync(o => o.Id == id, ct);
        if (order == null) return Error(404, "Không tìm thấy đơn.");

        if (order.Status == OrderStatus.Confirmed) return Success(order); // gọi lại không sao (idempotent)
        if (order.Status != OrderStatus.Pending) return Error(409, "Không thể xác nhận đơn ở trạng thái " + order.Status + ".");

        order.Status = OrderStatus.Confirmed;
        _db.Outbox.Add(new OutboxMessage
        {
            Type = "OrderConfirmed",
            Payload = JsonSerializer.Serialize(Mapper.ToOrderDto(order), JsonOptions)
        });
        await _db.SaveChangesAsync(ct);
        return Success(order);
    }

    public async Task<OrderResult> CancelAsync(Guid id, CancellationToken ct)
    {
        await using var tx = await _db.Database.BeginTransactionAsync(ct);

        var order = await _db.Orders.Include(o => o.Items).FirstOrDefaultAsync(o => o.Id == id, ct);
        if (order == null) return Error(404, "Không tìm thấy đơn.");
        if (order.Status == OrderStatus.Cancelled) return Success(order); // idempotent

        // Hoàn kho
        foreach (var item in order.Items)
        {
            int quantity = item.Quantity;
            Guid variantId = item.VariantId;
            await _db.Variants
                .Where(v => v.Id == variantId)
                .ExecuteUpdateAsync(s => s.SetProperty(v => v.Stock, v => v.Stock + quantity), ct);
        }

        order.Status = OrderStatus.Cancelled;
        _db.Outbox.Add(new OutboxMessage
        {
            Type = "OrderCancelled",
            Payload = JsonSerializer.Serialize(Mapper.ToOrderDto(order), JsonOptions)
        });

        await _db.SaveChangesAsync(ct);
        await tx.CommitAsync(ct);
        return Success(order);
    }

    // ===== Hàm phụ =====

    private OrderResult Error(int statusCode, string message)
    {
        string json = JsonSerializer.Serialize(new { error = message }, JsonOptions);
        return new OrderResult(statusCode, json);
    }

    private OrderResult Success(Order order)
    {
        return new OrderResult(200, JsonSerializer.Serialize(Mapper.ToOrderDto(order), JsonOptions));
    }

    private OrderResult Replay(IdempotencyRecord record, string hash)
    {
        if (record.RequestHash != hash)
        {
            return Error(422, "Idempotency-Key này đã được dùng cho một request khác nội dung.");
        }
        return new OrderResult(record.StatusCode, record.ResponseJson, replayed: true);
    }

    // Băm nội dung request để phát hiện "cùng key nhưng khác nội dung"
    private static string ComputeHash(CreateOrderRequest request)
    {
        var canonical = JsonSerializer.Serialize(new
        {
            name = request.CustomerName.Trim(),
            email = request.CustomerEmail.Trim().ToLowerInvariant(),
            items = request.Items.OrderBy(i => i.VariantId).Select(i => new { i.VariantId, i.Quantity }).ToList()
        });
        byte[] bytes = SHA256.HashData(Encoding.UTF8.GetBytes(canonical));
        return Convert.ToHexString(bytes);
    }
}
