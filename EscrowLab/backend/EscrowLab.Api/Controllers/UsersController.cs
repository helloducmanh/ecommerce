using EscrowLab.Api.Data;
using EscrowLab.Api.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace EscrowLab.Api.Controllers;

[ApiController]
public class UsersController : EscrowControllerBase
{
    public UsersController(AppDbContext db) : base(db) { }

    // GET /api/users
    [HttpGet("api/users")]
    public async Task<ActionResult<List<UserDto>>> GetAll()
    {
        await ActorAsync();   // đảm bảo người vừa đăng nhập đã có trong bảng users trước khi liệt kê
        return await Db.Users.AsNoTracking()
            .OrderBy(u => u.Role).ThenBy(u => u.Name)
            .Select(u => new UserDto { Id = u.Id, Name = u.Name, Role = u.Role })
            .ToListAsync();
    }

    // GET /api/me/wallet
    [HttpGet("api/me/wallet")]
    public async Task<IActionResult> MyWallet()
    {
        var u = await ActorAsync();
        var code = EscrowLab.Api.Domain.Codes.Wallet(u.Id);
        var bal = await Db.Accounts.AsNoTracking().Where(a => a.Code == code)
            .Select(a => (long?)a.Balance).FirstOrDefaultAsync();
        return Ok(new { accountCode = code, balance = bal ?? 0, hasWallet = bal.HasValue });
    }
}
