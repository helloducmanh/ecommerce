using System.Globalization;
using System.Text;
using ReliableOrder.Api.Domain;

namespace ReliableOrder.Api.Services;

public static class ProductFactory
{
    public const int MaxVariants = 200;

    /// <summary>Tạo sản phẩm và tự sinh toàn bộ biến thể từ các option.</summary>
    public static Product Create(string name, string? description, decimal basePrice,
        Dictionary<string, string> attributes, List<OptionDto> options, int initialStock)
    {
        var product = new Product
        {
            Name = name.Trim(),
            Description = description,
            BasePrice = basePrice,
            Attributes = attributes
        };

        foreach (var o in options)
        {
            product.Options.Add(new ProductOption
            {
                ProductId = product.Id,
                Name = o.Name.Trim(),
                Values = o.Values.Select(v => v.Trim()).Distinct().ToList()
            });
        }

        string prefix = Slug(product.Name) + "-" + product.Id.ToString("N").Substring(0, 4).ToUpper();

        foreach (var combo in Combine(product.Options))
        {
            string suffix = combo.Count == 0 ? "DEFAULT" : string.Join("-", combo.Values.Select(Slug));
            product.Variants.Add(new ProductVariant
            {
                ProductId = product.Id,
                Sku = prefix + "-" + suffix,
                Price = basePrice,
                Stock = initialStock,
                OptionValues = combo
            });
        }

        return product;
    }

    /// <summary>
    /// Tích Descartes các option. Ví dụ Màu [Đỏ, Xanh] x Size [S, M] => 4 tổ hợp.
    /// Không có option nào => 1 tổ hợp rỗng (biến thể mặc định).
    /// </summary>
    public static List<Dictionary<string, string>> Combine(IEnumerable<ProductOption> options)
    {
        var result = new List<Dictionary<string, string>>();
        result.Add(new Dictionary<string, string>());

        foreach (var option in options)
        {
            var next = new List<Dictionary<string, string>>();
            foreach (var combo in result)
            {
                foreach (var value in option.Values)
                {
                    var copy = new Dictionary<string, string>(combo);
                    copy[option.Name] = value;
                    next.Add(copy);
                }
            }

            result = next;
            if (result.Count > MaxVariants)
            {
                throw new InvalidOperationException("Quá " + MaxVariants + " biến thể.");
            }
        }

        return result;
    }

    // Chuyển "Áo thun" => "AOTHUN" để làm SKU
    private static string Slug(string text)
    {
        var sb = new StringBuilder();
        foreach (char ch in text.Normalize(NormalizationForm.FormD))
        {
            if (CharUnicodeInfo.GetUnicodeCategory(ch) == UnicodeCategory.NonSpacingMark) continue;
            if (ch == 'đ' || ch == 'Đ') sb.Append('D');
            else if (char.IsLetterOrDigit(ch)) sb.Append(char.ToUpperInvariant(ch));
        }

        string result = sb.ToString();
        if (result.Length == 0) return "X";
        return result.Length > 12 ? result.Substring(0, 12) : result;
    }
}
