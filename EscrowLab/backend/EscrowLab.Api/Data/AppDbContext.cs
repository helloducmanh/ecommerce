using System.Text.RegularExpressions;
using EscrowLab.Api.Domain;
using Microsoft.EntityFrameworkCore;

namespace EscrowLab.Api.Data;

/// <summary>
/// Bảng, cột, ràng buộc CHECK và trigger bảo vệ sổ cái được tạo bởi Data/schema.sql (xem Seed.RunAsync),
/// KHÔNG phải EnsureCreated, vì EF không sinh được trigger (sổ cái append-only, giao dịch luôn cân bằng).
/// DbContext chỉ ánh xạ lại các bảng đó.
/// </summary>
public class AppDbContext : DbContext
{
    public AppDbContext(DbContextOptions<AppDbContext> options) : base(options)
    {
    }

    public DbSet<User> Users => Set<User>();
    public DbSet<Account> Accounts => Set<Account>();
    public DbSet<LedgerTransaction> LedgerTransactions => Set<LedgerTransaction>();
    public DbSet<LedgerEntry> LedgerEntries => Set<LedgerEntry>();
    public DbSet<Order> Orders => Set<Order>();
    public DbSet<OrderEvent> OrderEvents => Set<OrderEvent>();
    public DbSet<PaymentIntent> PaymentIntents => Set<PaymentIntent>();
    public DbSet<WebhookEvent> WebhookEvents => Set<WebhookEvent>();
    public DbSet<Withdrawal> Withdrawals => Set<Withdrawal>();

    protected override void OnModelCreating(ModelBuilder b)
    {
        b.Entity<User>().ToTable("users");
        b.Entity<Account>().ToTable("accounts");
        b.Entity<LedgerTransaction>().ToTable("ledger_transactions");
        b.Entity<LedgerEntry>(e => { e.ToTable("ledger_entries"); e.Property(x => x.Id).ValueGeneratedOnAdd(); });   // bigserial
        b.Entity<Order>().ToTable("orders");
        b.Entity<OrderEvent>(e => { e.ToTable("order_events"); e.Property(x => x.Id).ValueGeneratedOnAdd(); });     // bigserial
        b.Entity<PaymentIntent>().ToTable("payment_intents");
        b.Entity<WebhookEvent>(e => { e.ToTable("webhook_events"); e.HasKey(x => x.EventId); });
        b.Entity<Withdrawal>().ToTable("withdrawals");

        // PascalCase (BalanceAfter) -> snake_case (balance_after), khớp với schema.sql.
        foreach (var entity in b.Model.GetEntityTypes())
            foreach (var p in entity.GetProperties())
                p.SetColumnName(ToSnake(p.Name));
    }

    private static string ToSnake(string name) =>
        Regex.Replace(name, "(?<=[a-z0-9])(?=[A-Z])", "_").ToLowerInvariant();
}
