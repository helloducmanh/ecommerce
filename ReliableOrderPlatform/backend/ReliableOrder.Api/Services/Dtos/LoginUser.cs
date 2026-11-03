namespace ReliableOrder.Api.Services.Dtos;

public class LoginUser
{
    public Guid UserId { get; set; }
    public Guid TenantId { get; set; }
    public string FullName { get; set; }
}