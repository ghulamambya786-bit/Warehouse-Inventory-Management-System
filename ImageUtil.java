import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/** Small helpers for loading product pictures and scaling images to fit a box. */
public final class ImageUtil {
    private ImageUtil() { }

    /** @return the image at path, or null if the path is empty / missing / not an image */
    public static BufferedImage load(String path) {
        if (path == null || path.trim().isEmpty()) return null;
        try {
            File f = new File(path);
            return f.isFile() ? ImageIO.read(f) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Scales to fit inside w x h while keeping the aspect ratio. */
    public static ImageIcon fit(BufferedImage img, int w, int h) {
        double s = Math.min(w / (double) img.getWidth(), h / (double) img.getHeight());
        int nw = Math.max(1, (int) Math.round(img.getWidth() * s));
        int nh = Math.max(1, (int) Math.round(img.getHeight() * s));
        return new ImageIcon(img.getScaledInstance(nw, nh, Image.SCALE_SMOOTH));
    }
}
