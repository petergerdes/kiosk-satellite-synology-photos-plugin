// SPDX-License-Identifier: Apache-2.0
package io.github.petergerdes.kiosk.synology;

import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.json.JSONObject;

/** Run using app_process on a developer emulator; exercises the real Android codec. */
public final class AndroidLiveCheck {
    public static void main(String[] args) throws Exception {
        ByteArrayOutputStream configBytes = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(args[0])) {
            byte[] buffer = new byte[1024]; int length;
            while ((length = in.read(buffer)) != -1) configBytes.write(buffer, 0, length);
        }
        JSONObject config = new JSONObject(new String(configBytes.toByteArray(), StandardCharsets.UTF_8));
        try (SynologyClient client = new SynologyClient(config.getString("albumUrl"), config.optString("albumPassword"))) {
            client.login();
            List<SynologyClient.Photo> photos = client.listPhotos();
            if (photos.isEmpty()) throw new AssertionError("No live album photos");
            int count = 0, max = 0;
            for (SynologyClient.Photo photo : photos) {
                SynologyClient.Image image = client.image(photo);
                String html = PhotoHtml.photo(image.bytes, image.mime, false);
                if (html.getBytes(StandardCharsets.UTF_8).length > 524288) throw new AssertionError("HTML budget exceeded");
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.length, bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || Math.max(bounds.outWidth, bounds.outHeight) > 1920) {
                    throw new AssertionError("Invalid or oversized display image");
                }
                if (count == 0) try (OutputStream out = new FileOutputStream(args[1])) {
                    out.write(html.getBytes(StandardCharsets.UTF_8));
                }
                count++;
                max = Math.max(max, image.bytes.length);
            }
            System.out.println("Android live check passed: " + count + " photos fetched and encoded; largest display image " + max + " bytes.");
        }
    }
}
