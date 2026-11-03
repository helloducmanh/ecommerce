using EscrowLab.Api.Domain;
using EscrowLab.Api.Services;

namespace EscrowLab.Tests;

public class OrderStateMachineTests
{
    [Theory]
    [InlineData("CREATED", "HELD")]
    [InlineData("HELD", "SHIPPED")]
    [InlineData("SHIPPED", "RELEASED")]
    [InlineData("HELD", "DISPUTED")]
    [InlineData("DISPUTED", "REFUNDED")]
    public void Valid_transitions_are_allowed(string from, string to) =>
        Assert.True(OrderStateMachine.CanTransition(from, to));

    [Theory]
    [InlineData("CREATED", "RELEASED")]   // không được giải ngân khi chưa trả tiền
    [InlineData("RELEASED", "REFUNDED")]  // đã trả người bán thì không hoàn lại được
    [InlineData("REFUNDED", "RELEASED")]
    [InlineData("SHIPPED", "REFUNDED")]
    [InlineData("EXPIRED", "HELD")]
    public void Invalid_transitions_are_rejected(string from, string to)
    {
        Assert.False(OrderStateMachine.CanTransition(from, to));
        Assert.Throws<DomainException>(() => OrderStateMachine.EnsureTransition(from, to));
    }
}

public class FeeTests
{
    [Theory]
    [InlineData(100_000, 200, 2_000)]
    [InlineData(10_000, 200, 200)]
    [InlineData(99_999, 200, 1_999)]   // làm tròn xuống
    [InlineData(100_000, 0, 0)]
    public void Fee_uses_integer_math(long amount, int bps, long expected) =>
        Assert.Equal(expected, Fees.Calculate(amount, bps));
}

public class WebhookSignerTests
{
    private const string Secret = "whsec_test";
    private static readonly DateTimeOffset Now = DateTimeOffset.FromUnixTimeSeconds(1_800_000_000);
    private static readonly TimeSpan Tol = TimeSpan.FromMinutes(5);

    [Fact]
    public void Valid_signature_passes()
    {
        var header = WebhookSigner.BuildHeader(Secret, Now.ToUnixTimeSeconds(), "{\"a\":1}");
        Assert.True(WebhookSigner.Verify(Secret, header, "{\"a\":1}", Now, Tol, out _));
    }

    [Fact]
    public void Tampered_body_fails()
    {
        var header = WebhookSigner.BuildHeader(Secret, Now.ToUnixTimeSeconds(), "{\"amount\":100}");
        Assert.False(WebhookSigner.Verify(Secret, header, "{\"amount\":999}", Now, Tol, out _));
    }

    [Fact]
    public void Wrong_secret_fails()
    {
        var header = WebhookSigner.BuildHeader("other", Now.ToUnixTimeSeconds(), "x");
        Assert.False(WebhookSigner.Verify(Secret, header, "x", Now, Tol, out _));
    }

    [Fact]
    public void Old_timestamp_is_rejected_as_replay()
    {
        var old = Now.AddMinutes(-10).ToUnixTimeSeconds();
        var header = WebhookSigner.BuildHeader(Secret, old, "x");
        Assert.False(WebhookSigner.Verify(Secret, header, "x", Now, Tol, out var reason));
        Assert.Contains("Timestamp", reason);
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("garbage")]
    public void Missing_or_malformed_header_fails(string? header) =>
        Assert.False(WebhookSigner.Verify(Secret, header, "x", Now, Tol, out _));
}
