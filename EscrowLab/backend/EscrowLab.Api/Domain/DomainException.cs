namespace EscrowLab.Api.Domain;

/// <summary>Lỗi nghiệp vụ. Program.cs biến nó thành JSON { code, message } với HTTP status tương ứng.</summary>
public class DomainException : Exception
{
    public string Code { get; }
    public int Status { get; }
    public DomainException(string code, string message, int status = 400) : base(message)
    {
        Code = code;
        Status = status;
    }
}
