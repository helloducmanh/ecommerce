using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using ReliableOrder.Api.Auth;
using ReliableOrder.Api.Data;
using ReliableOrder.Api.Services;

namespace ReliableOrder.Api.Controllers;

[ApiController]
[Route("api/variants")]
[Authorize(Policy = SsoAuth.AdminPolicy)]   // sửa giá/tồn kho: chỉ admin
public class VariantsController : ControllerBase
{
    private readonly AppDbContext _db;

    public VariantsController(AppDbContext db)
    {
        _db = db;
    }

    // PUT /api/variants/{id}  -> sửa giá, tồn kho, bật/tắt bán
    [HttpPut("{id:guid}")]
    public async Task<ActionResult<VariantDto>> Update(Guid id, [FromBody] UpdateVariantRequest request)
    {
        var variant = await _db.Variants.FindAsync(id);
        if (variant == null) return NotFound();

        if (request.Price < 0 || request.Stock < 0)
            return BadRequest(new { error = "Giá/tồn kho không hợp lệ." });

        if (request.Price.HasValue) variant.Price = request.Price.Value;
        if (request.Stock.HasValue) variant.Stock = request.Stock.Value;
        if (request.IsActive.HasValue) variant.IsActive = request.IsActive.Value;

        await _db.SaveChangesAsync();
        return Mapper.ToVariantDto(variant);
    }
}
