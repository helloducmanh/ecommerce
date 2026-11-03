using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.ChangeTracking;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using ReliableOrder.Api.Domain;

namespace ReliableOrder.Api.Data;

/// <summary>Tiện ích chuyển object <-> JSON, dùng để lưu Dictionary/List vào 1 cột text.</summary>
public static class Js
{
    public static string To<T>(T value)
    {
        return JsonSerializer.Serialize(value);
    }

    public static T From<T>(string json) where T : class, new()
    {
        T? result = JsonSerializer.Deserialize<T>(json);
        return result ?? new T();
    }

    public static T Clone<T>(T value) where T : class, new()
    {
        return From<T>(To(value));
    }
}

public static class ModelExtensions
{
    /// <summary>Lưu Dictionary/List dưới dạng JSON trong 1 cột, để schema vẫn "động".</summary>
    public static PropertyBuilder<T> HasJson<T>(this PropertyBuilder<T> builder) where T : class, new()
    {
        builder.HasConversion(
            v => Js.To(v),
            s => Js.From<T>(s),
            new ValueComparer<T>(
                (a, b) => Js.To(a) == Js.To(b),
                v => Js.To(v).GetHashCode(),
                v => Js.Clone(v)));
        return builder;
    }
}

public class AppDbContext : DbContext
{
    public AppDbContext(DbContextOptions<AppDbContext> options) : base(options)
    {
    }

    public DbSet<Product> Products => Set<Product>();
    public DbSet<ProductOption> ProductOptions => Set<ProductOption>();
    public DbSet<ProductVariant> Variants => Set<ProductVariant>();
    public DbSet<Order> Orders => Set<Order>();
    public DbSet<OrderItem> OrderItems => Set<OrderItem>();
    public DbSet<IdempotencyRecord> IdempotencyRecords => Set<IdempotencyRecord>();
    public DbSet<OutboxMessage> Outbox => Set<OutboxMessage>();

    protected override void OnModelCreating(ModelBuilder b)
    {
        b.Entity<Product>(e =>
        {
            e.Property(x => x.Attributes).HasJson();
            e.HasMany(x => x.Options).WithOne().HasForeignKey(o => o.ProductId).OnDelete(DeleteBehavior.Cascade);
            e.HasMany(x => x.Variants).WithOne(v => v.Product).HasForeignKey(v => v.ProductId).OnDelete(DeleteBehavior.Cascade);
        });

        b.Entity<ProductOption>().Property(x => x.Values).HasJson();

        b.Entity<ProductVariant>(e =>
        {
            e.Property(x => x.OptionValues).HasJson();
            e.HasIndex(x => x.Sku).IsUnique();
        });

        b.Entity<Order>(e =>
        {
            e.HasIndex(x => x.OrderNumber).IsUnique();
            e.HasMany(x => x.Items).WithOne().HasForeignKey(i => i.OrderId).OnDelete(DeleteBehavior.Cascade);
        });

        b.Entity<OrderItem>().Property(x => x.OptionValues).HasJson();
        b.Entity<IdempotencyRecord>().HasKey(x => x.Key);
        b.Entity<OutboxMessage>().HasIndex(x => new { x.ProcessedAt, x.IsDeadLetter, x.NextAttemptAt });
    }
}
