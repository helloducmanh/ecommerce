using ReliableOrder.Api.Domain;

namespace ReliableOrder.Api.Services;

// ===== Request =====

public class OptionDto
{
    public string Name { get; set; } = "";
    public List<string> Values { get; set; } = new List<string>();
}

public class CreateProductRequest
{
    public string Name { get; set; } = "";
    public string? Description { get; set; }
    public decimal BasePrice { get; set; }
    public int InitialStock { get; set; }
    public Dictionary<string, string>? Attributes { get; set; }
    public List<OptionDto>? Options { get; set; }
}

public class UpdateVariantRequest
{
    public decimal? Price { get; set; }
    public int? Stock { get; set; }
    public bool? IsActive { get; set; }
}

public class OrderLineRequest
{
    public Guid VariantId { get; set; }
    public int Quantity { get; set; }
}

public class CreateOrderRequest
{
    public string CustomerName { get; set; } = "";
    public string CustomerEmail { get; set; } = "";
    public List<OrderLineRequest> Items { get; set; } = new List<OrderLineRequest>();
}

// ===== Response =====

public class VariantDto
{
    public Guid Id { get; set; }
    public string Sku { get; set; } = "";
    public decimal Price { get; set; }
    public int Stock { get; set; }
    public bool IsActive { get; set; }
    public Dictionary<string, string> OptionValues { get; set; } = new Dictionary<string, string>();
}

public class ProductDto
{
    public Guid Id { get; set; }
    public string Name { get; set; } = "";
    public string? Description { get; set; }
    public decimal BasePrice { get; set; }
    public Dictionary<string, string> Attributes { get; set; } = new Dictionary<string, string>();
    public List<OptionDto> Options { get; set; } = new List<OptionDto>();
    public List<VariantDto> Variants { get; set; } = new List<VariantDto>();
}

public class OrderItemDto
{
    public Guid VariantId { get; set; }
    public string ProductName { get; set; } = "";
    public string Sku { get; set; } = "";
    public Dictionary<string, string> OptionValues { get; set; } = new Dictionary<string, string>();
    public decimal UnitPrice { get; set; }
    public int Quantity { get; set; }
}

public class OrderDto
{
    public Guid Id { get; set; }
    public string OrderNumber { get; set; } = "";
    public string Status { get; set; } = "";
    public string CustomerName { get; set; } = "";
    public string CustomerEmail { get; set; } = "";
    public decimal Total { get; set; }
    public DateTime CreatedAt { get; set; }
    public List<OrderItemDto> Items { get; set; } = new List<OrderItemDto>();
}

/// <summary>Chuyển entity (database) sang DTO (trả về cho client).</summary>
public static class Mapper
{
    public static VariantDto ToVariantDto(ProductVariant v)
    {
        return new VariantDto
        {
            Id = v.Id,
            Sku = v.Sku,
            Price = v.Price,
            Stock = v.Stock,
            IsActive = v.IsActive,
            OptionValues = v.OptionValues
        };
    }

    public static ProductDto ToProductDto(Product p)
    {
        return new ProductDto
        {
            Id = p.Id,
            Name = p.Name,
            Description = p.Description,
            BasePrice = p.BasePrice,
            Attributes = p.Attributes,
            Options = p.Options.Select(o => new OptionDto { Name = o.Name, Values = o.Values }).ToList(),
            Variants = p.Variants.OrderBy(v => v.Sku).Select(v => ToVariantDto(v)).ToList()
        };
    }

    public static OrderDto ToOrderDto(Order o)
    {
        return new OrderDto
        {
            Id = o.Id,
            OrderNumber = o.OrderNumber,
            Status = o.Status.ToString(),
            CustomerName = o.CustomerName,
            CustomerEmail = o.CustomerEmail,
            Total = o.Total,
            CreatedAt = o.CreatedAt,
            Items = o.Items.Select(i => new OrderItemDto
            {
                VariantId = i.VariantId,
                ProductName = i.ProductName,
                Sku = i.Sku,
                OptionValues = i.OptionValues,
                UnitPrice = i.UnitPrice,
                Quantity = i.Quantity
            }).ToList()
        };
    }
}
