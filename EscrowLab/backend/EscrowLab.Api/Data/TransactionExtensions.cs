using EscrowLab.Api.Domain;
using Microsoft.EntityFrameworkCore;
using Npgsql;

namespace EscrowLab.Api.Data;

public static class DbErrors
{
    /// <summary>Tìm PostgresException trong chuỗi InnerException (EF bọc lỗi của SaveChanges trong DbUpdateException).</summary>
    public static PostgresException? Find(Exception? ex)
    {
        for (; ex is not null; ex = ex.InnerException)
            if (ex is PostgresException pg) return pg;
        return null;
    }

    /// <summary>40001 = serialization failure, 40P01 = deadlock: an toàn để chạy lại cả giao dịch.</summary>
    public static bool IsRetryable(Exception ex) => Find(ex)?.SqlState is "40001" or "40P01";
}

public static class TransactionExtensions
{
    /// <summary>
    /// Chạy một đơn vị công việc trong 1 transaction. Tự thử lại (tối đa 3 lần) nếu gặp deadlock hoặc serialization failure.
    /// Mỗi lần thử bắt đầu với ChangeTracker sạch, nên dữ liệu đọc sau khi khoá dòng luôn là bản mới nhất.
    /// Exception khác sẽ rollback rồi ném ra ngoài.
    /// </summary>
    public static async Task<T> InTransactionAsync<T>(this AppDbContext db, Func<Task<T>> work, CancellationToken ct = default)
    {
        for (var attempt = 1; ; attempt++)
        {
            db.ChangeTracker.Clear();
            await using var tx = await db.Database.BeginTransactionAsync(ct);
            try
            {
                var result = await work();
                await tx.CommitAsync(ct);
                return result;
            }
            catch (Exception ex)
            {
                db.ChangeTracker.Clear();   // bỏ các thay đổi chưa lưu của lần thử hỏng
                if (attempt >= 3 || !DbErrors.IsRetryable(ex)) throw;
                await Task.Delay(50 * attempt, ct);   // tx được rollback khi thoát vòng lặp này (Dispose)
            }
        }
    }

    public static async Task InTransactionAsync(this AppDbContext db, Func<Task> work, CancellationToken ct = default) =>
        await db.InTransactionAsync(async () => { await work(); return true; }, ct);
}
