// SPDX-License-Identifier: GPL-3.0-only
// JVM test substitute; mirrors the Android codec calls using JDK ImageIO.
package android.graphics;

import java.awt.image.BufferedImage;
import java.io.OutputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

public final class Bitmap {
    public enum CompressFormat { JPEG, PNG, WEBP }
    public enum Config { ARGB_8888 }
    private final BufferedImage image;
    Bitmap(BufferedImage image) { this.image = image; }

    public static Bitmap createBitmap(int width, int height, Config config) {
        return new Bitmap(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB));
    }
    public void setPixel(int x, int y, int color) { image.setRGB(x, y, color); }
    public int getWidth() { return image.getWidth(); }
    public int getHeight() { return image.getHeight(); }
    public static Bitmap createScaledBitmap(Bitmap source, int width, int height, boolean filter) {
        Bitmap scaled = createBitmap(width, height, Config.ARGB_8888);
        java.awt.Graphics2D graphics = scaled.image.createGraphics();
        if (filter) graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source.image, 0, 0, width, height, null);
        graphics.dispose();
        return scaled;
    }

    public boolean compress(CompressFormat format, int quality, OutputStream out) {
        // JDK ImageIO has no WebP codec. Exercise JPEG fallback here; the native
        // Android check verifies actual WebP encoding and decoding.
        if (format == CompressFormat.WEBP) return false;
        if (format == CompressFormat.PNG) {
            try { return ImageIO.write(image, "png", out); }
            catch (java.io.IOException error) { return false; }
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(quality / 100f);
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), params);
            return true;
        } catch (java.io.IOException error) { return false; }
        finally { writer.dispose(); }
    }
    public void recycle() { image.flush(); }
}
