using EscrowLab.Api.Data;
using EscrowLab.Api.Domain;
using Microsoft.EntityFrameworkCore;

namespace EscrowLab.Api.Services;

/// <summary>Rút tiền 2 pha: khoá tiền vào WITHDRAWAL_HOLD, sau đó admin hoàn tất hoặc từ chối.</summary>
public class WithdrawalService
{
    private readonly AppDbContext _db;
    private readonly LedgerService _ledger;

    public WithdrawalService(AppDbContext db, LedgerService ledger)
    {
        _db = db;
        _ledger = ledger;
    }

    public async Task<List<WithdrawalDto>> ListAsync(User actor)
    {
        var query = _db.Withdrawals.AsNoTracking().AsQueryable();
        if (actor.Role != "admin") query = query.Where(w => w.UserId == actor.Id);

        var rows = await (from w in query
                          join u in _db.Users on w.UserId equals u.Id
                          orderby w.CreatedAt descending
                          select new { Withdrawal = w, UserName = u.Name }).Take(100).ToListAsync();
        return rows.Select(r => Mapper.ToWithdrawalDto(r.Withdrawal, r.UserName)).ToList();
    }

    public async Task<WithdrawalDto> RequestAsync(User actor, long amount, string bankAccount)
    {
        if (actor.Role == "admin") throw new DomainException("FORBIDDEN", "Admin không có ví", 403);
        if (amount < 10_000) throw new DomainException("INVALID_AMOUNT", "Số tiền rút tối thiểu 10.000");
        if (string.IsNullOrWhiteSpace(bankAccount)) throw new DomainException("INVALID_BANK", "Thiếu số tài khoản nhận");

        return await _db.InTransactionAsync(async () =>
        {
            var w = new Withdrawal
            {
                Id = Guid.NewGuid(), UserId = actor.Id, Amount = amount, BankAccount = bankAccount.Trim(),
                Status = "PENDING", CreatedAt = DateTime.UtcNow, UpdatedAt = DateTime.UtcNow,
            };
            _db.Withdrawals.Add(w);
            await _db.SaveChangesAsync();
            // Ném INSUFFICIENT_FUNDS nếu ví không đủ -> rollback luôn cả dòng withdrawals ở trên.
            await _ledger.PostAsync($"withdrawal:{w.Id}:hold", "WITHDRAWAL_HOLD", "withdrawal", w.Id,
                "Khoá tiền chờ rút",
                new[] { new PostingLeg(Codes.Wallet(actor.Id), -amount), new PostingLeg(Codes.WithdrawalHold, amount) });
            return Mapper.ToWithdrawalDto(w, actor.Name);
        });
    }

    public Task<WithdrawalDto> CompleteAsync(Guid id, User actor) => FinishAsync(id, actor, true);
    public Task<WithdrawalDto> RejectAsync(Guid id, User actor) => FinishAsync(id, actor, false);

    private async Task<WithdrawalDto> FinishAsync(Guid id, User actor, bool complete)
    {
        if (actor.Role != "admin") throw new DomainException("FORBIDDEN", "Chỉ admin được xử lý rút tiền", 403);
        return await _db.InTransactionAsync(async () =>
        {
            await _db.Database.ExecuteSqlAsync($"SELECT 1 FROM withdrawals WHERE id = {id} FOR UPDATE");
            var w = await _db.Withdrawals.FirstOrDefaultAsync(x => x.Id == id)
                ?? throw new DomainException("NOT_FOUND", "Không tìm thấy yêu cầu rút tiền", 404);
            if (w.Status != "PENDING")
                throw new DomainException("INVALID_TRANSITION", $"Yêu cầu đang ở trạng thái {w.Status}", 409);

            var legs = complete
                ? new[] { new PostingLeg(Codes.WithdrawalHold, -w.Amount), new PostingLeg(Codes.ExternalBank, w.Amount) }
                : new[] { new PostingLeg(Codes.WithdrawalHold, -w.Amount), new PostingLeg(Codes.Wallet(w.UserId), w.Amount) };
            await _ledger.PostAsync($"withdrawal:{w.Id}:{(complete ? "complete" : "reject")}",
                complete ? "WITHDRAWAL_COMPLETE" : "WITHDRAWAL_REJECT", "withdrawal", w.Id,
                complete ? "Đã chuyển tiền ra ngân hàng" : "Từ chối, hoàn tiền về ví", legs);

            w.Status = complete ? "COMPLETED" : "REJECTED";
            w.UpdatedAt = DateTime.UtcNow;
            await _db.SaveChangesAsync();
            return Mapper.ToWithdrawalDto(w);
        });
    }
}
