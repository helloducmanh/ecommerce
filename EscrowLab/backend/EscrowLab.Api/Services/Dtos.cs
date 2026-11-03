using EscrowLab.Api.Domain;

namespace EscrowLab.Api.Services;

// ===== Request =====

public record CreateOrderRequest(Guid SellerId, string Title, long Amount);
public record ResolveRequest(string Decision);
public record AmountRequest(long Amount);
public record WithdrawRequest(long Amount, string BankAccount);
public record ReconcileRequest(List<StatementLine> Lines);
public record TimeTravelRequest(int Hours);

// ===== Response (hình dạng JSON giữ nguyên như trước, frontend không phải sửa) =====

public class UserDto
{
    public Guid Id { get; set; }
    public string Name { get; set; } = "";
    public string Role { get; set; } = "";
}

public class OrderDto
{
    public Guid Id { get; set; }
    public Guid BuyerId { get; set; }
    public Guid SellerId { get; set; }
    public string Title { get; set; } = "";
    public long Amount { get; set; }
    public long Fee { get; set; }
    public string Status { get; set; } = "";
    public DateTime CreatedAt { get; set; }
    public DateTime ExpiresAt { get; set; }
    public DateTime UpdatedAt { get; set; }
    public DateTime? ShippedAt { get; set; }
    public string? BuyerName { get; set; }
    public string? SellerName { get; set; }
}

public class OrderEventDto
{
    public long Id { get; set; }
    public Guid OrderId { get; set; }
    public string? FromStatus { get; set; }
    public string ToStatus { get; set; } = "";
    public Guid? ActorId { get; set; }
    public string? ActorName { get; set; }
    public string? Note { get; set; }
    public DateTime CreatedAt { get; set; }
}

public class TransferInfo
{
    public string BankName { get; set; } = "";
    public string AccountNumber { get; set; } = "";
    public string AccountHolder { get; set; } = "";
    public long Amount { get; set; }
    public string Content { get; set; } = "";
    public string QrUrl { get; set; } = "";
}

public class PaymentIntentDto
{
    public Guid Id { get; set; }
    public Guid UserId { get; set; }
    public long Amount { get; set; }
    public string Status { get; set; } = "";
    public string ProviderRef { get; set; } = "";
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
    public string? UserName { get; set; }
    public TransferInfo? Transfer { get; set; }   // chỉ có khi còn PENDING
}

public class WithdrawalDto
{
    public Guid Id { get; set; }
    public Guid UserId { get; set; }
    public long Amount { get; set; }
    public string BankAccount { get; set; } = "";
    public string Status { get; set; } = "";
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
    public string? UserName { get; set; }
}

public class AccountView
{
    public Guid Id { get; set; }
    public string Code { get; set; } = "";
    public string Type { get; set; } = "";
    public long Balance { get; set; }
    public bool AllowNegative { get; set; }
    public string? OwnerName { get; set; }
}

public class LedgerEntryView
{
    public long Id { get; set; }
    public Guid TransactionId { get; set; }
    public string Type { get; set; } = "";
    public string? Description { get; set; }
    public string AccountCode { get; set; } = "";
    public long Amount { get; set; }
    public long BalanceAfter { get; set; }
    public DateTime CreatedAt { get; set; }
}

/// <summary>Chuyển entity (database) sang DTO (trả về cho client).</summary>
public static class Mapper
{
    public static OrderDto ToOrderDto(Order o, string? buyerName = null, string? sellerName = null) => new()
    {
        Id = o.Id, BuyerId = o.BuyerId, SellerId = o.SellerId, Title = o.Title,
        Amount = o.Amount, Fee = o.Fee, Status = o.Status,
        CreatedAt = o.CreatedAt, ExpiresAt = o.ExpiresAt, UpdatedAt = o.UpdatedAt, ShippedAt = o.ShippedAt,
        BuyerName = buyerName, SellerName = sellerName,
    };

    public static PaymentIntentDto ToPaymentIntentDto(PaymentIntent p, string? userName = null) => new()
    {
        Id = p.Id, UserId = p.UserId, Amount = p.Amount, Status = p.Status, ProviderRef = p.ProviderRef,
        CreatedAt = p.CreatedAt, UpdatedAt = p.UpdatedAt, UserName = userName,
    };

    public static WithdrawalDto ToWithdrawalDto(Withdrawal w, string? userName = null) => new()
    {
        Id = w.Id, UserId = w.UserId, Amount = w.Amount, BankAccount = w.BankAccount, Status = w.Status,
        CreatedAt = w.CreatedAt, UpdatedAt = w.UpdatedAt, UserName = userName,
    };
}
