package om.mtc.attendance.service;

import om.mtc.attendance.web.error.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class ValidationUtilsTest {

    @Test
    void imageSignatures() {
        byte[] jpeg = new byte[16];
        jpeg[0] = (byte) 0xFF; jpeg[1] = (byte) 0xD8; jpeg[2] = (byte) 0xFF;
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        byte[] html = "<html><script>".getBytes();
        assertThat(ImageGuard.isSupportedImage(jpeg)).isTrue();
        assertThat(ImageGuard.isSupportedImage(png)).isTrue();
        assertThat(ImageGuard.isSupportedImage(webp)).isTrue();
        assertThat(ImageGuard.isSupportedImage(html)).isFalse();
        assertThat(ImageGuard.isSupportedImage(new byte[3])).isFalse();
    }

    @Test
    void passwordPolicy() {
        assertThatThrownBy(() -> PasswordPolicy.check("short1", "u")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PasswordPolicy.check("onlyletterspassword", "u")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PasswordPolicy.check("1234567890123", "u")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PasswordPolicy.check("senior1Password9", "senior1")).isInstanceOf(ApiException.class);
        assertThatCode(() -> PasswordPolicy.check("Parade-Ground-2026", "senior1")).doesNotThrowAnyException();
    }
}
