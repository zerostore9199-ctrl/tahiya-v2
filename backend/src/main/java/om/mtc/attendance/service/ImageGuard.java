package om.mtc.attendance.service;

import om.mtc.attendance.web.error.ApiException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;

/** Validates uploads before they leave the backend (defence in depth; the face service validates again). */
public final class ImageGuard {
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    private ImageGuard() {
    }

    public static byte[] readValidated(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "لم يتم إرفاق صورة");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("FILE_TOO_LARGE", "حجم الصورة أكبر من المسموح (10MB)");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("UNREADABLE_FILE", "تعذّر قراءة الصورة");
        }
        if (!isSupportedImage(data)) {
            throw ApiException.badRequest("UNSUPPORTED_FORMAT", "الصيغ المقبولة: JPEG أو PNG أو WebP");
        }
        return data;
    }

    static boolean isSupportedImage(byte[] d) {
        if (d.length < 12) {
            return false;
        }
        boolean jpeg = (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF;
        boolean png = Arrays.equals(Arrays.copyOf(d, 8),
                new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        boolean webp = d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P';
        return jpeg || png || webp;
    }
}
