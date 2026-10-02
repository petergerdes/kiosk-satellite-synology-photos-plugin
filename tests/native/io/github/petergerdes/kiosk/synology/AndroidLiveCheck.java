// SPDX-License-Identifier: GPL-3.0-only
package io.github.petergerdes.kiosk.synology;

import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

/** Run using app_process on a developer emulator; exercises the real Android codec. */
public final class AndroidLiveCheck {
    public static void main(String[] args) throws Exception {
        try { run(args); }
        catch (Exception | AssertionError error) {
            // app_process otherwise reports failures only to Logcat, then exits
            // with SIGKILL, hiding the actual failure from the test runner.
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        codecQuality();
        System.out.println("Android WebP codec and resolution checks passed.");
        if (args.length == 1 && "--codec-only".equals(args[0])) return;
        ByteArrayOutputStream configBytes = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(args[0])) {
            byte[] buffer = new byte[1024]; int length;
            while ((length = in.read(buffer)) != -1) configBytes.write(buffer, 0, length);
        }
        JSONObject config = new JSONObject(new String(configBytes.toByteArray(), StandardCharsets.UTF_8));
        try (SynologyClient client = new SynologyClient(config.getString("albumUrl"), config.optString("albumPassword"))) {
            client.login();
            System.out.println("Shared album connected; checking display images.");
            List<SynologyClient.Photo> photos = client.listPhotos();
            if (photos.isEmpty()) throw new AssertionError("No live album photos");
            int count = 0, max = 0, minSide = Integer.MAX_VALUE, maxSide = 0, small = 0, webp = 0;
            SynologyClient.Image previous = null;
            Map<String, Object> settings = new HashMap<>();
            settings.put("transition", "Fade");
            settings.put("motion", "Ken Burns");
            for (SynologyClient.Photo photo : photos) {
                SynologyClient.Image image = client.image(photo, PhotoHtml.imageBudget(settings));
                SynologyClient.Image outgoing = previous;
                if (outgoing != null && outgoing.bytes.length + image.bytes.length > PhotoHtml.MAX_IMAGE_BYTES) {
                    outgoing = PhotoEncoder.fit(outgoing.bytes, outgoing.mime, PhotoHtml.MAX_IMAGE_BYTES - image.bytes.length);
                }
                String html = PhotoHtml.photo(outgoing, image, settings);
                if (html.getBytes(StandardCharsets.UTF_8).length > 524288) throw new AssertionError("HTML budget exceeded");
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.length, bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || Math.max(bounds.outWidth, bounds.outHeight) > 1920) {
                    throw new AssertionError("Invalid or oversized display image");
                }
                int side = Math.max(bounds.outWidth, bounds.outHeight);
                minSide = Math.min(minSide, side);
                maxSide = Math.max(maxSide, side);
                if (side < 1024) small++;
                if ("image/webp".equals(image.mime)) webp++;
                System.out.println("Photo " + (count + 1) + ": " + bounds.outWidth + " × " + bounds.outHeight
                    + " px, " + image.bytes.length + " bytes; document " + html.getBytes(StandardCharsets.UTF_8).length + " bytes.");
                if (count == 0 || count == 1) try (OutputStream out = new FileOutputStream(args[1])) {
                    out.write(html.getBytes(StandardCharsets.UTF_8));
                }
                count++;
                previous = image;
                max = Math.max(max, image.bytes.length);
            }
            System.out.println("Android live check passed: " + count + " photos fetched and encoded; largest display image " + max
                + " bytes; longest-side range " + minSide + "–" + maxSide + " px; " + small + " images below 1024 px; " + webp + " WebP images.");
        }
    }

    private static void codecQuality() throws Exception {
        for (boolean noisy : new boolean[]{false, true}) codecQuality(noisy);
    }

    private static void codecQuality(boolean noisy) throws Exception {
        Bitmap fixture = Bitmap.createBitmap(2050, 1025, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream original = new ByteArrayOutputStream();
        java.util.Random random = new java.util.Random(42);
        try {
            for (int y = 0; y < 1025; y++) for (int x = 0; x < 2050; x++) {
                fixture.setPixel(x, y, noisy ? 0xff000000 | random.nextInt(0x1000000)
                    : (x / 64 + y / 64) % 2 == 0 ? 0xff507ca3 : 0xffdbb876);
            }
            if (!fixture.compress(Bitmap.CompressFormat.PNG, 100, original)) throw new AssertionError("Fixture encoding failed");
        } finally { fixture.recycle(); }
        SynologyClient.Image image = PhotoEncoder.fit(original.toByteArray(), "image/png");
        if (!"image/webp".equals(image.mime) || !image.mime.equals(SynologyClient.imageMime(image.bytes))) {
            throw new AssertionError("Native WebP encoding failed");
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.length, bounds);
        if (bounds.outWidth > 1920 || bounds.outWidth <= 640 || image.bytes.length > PhotoHtml.MAX_IMAGE_BYTES
                || (!noisy && (bounds.outWidth != 1920 || bounds.outHeight != 960))) {
            throw new AssertionError("High-detail fixture lost resolution or exceeded its budget: " + bounds.outWidth + "x" + bounds.outHeight);
        }
        Bitmap decoded = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.length, new BitmapFactory.Options());
        if (decoded == null) throw new AssertionError("WebP image cannot be fully decoded");
        decoded.recycle();
        if (noisy) {
            SynologyClient.Image constrained = PhotoEncoder.fit(original.toByteArray(), "image/png", 190_000);
            if (image.width <= constrained.width) throw new AssertionError("Larger budget did not retain more detail");
            SynologyClient.Image outgoing = PhotoEncoder.fit(image.bytes, image.mime, 80_000);
            if (outgoing.bytes.length > 80_000 || outgoing.width <= 0) throw new AssertionError("Outgoing budget exceeded");
        }
    }
}
