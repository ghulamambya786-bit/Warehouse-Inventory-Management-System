import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Writes a one-page PDF that contains a picture (the report), using no external libraries. */
public final class PdfExport {
    private PdfExport() { }

    public static void saveImage(File file, BufferedImage img) throws IOException {
        int w = img.getWidth(), h = img.getHeight();
        BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.drawImage(img, 0, 0, null);
        g.dispose();
        ByteArrayOutputStream jpg = new ByteArrayOutputStream();
        if (!ImageIO.write(rgb, "jpg", jpg)) throw new IOException("JPEG encoder not available");

        double pw = 842, ph = Math.round(pw * h / w);          // A4-landscape width, height follows the picture
        String size = String.format(Locale.ROOT, "%.0f %.0f", pw, ph);
        String draw = String.format(Locale.ROOT, "q %.0f 0 0 %.0f 0 0 cm /Im0 Do Q", pw, ph);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        put(out, "%PDF-1.4\n");
        obj(out, offsets, "<< /Type /Catalog /Pages 2 0 R >>");
        obj(out, offsets, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        obj(out, offsets, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + size
                + "] /Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>");
        offsets.add(out.size());
        put(out, "4 0 obj\n<< /Type /XObject /Subtype /Image /Width " + w + " /Height " + h
                + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length " + jpg.size()
                + " >>\nstream\n");
        out.write(jpg.toByteArray());
        put(out, "\nendstream\nendobj\n");
        offsets.add(out.size());
        put(out, "5 0 obj\n<< /Length " + draw.length() + " >>\nstream\n" + draw + "\nendstream\nendobj\n");

        int xref = out.size();
        StringBuilder x = new StringBuilder("xref\n0 6\n0000000000 65535 f \n");
        for (int off : offsets) x.append(String.format(Locale.ROOT, "%010d 00000 n \n", off));
        x.append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        put(out, x.toString());
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(out.toByteArray());
        }
    }

    private static void obj(ByteArrayOutputStream out, List<Integer> offsets, String body) throws IOException {
        offsets.add(out.size());
        put(out, (offsets.size()) + " 0 obj\n" + body + "\nendobj\n");
    }

    private static void put(ByteArrayOutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.ISO_8859_1));
    }
}
