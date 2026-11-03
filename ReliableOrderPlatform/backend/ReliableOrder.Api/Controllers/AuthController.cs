using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.Cookies;
using Microsoft.AspNetCore.Authentication.OpenIdConnect;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using ReliableOrder.Api.Controllers.Dtos;
using ReliableOrder.Api.Services;

namespace ReliableOrder.Api.Controllers;

[ApiController]
[Route("api/auth")]
public class AuthController : ControllerBase
{
    private const string CookieScheme =
        CookieAuthenticationDefaults.AuthenticationScheme;

    private const string OidcScheme =
        OpenIdConnectDefaults.AuthenticationScheme;

    private readonly ISsoUserService _userService;

    public AuthController(ISsoUserService userService)
    {
        _userService = userService;
    }
    
    [AllowAnonymous]
    [HttpGet("login")]
    public IActionResult Login([FromQuery] string? returnUrl = "/")
    {
        // Giả định FE cùng origin.
        var destination =
            !string.IsNullOrWhiteSpace(returnUrl) &&
            Url.IsLocalUrl(returnUrl)
                ? returnUrl
                : "/";

        return Challenge(
            new AuthenticationProperties
            {
                RedirectUri = destination
            },
            OidcScheme);
    }
}