namespace ReliableOrder.Api.Controllers.Dtos;

public class ServiceResponse
{
    public string Message { get; set; }
    public bool Success { get; set; }
    public object Data { get; set; }
    public ServiceResponseCode Code { get; set; }
}

public enum ServiceResponseCode
{
    Success = 0,
    NotPermission = 1,
    LicenseExpire = 2,
    NotPermissionUpload = 5,
    NotFound = 6,
    Error = 99,
    Exception = 999
}