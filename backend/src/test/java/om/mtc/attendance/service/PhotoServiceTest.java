package om.mtc.attendance.service;

import om.mtc.attendance.web.error.ApiException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Image handling of profile photos (no Spring context, no database). */
class PhotoServiceTest {
    private final PhotoService service = new PhotoService(null, null);

    private static byte[] image(int w, int h, String format, boolean alpha) throws Exception {
        BufferedImage img = new BufferedImage(w, h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.RED);                       // a red "face" in the middle of the frame
        g.fillRect(w / 2 - 50, h / 2 - 50, 100, 100);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, format, bos);
        return bos.toByteArray();
    }

    private static BufferedImage read(byte[] jpeg) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }

    @Test
    void producesA384SquareJpegWithoutMetadata() throws Exception {
        byte[] out = service.toAvatar(image(1600, 900, "jpg", false), null);
        BufferedImage r = read(out);
        assertThat(r.getWidth()).isEqualTo(PhotoService.AVATAR_PX);
        assertThat(r.getHeight()).isEqualTo(PhotoService.AVATAR_PX);
        assertThat(out[0] & 0xFF).isEqualTo(0xFF);
        assertThat(out[1] & 0xFF).isEqualTo(0xD8);
        assertThat(out.length).isLessThan(120_000);
    }

    @Test
    void cropsAroundTheFaceBox() throws Exception {
        // Face box = the red square; the crop (2.2x the face) must be dominated by red, not by the blue background.
        byte[] src = image(1200, 800, "png", false);
        BufferedImage tight = read(service.toAvatar(src, new int[]{550, 350, 650, 450}));
        BufferedImage centre = read(service.toAvatar(src, null));
        int c = tight.getRGB(PhotoService.AVATAR_PX / 2, PhotoService.AVATAR_PX / 2);
        assertThat(new Color(c).getRed()).isGreaterThan(200);
        assertThat(new Color(c).getBlue()).isLessThan(80);
        // The centre-crop of an 800px-high square shows the same red square but much smaller.
        long redTight = redPixels(tight);
        long redCentre = redPixels(centre);
        assertThat(redTight).isGreaterThan(redCentre);
    }

    @Test
    void faceBoxNearTheEdgeStaysInsideTheImage() throws Exception {
        byte[] src = image(640, 480, "jpg", false);
        BufferedImage r = read(service.toAvatar(src, new int[]{0, 0, 90, 90}));
        assertThat(r.getWidth()).isEqualTo(PhotoService.AVATAR_PX);
        BufferedImage r2 = read(service.toAvatar(src, new int[]{600, 440, 640, 480}));
        assertThat(r2.getHeight()).isEqualTo(PhotoService.AVATAR_PX);
    }

    @Test
    void transparentPngIsFlattenedOnWhite() throws Exception {
        BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB); // fully transparent
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        BufferedImage r = read(service.toAvatar(bos.toByteArray(), null));
        assertThat(new Color(r.getRGB(10, 10)).getRed()).isGreaterThan(240);
    }

    @Test
    void garbageIsRejected() {
        assertThatThrownBy(() -> service.toAvatar(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13}, null))
                .isInstanceOf(ApiException.class);
        byte[] fakeJpeg = new byte[64];
        fakeJpeg[0] = (byte) 0xFF;
        fakeJpeg[1] = (byte) 0xD8;
        fakeJpeg[2] = (byte) 0xFF;
        assertThatThrownBy(() -> service.toAvatar(fakeJpeg, null)).isInstanceOf(ApiException.class);
    }

    private static long redPixels(BufferedImage img) {
        long n = 0;
        for (int x = 0; x < img.getWidth(); x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                Color c = new Color(img.getRGB(x, y));
                if (c.getRed() > 180 && c.getBlue() < 100) {
                    n++;
                }
            }
        }
        return n;
    }
}
