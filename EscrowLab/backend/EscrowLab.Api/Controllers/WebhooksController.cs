using System.Text;
using EscrowLab.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace EscrowLab.Api.Controllers;

/// <summary>
/// Công khai (AllowAnonymous) vì bên ngoài gọi vào, không có token SSO.
/// Mỗi webhook tự xác thực bằng cơ chế riêng: chữ ký HMAC (gateway) hoặc API key (SePay).
/// </summary>
[ApiController]
[Route("api/webhooks")]
[AllowAnonymous]
public class WebhooksController : ControllerBase
{
    private readonly PaymentService _payments;

    public WebhooksController(PaymentService payments) => _payments = payments;

    // POST /api/webhooks/gateway   header X-Gateway-Signature: t=...,v1=...
    [HttpPost("gateway")]
    public async Task<IActionResult> Gateway()
    {
        var body = await ReadBodyAsync();
        var status = await _payments.HandleWebhookAsync(body, Request.Headers["X-Gateway-Signature"].ToString());
        return Ok(new { status });
    }

    // POST /api/webhooks/sepay   header Authorization: Apikey <khoá>
    // SePay yêu cầu trả 200/201 kèm {"success": true}.
    [HttpPost("sepay")]
    public async Task<IActionResult> SePay()
    {
        var body = await ReadBodyAsync();
        var status = await _payments.HandleSePayAsync(body, Request.Headers["Authorization"].ToString());
        return Ok(new { success = true, status });
    }

    // Cần đọc nguyên văn body (chữ ký được tính trên chuỗi gốc), nên không dùng [FromBody].
    private async Task<string> ReadBodyAsync()
    {
        using var reader = new StreamReader(Request.Body, Encoding.UTF8);
        return await reader.ReadToEndAsync();
    }
}
