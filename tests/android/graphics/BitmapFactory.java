// SPDX-License-Identifier: GPL-3.0-only
// JVM test substitute. Android's real decoder is supplied by the device.
package android.graphics;

import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public final class BitmapFactory {
    public static final class Options {
        public boolean inJustDecodeBounds;
        public boolean inScaled = true;
        public int inDensity, inTargetDensity;
        public int outWidth, outHeight, inSampleSize = 1;
    }

    public static Bitmap decodeByteArray(byte[] bytes, int offset, int length, Options options) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes, offset, length));
            if (image == null) return null;
            options.outWidth = image.getWidth(); options.outHeight = image.getHeight();
            if (options.inJustDecodeBounds) return null;
            int width = Math.max(1, image.getWidth() / options.inSampleSize);
            int height = Math.max(1, image.getHeight() / options.inSampleSize);
            if (options.inScaled && options.inDensity > 0 && options.inTargetDensity > 0) {
                width = Math.max(1, Math.round(width * (float) options.inTargetDensity / options.inDensity));
                height = Math.max(1, Math.round(height * (float) options.inTargetDensity / options.inDensity));
            }
            BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D graphics = scaled.createGraphics();
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(image, 0, 0, width, height, null);
            graphics.dispose();
            return new Bitmap(scaled);
        } catch (java.io.IOException error) { return null; }
    }
}
