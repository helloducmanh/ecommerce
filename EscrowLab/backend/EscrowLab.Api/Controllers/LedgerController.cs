using EscrowLab.Api.Data;
using EscrowLab.Api.Domain;
using EscrowLab.Api.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace EscrowLab.Api.Controllers;

[ApiController]
public class LedgerController : EscrowControllerBase
{
    public LedgerController(AppDbContext db) : base(db) { }

    // GET /api/ledger?accountCode=&limit=
    [HttpGet("api/ledger")]
    public async Task<ActionResult<List<LedgerEntryView>>> GetEntries(string? accountCode, int? limit)
    {
        var u = await ActorAsync();
        // Người dùng thường chỉ xem được sổ của ví mình.
        var code = u.Role == "admin" ? accountCode : Codes.Wallet(u.Id);

        var query = from e in Db.LedgerEntries.AsNoTracking()
                    join t in Db.LedgerTransactions on e.TransactionId equals t.Id
                    join a in Db.Accounts on e.AccountId equals a.Id
                    select new { e, t, a };
        if (code is not null) query = query.Where(x => x.a.Code == code);

        return await query
            .OrderByDescending(x => x.e.Id)
            .Take(Math.Clamp(limit ?? 100, 1, 500))
            .Select(x => new LedgerEntryView
            {
                Id = x.e.Id, TransactionId = x.e.TransactionId, Type = x.t.Type, Description = x.t.Description,
                AccountCode = x.a.Code, Amount = x.e.Amount, BalanceAfter = x.e.BalanceAfter, CreatedAt = x.e.CreatedAt,
            })
            .ToListAsync();
    }

    // GET /api/admin/accounts  (chỉ admin)
    [HttpGet("api/admin/accounts")]
    public async Task<ActionResult<List<AccountView>>> GetAccounts()
    {
        await AdminActorAsync();
        return await (from a in Db.Accounts.AsNoTracking()
                      from u in Db.Users.Where(usr => usr.Id == a.OwnerId).DefaultIfEmpty()
                      orderby a.Type, a.Code
                      select new AccountView
                      {
                          Id = a.Id, Code = a.Code, Type = a.Type, Balance = a.Balance,
                          AllowNegative = a.AllowNegative, OwnerName = u != null ? u.Name : null,
                      }).ToListAsync();
    }
}
