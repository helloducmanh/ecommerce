using EscrowLab.Api.Domain;
using Microsoft.EntityFrameworkCore;

namespace EscrowLab.Api.Data;

public static class Seed
{
    public static readonly Guid AdminId = Guid.Parse("00000000-0000-0000-0000-000000000001");
    public static readonly Guid AliceId = Guid.Parse("00000000-0000-0000-0000-000000000002");
    public static readonly Guid BobId   = Guid.Parse("00000000-0000-0000-0000-000000000003");
    public static readonly Guid CarolId = Guid.Parse("00000000-0000-0000-0000-000000000004");

    /// <summary>Tạo bảng + trigger (schema.sql, chạy lại nhiều lần vẫn an toàn) rồi nạp dữ liệu mẫu.</summary>
    public static async Task RunAsync(AppDbContext db)
    {
        // Giữ MỘT kết nối mở suốt quá trình: advisory lock gắn với kết nối, và mọi câu lệnh EF đều dùng lại kết nối này.
        await db.Database.OpenConnectionAsync();
        try
        {
            // Advisory lock: tránh 2 tiến trình (API + test) chạy migration cùng lúc.
            await db.Database.ExecuteSqlRawAsync("SELECT pg_advisory_lock(727274)");
            try
            {
                var conn = db.Database.GetDbConnection();
                await using (var cmd = conn.CreateCommand())
                {
                    cmd.CommandText = ReadSchema();
                    await cmd.ExecuteNonQueryAsync();
                }

                var users = new[]
                {
                    (AdminId, "Admin", "admin"),
                    (AliceId, "Alice (người mua)", "buyer"),
                    (BobId,   "Bob (người bán)", "seller"),
                    (CarolId, "Carol (người mua)", "buyer"),
                };
                foreach (var (id, name, role) in users)
                {
                    if (!await db.Users.AnyAsync(u => u.Id == id))
                        db.Users.Add(new User { Id = id, Name = name, Role = role });
                }
                await db.SaveChangesAsync();
                foreach (var (id, _, role) in users)
                    if (role != "admin") await EnsureWalletAsync(db, id);

                await EnsureSystemAccountAsync(db, Codes.Escrow, "ESCROW", false);
                await EnsureSystemAccountAsync(db, Codes.FeeRevenue, "FEE", false);
                await EnsureSystemAccountAsync(db, Codes.WithdrawalHold, "WITHDRAWAL_HOLD", false);
                await EnsureSystemAccountAsync(db, Codes.ExternalBank, "EXTERNAL", true);   // được phép âm
            }
            finally
            {
                await db.Database.ExecuteSqlRawAsync("SELECT pg_advisory_unlock(727274)");
            }
        }
        finally
        {
            await db.Database.CloseConnectionAsync();
        }
    }

    public static Task<int> EnsureWalletAsync(AppDbContext db, Guid userId)
    {
        var id = Guid.NewGuid();
        var code = Codes.Wallet(userId);
        return db.Database.ExecuteSqlAsync(
            $@"INSERT INTO accounts (id, code, owner_id, type, allow_negative)
               VALUES ({id}, {code}, {userId}, 'WALLET', false) ON CONFLICT (code) DO NOTHING");
    }

    private static Task<int> EnsureSystemAccountAsync(AppDbContext db, string code, string type, bool allowNegative)
    {
        var id = Guid.NewGuid();
        return db.Database.ExecuteSqlAsync(
            $@"INSERT INTO accounts (id, code, type, allow_negative)
               VALUES ({id}, {code}, {type}, {allowNegative}) ON CONFLICT (code) DO NOTHING");
    }

    private static string ReadSchema()
    {
        var asm = typeof(Seed).Assembly;
        var name = asm.GetManifestResourceNames().First(n => n.EndsWith("schema.sql"));
        using var stream = asm.GetManifestResourceStream(name)!;
        using var reader = new StreamReader(stream);
        return reader.ReadToEnd();
    }
}
