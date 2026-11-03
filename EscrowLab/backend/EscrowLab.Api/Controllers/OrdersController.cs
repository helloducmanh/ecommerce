using EscrowLab.Api.Data;
using EscrowLab.Api.Services;
using Microsoft.AspNetCore.Mvc;

namespace EscrowLab.Api.Controllers;

[ApiController]
[Route("api/orders")]
public class OrdersController : EscrowControllerBase
{
    private readonly OrderService _orders;

    public OrdersController(AppDbContext db, OrderService orders) : base(db) => _orders = orders;

    // GET /api/orders
    [HttpGet]
    public async Task<ActionResult<List<OrderDto>>> GetAll() => await _orders.ListAsync(await ActorAsync());

    // GET /api/orders/{id}
    [HttpGet("{id:guid}")]
    public async Task<IActionResult> GetById(Guid id)
    {
        var (order, events) = await _orders.GetAsync(id, await ActorAsync());
        return Ok(new { order, events });
    }

    // POST /api/orders  { sellerId, title, amount }
    [HttpPost]
    public async Task<IActionResult> Create([FromBody] CreateOrderRequest r, CancellationToken ct) =>
        Ok(await _orders.CreateAsync(await ActorAsync(), r.SellerId, r.Title, r.Amount, ct));

    // Mỗi hành động = một lần chuyển trạng thái của state machine
    [HttpPost("{id:guid}/pay")]
    public async Task<IActionResult> Pay(Guid id) => Ok(await _orders.PayAsync(id, await ActorAsync()));

    [HttpPost("{id:guid}/ship")]
    public async Task<IActionResult> Ship(Guid id) => Ok(await _orders.ShipAsync(id, await ActorAsync()));

    [HttpPost("{id:guid}/confirm")]
    public async Task<IActionResult> Confirm(Guid id) => Ok(await _orders.ConfirmAsync(id, await ActorAsync()));

    [HttpPost("{id:guid}/cancel")]
    public async Task<IActionResult> Cancel(Guid id) => Ok(await _orders.CancelAsync(id, await ActorAsync()));

    [HttpPost("{id:guid}/dispute")]
    public async Task<IActionResult> Dispute(Guid id) => Ok(await _orders.DisputeAsync(id, await ActorAsync()));

    [HttpPost("{id:guid}/refund")]
    public async Task<IActionResult> Refund(Guid id) => Ok(await _orders.SellerRefundAsync(id, await ActorAsync()));

    // POST /api/orders/{id}/resolve  { decision: "release" | "refund" }  (chỉ admin)
    [HttpPost("{id:guid}/resolve")]
    public async Task<IActionResult> Resolve(Guid id, [FromBody] ResolveRequest r) =>
        Ok(await _orders.ResolveAsync(id, await ActorAsync(), r.Decision));
}
