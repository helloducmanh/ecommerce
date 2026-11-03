using EscrowLab.Api.Data;
using EscrowLab.Api.Domain;
using EscrowLab.Api.Services;
using Microsoft.EntityFrameworkCore;

namespace EscrowLab.Tests;

/// <summary>
/// Test tích hợp cần PostgreSQL thật. Đặt biến môi trường để bật:
///   ESCROW_TEST_DB="Host=localhost;Port=5433;Database=escrowlab;Username=escrow;Password=escrow"
/// Nếu không đặt, các test này tự bỏ qua (pass ngay).
/// </summary>
public class LedgerIntegrationTests
{
    private static readonly string? Cs = Environment.GetEnvironmentVariable("ESCROW_TEST_DB");

    // DbContext không thread-safe: mỗi tác vụ song song phải có DbContext riêng (giống mỗi HTTP request một scope).
    private static AppDbContext NewDb() =>
        new(new DbContextOptionsBuilder<AppDbContext>().UseNpgsql(Cs!).Options);

    private static async Task<Guid> SetupAsync(long initialBalance)
    {
        await using var db = NewDb();
        await Seed.RunAsync(db);
        var userId = Guid.NewGuid();
        db.Users.Add(new User { Id = userId, Name = "test", Role = "buyer" });
        await db.SaveChangesAsync();
        await Seed.EnsureWalletAsync(db, userId);

        if (initialBalance > 0)
        {
            var ledger = new LedgerService(db);
            await db.InTransactionAsync(() => ledger.PostAsync($"test-deposit:{userId}", "DEPOSIT", null, null, null,
                new[] { new PostingLeg(Codes.ExternalBank, -initialBalance), new PostingLeg(Codes.Wallet(userId), initialBalance) }));
        }
        return userId;
    }

    private static async Task<long> BalanceAsync(Guid userId)
    {
        await using var db = NewDb();
        var code = Codes.Wallet(userId);
        return await db.Accounts.AsNoTracking().Where(a => a.Code == code).Select(a => a.Balance).SingleAsync();
    }

    [Fact]
    public async Task Parallel_spending_never_overdraws_the_wallet()
    {
        if (Cs is null) return;
        var userId = await SetupAsync(100_000);

        // 10 yêu cầu song song, mỗi yêu cầu tiêu 30.000: đúng 3 cái được thành công.
        var tasks = Enumerable.Range(0, 10).Select(async i =>
        {
            await using var db = NewDb();
            var ledger = new LedgerService(db);
            try
            {
                await db.InTransactionAsync(() => ledger.PostAsync($"spend:{userId}:{i}", "TEST_SPEND", null, null, null,
                    new[] { new PostingLeg(Codes.Wallet(userId), -30_000), new PostingLeg(Codes.Escrow, 30_000) }));
                return true;
            }
            catch (DomainException ex) when (ex.Code == "INSUFFICIENT_FUNDS")
            {
                return false;
            }
        });
        var results = await Task.WhenAll(tasks);

        Assert.Equal(3, results.Count(r => r));
        Assert.Equal(10_000, await BalanceAsync(userId));
    }

    [Fact]
    public async Task Same_idempotency_key_posts_only_once()
    {
        if (Cs is null) return;
        var userId = await SetupAsync(50_000);
        var legs = new[] { new PostingLeg(Codes.Wallet(userId), -10_000), new PostingLeg(Codes.Escrow, 10_000) };
        var key = $"idem:{userId}";

        await using var db = NewDb();
        var ledger = new LedgerService(db);
        var first = await db.InTransactionAsync(() => ledger.PostAsync(key, "TEST", null, null, null, legs));
        var second = await db.InTransactionAsync(() => ledger.PostAsync(key, "TEST", null, null, null, legs));

        Assert.False(first.Replayed);
        Assert.True(second.Replayed);
        Assert.Equal(first.TransactionId, second.TransactionId);
        Assert.Equal(40_000, await BalanceAsync(userId));
    }

    [Fact]
    public async Task Unbalanced_posting_is_rejected()
    {
        if (Cs is null) return;
        var userId = await SetupAsync(50_000);
        await using var db = NewDb();
        var ledger = new LedgerService(db);
        await Assert.ThrowsAsync<DomainException>(() =>
            db.InTransactionAsync(() => ledger.PostAsync($"bad:{userId}", "TEST", null, null, null,
                new[] { new PostingLeg(Codes.Wallet(userId), -10_000), new PostingLeg(Codes.Escrow, 9_000) })));
    }

    [Fact]
    public async Task Ledger_entries_cannot_be_modified()
    {
        if (Cs is null) return;
        await SetupAsync(10_000);
        await using var db = NewDb();

        var update = await Record.ExceptionAsync(() => db.Database.ExecuteSqlRawAsync("UPDATE ledger_entries SET amount = amount + 1"));
        var delete = await Record.ExceptionAsync(() => db.Database.ExecuteSqlRawAsync("DELETE FROM ledger_entries"));

        Assert.NotNull(DbErrors.Find(update));   // trigger forbid_mutation chặn ở tầng DB
        Assert.NotNull(DbErrors.Find(delete));
    }
}
