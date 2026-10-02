// SPDX-License-Identifier: GPL-3.0-only
package io.github.petergerdes.kiosk.synology;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Keep XL detail within KS's HTML limit using Android's built-in image codec. */
final class PhotoEncoder {
    static SynologyClient.Image fit(byte[] bytes, String mime) throws IOException {
        return fit(bytes, mime, PhotoHtml.MAX_IMAGE_BYTES);
    }

    @SuppressWarnings("deprecation") // WEBP is available on every supported Android version (API 24+).
    static SynologyClient.Image fit(byte[] bytes, String mime, int budget) throws IOException {
        if (budget <= 0 || budget > PhotoHtml.MAX_IMAGE_BYTES) throw new IllegalArgumentException("Invalid photo budget");
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("Synology returned an invalid thumbnail.");
        if (bytes.length <= budget && Math.max(options.outWidth, options.outHeight) <= 1920) {
            return new SynologyClient.Image(bytes, mime, options.outWidth, options.outHeight);
        }
        options.inSampleSize = 1;
        int longest = Math.max(options.outWidth, options.outHeight);
        while (longest / (options.inSampleSize * 2) >= 1920) options.inSampleSize *= 2;
        // Density scaling reaches the exact target instead of halving below it.
        options.inScaled = true;
        options.inDensity = longest;
        options.inTargetDensity = Math.min(longest, 1920 * options.inSampleSize);
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (bitmap == null) throw new IOException("Synology returned an unsupported thumbnail.");
        try {
            while (true) {
                for (int quality : new int[]{95, 90, 85, 80}) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    String encodedMime = "image/webp";
                    boolean encoded = bitmap.compress(Bitmap.CompressFormat.WEBP, quality, out);
                    if (!encoded) {
                        out.reset();
                        encodedMime = "image/jpeg";
                        encoded = bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
                    }
                    if (encoded && out.size() <= budget) {
                        return new SynologyClient.Image(out.toByteArray(), encodedMime, bitmap.getWidth(), bitmap.getHeight());
                    }
                }
                if (Math.max(bitmap.getWidth(), bitmap.getHeight()) <= 256) {
                    throw new IOException("Unable to encode this photo within the screensaver budget.");
                }
                // Retain XL detail with a filtered, gradual resize rather than
                // jumping straight to Synology's much smaller M thumbnail.
                Bitmap smaller = Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(bitmap.getWidth() * 0.85f)),
                    Math.max(1, Math.round(bitmap.getHeight() * 0.85f)), true);
                bitmap.recycle();
                bitmap = smaller;
            }
        } finally { bitmap.recycle(); }
    }
}
