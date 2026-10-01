// SPDX-License-Identifier: GPL-3.0-only
package io.github.petergerdes.kiosk.synology;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import me.jxl.kiosk.plugins.PluginHost;
import org.json.JSONArray;
import org.json.JSONObject;

/** An actual HTTP fake NAS checks encoding, sessions, paging, rendering and cancellation. */
public final class PluginTest {
    static final byte[] JPEG = jpegFixture();
    static final String PASSWORD = "secret &+é=";

    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 2 && "--preview".equals(arguments[0])) {
            previews(Paths.get(arguments[1]));
            return;
        }
        if (arguments.length == 1) {
            liveNas(Paths.get(arguments[0]));
            return;
        }
        linksAndRenderer();
        imageBudget();
        orderAndEffects();
        try (Nas nas = new Nas()) {
            protocol(nas);
            lifecycle(nas);
        }
        System.out.println("Synology protocol, renderer and lifecycle checks passed.");
    }

    static void orderAndEffects() {
        SynologyClient.Photo first = new SynologyClient.Photo(1, "one");
        SynologyClient.Photo second = new SynologyClient.Photo(2, "two");
        SynologyClient.Photo third = new SynologyClient.Photo(3, "three");
        List<SynologyClient.Photo> photos = new ArrayList<>(Arrays.asList(first, second, third));
        SynologyPhotosPlugin.orderPhotos(photos, "Oldest first", -1);
        assert photos.get(0).id == 1 && photos.get(2).id == 3;
        SynologyPhotosPlugin.orderPhotos(photos, "Newest first", -1);
        assert photos.get(0).id == 3 && photos.get(2).id == 1;
        for (int i = 0; i < 50; i++) {
            SynologyPhotosPlugin.orderPhotos(photos, "Shuffle", 2);
            assert photos.get(0).id != 2;
            assert new java.util.HashSet<>(photos).size() == 3;
        }
        SynologyPhotosPlugin.orderPhotos(new ArrayList<>(), "Shuffle", -1);
        SynologyPhotosPlugin.orderPhotos(new ArrayList<>(Collections.singletonList(first)), "Shuffle", 1);

        SynologyClient.Image image = new SynologyClient.Image(JPEG, "image/jpeg");
        Map<String, Object> settings = settings("");
        settings.put("transition", "Fade");
        settings.put("transitionSeconds", 1.4);
        settings.put("motion", "Ken Burns");
        String fade = PhotoHtml.photo(image, image, settings);
        assert fade.contains("class=\"fade motion\"") && fade.contains("class=\"previous\"");
        assert fade.contains("--transition:1.4s;--interval:5.0s");
        assert fade.contains("images[i].addEventListener('load'") && fade.contains("images[i].naturalWidth");
        settings.put("transition", "Slide");
        assert PhotoHtml.photo(image, image, settings).contains("class=\"slide motion\"");
        settings.put("transition", "None");
        assert !PhotoHtml.photo(image, image, settings).contains("class=\"previous\"");
        settings.put("transition", "<script>");
        settings.put("transitionSeconds", Double.NaN);
        assert PhotoHtml.photo(image, image, settings).contains("class=\"fade motion\"");
        assert PhotoHtml.photo(image, image, settings).contains("--transition:1.0s");
        SynologyClient.Image max = new SynologyClient.Image(new byte[PhotoHtml.MAX_IMAGE_BYTES], "image/jpeg");
        assert PhotoHtml.photo(max, max, settings).getBytes(StandardCharsets.UTF_8).length < 524288 : "Two-photo transition exceeds KS limit";
    }

    static void previews(Path folder) throws Exception {
        Files.createDirectories(folder);
        SynologyClient.Image first = illustration(false), second = illustration(true);
        Map<String, Object> settings = settings("");
        settings.put("transitionSeconds", 2);
        settings.put("motion", "Ken Burns");
        for (String effect : Arrays.asList("None", "Fade", "Slide")) {
            settings.put("transition", effect);
            Files.write(folder.resolve(effect.toLowerCase() + ".html"),
                PhotoHtml.photo(first, second, settings).getBytes(StandardCharsets.UTF_8));
        }
        System.out.println("Synthetic transition previews generated.");
    }

    static SynologyClient.Image illustration(boolean second) throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(1200, 800, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        graphics.setPaint(new java.awt.GradientPaint(0, 0, new java.awt.Color(second ? 0xD99666 : 0x386B85),
            0, 800, new java.awt.Color(second ? 0x453849 : 0xA6C4C2)));
        graphics.fillRect(0, 0, 1200, 800);
        graphics.setColor(new java.awt.Color(second ? 0xF5D89D : 0xE9E6CB));
        graphics.fillOval(second ? 820 : 200, 120, 120, 120);
        graphics.setColor(new java.awt.Color(second ? 0x594453 : 0x315B5A));
        graphics.fillPolygon(new int[]{0, 300, 600, 950, 1200, 1200, 0}, new int[]{550, 320, 620, 380, 600, 800, 800}, 7);
        graphics.setColor(new java.awt.Color(second ? 0x302B36 : 0x213C3B));
        graphics.fillPolygon(new int[]{0, 350, 650, 1000, 1200, 1200, 0}, new int[]{700, 580, 750, 550, 700, 800, 800}, 7);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "jpeg", out);
        return PhotoEncoder.fit(out.toByteArray(), "image/jpeg");
    }

    static byte[] jpegFixture() {
        try {
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "jpeg", out);
            return out.toByteArray();
        } catch (IOException error) { throw new AssertionError(error); }
    }

    static void imageBudget() throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(2048, 1024, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.util.Random random = new java.util.Random(42);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, random.nextInt());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        assert out.size() > PhotoHtml.MAX_IMAGE_BYTES;
        SynologyClient.Image result = PhotoEncoder.fit(out.toByteArray(), "image/png");
        assert result.bytes.length <= PhotoHtml.MAX_IMAGE_BYTES && result.mime.equals("image/jpeg");
        java.awt.image.BufferedImage decoded = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(result.bytes));
        assert decoded != null && Math.max(decoded.getWidth(), decoded.getHeight()) <= 1920;
        assert PhotoHtml.photo(result.bytes, result.mime, false).getBytes(StandardCharsets.UTF_8).length < 524288;
    }

    static void liveNas(Path configPath) throws Exception {
        JSONObject config = new JSONObject(new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8));
        try (SynologyClient client = new SynologyClient(config.getString("albumUrl"), config.optString("albumPassword"))) {
            client.login();
            List<SynologyClient.Photo> photos = client.listPhotos();
            if (photos.isEmpty()) throw new AssertionError("Live album has no displayable photos");
            SynologyClient.Image image = client.image(photos.get(0));
            String html = PhotoHtml.photo(image.bytes, image.mime, false);
            if (html.getBytes(StandardCharsets.UTF_8).length > 524288) throw new AssertionError("Renderer budget exceeded");
            Path preview = configPath.getParent().resolve("preview/live.html");
            Files.createDirectories(preview.getParent());
            Files.write(preview, html.getBytes(StandardCharsets.UTF_8));
            System.out.println("Live NAS check passed: sharing login, " + photos.size() + " photos, "
                + image.mime + " thumbnail (" + image.bytes.length + " bytes). Private preview saved in the config's preview subdirectory.");
        }
    }

    static void linksAndRenderer() {
        SynologyClient client = new SynologyClient(" https://nas.example:5001/photo/mo/sharing/abc_123/?ignored=1#x ", "");
        assert client.loginEndpoint.toString().equals("https://nas.example:5001/photo/webapi/entry.cgi");
        assert client.albumEndpoint.toString().equals("https://nas.example:5001/photo/mo/sharing/webapi/entry.cgi");
        assert client.sharingId.equals("abc_123");
        client.close();
        SynologyClient alias = new SynologyClient("http://nas.example/photos/sharing/abc", "");
        assert alias.loginEndpoint.toString().equals("http://nas.example/photos/webapi/entry.cgi");
        alias.close();
        SynologyClient ipv6 = new SynologyClient("https://[::1]:5001/photo/mo/sharing/token", "");
        assert ipv6.albumEndpoint.getHost().contains("::1");
        ipv6.close();
        for (String invalid : Arrays.asList("nas.local", "file:///photo/mo/sharing/a", "https://nas/photo/album/1",
                "https://user:password@nas/photo/mo/sharing/a", "https://nas/photo/mo/sharing/a%0d%0a",
                "https://nas/photo/mo/sharing/a/extra", "https://quickconnect.to/album")) {
            try { new SynologyClient(invalid, ""); throw new AssertionError("Accepted invalid URL"); }
            catch (IllegalArgumentException expected) { }
        }
        String fit = PhotoHtml.photo(JPEG, "image/jpeg", false);
        assert fit.contains("object-fit:contain") && fit.contains("data:image/jpeg;base64,");
        assert PhotoHtml.photo(JPEG, "image/jpeg", true).contains("object-fit:cover");
        assert PhotoHtml.message("<script>&").contains("&lt;script&gt;&amp;");
        assert PhotoHtml.photo(new byte[PhotoHtml.MAX_IMAGE_BYTES], "image/jpeg", false)
            .getBytes(StandardCharsets.UTF_8).length < 524288;
        assert SynologyClient.imageMime("<html>not a photo".getBytes(StandardCharsets.UTF_8)) == null;
        try { PhotoHtml.photo(JPEG, "image/svg+xml", false); throw new AssertionError("Accepted active SVG"); }
        catch (IllegalArgumentException expected) { }
    }

    static void protocol(Nas nas) throws Exception {
        try (SynologyClient client = new SynologyClient(nas.link(), PASSWORD)) {
            client.login();
            List<SynologyClient.Photo> photos = client.listPhotos();
            assert photos.size() == 2 : "Pagination must include the second page and exclude videos";
            assert photos.get(1).id == 2 : "Live Photos must be shown as stills";
            SynologyClient.Image image = client.image(photos.get(0));
            assert image.mime.equals("image/jpeg") && Arrays.equals(image.bytes, JPEG);
            assert nas.sizes.contains("xl") && nas.sizes.contains("m") : "Oversized XL must fall back";
            assert nas.violations.isEmpty() : nas.violations;
        }
        try (SynologyClient badPassword = new SynologyClient(nas.link(), "wrong")) {
            try { badPassword.login(); throw new AssertionError("Wrong password accepted"); }
            catch (IOException expected) { assert expected.getMessage().contains("album password"); }
        }
        nas.redirect = true;
        try (SynologyClient redirect = new SynologyClient(nas.link(), PASSWORD)) {
            try { redirect.login(); throw new AssertionError("Followed a redirect"); }
            catch (IOException expected) { assert expected.getMessage().contains("redirect"); }
        } finally { nas.redirect = false; }
        assert nas.violations.isEmpty() : nas.violations;
    }

    static void lifecycle(Nas nas) throws Exception {
        SynologyPhotosPlugin plugin = new SynologyPhotosPlugin();
        Host host = new Host();
        Map<String, Object> config = settings(nas.link());
        plugin.start(host, settings(""));
        assert host.html.contains("Connect your album");
        plugin.configure(settings("invalid"));
        assert host.error && host.status.contains("sharing link");
        plugin.configure(config);
        await(() -> host.html.contains("data:image/jpeg"), 5000);
        assert host.status.contains("2 photos") && !host.error;
        final int count = host.photos.get();
        Thread.sleep(1200);
        assert host.photos.get() == count : "Idle screensaver must not advance";
        plugin.onEvent("ks.screensaver.state", Collections.singletonMap("active", true));
        plugin.onEvent("ks.screensaver.view", Collections.singletonMap("view", SynologyPhotosPlugin.MODE));
        await(() -> host.photos.get() > count, 6500);
        assert host.html.contains("class=\"previous\"") : "Fade must carry the previously displayed photo";
        plugin.onEvent("ks.screen.state", Collections.singletonMap("on", false));
        int pausedCount = host.photos.get();
        Thread.sleep(5500);
        assert host.photos.get() == pausedCount : "Screen-off must pause downloads";
        plugin.onEvent("ks.screen.state", Collections.singletonMap("on", true));
        nas.failImages = true;
        await(() -> host.error, 6500);
        assert host.html.contains("data:image/jpeg") : "Outage must keep the last photo";
        nas.failImages = false;
        plugin.execute("refresh", Collections.emptyMap());
        await(() -> !host.error && host.html.contains("data:image/jpeg"), 5000);

        nas.brokenFirst = true;
        plugin.execute("refresh", Collections.emptyMap());
        await(() -> !host.error && host.html.contains("data:image/jpeg"), 5000);
        assert nas.lastImageId.equals("2") : "A broken first image must not starve the album";
        nas.brokenFirst = false;

        Map<String, Object> newest = new HashMap<>(config);
        newest.put("order", "Newest first");
        newest.put("transition", "None");
        newest.put("motion", "Ken Burns");
        plugin.configure(newest);
        await(() -> !host.error && host.html.contains("data:image/jpeg"), 5000);
        assert nas.lastImageId.equals("2") : "Newest-first setting did not select the latest photo";
        assert host.html.contains("class=\"none motion\"") && !host.html.contains("class=\"previous\"");

        // A delayed response must be cancelled without publishing into a new configuration.
        nas.slowLogin = true;
        plugin.execute("refresh", Collections.emptyMap());
        assert nas.started.await(3, TimeUnit.SECONDS);
        plugin.configure(settings(""));
        nas.release.countDown();
        nas.slowLogin = false;
        Thread.sleep(1200);
        assert host.html.contains("Connect your album") : "Old request overwrote new settings";
        nas.started = new CountDownLatch(1);
        nas.release = new CountDownLatch(1);
        nas.slowLogin = true;
        plugin.configure(config);
        assert nas.started.await(3, TimeUnit.SECONDS);
        long stopStarted = System.nanoTime();
        plugin.stop();
        nas.release.countDown();
        nas.slowLogin = false;
        host.stopped = true;
        assert TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStarted) < 2800;
        Thread.sleep(1100);
        assert !host.calledAfterStop;
        assert nas.violations.isEmpty() : nas.violations;
    }

    static Map<String, Object> settings(String link) {
        Map<String, Object> settings = new HashMap<>();
        settings.put("albumUrl", link); settings.put("albumPassword", PASSWORD);
        settings.put("order", "Oldest first"); settings.put("intervalSeconds", 5);
        settings.put("transition", "Fade"); settings.put("motion", "None");
        settings.put("refreshMinutes", 15); settings.put("fit", "Fit whole photo");
        return settings;
    }

    static void await(BooleanSupplier condition, long timeout) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(20);
        if (!condition.getAsBoolean()) throw new AssertionError("Timed out waiting for expected state");
    }

    static final class Host implements PluginHost {
        volatile String html = "", status = "";
        volatile boolean error, stopped, calledAfterStop;
        final AtomicInteger photos = new AtomicInteger();
        public void showWindow(String title, String message, String button) { throw new AssertionError("No overlay required"); }
        public void hideWindow() { }
        public void log(String message) { throw new AssertionError("No secrets should be logged"); }
        public void subscribe(String event) { }
        public void executeCommand(String command, Map<String, Object> args, CommandCallback callback) {
            callback.onResult(true, "isScreenOn".equals(command), null);
        }
        public void publishScreensaver(String key, String title, String html) {
            if (stopped) calledAfterStop = true;
            assert html.getBytes(StandardCharsets.UTF_8).length <= 524288;
            assert !html.contains(PASSWORD) && !html.contains("sharing_sid") && !html.contains("album-token");
            this.html = html;
            if (html.contains("data:image/")) photos.incrementAndGet();
        }
        public void status(String text, boolean error) { this.status = text; this.error = error; }
        public void publishStatusTile(String key, String title, String level, String text) { assert text.length() <= 80; }
    }

    static final class Nas implements AutoCloseable {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final ExecutorService workers = Executors.newCachedThreadPool();
        final List<String> violations = new CopyOnWriteArrayList<>();
        final List<String> sizes = new CopyOnWriteArrayList<>();
        volatile CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        volatile boolean redirect, failImages, slowLogin, brokenFirst;
        volatile String lastImageId = "";
        Nas() throws IOException {
            server.createContext("/", this::handle);
            server.setExecutor(workers);
            server.start();
        }
        String link() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/photo/mo/sharing/album-token"; }
        void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"POST".equals(exchange.getRequestMethod())) violations.add("Unexpected method");
                Map<String, String> params = decode(exchange);
                if ("SYNO.Core.Sharing.Login".equals(params.get("api"))) {
                    assert exchange.getRequestHeaders().getFirst("X-SYNO-SHARING") == null : "Sharing header must not route the core login";
                    if (!exchange.getRequestURI().getPath().equals("/photo/webapi/entry.cgi")) violations.add("Wrong login path");
                    if (redirect) {
                        exchange.getResponseHeaders().set("Location", "http://unrelated.invalid/");
                        exchange.sendResponseHeaders(302, -1); return;
                    }
                    if (slowLogin) { started.countDown(); release.await(10, TimeUnit.SECONDS); }
                    if (!JSONObject.quote(PASSWORD).equals(params.get("password"))) {
                        send(exchange, "{\"success\":false,\"error\":{\"code\":400}}".getBytes(StandardCharsets.UTF_8)); return;
                    }
                    assert params.get("sharing_id").equals(JSONObject.quote("album-token"));
                    exchange.getResponseHeaders().add("Set-Cookie", "sharing_sid=test-session; Path=/photo; HttpOnly");
                    send(exchange, "{\"success\":true}".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                if (!exchange.getRequestURI().getPath().equals("/photo/mo/sharing/webapi/entry.cgi")) violations.add("Wrong album path");
                String cookie = exchange.getRequestHeaders().getFirst("Cookie");
                if (cookie == null || !cookie.contains("sharing_sid=test-session")) violations.add("Missing session cookie");
                if (!"album-token".equals(exchange.getRequestHeaders().getFirst("X-SYNO-SHARING"))) violations.add("Missing sharing header");
                if ("SYNO.Foto.Browse.Item".equals(params.get("api"))) {
                    assert params.get("limit").equals("100") && params.get("additional").equals("[\"thumbnail\"]");
                    assert params.get("sort_by").equals("\"takentime\"") && params.get("passphrase").equals("\"album-token\"");
                    JSONArray items = new JSONArray();
                    if (params.get("offset").equals("0")) {
                        items.put(photo(1));
                        for (int i = 0; i < 99; i++) items.put(new JSONObject().put("id", i + 10).put("type", "video"));
                    } else { assert params.get("offset").equals("100"); items.put(photo(2).put("type", "live")); }
                    send(exchange, new JSONObject().put("success", true).put("data", new JSONObject().put("list", items))
                        .toString().getBytes(StandardCharsets.UTF_8));
                } else if ("SYNO.Foto.Thumbnail".equals(params.get("api"))) {
                    assert params.get("_sharing_id").equals("album-token");
                    String size = new JSONArray("[" + params.get("size") + "]").getString(0);
                    sizes.add(size);
                    if (failImages || (brokenFirst && params.get("id").equals("1"))) send(exchange, "{\"success\":false}".getBytes(StandardCharsets.UTF_8));
                    else if (size.equals("xl")) send(exchange, new byte[PhotoHtml.MAX_IMAGE_BYTES + 1]);
                    else { lastImageId = params.get("id"); send(exchange, JPEG); }
                } else { violations.add("Unexpected API call"); exchange.sendResponseHeaders(400, -1); }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } catch (AssertionError error) {
                violations.add(error.toString());
                exchange.sendResponseHeaders(500, -1);
            } finally { exchange.close(); }
        }
        JSONObject photo(int id) {
            return new JSONObject().put("id", id).put("type", "photo").put("additional",
                new JSONObject().put("thumbnail", new JSONObject().put("cache_key", "cache-" + id)));
        }
        Map<String, String> decode(HttpExchange exchange) throws IOException {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024]; int count;
            while ((count = exchange.getRequestBody().read(buffer)) != -1) body.write(buffer, 0, count);
            Map<String, String> params = new HashMap<>();
            for (String pair : new String(body.toByteArray(), StandardCharsets.UTF_8).split("&")) {
                String[] parts = pair.split("=", 2);
                params.put(URLDecoder.decode(parts[0], "UTF-8"), URLDecoder.decode(parts.length == 2 ? parts[1] : "", "UTF-8"));
            }
            return params;
        }
        void send(HttpExchange exchange, byte[] bytes) throws IOException {
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
        public void close() { release.countDown(); server.stop(0); workers.shutdownNow(); }
    }
}
