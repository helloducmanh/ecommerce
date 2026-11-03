using System.Security.Cryptography;
using System.Text;

namespace EscrowLab.Api.Services;

/// <summary>
/// Chữ ký webhook kiểu Stripe: header "t=&lt;unix&gt;,v1=&lt;hmac-sha256-hex&gt;".
/// Chuỗi được ký là "{t}.{body}". Kiểm tra thêm timestamp để chống replay.
/// </summary>
public static class WebhookSigner
{
    public static string Sign(string secret, long timestamp, string body)
    {
        using var h = new HMACSHA256(Encoding.UTF8.GetBytes(secret));
        var mac = h.ComputeHash(Encoding.UTF8.GetBytes($"{timestamp}.{body}"));
        return Convert.ToHexString(mac).ToLowerInvariant();
    }

    public static string BuildHeader(string secret, long timestamp, string body) =>
        $"t={timestamp},v1={Sign(secret, timestamp, body)}";

    public static bool Verify(string secret, string? header, string body, DateTimeOffset now,
        TimeSpan tolerance, out string reason)
    {
        reason = "";
        if (string.IsNullOrWhiteSpace(header)) { reason = "Thiếu header chữ ký"; return false; }

        long? ts = null;
        string? v1 = null;
        foreach (var part in header.Split(','))
        {
            var kv = part.Split('=', 2);
            if (kv.Length != 2) continue;
            if (kv[0].Trim() == "t" && long.TryParse(kv[1], out var t)) ts = t;
            if (kv[0].Trim() == "v1") v1 = kv[1].Trim();
        }
        if (ts is null || v1 is null) { reason = "Header chữ ký sai định dạng"; return false; }

        var age = now - DateTimeOffset.FromUnixTimeSeconds(ts.Value);
        if (age.Duration() > tolerance) { reason = "Timestamp ngoài khoảng cho phép (nghi replay)"; return false; }

        var expected = Sign(secret, ts.Value, body);
        var ok = CryptographicOperations.FixedTimeEquals(Encoding.UTF8.GetBytes(expected), Encoding.UTF8.GetBytes(v1));
        if (!ok) reason = "Chữ ký không khớp";
        return ok;
    }
}
