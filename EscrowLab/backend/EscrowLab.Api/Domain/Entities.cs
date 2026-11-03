namespace EscrowLab.Api.Domain;

// Các entity ánh xạ 1-1 với bảng trong Data/schema.sql (tên cột snake_case do AppDbContext tự đổi).

public class User
{
    public Guid Id { get; set; }
    public string Name { get; set; } = "";
    /// <summary>buyer | seller | admin</summary>
    public string Role { get; set; } = "";
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}

/// <summary>Một "nơi chứa tiền": ví người dùng hoặc tài khoản hệ thống (ESCROW, FEE_REVENUE, ...).</summary>
public class Account
{
    public Guid Id { get; set; }
    public string Code { get; set; } = "";
    public Guid? OwnerId { get; set; }
    public string Type { get; set; } = "";
    public string Currency { get; set; } = "VND";
    public bool AllowNegative { get; set; }
    /// <summary>Số dư cache, luôn bằng SUM(ledger_entries.amount) của tài khoản này.</summary>
    public long Balance { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}

public class LedgerTransaction
{
    public Guid Id { get; set; }
    public string IdempotencyKey { get; set; } = "";
    public string Type { get; set; } = "";
    public string? ReferenceType { get; set; }
    public Guid? ReferenceId { get; set; }
    public string? Description { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}

public class LedgerEntry
{
    public long Id { get; set; }
    public Guid TransactionId { get; set; }
    public Guid AccountId { get; set; }
    public long Amount { get; set; }
    public long BalanceAfter { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}

public class Order
{
    public Guid Id { get; set; }
    public Guid BuyerId { get; set; }
    public Guid SellerId { get; set; }
    public string Title { get; set; } = "";
    public long Amount { get; set; }
    public long Fee { get; set; }
    public string Status { get; set; } = "";
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime ExpiresAt { get; set; }
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
    public DateTime? ShippedAt { get; set; }
}

public class OrderEvent
{
    public long Id { get; set; }
    public Guid OrderId { get; set; }
    public string? FromStatus { get; set; }
    public string ToStatus { get; set; } = "";
    public Guid? ActorId { get; set; }
    public string? Note { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}

public class PaymentIntent
{
    public Guid Id { get; set; }
    public Guid UserId { get; set; }
    public long Amount { get; set; }
    /// <summary>PENDING | SUCCEEDED | FAILED | REVIEW</summary>
    public string Status { get; set; } = "";
    public string ProviderRef { get; set; } = "";
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}

/// <summary>Mỗi event_id của gateway chỉ được xử lý một lần.</summary>
public class WebhookEvent
{
    public string EventId { get; set; } = "";
    public string Payload { get; set; } = "";
    public DateTime ReceivedAt { get; set; } = DateTime.UtcNow;
}

public class Withdrawal
{
    public Guid Id { get; set; }
    public Guid UserId { get; set; }
    public long Amount { get; set; }
    public string BankAccount { get; set; } = "";
    /// <summary>PENDING | COMPLETED | REJECTED</summary>
    public string Status { get; set; } = "";
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}

public static class Codes
{
    public const string Escrow = "ESCROW";
    public const string FeeRevenue = "FEE_REVENUE";
    public const string ExternalBank = "EXTERNAL_BANK";
    public const string WithdrawalHold = "WITHDRAWAL_HOLD";
    public static string Wallet(Guid userId) => $"WALLET:{userId}";
}
