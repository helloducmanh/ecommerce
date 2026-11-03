using EscrowLab.Api.Data;
using EscrowLab.Api.Services;
using Microsoft.AspNetCore.Mvc;

namespace EscrowLab.Api.Controllers;

[ApiController]
[Route("api/withdrawals")]
public class WithdrawalsController : EscrowControllerBase
{
    private readonly WithdrawalService _withdrawals;

    public WithdrawalsController(AppDbContext db, WithdrawalService withdrawals) : base(db) => _withdrawals = withdrawals;

    // GET /api/withdrawals
    [HttpGet]
    public async Task<IActionResult> GetAll() => Ok(await _withdrawals.ListAsync(await ActorAsync()));

    // POST /api/withdrawals  { amount, bankAccount }
    [HttpPost]
    public async Task<IActionResult> Create([FromBody] WithdrawRequest r) =>
        Ok(await _withdrawals.RequestAsync(await ActorAsync(), r.Amount, r.BankAccount));

    // POST /api/withdrawals/{id}/complete  (chỉ admin)
    [HttpPost("{id:guid}/complete")]
    public async Task<IActionResult> Complete(Guid id) => Ok(await _withdrawals.CompleteAsync(id, await ActorAsync()));

    // POST /api/withdrawals/{id}/reject  (chỉ admin)
    [HttpPost("{id:guid}/reject")]
    public async Task<IActionResult> Reject(Guid id) => Ok(await _withdrawals.RejectAsync(id, await ActorAsync()));
}
