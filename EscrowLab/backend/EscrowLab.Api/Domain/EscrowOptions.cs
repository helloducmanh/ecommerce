namespace EscrowLab.Api.Domain;

public class EscrowOptions
{
    public int FeeBps { get; set; } = 200;                 // 200 bps = 2%
    public int OrderExpiryMinutes { get; set; } = 60;
    public int AutoReleaseDays { get; set; } = 7;
    public string WebhookSecret { get; set; } = "whsec_dev_change_me";
    public int WebhookToleranceSeconds { get; set; } = 300;
    public bool EnableDevEndpoints { get; set; } = true;

    // --- SePay + tài khoản nhận tiền (dùng để sinh QR) ---
    public string SePayApiKey { get; set; } = "";          // khoá bạn đặt khi tạo webhook trên SePay
    public string BankName { get; set; } = "MBBank";       // tên ngắn theo qr.sepay.vn/banks.json
    public string BankAccountNumber { get; set; } = "";
    public string AccountHolder { get; set; } = "";
}
