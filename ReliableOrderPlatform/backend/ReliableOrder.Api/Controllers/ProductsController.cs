using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using ReliableOrder.Api.Auth;
using ReliableOrder.Api.Data;
using ReliableOrder.Api.Services;

namespace ReliableOrder.Api.Controllers;

[ApiController]
[Route("api/products")]
public class ProductsController : ControllerBase
{
    private readonly AppDbContext _db;

    public ProductsController(AppDbContext db)
    {
        _db = db;
    }

    // GET /api/products  (công khai: xem cửa hàng không cần đăng nhập)
    [HttpGet]
    [AllowAnonymous]
    public async Task<ActionResult<List<ProductDto>>> GetAll()
    {
        var products = await _db.Products.AsNoTracking()
            .Include(p => p.Options)
            .Include(p => p.Variants)
            .Where(p => p.IsActive)
            .OrderBy(p => p.Name)
            .ToListAsync();

        return products.Select(p => Mapper.ToProductDto(p)).ToList();
    }

    // GET /api/products/{id}  (công khai)
    [HttpGet("{id:guid}")]
    [AllowAnonymous]
    public async Task<ActionResult<ProductDto>> GetById(Guid id)
    {
        var product = await _db.Products.AsNoTracking()
            .Include(p => p.Options)
            .Include(p => p.Variants)
            .FirstOrDefaultAsync(p => p.Id == id);

        if (product == null) return NotFound();
        return Mapper.ToProductDto(product);
    }

    // POST /api/products  -> tạo sản phẩm + tự sinh biến thể từ các option (chỉ admin)
    [HttpPost]
    [Authorize(Policy = SsoAuth.AdminPolicy)]
    public async Task<ActionResult<ProductDto>> Create([FromBody] CreateProductRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.Name))
            return BadRequest(new { error = "Tên sản phẩm bắt buộc." });

        if (request.BasePrice < 0 || request.InitialStock < 0)
            return BadRequest(new { error = "Giá/tồn kho không hợp lệ." });

        // Làm sạch option: bỏ giá trị rỗng
        var options = new List<OptionDto>();
        foreach (var o in request.Options ?? new List<OptionDto>())
        {
            var values = o.Values.Where(v => !string.IsNullOrWhiteSpace(v)).ToList();
            if (string.IsNullOrWhiteSpace(o.Name) || values.Count == 0)
                return BadRequest(new { error = "Mỗi option cần tên và ít nhất 1 giá trị." });

            options.Add(new OptionDto { Name = o.Name, Values = values });
        }

        if (options.Select(o => o.Name.Trim().ToLower()).Distinct().Count() != options.Count)
            return BadRequest(new { error = "Tên option bị trùng." });

        try
        {
            var product = ProductFactory.Create(
                request.Name, request.Description, request.BasePrice,
                request.Attributes ?? new Dictionary<string, string>(),
                options, request.InitialStock);

            _db.Products.Add(product);
            await _db.SaveChangesAsync();

            return CreatedAtAction(nameof(GetById), new { id = product.Id }, Mapper.ToProductDto(product));
        }
        catch (InvalidOperationException ex) // quá nhiều biến thể
        {
            return BadRequest(new { error = ex.Message });
        }
    }

    // DELETE /api/products/{id}  -> ngừng bán (soft delete), đơn cũ vẫn tham chiếu được (chỉ admin)
    [HttpDelete("{id:guid}")]
    [Authorize(Policy = SsoAuth.AdminPolicy)]
    public async Task<IActionResult> Delete(Guid id)
    {
        var product = await _db.Products.FindAsync(id);
        if (product == null) return NotFound();

        product.IsActive = false;
        await _db.SaveChangesAsync();
        return NoContent();
    }
}
