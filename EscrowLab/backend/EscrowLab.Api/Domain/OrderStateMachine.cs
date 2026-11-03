namespace EscrowLab.Api.Domain;

public static class OrderStatus
{
    public const string Created = "CREATED";
    public const string Held = "HELD";           // tiền đã nằm trong escrow
    public const string Shipped = "SHIPPED";
    public const string Disputed = "DISPUTED";
    public const string Released = "RELEASED";   // đã trả cho người bán
    public const string Refunded = "REFUNDED";   // đã hoàn cho người mua
    public const string Cancelled = "CANCELLED";
    public const string Expired = "EXPIRED";
}

/// <summary>Chỉ những chuyển trạng thái được liệt kê ở đây mới hợp lệ.</summary>
public static class OrderStateMachine
{
    private static readonly Dictionary<string, string[]> Transitions = new()
    {
        [OrderStatus.Created]  = new[] { OrderStatus.Held, OrderStatus.Cancelled, OrderStatus.Expired },
        [OrderStatus.Held]     = new[] { OrderStatus.Shipped, OrderStatus.Refunded, OrderStatus.Disputed },
        [OrderStatus.Shipped]  = new[] { OrderStatus.Released, OrderStatus.Disputed },
        [OrderStatus.Disputed] = new[] { OrderStatus.Released, OrderStatus.Refunded },
    };

    public static bool CanTransition(string from, string to) =>
        Transitions.TryGetValue(from, out var next) && next.Contains(to);

    public static void EnsureTransition(string from, string to)
    {
        if (!CanTransition(from, to))
            throw new DomainException("INVALID_TRANSITION", $"Không thể chuyển đơn từ {from} sang {to}", 409);
    }
}

public static class Fees
{
    /// <summary>Phí theo basis point, làm tròn xuống. Luôn dùng số nguyên cho tiền.</summary>
    public static long Calculate(long amount, int bps) => amount * bps / 10_000;
}
