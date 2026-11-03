using System.Security.Claims;
using System.Text.Json;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Authorization;

namespace ReliableOrder.Api.Auth;

/// <summary>
/// SSO: API không tự đăng nhập. Người dùng đăng nhập ở Identity Provider (Keycloak), frontend gửi
/// access token (JWT) lên, API chỉ việc kiểm tra chữ ký + issuer + audience + hạn dùng.
/// </summary>
public static class SsoAuth
{
    public const string AdminPolicy = "Admin";

    public static IServiceCollection AddSso(this IServiceCollection services, IConfiguration config)
    {
        var authority = config["Auth:Authority"] ?? throw new InvalidOperationException("Thiếu Auth:Authority");
        var clientId = config["Auth:ClientId"] ?? throw new InvalidOperationException("Thiếu Auth:ClientId");

        services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
            .AddJwtBearer(o =>
            {
                o.Authority = authority;                 // lấy khoá công khai (JWKS) từ {authority}/.well-known/openid-configuration
                o.Audience = clientId;                   // token phải được cấp cho đúng ứng dụng này (claim aud)
                o.RequireHttpsMetadata = authority.StartsWith("https://", StringComparison.OrdinalIgnoreCase);
                o.MapInboundClaims = false;              // giữ nguyên tên claim: sub, name, email, ...
                o.TokenValidationParameters.NameClaimType = "preferred_username";
                o.TokenValidationParameters.RoleClaimType = ClaimTypes.Role;
                o.Events = new JwtBearerEvents
                {
                    OnTokenValidated = ctx =>
                    {
                        AddClientRoles(ctx.Principal, clientId);
                        return Task.CompletedTask;
                    },
                };
            });

        services.AddAuthorization(o =>
        {
            // Mặc định MỌI endpoint đều phải đăng nhập. Endpoint công khai phải ghi rõ [AllowAnonymous].
            o.FallbackPolicy = new AuthorizationPolicyBuilder().RequireAuthenticatedUser().Build();
            o.AddPolicy(AdminPolicy, p => p.RequireRole("admin"));
        });
        return services;
    }

    /// <summary>Keycloak đặt role của từng client trong resource_access.{clientId}.roles. Chỉ đọc role của CHÍNH app này.</summary>
    private static void AddClientRoles(ClaimsPrincipal? principal, string clientId)
    {
        if (principal?.Identity is not ClaimsIdentity identity) return;
        var raw = identity.FindFirst("resource_access")?.Value;
        if (string.IsNullOrEmpty(raw)) return;
        try
        {
            using var doc = JsonDocument.Parse(raw);
            if (doc.RootElement.TryGetProperty(clientId, out var client) &&
                client.TryGetProperty("roles", out var roles) &&
                roles.ValueKind == JsonValueKind.Array)
            {
                foreach (var r in roles.EnumerateArray())
                    if (r.GetString() is { Length: > 0 } role)
                        identity.AddClaim(new Claim(ClaimTypes.Role, role));
            }
        }
        catch (JsonException) { /* claim lỗi định dạng: coi như không có role */ }
    }
}
