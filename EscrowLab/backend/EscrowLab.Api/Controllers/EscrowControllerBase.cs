using EscrowLab.Api.Auth;
using EscrowLab.Api.Data;
using EscrowLab.Api.Domain;
using Microsoft.AspNetCore.Mvc;

namespace EscrowLab.Api.Controllers;

/// <summary>Lớp cha của các controller cần biết "ai đang gọi" (lấy từ access token SSO).</summary>
[ApiController]
public abstract class EscrowControllerBase : ControllerBase
{
    protected readonly AppDbContext Db;

    protected EscrowControllerBase(AppDbContext db) => Db = db;

    /// <summary>Người dùng hiện tại. Lần đầu đăng nhập sẽ tự tạo user + ví trong DB.</summary>
    protected Task<User> ActorAsync() => CurrentUser.ResolveAsync(HttpContext, Db);

    protected async Task<User> AdminActorAsync()
    {
        var u = await ActorAsync();
        if (u.Role != "admin") throw new DomainException("FORBIDDEN", "Chỉ admin", 403);
        return u;
    }
}
