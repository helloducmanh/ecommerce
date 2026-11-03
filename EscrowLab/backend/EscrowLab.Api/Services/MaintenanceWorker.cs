namespace EscrowLab.Api.Services;

/// <summary>Mỗi 30 giây: huỷ đơn quá hạn thanh toán và tự giải ngân đơn đã giao quá lâu.</summary>
public class MaintenanceWorker : BackgroundService
{
    private readonly IServiceScopeFactory _scopes;
    private readonly ILogger<MaintenanceWorker> _log;

    public MaintenanceWorker(IServiceScopeFactory scopes, ILogger<MaintenanceWorker> log)
    {
        _scopes = scopes;
        _log = log;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        await Task.Delay(TimeSpan.FromSeconds(5), stoppingToken);
        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                // OrderService/AppDbContext là scoped, nên mỗi vòng lặp dùng một scope riêng.
                using var scope = _scopes.CreateScope();
                var orders = scope.ServiceProvider.GetRequiredService<OrderService>();
                var (expired, released) = await orders.RunMaintenanceAsync();
                if (expired + released > 0)
                    _log.LogInformation("Maintenance: {Expired} đơn hết hạn, {Released} đơn tự giải ngân", expired, released);
            }
            catch (Exception ex) when (ex is not OperationCanceledException)
            {
                _log.LogError(ex, "Maintenance lỗi");
            }
            await Task.Delay(TimeSpan.FromSeconds(30), stoppingToken);
        }
    }
}
