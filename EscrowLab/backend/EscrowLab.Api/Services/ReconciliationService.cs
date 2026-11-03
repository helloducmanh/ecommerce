using EscrowLab.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace EscrowLab.Api.Services;

public record StatementLine(string ProviderRef, long Amount);
public record AmountMismatch(string ProviderRef, long Ours, long Theirs);
public class DriftRow { public string Code { get; set; } = ""; public long Balance { get; set; } public long Ledger { get; set; } }

public record ReconciliationReport(
    int StatementLines, int OurSucceeded, int Matched,
    List<string> MissingInLedger, List<string> MissingInStatement, List<AmountMismatch> AmountMismatches,
    long LedgerTotal, List<DriftRow> BalanceDrift, List<Guid> UnbalancedTransactions, bool AllGood);

/// <summary>
/// Đối soát = so sánh nguồn sự thật của ta với nguồn bên ngoài (sao kê gateway),
/// cộng thêm kiểm tra tính toàn vẹn nội bộ của sổ cái.
/// </summary>
public class ReconciliationService
{
    private readonly AppDbContext _db;
    public ReconciliationService(AppDbContext db) => _db = db;

    public async Task<ReconciliationReport> RunAsync(IReadOnlyList<StatementLine> lines)
    {
        var ours = (await _db.PaymentIntents.AsNoTracking()
            .Where(p => p.Status == "SUCCEEDED")
            .Select(p => new { p.ProviderRef, p.Amount })
            .ToListAsync()).ToDictionary(x => x.ProviderRef, x => x.Amount);
        var theirs = lines.GroupBy(l => l.ProviderRef).ToDictionary(g => g.Key, g => g.Sum(x => x.Amount));

        var missingInLedger = theirs.Keys.Where(k => !ours.ContainsKey(k)).OrderBy(k => k).ToList();
        var missingInStatement = ours.Keys.Where(k => !theirs.ContainsKey(k)).OrderBy(k => k).ToList();
        var mismatches = ours.Where(o => theirs.TryGetValue(o.Key, out var t) && t != o.Value)
            .Select(o => new AmountMismatch(o.Key, o.Value, theirs[o.Key])).ToList();
        var matched = ours.Count(o => theirs.TryGetValue(o.Key, out var t) && t == o.Value);

        var total = await _db.LedgerEntries.AsNoTracking().SumAsync(e => (long?)e.Amount) ?? 0;

        // Số dư cache của account phải bằng tổng các dòng sổ cái của nó.
        var drift = await _db.Database.SqlQueryRaw<DriftRow>(
            @"SELECT a.code AS ""Code"", a.balance AS ""Balance"", COALESCE(SUM(e.amount), 0)::bigint AS ""Ledger""
              FROM accounts a LEFT JOIN ledger_entries e ON e.account_id = a.id
              GROUP BY a.id, a.code, a.balance
              HAVING a.balance <> COALESCE(SUM(e.amount), 0)").ToListAsync();

        var unbalanced = await _db.LedgerEntries.AsNoTracking()
            .GroupBy(e => e.TransactionId)
            .Where(g => g.Sum(x => x.Amount) != 0)
            .Select(g => g.Key)
            .ToListAsync();

        var good = missingInLedger.Count == 0 && missingInStatement.Count == 0 && mismatches.Count == 0
                   && total == 0 && drift.Count == 0 && unbalanced.Count == 0;
        return new ReconciliationReport(lines.Count, ours.Count, matched, missingInLedger, missingInStatement,
            mismatches, total, drift, unbalanced, good);
    }

    /// <summary>Tạo "sao kê gateway" giả từ dữ liệu của ta, có thể cố ý làm sai để học đối soát.</summary>
    public async Task<List<StatementLine>> BuildDemoStatementAsync(string tamper)
    {
        var rows = await _db.PaymentIntents.AsNoTracking()
            .Where(p => p.Status == "SUCCEEDED")
            .OrderBy(p => p.CreatedAt)
            .Select(p => new { p.ProviderRef, p.Amount })
            .ToListAsync();
        var lines = rows.Select(x => new StatementLine(x.ProviderRef, x.Amount)).ToList();

        switch (tamper)
        {
            case "drop" when lines.Count > 0: lines.RemoveAt(lines.Count - 1); break;
            case "alter" when lines.Count > 0: lines[0] = lines[0] with { Amount = lines[0].Amount + 1000 }; break;
            case "phantom": lines.Add(new StatementLine("MOCK-PHANTOM0001", 50_000)); break;
        }
        return lines;
    }
}
