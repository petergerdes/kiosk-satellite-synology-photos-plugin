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
    public enum CompressFormat { JPEG }
    private final BufferedImage image;
    Bitmap(BufferedImage image) { this.image = image; }

    public boolean compress(CompressFormat format, int quality, OutputStream out) {
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
