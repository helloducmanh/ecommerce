namespace ReliableOrder.Api.Services.Dtos;

public class CurrentUserDto
{
    public Guid UserId { get; set; }
    public   Guid TenantId { get; set; }
    public string FullName { get; set; }
    private readonly List<PermissionDto> _permissions = [];
    public IReadOnlyList<PermissionDto> Permissions; 
    
    public CurrentUserDto()
    {
        Permissions = _permissions.AsReadOnly();
    }

    public void AddPermission(PermissionDto permission)
    {
        ArgumentNullException.ThrowIfNull(permission);
        _permissions.Add(permission);
    } 
}