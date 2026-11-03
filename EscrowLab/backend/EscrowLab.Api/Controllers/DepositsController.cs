using EscrowLab.Api.Data;
using EscrowLab.Api.Services;
using Microsoft.AspNetCore.Mvc;

namespace EscrowLab.Api.Controllers;

[ApiController]
[Route("api/deposits")]
public class DepositsController : EscrowControllerBase
{
    private readonly PaymentService _payments;

    public DepositsController(AppDbContext db, PaymentService payments) : base(db) => _payments = payments;

    // POST /api/deposits  { amount }
    [HttpPost]
    public async Task<IActionResult> Create([FromBody] AmountRequest r) =>
        Ok(await _payments.CreateDepositAsync(await ActorAsync(), r.Amount));

    // GET /api/deposits
    [HttpGet]
    public async Task<IActionResult> GetAll() => Ok(await _payments.ListAsync(await ActorAsync()));
}
