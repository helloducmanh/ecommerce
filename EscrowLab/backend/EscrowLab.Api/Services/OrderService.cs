using EscrowLab.Api.Data;
using EscrowLab.Api.Domain;
using Microsoft.EntityFrameworkCore;

namespace EscrowLab.Api.Services;

public class OrderService
{
    private static readonly User SystemUser = new() { Id = Guid.Empty, Name = "system", Role = "system" };

    private readonly AppDbContext _db;
    private readonly LedgerService _ledger;
    private readonly EscrowOptions _opt;

    public OrderService(AppDbContext db, LedgerService ledger, EscrowOptions opt)
    {
        _db = db;
        _ledger = ledger;
        _opt = opt;
    }

    // ---------- Đọc ----------
    public async Task<List<OrderDto>> ListAsync(User actor)
    {
        var query = _db.Orders.AsNoTracking().AsQueryable();
        if (actor.Role != "admin") query = query.Where(o => o.BuyerId == actor.Id || o.SellerId == actor.Id);

        return await (from o in query
                      join b in _db.Users on o.BuyerId equals b.Id
                      join s in _db.Users on o.SellerId equals s.Id
                      orderby o.CreatedAt descending
                      select new OrderDto
                      {
                          Id = o.Id, BuyerId = o.BuyerId, SellerId = o.SellerId, Title = o.Title,
                          Amount = o.Amount, Fee = o.Fee, Status = o.Status,
                          CreatedAt = o.CreatedAt, ExpiresAt = o.ExpiresAt, UpdatedAt = o.UpdatedAt, ShippedAt = o.ShippedAt,
                          BuyerName = b.Name, SellerName = s.Name,
                      }).Take(200).ToListAsync();
    }

    public async Task<(OrderDto Order, List<OrderEventDto> Events)> GetAsync(Guid id, User actor)
    {
        var order = await (from o in _db.Orders.AsNoTracking()
                           where o.Id == id
                           join b in _db.Users on o.BuyerId equals b.Id
                           join s in _db.Users on o.SellerId equals s.Id
                           select new OrderDto
                           {
                               Id = o.Id, BuyerId = o.BuyerId, SellerId = o.SellerId, Title = o.Title,
                               Amount = o.Amount, Fee = o.Fee, Status = o.Status,
                               CreatedAt = o.CreatedAt, ExpiresAt = o.ExpiresAt, UpdatedAt = o.UpdatedAt, ShippedAt = o.ShippedAt,
                               BuyerName = b.Name, SellerName = s.Name,
                           }).FirstOrDefaultAsync()
                    ?? throw new DomainException("NOT_FOUND", "Không tìm thấy đơn hàng", 404);

        if (actor.Role != "admin" && actor.Id != order.BuyerId && actor.Id != order.SellerId)
            throw new DomainException("FORBIDDEN", "Bạn không có quyền xem đơn này", 403);

        var events = await (from e in _db.OrderEvents.AsNoTracking()
                            where e.OrderId == id
                            from u in _db.Users.Where(usr => usr.Id == e.ActorId).DefaultIfEmpty()
                            select new OrderEventDto
                            {
                                Id = e.Id, OrderId = e.OrderId, FromStatus = e.FromStatus, ToStatus = e.ToStatus,
                                ActorId = e.ActorId, ActorName = u != null ? u.Name : null, Note = e.Note, CreatedAt = e.CreatedAt,
                            }).ToListAsync();
        return (order, events.OrderBy(e => e.Id).ToList());
    }

