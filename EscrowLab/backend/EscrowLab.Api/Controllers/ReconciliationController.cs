using EscrowLab.Api.Data;
using EscrowLab.Api.Services;
using Microsoft.AspNetCore.Mvc;

namespace EscrowLab.Api.Controllers;

[ApiController]
[Route("api/reconciliation")]
public class ReconciliationController : EscrowControllerBase
{
    private readonly ReconciliationService _reconciliation;

    public ReconciliationController(AppDbContext db, ReconciliationService reconciliation) : base(db) =>
        _reconciliation = reconciliation;

    // POST /api/reconciliation/run  { lines: [{ providerRef, amount }] }  (chỉ admin)
    [HttpPost("run")]
    public async Task<IActionResult> Run([FromBody] ReconcileRequest r)
    {
        await AdminActorAsync();
        return Ok(await _reconciliation.RunAsync(r.Lines ?? new List<StatementLine>()));
    }
}
