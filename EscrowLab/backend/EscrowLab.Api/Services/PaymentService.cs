using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using EscrowLab.Api.Data;
using EscrowLab.Api.Domain;
using Microsoft.EntityFrameworkCore;

namespace EscrowLab.Api.Services;

public class GatewayEvent
{
    [JsonPropertyName("event_id")] public string EventId { get; set; } = "";
    [JsonPropertyName("type")] public string Type { get; set; } = "";
    [JsonPropertyName("provider_ref")] public string ProviderRef { get; set; } = "";
    [JsonPropertyName("amount")] public long Amount { get; set; }
}

public record SimulateRequest(string ProviderRef, string Outcome, bool SendTwice, bool BadSignature, long? AmountOverride);

/// <summary>Nạp tiền qua gateway (giả lập) và xử lý webhook an toàn.</summary>
public class PaymentService
{
    private readonly AppDbContext _db;
    private readonly LedgerService _ledger;
    private readonly EscrowOptions _opt;

    public PaymentService(AppDbContext db, LedgerService ledger, EscrowOptions opt)
    {
        _db = db;
        _ledger = ledger;
        _opt = opt;
    }

    public async Task<PaymentIntentDto> CreateDepositAsync(User actor, long amount)
    {
        if (actor.Role == "admin") throw new DomainException("FORBIDDEN", "Admin không có ví", 403);
        if (amount < 10_000 || amount > 500_000_000)
            throw new DomainException("INVALID_AMOUNT", "Số tiền nạp phải từ 10.000 đến 500.000.000");

        var intent = new PaymentIntent
        {
            Id = Guid.NewGuid(),
            UserId = actor.Id,
            Amount = amount,
            Status = "PENDING",
            ProviderRef = "EL" + Guid.NewGuid().ToString("N")[..12].ToUpperInvariant(),   // gọn, không có dấu gạch, ngân hàng khó làm hỏng
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow,
        };
        _db.PaymentIntents.Add(intent);
        await _db.SaveChangesAsync();
        return WithTransfer(Mapper.ToPaymentIntentDto(intent, actor.Name));
    }

    public async Task<List<PaymentIntentDto>> ListAsync(User actor)
    {
        var query = _db.PaymentIntents.AsNoTracking().AsQueryable();
        if (actor.Role != "admin") query = query.Where(p => p.UserId == actor.Id);

        var rows = await (from p in query
                          join u in _db.Users on p.UserId equals u.Id
                          orderby p.CreatedAt descending
                          select new { Intent = p, UserName = u.Name }).Take(100).ToListAsync();
        return rows.Select(r => WithTransfer(Mapper.ToPaymentIntentDto(r.Intent, r.UserName))).ToList();
    }

    /// <summary>
    /// Xử lý webhook: 1) xác thực chữ ký + timestamp  2) chống trùng bằng event_id
    /// 3) khoá intent  4) ghi sổ idempotent  -- tất cả trong một transaction.
    /// </summary>
    public async Task<string> HandleWebhookAsync(string body, string? signatureHeader)
    {
        if (!WebhookSigner.Verify(_opt.WebhookSecret, signatureHeader, body, DateTimeOffset.UtcNow,
                TimeSpan.FromSeconds(_opt.WebhookToleranceSeconds), out var reason))
            throw new DomainException("INVALID_SIGNATURE", reason, 401);

        GatewayEvent evt;
        try
        {
            evt = JsonSerializer.Deserialize<GatewayEvent>(body) ?? throw new JsonException("empty");
        }
        catch (JsonException)
        {
            throw new DomainException("INVALID_PAYLOAD", "Payload không phải JSON hợp lệ");
        }
        if (string.IsNullOrWhiteSpace(evt.EventId) || string.IsNullOrWhiteSpace(evt.ProviderRef))
            throw new DomainException("INVALID_PAYLOAD", "Thiếu event_id hoặc provider_ref");

        return await _db.InTransactionAsync(async () =>
        {
            if (!await TryRecordEventAsync(evt.EventId, body)) return "duplicate";

            var intent = await LockIntentAsync(evt.ProviderRef)
                ?? throw new DomainException("UNKNOWN_REF", "Không có giao dịch nạp tiền tương ứng", 404);

            if (intent.Status != "PENDING") return "ignored";   // đã ở trạng thái cuối

            if (evt.Type == "payment.failed")
            {
                await SetStatusAsync(intent, "FAILED");
                return "processed";
            }
            if (evt.Type != "payment.succeeded") return "ignored";

            if (evt.Amount != intent.Amount)
            {
                // Số tiền gateway báo khác số tiền ta tạo: không tự cộng tiền, chuyển sang xem xét thủ công.
                await SetStatusAsync(intent, "REVIEW");
                return "needs_review";
            }

            await PostDepositAsync(intent, $"Nạp tiền {intent.ProviderRef}");
            return "processed";
        });
    }

