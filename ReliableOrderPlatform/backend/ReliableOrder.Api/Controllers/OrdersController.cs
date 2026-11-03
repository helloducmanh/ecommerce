using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using ReliableOrder.Api.Data;
using ReliableOrder.Api.Services;

namespace ReliableOrder.Api.Controllers;

[ApiController]
[Route("api/orders")]
public class OrdersController : ControllerBase
{
    private readonly AppDbContext _db;
    private readonly OrderService _orderService;

    public OrdersController(AppDbContext db, OrderService orderService)
    {
        _db = db;
        _orderService = orderService;
    }

    // Danh tính lấy từ access token (SSO). Admin thấy mọi đơn; khách chỉ thấy đơn có email trùng email của mình.
    private bool IsAdmin => User.IsInRole("admin");
    private string? MyEmail => User.FindFirstValue("email");
    private string MyName => User.FindFirstValue("name") ?? User.FindFirstValue("preferred_username") ?? "";

    private async Task<bool> CanAccessAsync(Guid id, CancellationToken ct)
    {
        if (IsAdmin) return true;
        var email = MyEmail;
        if (string.IsNullOrEmpty(email)) return false;
        return await _db.Orders.AsNoTracking().AnyAsync(o => o.Id == id && o.CustomerEmail == email, ct);
    }

    // GET /api/orders
    [HttpGet]
    public async Task<ActionResult<List<OrderDto>>> GetAll()
    {
        var query = _db.Orders.AsNoTracking().Include(o => o.Items).AsQueryable();
        if (!IsAdmin)
        {
            var email = MyEmail;
            if (string.IsNullOrEmpty(email))
                return StatusCode(403, new { error = "Tài khoản SSO chưa có email nên không xem được đơn hàng." });
            query = query.Where(o => o.CustomerEmail == email);
        }

        var orders = await query
            .OrderByDescending(o => o.CreatedAt)
            .Take(100)
            .ToListAsync();

        return orders.Select(o => Mapper.ToOrderDto(o)).ToList();
    }

    // GET /api/orders/{id}
    [HttpGet("{id:guid}")]
    public async Task<ActionResult<OrderDto>> GetById(Guid id)
    {
        var order = await _db.Orders.AsNoTracking()
            .Include(o => o.Items)
            .FirstOrDefaultAsync(o => o.Id == id);

        // Đơn của người khác trả 404 (không lộ là có tồn tại)
        if (order == null || (!IsAdmin && (string.IsNullOrEmpty(MyEmail) || order.CustomerEmail != MyEmail)))
            return NotFound();
        return Mapper.ToOrderDto(order);
    }

    // POST /api/orders  (bắt buộc header Idempotency-Key)
    [HttpPost]
    public async Task<IActionResult> Create(
        [FromHeader(Name = "Idempotency-Key")] string? idempotencyKey,
        [FromBody] CreateOrderRequest request,
        CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(idempotencyKey) || idempotencyKey.Length > 100)
            return BadRequest(new { error = "Thiếu hoặc sai header Idempotency-Key." });

        // Thông tin khách lấy từ tài khoản SSO, không tin giá trị client gửi lên (tránh đặt đơn mạo danh người khác).
        request.CustomerName = MyName;
        request.CustomerEmail = MyEmail ?? "";

        if (string.IsNullOrWhiteSpace(request.CustomerName) || string.IsNullOrWhiteSpace(request.CustomerEmail))
            return BadRequest(new { error = "Tài khoản SSO cần có tên và email." });

        if (request.Items == null || request.Items.Count == 0 || request.Items.Any(i => i.Quantity <= 0 || i.Quantity > 1000))
            return BadRequest(new { error = "Danh sách sản phẩm/số lượng không hợp lệ." });

        OrderResult result = await _orderService.CreateAsync(idempotencyKey, request, ct);
        return ToActionResult(result);
    }

    // POST /api/orders/{id}/confirm
    [HttpPost("{id:guid}/confirm")]
    public async Task<IActionResult> Confirm(Guid id, CancellationToken ct)
    {
        if (!await CanAccessAsync(id, ct)) return NotFound();
        OrderResult result = await _orderService.ConfirmAsync(id, ct);
        return ToActionResult(result);
    }

    // POST /api/orders/{id}/cancel
    [HttpPost("{id:guid}/cancel")]
    public async Task<IActionResult> Cancel(Guid id, CancellationToken ct)
    {
        if (!await CanAccessAsync(id, ct)) return NotFound();
        OrderResult result = await _orderService.CancelAsync(id, ct);
        return ToActionResult(result);
    }

    // Chuyển OrderResult của service thành HTTP response
    private IActionResult ToActionResult(OrderResult result)
    {
        if (result.Replayed)
        {
            Response.Headers["Idempotent-Replayed"] = "true";
        }

        return new ContentResult
        {
            StatusCode = result.StatusCode,
            ContentType = "application/json",
            Content = result.Json
        };
    }
}
