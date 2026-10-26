using Microsoft.EntityFrameworkCore;
using ReliableOrder.Api.Data;
using ReliableOrder.Api.Services;

var builder = WebApplication.CreateBuilder(args);

// Đăng ký các service (Dependency Injection)
builder.Services.AddControllers();
builder.Services.AddDbContext<AppDbContext>(options =>
    options.UseSqlite(builder.Configuration.GetConnectionString("Default")));
builder.Services.AddScoped<OrderService>();
builder.Services.AddSingleton<IMessagePublisher, LoggingMessagePublisher>(); // thay bằng RabbitMQ/Kafka khi cần
builder.Services.AddHostedService<OutboxProcessor>();
builder.Services.AddCors(options =>
{
    options.AddDefaultPolicy(policy =>
        policy.WithOrigins("http://localhost:5173").AllowAnyHeader().AllowAnyMethod());
});
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

var app = builder.Build();

// Tạo database và dữ liệu mẫu khi khởi động
using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
    db.Database.EnsureCreated(); // Production: chuyển sang EF Migrations
    await Seed.RunAsync(db);
}

app.UseCors();
app.UseSwagger();
app.UseSwaggerUI();
app.MapControllers();
app.Run();