    // ---------- Tạo đơn ----------
    public async Task<OrderDto> CreateAsync(User actor, Guid sellerId, string title, long amount, CancellationToken ct = default)
    {
        if (actor.Role != "buyer") throw new DomainException("FORBIDDEN", "Chỉ người mua mới tạo được đơn", 403);
        if (string.IsNullOrWhiteSpace(title)) throw new DomainException("INVALID_TITLE", "Thiếu tiêu đề đơn hàng");
        if (amount < 10_000 || amount > 500_000_000)
            throw new DomainException("INVALID_AMOUNT", "Số tiền phải từ 10.000 đến 500.000.000");

        return await _db.InTransactionAsync(async () =>
        {
            var seller = await _db.Users.AsNoTracking().FirstOrDefaultAsync(u => u.Id == sellerId, ct);
            if (seller is null || seller.Role != "seller")
                throw new DomainException("INVALID_SELLER", "Người bán không hợp lệ");

            var now = DateTime.UtcNow;
            var order = new Order
            {
                Id = Guid.NewGuid(),
                BuyerId = actor.Id,
                SellerId = sellerId,
                Title = title.Trim(),
                Amount = amount,
                Fee = Fees.Calculate(amount, _opt.FeeBps),
                Status = OrderStatus.Created,
                CreatedAt = now,
                UpdatedAt = now,
                ExpiresAt = now.AddMinutes(_opt.OrderExpiryMinutes),
            };
            _db.Orders.Add(order);
            AddEvent(order.Id, null, OrderStatus.Created, actor, "Tạo đơn hàng");
            await _db.SaveChangesAsync(ct);
            return Mapper.ToOrderDto(order, actor.Name, seller.Name);
        }, ct);
    }

    // ---------- Các hành động (mỗi hành động = 1 lần chuyển trạng thái) ----------
    public Task<OrderDto> PayAsync(Guid id, User actor) =>
        TransitionAsync(id, actor, OrderStatus.Held, "Người mua thanh toán, tiền vào escrow",
            (o, u) =>
            {
                if (o.ExpiresAt < DateTime.UtcNow) throw new DomainException("ORDER_EXPIRED", "Đơn đã hết hạn thanh toán", 409);
                return u.Id == o.BuyerId;
            },
            o => ("ESCROW_HOLD", new List<PostingLeg>
            {
                new(Codes.Wallet(o.BuyerId), -o.Amount),
                new(Codes.Escrow, o.Amount),
            }));

    public Task<OrderDto> ShipAsync(Guid id, User actor) =>
        TransitionAsync(id, actor, OrderStatus.Shipped, "Người bán đã giao hàng", (o, u) => u.Id == o.SellerId, null);

    public Task<OrderDto> ConfirmAsync(Guid id, User actor) =>
        TransitionAsync(id, actor, OrderStatus.Released, "Người mua xác nhận, giải ngân cho người bán",
            (o, u) => u.Id == o.BuyerId, ReleasePosting);

    public Task<OrderDto> CancelAsync(Guid id, User actor) =>
        TransitionAsync(id, actor, OrderStatus.Cancelled, "Người mua huỷ đơn", (o, u) => u.Id == o.BuyerId, null);

    public Task<OrderDto> DisputeAsync(Guid id, User actor) =>
        TransitionAsync(id, actor, OrderStatus.Disputed, "Người mua mở tranh chấp", (o, u) => u.Id == o.BuyerId, null);

    public Task<OrderDto> SellerRefundAsync(Guid id, User actor) =>
        TransitionAsync(id, actor, OrderStatus.Refunded, "Người bán đồng ý hoàn tiền",
            (o, u) => u.Id == o.SellerId, RefundPosting);

    public Task<OrderDto> ResolveAsync(Guid id, User actor, string decision) => decision switch
    {
        "release" => TransitionAsync(id, actor, OrderStatus.Released, "Admin phán quyết: trả tiền cho người bán",
            (o, u) => u.Role == "admin", ReleasePosting),
        "refund" => TransitionAsync(id, actor, OrderStatus.Refunded, "Admin phán quyết: hoàn tiền cho người mua",
            (o, u) => u.Role == "admin", RefundPosting),
        _ => throw new DomainException("INVALID_DECISION", "decision phải là 'release' hoặc 'refund'"),
    };

