using System.Security.Claims;
using System.Text.Json;
using EscrowLab.Api.Data;
using EscrowLab.Api.Domain;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Authorization;
using Microsoft.EntityFrameworkCore;

namespace EscrowLab.Api.Auth;

/// <summary>
/// SSO: API không tự đăng nhập. Người dùng đăng nhập ở Identity Provider (Keycloak), frontend gửi
/// access token (JWT) lên, API chỉ việc kiểm tra chữ ký + issuer + audience + hạn dùng.
/// </summary>
public static class SsoAuth
{
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
            // Mặc định MỌI endpoint đều phải đăng nhập. Endpoint công khai phải ghi rõ AllowAnonymous().
            o.FallbackPolicy = new AuthorizationPolicyBuilder().RequireAuthenticatedUser().Build();
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

/// <summary>Chuyển danh tính từ token (sub, name, role) thành <see cref="User"/> trong bảng users của EscrowLab.</summary>
public static class CurrentUser
{
    // Một người chỉ có 1 vai trò trong EscrowLab (ràng buộc CHECK của bảng users). Nhiều role thì lấy vai cao nhất.
    private static readonly string[] RolePriority = { "admin", "seller", "buyer" };

    public static async Task<User> ResolveAsync(HttpContext ctx, AppDbContext db)
    {
        var p = ctx.User;
        // users.id = sub của Keycloak (Keycloak dùng UUID cho sub). IdP khác (Entra ID...) thì thêm cột ánh xạ.
        if (!Guid.TryParse(p.FindFirstValue("sub"), out var id))
            throw new DomainException("UNAUTHENTICATED", "Token không có claim 'sub' dạng GUID", 401);

        var role = RolePriority.FirstOrDefault(p.IsInRole) ?? "buyer";
        var name = p.FindFirstValue("name") ?? p.FindFirstValue("preferred_username") ?? id.ToString();

        var user = await db.Users.AsNoTracking().FirstOrDefaultAsync(u => u.Id == id, ctx.RequestAborted);
        if (user is not null && user.Name == name && user.Role == role) return user;

        // Lần đăng nhập đầu, hoặc tên/role bên IdP đã đổi: đồng bộ vào DB. IdP là nguồn sự thật cho tên và vai trò.
        // SQL thô (upsert) để hai request đầu tiên chạy song song không đụng nhau.
        await db.Database.ExecuteSqlAsync(
            $@"INSERT INTO users (id, name, role) VALUES ({id}, {name}, {role})
               ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, role = EXCLUDED.role", ctx.RequestAborted);
        if (role != "admin") await Seed.EnsureWalletAsync(db, id);
        return new User { Id = id, Name = name, Role = role };
    }
}
