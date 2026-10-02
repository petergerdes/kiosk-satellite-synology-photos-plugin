// SPDX-License-Identifier: GPL-3.0-only
package io.github.petergerdes.kiosk.synology;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.SocketTimeoutException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Read-only shared-album API. Each instance owns its cookies and cancellable connection. */
final class SynologyClient implements AutoCloseable {
    static final int PAGE_SIZE = 100;
    static final int MAX_ITEMS = 50_000;
    final URI loginEndpoint;
    final URI albumEndpoint;
    final String sharingId;
    private final String password;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    private volatile boolean closed;
    private HttpURLConnection connection;

    SynologyClient(String link, String password) {
        URI uri;
        try { uri = URI.create(link.trim()); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("Paste a complete Synology Photos album sharing link."); }
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("Use an http:// or https:// sharing link without embedded account credentials.");
        }
        Matcher path = Pattern.compile("^(.*?)(?:/mo)?/sharing/([A-Za-z0-9_-]{1,128})/?$").matcher(uri.getRawPath());
        if (!path.matches()) {
            throw new IllegalArgumentException("Use the full album link ending in /sharing/… . Open shortened or QuickConnect links in a browser first.");
        }
        sharingId = path.group(2);
        String origin = uri.getScheme() + "://" + uri.getRawAuthority();
        loginEndpoint = URI.create(origin + path.group(1) + "/webapi/entry.cgi");
        albumEndpoint = URI.create(origin + uri.getRawPath().replaceFirst("/" + sharingId + "/?$", "/webapi/entry.cgi"));
        this.password = password;
    }

    void login() throws IOException {
        cookies.getCookieStore().removeAll();
        api(loginEndpoint, params("SYNO.Core.Sharing.Login", "login", "1",
            "sharing_id", sharingId, "password", password));
    }

    List<Photo> listPhotos() throws IOException {
        List<Photo> photos = new ArrayList<>();
        for (int offset = 0; offset < MAX_ITEMS; offset += PAGE_SIZE) {
            JSONObject data = api(albumEndpoint, params("SYNO.Foto.Browse.Item", "list", "1",
                "offset", String.valueOf(offset), "limit", String.valueOf(PAGE_SIZE),
                "additional", "[\"thumbnail\"]", "sort_by", "takentime", "sort_direction", "asc",
                "passphrase", sharingId));
            try {
                JSONArray items = data.getJSONArray("list");
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    if (!"photo".equals(item.optString("type")) && !"live".equals(item.optString("type"))) continue;
                    JSONObject additional = item.optJSONObject("additional");
                    JSONObject thumbnail = additional == null ? null : additional.optJSONObject("thumbnail");
                    if (thumbnail != null && !thumbnail.optString("cache_key").isEmpty()) {
                        photos.add(new Photo(item.getLong("id"), thumbnail.getString("cache_key")));
                    }
                }
                if (items.length() < PAGE_SIZE) return photos;
            } catch (JSONException error) { throw new IOException("Unexpected Synology Photos album response. Check your Photos version."); }
        }
        throw new IOException("This album exceeds 50,000 items. Use a smaller screensaver album.");
    }

    Image image(Photo photo) throws IOException {
        return image(photo, PhotoHtml.MAX_IMAGE_BYTES);
    }

    Image image(Photo photo, int budget) throws IOException {
        IOException last = null;
        for (String size : new String[]{"xl", "m", "sm"}) {
            try {
                byte[] bytes = request(albumEndpoint, params("SYNO.Foto.Thumbnail", "get", "2",
                    "id", String.valueOf(photo.id), "cache_key", photo.cacheKey,
                    "type", "unit", "size", size, "_sharing_id", sharingId), 8 * 1024 * 1024);
                String mime = imageMime(bytes);
                if (mime != null) return PhotoEncoder.fit(bytes, mime, budget);
                throw new IOException("Synology did not return a supported photo thumbnail. Check album access and thumbnail generation.");
            } catch (InterruptedIOException error) { throw error; }
            catch (IOException error) { last = error; }
        }
        throw last;
    }

    static String imageMime(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255) return "image/jpeg";
        if (bytes.length >= 8 && bytes[0] == (byte) 137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71
                && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10) return "image/png";
        if (bytes.length >= 12 && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) return "image/webp";
        return null;
    }

    private JSONObject api(URI endpoint, Map<String, String> params) throws IOException {
        byte[] bytes = request(endpoint, params, 2 * 1024 * 1024);
        try {
            JSONObject response = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            if (!response.optBoolean("success")) {
                JSONObject error = response.optJSONObject("error");
                int code = error == null ? -1 : error.optInt("code", -1);
                throw new IOException("Synology API error " + code + ". Check the album password, link expiry and sharing permission (anyone with the link).");
            }
            return response.optJSONObject("data") == null ? new JSONObject() : response.getJSONObject("data");
        } catch (JSONException error) { throw new IOException("The NAS returned a web page or invalid JSON. Use a direct Photos URL and check reverse-proxy routing."); }
    }

    private byte[] request(URI endpoint, Map<String, String> params, int limit) throws IOException {
        HttpURLConnection current = (HttpURLConnection) endpoint.toURL().openConnection();
        synchronized (this) {
            if (closed || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Connection cancelled");
            connection = current;
        }
        try {
            current.setConnectTimeout(2000);
            current.setReadTimeout(2000);
            current.setInstanceFollowRedirects(false);
            current.setRequestMethod("POST");
            current.setDoOutput(true);
            current.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            if (!"SYNO.Core.Sharing.Login".equals(params.get("api"))) current.setRequestProperty("X-SYNO-SHARING", sharingId);
            for (Map.Entry<String, List<String>> header : cookies.get(endpoint, Collections.emptyMap()).entrySet()) {
                for (String value : header.getValue()) current.addRequestProperty(header.getKey(), value);
            }
            byte[] body = form(params).getBytes(StandardCharsets.UTF_8);
            current.setFixedLengthStreamingMode(body.length);
            try (java.io.OutputStream out = current.getOutputStream()) { out.write(body); }
            int code = current.getResponseCode();
            if (code >= 300 && code < 400) throw new IOException("The NAS redirects this URL. Paste the final direct Synology Photos sharing link; redirects are not followed.");
            if (code != 200) throw new IOException("NAS returned HTTP " + code + ". Check the address, sharing access and reverse proxy.");
            cookies.put(endpoint, current.getHeaderFields());
            if (current.getContentLengthLong() > limit) throw new IOException("Thumbnail or API response is too large.");
            try (InputStream in = current.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = in.read(buffer)) != -1) {
                    if (closed || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Connection cancelled");
                    if (out.size() + length > limit) throw new IOException("Thumbnail or API response is too large.");
                    out.write(buffer, 0, length);
                }
                return out.toByteArray();
            }
        } catch (SSLException error) {
            throw new IOException("HTTPS certificate could not be verified. Use a trusted certificate matching the NAS hostname.");
        } catch (UnknownHostException error) {
            throw new IOException("The kiosk cannot resolve the NAS hostname. Check DNS or use its local address.");
        } catch (SocketTimeoutException error) {
            throw new IOException("The NAS request timed out. Check Wi-Fi, the address and NAS availability.");
        } catch (ConnectException error) {
            throw new IOException("The kiosk cannot connect to the NAS. Check the address, port and firewall.");
        } finally {
            synchronized (this) { if (connection == current) connection = null; }
            current.disconnect();
        }
    }

    private static Map<String, String> params(String api, String method, String version, String... pairs) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("api", api); params.put("method", method); params.put("version", version);
        // Synology's JSON request format still travels as form fields. Routing keys
        // stay plain; API string arguments must be JSON string literals.
        for (int i = 0; i < pairs.length; i += 2) {
            String key = pairs[i];
            String value = pairs[i + 1];
            boolean literal = key.equals("additional") || key.equals("offset") || key.equals("limit")
                || key.equals("id") || key.equals("_sharing_id");
            params.put(key, literal ? value : JSONObject.quote(value));
        }
        return params;
    }

    private static String form(Map<String, String> params) throws IOException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(URLEncoder.encode(entry.getKey(), "UTF-8")).append('=')
                .append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return body.toString();
    }

    @Override public synchronized void close() {
        closed = true;
        if (connection != null) connection.disconnect();
    }

    static final class Photo {
        final long id;
        final String cacheKey;
        Photo(long id, String cacheKey) { this.id = id; this.cacheKey = cacheKey; }
    }

    static final class Image {
        final byte[] bytes;
        final String mime;
        final int width, height;
        Image(byte[] bytes, String mime) { this(bytes, mime, 0, 0); }
        Image(byte[] bytes, String mime, int width, int height) {
            this.bytes = bytes; this.mime = mime; this.width = width; this.height = height;
        }
    }
}
