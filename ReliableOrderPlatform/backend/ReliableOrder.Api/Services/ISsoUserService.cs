using ReliableOrder.Api.Services.Dtos;

namespace ReliableOrder.Api.Services;

public interface ISsoUserService
{
    // Map danh tính SSO đã xác thực sang user nội bộ.
    // Trả null nếu không có user/tenant hợp lệ.
    Task<LoginUser?> ResolveAsync(
        string issuer,
        string subject,
        CancellationToken cancellationToken);

    // Kiểm tra user còn hoạt động và còn thuộc tenant.
    Task<bool> CanAccessTenantAsync(
        Guid userId,
        Guid tenantId,
        CancellationToken cancellationToken);

    // Trả user và quyền hiện tại từ DB.
    // Trả null nếu user bị khóa/xóa hoặc không còn thuộc tenant.
    Task<CurrentUserDto?> GetCurrentUserAsync(
        Guid userId,
        Guid tenantId,
        CancellationToken cancellationToken);
}
