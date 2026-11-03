using Microsoft.EntityFrameworkCore;
using Microsoft.OpenApi.Models;
using ReliableOrder.Api.Auth;
using ReliableOrder.Api.Data;
using ReliableOrder.Api.Services;

var builder = WebApplication.CreateBuilder(args);

// Đăng ký các service (Dependency Injection)
builder.Services.AddControllers();
builder.Services.AddDbContext<AppDbContext>(options =>
    options.UseNpgsql(builder.Configuration.GetConnectionString("Default")));
builder.Services.AddScoped<OrderService>();
builder.Services.AddSingleton<IMessagePublisher, LoggingMessagePublisher>(); // thay bằng RabbitMQ/Kafka khi cần
builder.Services.AddHostedService<OutboxProcessor>();
builder.Services.AddCors(options =>
{
    options.AddDefaultPolicy(policy =>
        policy.WithOrigins("http://localhost:5173").AllowAnyHeader().AllowAnyMethod());
});
builder.Services.AddSso(builder.Configuration);
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

// Tạo database và dữ liệu mẫu khi khởi động
using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
    db.Database.EnsureCreated(); // Production: chuyển sang EF Migrations
    await Seed.RunAsync(db);
}

app.UseCors();
app.UseAuthentication();
app.UseAuthorization();
app.UseSwagger();
app.UseSwaggerUI();
app.MapControllers();
app.Run();
