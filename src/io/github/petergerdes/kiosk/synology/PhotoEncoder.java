// SPDX-License-Identifier: Apache-2.0
package io.github.petergerdes.kiosk.synology;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Keep XL detail within KS's HTML limit using Android's built-in image codec. */
final class PhotoEncoder {
    static SynologyClient.Image fit(byte[] bytes, String mime) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("Synology returned an invalid thumbnail.");
        if (bytes.length <= PhotoHtml.MAX_IMAGE_BYTES && Math.max(options.outWidth, options.outHeight) <= 1920) {
            return new SynologyClient.Image(bytes, mime);
        }
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 1920) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (bitmap == null) throw new IOException("Synology returned an unsupported thumbnail.");
        try {
            for (int quality : new int[]{85, 70, 55, 40}) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                if (bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out) && out.size() <= PhotoHtml.MAX_IMAGE_BYTES) {
                    return new SynologyClient.Image(out.toByteArray(), "image/jpeg");
                }
            }
            throw new IOException("Thumbnail cannot fit the screensaver budget; trying a smaller size.");
        } finally { bitmap.recycle(); }
    }
}
