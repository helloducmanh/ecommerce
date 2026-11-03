namespace ReliableOrder.Api.Services.Dtos;

public class PermissionDto
{
    public string SubSystemCode { get; set; }
    public string PermissionCode { get; set; }
    public int DataScope { get; set; }
    public Guid? OrganizationUnitId { get; set; }
}