using EscrowLab.Api.Data;
using EscrowLab.Api.Domain;
using EscrowLab.Api.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Filters;

namespace EscrowLab.Api.Controllers;

/// <summary>Chỉ dùng khi học. Tắt toàn bộ bằng Escrow:EnableDevEndpoints=false (trả 404).</summary>
public class DevOnlyAttribute : ActionFilterAttribute
{
    public override void OnActionExecuting(ActionExecutingContext context)
    {
        var opt = context.HttpContext.RequestServices.GetRequiredService<EscrowOptions>();
        if (!opt.EnableDevEndpoints) context.Result = new NotFoundResult();
    }
}

[ApiController]
[Route("api/dev")]
[DevOnly]
public class DevController : EscrowControllerBase
{
    private readonly PaymentService _payments;
    private readonly ReconciliationService _reconciliation;
    private readonly OrderService _orders;

    public DevController(AppDbContext db, PaymentService payments, ReconciliationService reconciliation, OrderService orders)
        : base(db)
    {
        _payments = payments;
        _reconciliation = reconciliation;
        _orders = orders;
    }

    // POST /api/dev/gateway/simulate
    [HttpPost("gateway/simulate")]
    public async Task<IActionResult> SimulateGateway([FromBody] SimulateRequest r) => Ok(await _payments.SimulateAsync(r));

    // POST /api/dev/sepay/simulate
    [HttpPost("sepay/simulate")]
    public async Task<IActionResult> SimulateSePay([FromBody] SePaySimulateRequest r) => Ok(await _payments.SimulateSePayAsync(r));

    // GET /api/dev/gateway/statement?tamper=none|drop|alter|phantom
    [HttpGet("gateway/statement")]
    public async Task<IActionResult> Statement(string? tamper) => Ok(await _reconciliation.BuildDemoStatementAsync(tamper ?? "none"));

    // POST /api/dev/maintenance/run
    [HttpPost("maintenance/run")]
    public async Task<IActionResult> RunMaintenance()
    {
        var (expired, autoReleased) = await _orders.RunMaintenanceAsync();
        return Ok(new { expired, autoReleased });
    }

    // POST /api/dev/orders/{id}/time-travel  { hours }
    // Tua thời gian của một đơn lùi về quá khứ để thử hết hạn / tự giải ngân mà không phải chờ.
    [HttpPost("orders/{id:guid}/time-travel")]
    public async Task<IActionResult> TimeTravel(Guid id, [FromBody] TimeTravelRequest r)
    {
        var hours = r.Hours;
        var n = await Db.Database.ExecuteSqlAsync(
            $@"UPDATE orders SET expires_at = expires_at - ({hours} * interval '1 hour'),
                                 shipped_at = shipped_at - ({hours} * interval '1 hour')
               WHERE id = {id}");
        return n == 0 ? NotFound() : Ok(new { shiftedHours = r.Hours });
    }
}
