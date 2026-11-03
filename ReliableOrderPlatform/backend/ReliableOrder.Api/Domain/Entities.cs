namespace ReliableOrder.Api.Domain;

/// <summary>Sản phẩm "cha". Thuộc tính động (Attributes) và tuỳ chọn biến thể (Options) đều tự do.</summary>
public class Product
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public string Name { get; set; } = "";
    public string? Description { get; set; }
    public decimal BasePrice { get; set; }
    public bool IsActive { get; set; } = true;
    /// <summary>Thuộc tính động tuỳ ý: {"Chất liệu":"Cotton","Xuất xứ":"VN"}</summary>
    public Dictionary<string, string> Attributes { get; set; } = new();
    public List<ProductOption> Options { get; set; } = new();
    public List<ProductVariant> Variants { get; set; } = new();
}

/// <summary>Một chiều biến thể, ví dụ Màu sắc = [Đỏ, Xanh], Size = [S, M, L].</summary>
public class ProductOption
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid ProductId { get; set; }
    public string Name { get; set; } = "";
    public List<string> Values { get; set; } = new();
}

/// <summary>Biến thể bán được (SKU). Mỗi biến thể có giá và tồn kho riêng.</summary>
public class ProductVariant
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid ProductId { get; set; }
    public Product Product { get; set; } = null!;
    public string Sku { get; set; } = "";
    public decimal Price { get; set; }
    public int Stock { get; set; }
    public bool IsActive { get; set; } = true;
    /// <summary>Ví dụ {"Màu sắc":"Đỏ","Size":"M"}. Rỗng nếu sản phẩm không có biến thể.</summary>
    public Dictionary<string, string> OptionValues { get; set; } = new();
}

public enum OrderStatus { Pending, Confirmed, Cancelled }

public class Order
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public string OrderNumber { get; set; } = "";
    public string CustomerName { get; set; } = "";
    public string CustomerEmail { get; set; } = "";
    public OrderStatus Status { get; set; } = OrderStatus.Pending;
    public decimal Total { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public List<OrderItem> Items { get; set; } = new();
}

/// <summary>Snapshot tại thời điểm đặt: đổi giá/tên sau này không làm sai đơn cũ.</summary>
public class OrderItem
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid OrderId { get; set; }
    public Guid VariantId { get; set; }
    public string ProductName { get; set; } = "";
    public string Sku { get; set; } = "";
    public Dictionary<string, string> OptionValues { get; set; } = new();
    public decimal UnitPrice { get; set; }
    public int Quantity { get; set; }
}

/// <summary>Lưu kết quả theo Idempotency-Key: gửi lại cùng key sẽ nhận đúng kết quả cũ, không tạo đơn trùng.</summary>
public class IdempotencyRecord
{
    public string Key { get; set; } = "";
    public string RequestHash { get; set; } = "";
    public int StatusCode { get; set; }
    public string ResponseJson { get; set; } = "";
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}

/// <summary>Transactional outbox: sự kiện được ghi cùng transaction với đơn hàng, worker phát đi sau.</summary>
public class OutboxMessage
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public string Type { get; set; } = "";
    public string Payload { get; set; } = "";
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime NextAttemptAt { get; set; } = DateTime.UtcNow;
    public DateTime? ProcessedAt { get; set; }
    public int Attempts { get; set; }
    public bool IsDeadLetter { get; set; }
    public string? LastError { get; set; }
}
