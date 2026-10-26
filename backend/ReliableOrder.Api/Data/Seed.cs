using Microsoft.EntityFrameworkCore;
using ReliableOrder.Api.Services;

namespace ReliableOrder.Api.Data;

public static class Seed
{
    public static async Task RunAsync(AppDbContext db)
    {
        if (await db.Products.AnyAsync()) return;

        // 1. Áo thun: Màu sắc x Size = 9 biến thể
        db.Products.Add(ProductFactory.Create(
            "Áo thun Basic", "Áo thun cotton 100%", 199000,
            new Dictionary<string, string> { { "Chất liệu", "Cotton" }, { "Xuất xứ", "Việt Nam" } },
            new List<OptionDto>
            {
                new OptionDto { Name = "Màu sắc", Values = new List<string> { "Đen", "Trắng", "Đỏ" } },
                new OptionDto { Name = "Size", Values = new List<string> { "S", "M", "L" } }
            },
            20));

        // 2. Điện thoại: Dung lượng x Màu sắc = 4 biến thể
        db.Products.Add(ProductFactory.Create(
            "Điện thoại X1", "Smartphone 6.5 inch", 8990000,
            new Dictionary<string, string> { { "Màn hình", "6.5 inch OLED" }, { "Bảo hành", "12 tháng" } },
            new List<OptionDto>
            {
                new OptionDto { Name = "Dung lượng", Values = new List<string> { "128GB", "256GB" } },
                new OptionDto { Name = "Màu sắc", Values = new List<string> { "Xám", "Xanh" } }
            },
            10));

        // 3. Sản phẩm không có biến thể => hệ thống tự tạo 1 biến thể mặc định
        db.Products.Add(ProductFactory.Create(
            "Cáp sạc USB-C 1m", "Cáp sạc nhanh", 79000,
            new Dictionary<string, string>(),
            new List<OptionDto>(),
            100));

        await db.SaveChangesAsync();
    }
}
