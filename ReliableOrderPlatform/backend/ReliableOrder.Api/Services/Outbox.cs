using Microsoft.EntityFrameworkCore;
using ReliableOrder.Api.Data;

namespace ReliableOrder.Api.Services;

/// <summary>
/// Điểm cắm broker thật (RabbitMQ, Kafka, Azure Service Bus...).
/// Consumer phải idempotent vì giao tin là at-least-once (có thể nhận trùng).
/// </summary>
public interface IMessagePublisher
{
    Task PublishAsync(Guid messageId, string type, string payload, CancellationToken ct);
}

public class LoggingMessagePublisher : IMessagePublisher
{
    private readonly ILogger<LoggingMessagePublisher> _logger;

    public LoggingMessagePublisher(ILogger<LoggingMessagePublisher> logger)
    {
        _logger = logger;
    }

    public Task PublishAsync(Guid messageId, string type, string payload, CancellationToken ct)
    {
        _logger.LogInformation("[OUTBOX] publish {Type} id={Id}", type, messageId);
        return Task.CompletedTask;
    }
}

/// <summary>
/// Worker chạy nền: đọc bảng Outbox, phát tin, lỗi thì retry với thời gian chờ tăng dần,
/// quá số lần cho phép thì đưa vào dead-letter.
/// </summary>
public class OutboxProcessor : BackgroundService
{
    private const int MaxAttempts = 5;

    private readonly IServiceScopeFactory _scopeFactory;
    private readonly IMessagePublisher _publisher;
    private readonly ILogger<OutboxProcessor> _logger;

    public OutboxProcessor(IServiceScopeFactory scopeFactory, IMessagePublisher publisher, ILogger<OutboxProcessor> logger)
    {
        _scopeFactory = scopeFactory;
        _publisher = publisher;
        _logger = logger;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                int processed = await ProcessBatchAsync(stoppingToken);
                if (processed == 0)
                {
                    await Task.Delay(TimeSpan.FromSeconds(2), stoppingToken);
                }
            }
            catch (OperationCanceledException)
            {
                break; // ứng dụng đang tắt
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Outbox loop bị lỗi");
                try { await Task.Delay(TimeSpan.FromSeconds(5), stoppingToken); }
                catch (OperationCanceledException) { break; }
            }
        }
    }

    private async Task<int> ProcessBatchAsync(CancellationToken ct)
    {
        using var scope = _scopeFactory.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();

        DateTime now = DateTime.UtcNow;
        var batch = await db.Outbox
            .Where(m => m.ProcessedAt == null && !m.IsDeadLetter && m.NextAttemptAt <= now)
            .OrderBy(m => m.CreatedAt)
            .Take(20)
            .ToListAsync(ct);

        foreach (var message in batch)
        {
            try
            {
                await _publisher.PublishAsync(message.Id, message.Type, message.Payload, ct);
                message.ProcessedAt = DateTime.UtcNow;
            }
            catch (Exception ex) when (ex is not OperationCanceledException)
            {
                message.Attempts++;
                message.LastError = ex.Message;
                message.NextAttemptAt = DateTime.UtcNow.AddSeconds(Math.Pow(2, message.Attempts));
                if (message.Attempts >= MaxAttempts)
                {
                    message.IsDeadLetter = true;
                    _logger.LogError("Outbox {Id} vào dead-letter", message.Id);
                }
            }
        }

        await db.SaveChangesAsync(ct);
        return batch.Count;
    }
}
