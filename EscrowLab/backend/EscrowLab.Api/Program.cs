using EscrowLab.Api.Auth;
using EscrowLab.Api.Data;
using EscrowLab.Api.Domain;
using EscrowLab.Api.Services;
using Microsoft.EntityFrameworkCore;
using Microsoft.OpenApi.Models;

var builder = WebApplication.CreateBuilder(args);

var options = builder.Configuration.GetSection("Escrow").Get<EscrowOptions>() ?? new EscrowOptions();
var connectionString = builder.Configuration.GetConnectionString("Default")
    ?? throw new InvalidOperationException("Thiếu ConnectionStrings:Default");

// Đăng ký các service (Dependency Injection)
builder.Services.AddSingleton(options);
builder.Services.AddControllers(o =>
{
    // Thiếu field trong body thì để service báo DomainException như trước, thay vì MVC tự trả 400 ProblemDetails.
    o.SuppressImplicitRequiredAttributeForNonNullableReferenceTypes = true;
});
builder.Services.AddDbContext<AppDbContext>(o => o.UseNpgsql(connectionString));
builder.Services.AddScoped<LedgerService>();
builder.Services.AddScoped<OrderService>();
builder.Services.AddScoped<PaymentService>();
builder.Services.AddScoped<WithdrawalService>();
builder.Services.AddScoped<ReconciliationService>();
builder.Services.AddHostedService<MaintenanceWorker>();
builder.Services.AddSso(builder.Configuration);
builder.Services.AddCors(o => o.AddDefaultPolicy(p =>
    p.WithOrigins("http://localhost:5174").AllowAnyHeader().AllowAnyMethod()));
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(c =>
{
    // Nút "Authorize" trong Swagger UI: dán access token (không gồm chữ "Bearer ") để thử API.
    c.AddSecurityDefinition("Bearer", new OpenApiSecurityScheme
    {
        Type = SecuritySchemeType.Http, Scheme = "bearer", BearerFormat = "JWT", In = ParameterLocation.Header,
    });
    c.AddSecurityRequirement(new OpenApiSecurityRequirement
    {
        { new OpenApiSecurityScheme { Reference = new OpenApiReference { Type = ReferenceType.SecurityScheme, Id = "Bearer" } }, Array.Empty<string>() },
    });
});

var app = builder.Build();

// Tạo bảng + trigger bảo vệ sổ cái và dữ liệu mẫu khi khởi động
using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
    await Seed.RunAsync(db);   // Production: chuyển sang EF Migrations (nhớ giữ lại các trigger trong schema.sql)
}

app.UseCors();

// Biến lỗi nghiệp vụ thành JSON { code, message } với HTTP status phù hợp.
app.Use(async (ctx, next) =>
{
    try
    {
        await next();
    }
    catch (DomainException ex)
    {
        ctx.Response.StatusCode = ex.Status;
        await ctx.Response.WriteAsJsonAsync(new { code = ex.Code, message = ex.Message });
    }
    catch (Exception ex) when (DbErrors.Find(ex)?.SqlState == "23514")   // CHECK constraint (vd: số dư âm)
    {
        ctx.Response.StatusCode = 409;
        await ctx.Response.WriteAsJsonAsync(new { code = "DB_CONSTRAINT", message = DbErrors.Find(ex)!.MessageText });
    }
    catch (Exception ex)
    {
        app.Logger.LogError(ex, "Unhandled error");
        ctx.Response.StatusCode = 500;
        await ctx.Response.WriteAsJsonAsync(new { code = "INTERNAL", message = "Lỗi hệ thống" });
    }
});

app.UseAuthentication();
app.UseAuthorization();
app.UseSwagger();
app.UseSwaggerUI();
app.MapControllers();
app.Run();