    /// <summary>
    /// Webhook SePay: 1) kiểm tra API key  2) chỉ nhận tiền vào  3) chống trùng theo id giao dịch
    /// 4) khớp mã nạp tiền  5) đối chiếu số tiền  6) ghi sổ idempotent. Tất cả trong một transaction.
    /// </summary>
    public async Task<string> HandleSePayAsync(string body, string? authorizationHeader)
    {
        var expected = Encoding.UTF8.GetBytes($"Apikey {_opt.SePayApiKey}");
        var actual = Encoding.UTF8.GetBytes(authorizationHeader ?? "");
        if (string.IsNullOrEmpty(_opt.SePayApiKey) || !CryptographicOperations.FixedTimeEquals(expected, actual))
            throw new DomainException("UNAUTHORIZED", "API key không hợp lệ", 401);

        SePayWebhook evt;
        try { evt = JsonSerializer.Deserialize<SePayWebhook>(body) ?? throw new JsonException("empty"); }
        catch (JsonException) { throw new DomainException("INVALID_PAYLOAD", "Payload không hợp lệ"); }

        if (evt.TransferType != "in") return "ignored";

        var providerRef = SePayHelper.ExtractRef(evt.Code, evt.Content);

        return await _db.InTransactionAsync(async () =>
        {
            if (!await TryRecordEventAsync($"sepay:{evt.Id}", body)) return "duplicate";

            if (providerRef is null) return "unmatched";   // tiền đã về nhưng không có mã: cần xử lý tay

            var intent = await LockIntentAsync(providerRef);
            if (intent is null) return "unmatched";
            if (intent.Status != "PENDING") return "ignored";

            if (evt.TransferAmount != intent.Amount)
            {
                await SetStatusAsync(intent, "REVIEW");   // lệch số tiền: không tự cộng
                return "needs_review";
            }

            await PostDepositAsync(intent, $"Nạp tiền qua SePay {evt.ReferenceCode}");
            return "processed";
        });
    }

    // ---------- Chỉ dùng khi học: giả lập gateway / SePay gọi webhook ----------
    public async Task<List<object>> SimulateSePayAsync(SePaySimulateRequest r)
    {
        var intent = await _db.PaymentIntents.AsNoTracking().FirstOrDefaultAsync(i => i.ProviderRef == r.ProviderRef)
            ?? throw new DomainException("NOT_FOUND", "Không tìm thấy mã nạp tiền", 404);

        var payload = new
        {
            id = Random.Shared.NextInt64(100_000, 999_999_999),
            gateway = _opt.BankName,
            transactionDate = DateTime.UtcNow.ToString("yyyy-MM-dd HH:mm:ss"),
            accountNumber = _opt.BankAccountNumber,
            code = (string?)null,
            content = $"{intent.ProviderRef} nap tien",
            transferType = "in",
            transferAmount = r.AmountOverride ?? intent.Amount,
            accumulated = 0,
            subAccount = (string?)null,
            referenceCode = "FT" + Guid.NewGuid().ToString("N")[..10].ToUpperInvariant(),
            description = "",
        };
        var body = JsonSerializer.Serialize(payload);
        var header = $"Apikey {_opt.SePayApiKey}";

        var results = new List<object>();
        for (var i = 1; i <= (r.SendTwice ? 2 : 1); i++)
        {
            try { results.Add(new { attempt = i, status = await HandleSePayAsync(body, header) }); }
            catch (DomainException ex) { results.Add(new { attempt = i, status = "rejected", code = ex.Code, message = ex.Message }); }
        }
        return results;
    }

