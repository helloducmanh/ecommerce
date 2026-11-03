using System.Text.Json.Serialization;
using System.Text.RegularExpressions;
using EscrowLab.Api.Domain;

namespace EscrowLab.Api.Services;

/// <summary>Payload SePay gửi tới webhook khi có biến động số dư.</summary>
public class SePayWebhook
{
    [JsonPropertyName("id")] public long Id { get; set; }
    [JsonPropertyName("gateway")] public string? Gateway { get; set; }
    [JsonPropertyName("code")] public string? Code { get; set; }
    [JsonPropertyName("content")] public string? Content { get; set; }
    [JsonPropertyName("transferType")] public string TransferType { get; set; } = "";
    [JsonPropertyName("transferAmount")] public long TransferAmount { get; set; }
    [JsonPropertyName("referenceCode")] public string? ReferenceCode { get; set; }
}

public record SePaySimulateRequest(string ProviderRef, long? AmountOverride, bool SendTwice);

public static class SePayHelper
{
    // Mã nạp tiền có dạng EL + 12 ký tự hex (xem PaymentService.CreateDepositAsync).
    private static readonly Regex RefPattern = new(@"EL[0-9A-F]{12}", RegexOptions.IgnoreCase | RegexOptions.Compiled);

    /// <summary>Ưu tiên trường code do SePay nhận diện, nếu không có thì tìm trong nội dung chuyển khoản.</summary>
    public static string? ExtractRef(string? code, string? content)
    {
        foreach (var candidate in new[] { code, content })
        {
            if (string.IsNullOrWhiteSpace(candidate)) continue;
            var m = RefPattern.Match(candidate);
            if (m.Success) return m.Value.ToUpperInvariant();
        }
        return null;
    }

    /// <summary>Ảnh QR động của SePay: https://qr.sepay.vn/img?acc=..&bank=..&amount=..&des=..</summary>
    public static string BuildQrUrl(EscrowOptions o, long amount, string content) =>
        "https://qr.sepay.vn/img?" +
        $"acc={Uri.EscapeDataString(o.BankAccountNumber)}&bank={Uri.EscapeDataString(o.BankName)}" +
        $"&amount={amount}&des={Uri.EscapeDataString(content)}";
}
