package om.mtc.attendance.service;

import om.mtc.attendance.domain.AppUser;
import om.mtc.attendance.domain.ProfilePhoto;
import om.mtc.attendance.domain.Soldier;
import om.mtc.attendance.repository.ProfilePhotoRepository;
import om.mtc.attendance.web.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;

/**
 * Profile pictures of soldiers and users.
 * <p>
 * The uploaded image is decoded, cropped to a square around the face (when the face service gave a box),
 * scaled to {@value #AVATAR_PX}px and re-encoded as JPEG — which also strips EXIF/GPS metadata.
 * The result is encrypted with AES-256-GCM (owner id bound as AAD) before it reaches the database.
 * Photos captured while taking attendance never pass through this class and are never stored.
 */
@Service
public class PhotoService {
    public static final int AVATAR_PX = 384;
    static final long MAX_PIXELS = 40_000_000L;
    private static final float JPEG_QUALITY = 0.86f;

    private final ProfilePhotoRepository photos;
    private final BiometricCrypto crypto;

    public PhotoService(ProfilePhotoRepository photos, BiometricCrypto crypto) {
        this.photos = photos;
        this.crypto = crypto;
    }

    // ------------------------------------------------------------------ image processing

    /** Decode, crop around {@code faceBox} (x1,y1,x2,y2; may be null), scale and re-encode as a small JPEG. */
    public byte[] toAvatar(byte[] original, int[] faceBox) {
        BufferedImage src = decode(original);
        int w = src.getWidth();
        int h = src.getHeight();
        int side;
        int x;
        int y;
        if (faceBox != null && faceBox.length == 4 && faceBox[2] > faceBox[0] && faceBox[3] > faceBox[1]) {
            double cx = (faceBox[0] + faceBox[2]) / 2.0;
            double cy = (faceBox[1] + faceBox[3]) / 2.0;
            double faceSide = Math.max(faceBox[2] - faceBox[0], faceBox[3] - faceBox[1]);
            side = (int) Math.round(Math.min(Math.min(w, h), faceSide * 2.2));
            x = (int) Math.round(cx - side / 2.0);
            y = (int) Math.round(cy - side / 2.0);
        } else {
            side = Math.min(w, h);
            x = (w - side) / 2;
            y = (h - side) / 2;
        }
        side = Math.max(1, side);
        x = Math.max(0, Math.min(x, w - side));
        y = Math.max(0, Math.min(y, h - side));

        BufferedImage out = new BufferedImage(AVATAR_PX, AVATAR_PX, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, AVATAR_PX, AVATAR_PX);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, AVATAR_PX, AVATAR_PX, x, y, x + side, y + side, null);
        } finally {
            g.dispose();
        }
        return encodeJpeg(out);
    }

    static BufferedImage decode(byte[] data) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw ApiException.unprocessable("UNSUPPORTED_FORMAT", "صيغة الصورة غير مدعومة، استخدم JPEG أو PNG");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw ApiException.unprocessable("IMAGE_TOO_LARGE", "دقة الصورة كبيرة جداً");
                }
                BufferedImage img = reader.read(0);
                // Normalise to opaque RGB (PNG alpha, palette and grey images)
                BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                try {
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                    g.drawImage(img, 0, 0, null);
                } finally {
                    g.dispose();
                }
                return rgb;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException ae) {
                throw ae;
            }
            throw ApiException.unprocessable("CORRUPT_IMAGE", "الصورة تالفة أو لا يمكن قراءتها");
        }
    }

    private static byte[] encodeJpeg(BufferedImage img) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(bos)) {
            ImageWriteParam p = writer.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), p);
            ios.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("JPEG encoding failed", e);
        } finally {
            writer.dispose();
        }
    }

    // ------------------------------------------------------------------ persistence (soldiers)

    @Transactional
    public void save(Soldier s, byte[] avatarJpeg, Long uploadedBy) {
        ProfilePhoto p = photos.findBySoldierId(s.getId()).orElseGet(() -> {
            ProfilePhoto n = new ProfilePhoto();
            n.setSoldierId(s.getId());
            return n;
        });
        fill(p, avatarJpeg, aad("soldier", s.getId()), uploadedBy);
        photos.save(p);
        s.setPhotoUpdatedAt(p.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public byte[] load(Soldier s) {
        ProfilePhoto p = photos.findBySoldierId(s.getId()).orElseThrow(() -> ApiException.notFound("الصورة"));
        return crypto.decryptBytes(p.getContentEnc(), aad("soldier", s.getId()));
    }

    @Transactional
    public boolean delete(Soldier s) {
        boolean had = photos.deleteBySoldierId(s.getId()) > 0;
        s.setPhotoUpdatedAt(null);
        return had;
    }

    // ------------------------------------------------------------------ persistence (users)

    @Transactional
    public void save(AppUser u, byte[] avatarJpeg, Long uploadedBy) {
        ProfilePhoto p = photos.findByUserId(u.getId()).orElseGet(() -> {
            ProfilePhoto n = new ProfilePhoto();
            n.setUserId(u.getId());
            return n;
        });
        fill(p, avatarJpeg, aad("user", u.getId()), uploadedBy);
        photos.save(p);
        u.setPhotoUpdatedAt(p.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public byte[] load(AppUser u) {
        ProfilePhoto p = photos.findByUserId(u.getId()).orElseThrow(() -> ApiException.notFound("الصورة"));
        return crypto.decryptBytes(p.getContentEnc(), aad("user", u.getId()));
    }

    @Transactional
    public boolean delete(AppUser u) {
        boolean had = photos.deleteByUserId(u.getId()) > 0;
        u.setPhotoUpdatedAt(null);
        return had;
    }

    // ------------------------------------------------------------------ helpers

    private void fill(ProfilePhoto p, byte[] jpeg, String aad, Long uploadedBy) {
        p.setContentEnc(crypto.encryptBytes(jpeg, aad));
        p.setContentType("image/jpeg");
        p.setSizeBytes(jpeg.length);
        p.setUploadedBy(uploadedBy);
        p.setUpdatedAt(Instant.now());
    }

    static String aad(String kind, Long id) {
        return "photo:" + kind + ":" + id;
    }
}
