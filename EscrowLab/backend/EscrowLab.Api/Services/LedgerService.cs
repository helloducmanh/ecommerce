using EscrowLab.Api.Data;
using EscrowLab.Api.Domain;
using Microsoft.EntityFrameworkCore;

namespace EscrowLab.Api.Services;

public record PostingLeg(string AccountCode, long Amount);
public record PostingResult(Guid TransactionId, bool Replayed);

/// <summary>
/// Lõi của hệ thống thanh toán: ghi sổ kép.
/// 1) Idempotency key  -> gọi lại cùng một thao tác không ghi sổ hai lần.
/// 2) Khoá account theo thứ tự id -> tránh deadlock khi nhiều giao dịch giao nhau.
/// 3) Kiểm tra số dư dưới khoá -> không thể rút quá số dư dù chạy song song.
/// 4) Tổng các dòng = 0 -> tiền không tự sinh ra hay biến mất.
/// Hàm này PHẢI chạy trong <see cref="TransactionExtensions.InTransactionAsync{T}"/> của người gọi,
/// để cập nhật đơn hàng và ghi sổ cùng commit.
/// </summary>
public class LedgerService
{
    private readonly AppDbContext _db;

    public LedgerService(AppDbContext db) => _db = db;

    public async Task<PostingResult> PostAsync(
        string idempotencyKey, string type, string? referenceType, Guid? referenceId, string? description,
        IEnumerable<PostingLeg> legs, CancellationToken ct = default)
    {
        var merged = legs.GroupBy(l => l.AccountCode)
            .Select(g => new PostingLeg(g.Key, g.Sum(x => x.Amount)))
            .Where(l => l.Amount != 0)
            .ToList();

        if (merged.Count < 2)
            throw new DomainException("INVALID_POSTING", "Một bút toán cần ít nhất hai dòng");
        if (merged.Sum(l => l.Amount) != 0)
            throw new DomainException("UNBALANCED", "Tổng các dòng của bút toán phải bằng 0");

        // Chèn giao dịch; trùng idempotency_key thì không làm gì (0 dòng bị ảnh hưởng).
        // Dùng SQL thô vì ON CONFLICT DO NOTHING không có tương đương trong EF, và một lỗi unique sẽ làm hỏng cả transaction.
        var txId = Guid.NewGuid();
        var inserted = await _db.Database.ExecuteSqlAsync(
            $@"INSERT INTO ledger_transactions (id, idempotency_key, type, reference_type, reference_id, description)
               VALUES ({txId}, {idempotencyKey}, {type}, {referenceType}, {referenceId}, {description})
               ON CONFLICT (idempotency_key) DO NOTHING", ct);

        if (inserted == 0)
        {
            // Đã ghi trước đó: trả lại kết quả cũ, không làm gì thêm.
            var existing = await _db.LedgerTransactions.AsNoTracking()
                .Where(t => t.IdempotencyKey == idempotencyKey)
                .Select(t => t.Id)
                .SingleAsync(ct);
            return new PostingResult(existing, true);
        }

        // Khoá các account theo thứ tự id. Đây là câu lệnh then chốt chống deadlock nên giữ nguyên dạng SQL.
        var codes = merged.Select(l => l.AccountCode).ToArray();
        await _db.Database.ExecuteSqlAsync(
            $"SELECT 1 FROM accounts WHERE code = ANY({codes}) ORDER BY id FOR UPDATE", ct);

        // Đọc SAU khi đã khoá để có số dư mới nhất (AsNoTracking: không lấy nhầm bản cũ trong bộ nhớ).
        var accounts = (await _db.Accounts.AsNoTracking()
            .Where(a => codes.Contains(a.Code))
            .ToListAsync(ct)).ToDictionary(a => a.Code);

        foreach (var leg in merged)
        {
            if (!accounts.TryGetValue(leg.AccountCode, out var acc))
                throw new DomainException("ACCOUNT_NOT_FOUND", $"Không có tài khoản {leg.AccountCode}", 404);

            var newBalance = acc.Balance + leg.Amount;
            if (!acc.AllowNegative && newBalance < 0)
                throw new DomainException("INSUFFICIENT_FUNDS", "Số dư không đủ", 409);

            // Cộng ngay trong DB (CHECK balance_non_negative là lớp bảo vệ cuối cùng).
            await _db.Database.ExecuteSqlAsync(
                $"UPDATE accounts SET balance = balance + {leg.Amount} WHERE id = {acc.Id}", ct);

            _db.LedgerEntries.Add(new LedgerEntry
            {
                TransactionId = txId, AccountId = acc.Id, Amount = leg.Amount, BalanceAfter = newBalance,
            });
        }
        await _db.SaveChangesAsync(ct);

        return new PostingResult(txId, false);
    }
}