    public async Task<List<object>> SimulateAsync(SimulateRequest r)
    {
        var intent = await _db.PaymentIntents.AsNoTracking().FirstOrDefaultAsync(i => i.ProviderRef == r.ProviderRef)
            ?? throw new DomainException("NOT_FOUND", "Không tìm thấy provider_ref", 404);

        var payload = new
        {
            event_id = $"evt_{Guid.NewGuid():N}",
            type = r.Outcome == "failed" ? "payment.failed" : "payment.succeeded",
            provider_ref = intent.ProviderRef,
            amount = r.AmountOverride ?? intent.Amount,
        };
        var body = JsonSerializer.Serialize(payload);
        var ts = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        var header = WebhookSigner.BuildHeader(r.BadSignature ? "wrong_secret" : _opt.WebhookSecret, ts, body);

        var results = new List<object>();
        for (var i = 1; i <= (r.SendTwice ? 2 : 1); i++)
        {
            try
            {
                results.Add(new { attempt = i, status = await HandleWebhookAsync(body, header) });
            }
            catch (DomainException ex)
            {
                results.Add(new { attempt = i, status = "rejected", code = ex.Code, message = ex.Message });
            }
        }
        return results;
    }

    // ---------- Nội bộ ----------

    /// <summary>Ghi nhận event_id; false nếu đã xử lý trước đó. SQL thô vì cần ON CONFLICT DO NOTHING.</summary>
    private async Task<bool> TryRecordEventAsync(string eventId, string body) =>
        await _db.Database.ExecuteSqlAsync(
            $@"INSERT INTO webhook_events (event_id, payload) VALUES ({eventId}, {body})
               ON CONFLICT (event_id) DO NOTHING") > 0;

    /// <summary>Khoá dòng intent rồi mới đọc (tracked) để sửa trạng thái an toàn.</summary>
    private async Task<PaymentIntent?> LockIntentAsync(string providerRef)
    {
        await _db.Database.ExecuteSqlAsync($"SELECT 1 FROM payment_intents WHERE provider_ref = {providerRef} FOR UPDATE");
        return await _db.PaymentIntents.FirstOrDefaultAsync(i => i.ProviderRef == providerRef);
    }

    private async Task PostDepositAsync(PaymentIntent intent, string description)
    {
        await _ledger.PostAsync($"deposit:{intent.Id}", "DEPOSIT", "payment_intent", intent.Id, description,
            new[]
            {
                new PostingLeg(Codes.ExternalBank, -intent.Amount),
                new PostingLeg(Codes.Wallet(intent.UserId), intent.Amount),
            });
        await SetStatusAsync(intent, "SUCCEEDED");
    }

    private async Task SetStatusAsync(PaymentIntent intent, string status)
    {
        intent.Status = status;
        intent.UpdatedAt = DateTime.UtcNow;
        await _db.SaveChangesAsync();
    }

    private PaymentIntentDto WithTransfer(PaymentIntentDto i)
    {
        if (i.Status == "PENDING" && !string.IsNullOrWhiteSpace(_opt.BankAccountNumber))
        {
            i.Transfer = new TransferInfo
            {
                BankName = _opt.BankName,
                AccountNumber = _opt.BankAccountNumber,
                AccountHolder = _opt.AccountHolder,
                Amount = i.Amount,
                Content = i.ProviderRef,
                QrUrl = SePayHelper.BuildQrUrl(_opt, i.Amount, i.ProviderRef),
            };
        }
        return i;
    }
}
