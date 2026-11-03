using EscrowLab.Api.Domain;
using EscrowLab.Api.Services;

namespace EscrowLab.Tests;

public class SePayHelperTests
{
    [Fact]
    public void Extracts_ref_from_content_regardless_of_case_and_noise()
    {
        var r = SePayHelper.ExtractRef(null, "NGUYEN VAN A chuyen tien el1a2b3c4d5e6f cam on");
        Assert.Equal("EL1A2B3C4D5E6F", r);
    }

    [Fact]
    public void Prefers_code_field_when_present()
    {
        var r = SePayHelper.ExtractRef("EL0000000000AA", "noi dung EL1111111111BB");
        Assert.Equal("EL0000000000AA", r);
    }

    [Theory]
    [InlineData(null, null)]
    [InlineData("", "chuyen tien an trua")]
    [InlineData(null, "EL12345")]   // quá ngắn
    public void Returns_null_when_no_ref(string? code, string? content) =>
        Assert.Null(SePayHelper.ExtractRef(code, content));

    [Fact]
    public void Qr_url_encodes_parameters()
    {
        var opt = new EscrowOptions { BankName = "MBBank", BankAccountNumber = "0123456789" };
        var url = SePayHelper.BuildQrUrl(opt, 250000, "EL1A2B3C4D5E6F nap tien");
        Assert.Equal(
            "https://qr.sepay.vn/img?acc=0123456789&bank=MBBank&amount=250000&des=EL1A2B3C4D5E6F%20nap%20tien", url);
    }
}