    // ---------- Việc nền ----------
    public async Task<(int Expired, int AutoReleased)> RunMaintenanceAsync()
    {
        var now = DateTime.UtcNow;
        var releaseBefore = now.AddDays(-_opt.AutoReleaseDays);

        var expiredIds = await _db.Orders.AsNoTracking()
            .Where(o => o.Status == OrderStatus.Created && o.ExpiresAt < now)
            .Select(o => o.Id).Take(100).ToListAsync();
        var releaseIds = await _db.Orders.AsNoTracking()
            .Where(o => o.Status == OrderStatus.Shipped && o.ShippedAt < releaseBefore)
            .Select(o => o.Id).Take(100).ToListAsync();

        int expired = 0, released = 0;
        foreach (var id in expiredIds)
        {
            try
            {
                await TransitionAsync(id, SystemUser, OrderStatus.Expired, "Hết hạn thanh toán", (o, u) => u.Role == "system", null);
                expired++;
            }
            catch (DomainException) { /* đơn đã đổi trạng thái do người khác thao tác: bỏ qua */ }
        }
        foreach (var id in releaseIds)
        {
            try
            {
                await TransitionAsync(id, SystemUser, OrderStatus.Released, "Tự động giải ngân sau thời gian chờ",
                    (o, u) => u.Role == "system", ReleasePosting);
                released++;
            }
            catch (DomainException) { }
        }
        return (expired, released);
    }

    // ---------- Nội bộ ----------
    private static (string, List<PostingLeg>) ReleasePosting(Order o) => ("ESCROW_RELEASE", new List<PostingLeg>
    {
        new(Codes.Escrow, -o.Amount),
        new(Codes.Wallet(o.SellerId), o.Amount - o.Fee),
        new(Codes.FeeRevenue, o.Fee),
    });

    private static (string, List<PostingLeg>) RefundPosting(Order o) => ("ESCROW_REFUND", new List<PostingLeg>
    {
        new(Codes.Escrow, -o.Amount),
        new(Codes.Wallet(o.BuyerId), o.Amount),
    });

    /// <summary>
    /// Khuôn mẫu của mọi thao tác: khoá dòng đơn -> kiểm tra quyền -> kiểm tra state machine
    /// -> ghi sổ (nếu có tiền di chuyển) -> cập nhật trạng thái -> ghi audit. Tất cả trong 1 transaction.
    /// </summary>
    private Task<OrderDto> TransitionAsync(
        Guid orderId, User actor, string to, string note,
        Func<Order, User, bool> authorize,
        Func<Order, (string Type, List<PostingLeg> Legs)>? posting)
    {
        return _db.InTransactionAsync(async () =>
        {
            // Khoá dòng đơn rồi mới đọc, để chắc chắn đọc trạng thái mới nhất.
            await _db.Database.ExecuteSqlAsync($"SELECT 1 FROM orders WHERE id = {orderId} FOR UPDATE");
            var order = await _db.Orders.FirstOrDefaultAsync(o => o.Id == orderId)
                ?? throw new DomainException("NOT_FOUND", "Không tìm thấy đơn hàng", 404);

            if (!authorize(order, actor))
                throw new DomainException("FORBIDDEN", "Bạn không có quyền thực hiện thao tác này", 403);

            var from = order.Status;
            OrderStateMachine.EnsureTransition(from, to);

            if (posting is not null)
            {
                var (type, legs) = posting(order);
                await _ledger.PostAsync($"order:{order.Id}:{to.ToLowerInvariant()}", type, "order", order.Id, note, legs);
            }

            var now = DateTime.UtcNow;
            order.Status = to;
            order.UpdatedAt = now;
            if (to == OrderStatus.Shipped) order.ShippedAt = now;
            AddEvent(order.Id, from, to, actor, note);
            await _db.SaveChangesAsync();

            return Mapper.ToOrderDto(order);
        });
    }

    private void AddEvent(Guid orderId, string? fromStatus, string to, User actor, string note) =>
        _db.OrderEvents.Add(new OrderEvent
        {
            OrderId = orderId, FromStatus = fromStatus, ToStatus = to,
            ActorId = actor.Id == Guid.Empty ? null : actor.Id, Note = note,
        });
}
