// SPDX-License-Identifier: Apache-2.0
package io.github.petergerdes.kiosk.synology;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.PluginHost;

/** KS owns activation and wake behavior. A single worker owns all NAS requests. */
public final class SynologyPhotosPlugin implements KioskPlugin {
    static final String KEY = "album";
    static final String MODE = "plugin:synology-photos:album";
    private PluginHost host;
    private ScheduledExecutorService worker;
    private ScheduledFuture<?> task;
    private Session session;
    private Map<String, Object> settings;
    private volatile boolean active;
    private volatile boolean screenOn = true;
    private volatile String view;

    @Override public synchronized void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        active = false;
        screenOn = true;
        view = null;
        worker = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "synology-photos");
            thread.setDaemon(true);
            return thread;
        });
        host.subscribe("screensaver.state");
        host.subscribe("screensaver.view");
        host.subscribe("screen.state");
        host.executeCommand("isScreensaverActive", Collections.emptyMap(), (ok, data, error) -> {
            synchronized (SynologyPhotosPlugin.this) {
                if (this.host == host && ok) active = Boolean.TRUE.equals(data);
            }
        });
        host.executeCommand("isScreenOn", Collections.emptyMap(), (ok, data, error) -> {
            synchronized (SynologyPhotosPlugin.this) {
                if (this.host == host && ok) screenOn = Boolean.TRUE.equals(data);
            }
        });
        configure(settings);
    }

    @Override public synchronized void configure(Map<String, Object> settings) {
        if (host == null) return;
        this.settings = new HashMap<>(settings);
        cancelSession();
        String link = string(settings, "albumUrl");
        if (link.trim().isEmpty()) {
            status("Paste your Synology Photos album sharing link to connect.", false);
            host.publishScreensaver(KEY, "Album", PhotoHtml.message("Connect your album in Plugin Manager → Synology Photos."));
            return;
        }
        try {
            session = new Session(settings);
        } catch (IllegalArgumentException error) {
            status(error.getMessage(), true);
            host.publishScreensaver(KEY, "Album", PhotoHtml.message("Check the album sharing link in Plugin Manager → Synology Photos."));
            return;
        }
        host.publishScreensaver(KEY, "Album", PhotoHtml.message("Connecting to your album…"));
        status("Checking album access and loading the first photo…", false);
        Session current = session;
        task = worker.scheduleWithFixedDelay(current::tick, 0, 1, TimeUnit.SECONDS);
    }

    @Override public synchronized void execute(String command, Map<String, Object> arguments) {
        if ("refresh".equals(command) && settings != null) configure(settings);
    }

    @Override public void onEvent(String event, Map<String, Object> payload) {
        if ("ks.screensaver.state".equals(event)) active = Boolean.TRUE.equals(payload.get("active"));
        if ("ks.screen.state".equals(event)) screenOn = Boolean.TRUE.equals(payload.get("on"));
        if ("ks.screensaver.view".equals(event)) view = payload.get("view") instanceof String ? (String) payload.get("view") : null;
    }

    @Override public void stop() throws InterruptedException {
        ScheduledExecutorService previous;
        synchronized (this) {
            host = null;
            cancelSession();
            previous = worker;
            worker = null;
            settings = null;
        }
        if (previous != null) {
            previous.shutdownNow();
            previous.awaitTermination(2400, TimeUnit.MILLISECONDS);
        }
    }

    private void cancelSession() {
        if (session != null) session.client.close();
        session = null;
        if (task != null) task.cancel(true);
        task = null;
    }

    private void status(String message, boolean error) {
        host.status(message, error);
        host.publishStatusTile("album", "Synology Photos", error ? "warn" : "on",
            error ? "Album needs attention" : "" + message.substring(0, Math.min(80, message.length())));
    }

    private boolean visible() {
        return active && screenOn && (view == null || MODE.equals(view));
    }

    private static String string(Map<String, Object> settings, String key) {
        Object value = settings.get(key);
        return value instanceof String ? (String) value : "";
    }

    private static int number(Map<String, Object> settings, String key, int fallback, int min, int max) {
        Object value = settings.get(key);
        double number = value instanceof Number ? ((Number) value).doubleValue() : fallback;
        return Double.isFinite(number) ? (int) Math.max(min, Math.min(max, number)) : fallback;
    }

    private final class Session {
        final SynologyClient client;
        final boolean shuffle;
        final boolean fill;
        final long interval;
        final long refresh;
        List<SynologyClient.Photo> photos = new ArrayList<>();
        int index;
        int failures;
        long lastPhoto = -1;
        long nextSlide;
        long nextRefresh;
        long retryAt;
        boolean reconnect = true;
        boolean hasPhoto;

        Session(Map<String, Object> settings) {
            client = new SynologyClient(string(settings, "albumUrl"), string(settings, "albumPassword"));
            shuffle = !Boolean.FALSE.equals(settings.get("shuffle"));
            fill = "Fill screen".equals(string(settings, "fit"));
            interval = TimeUnit.SECONDS.toNanos(number(settings, "intervalSeconds", 30, 5, 300));
            refresh = TimeUnit.MINUTES.toNanos(number(settings, "refreshMinutes", 15, 1, 120));
        }

        void tick() {
            synchronized (SynologyPhotosPlugin.this) { if (session != this || host == null) return; }
            long now = System.nanoTime();
            if (now < retryAt || (hasPhoto && !visible())) return;
            boolean updating = reconnect || now >= nextRefresh;
            try {
                if (updating) {
                    client.login();
                    List<SynologyClient.Photo> updated = client.listPhotos();
                    if (updated.isEmpty()) throw new java.io.IOException("No photos with thumbnails found. Add photos to this album and let Synology finish indexing.");
                    photos = updated;
                    index = 0;
                    order();
                    reconnect = false;
                    nextRefresh = System.nanoTime() + refresh;
                    failures = 0;
                    updating = false;
                }
                if (hasPhoto && now < nextSlide) return;
                SynologyClient.Photo photo = photos.get(index++);
                if (index == photos.size()) { index = 0; }
                SynologyClient.Image image = client.image(photo);
                String html = PhotoHtml.photo(image.bytes, image.mime, fill);
                synchronized (SynologyPhotosPlugin.this) {
                    if (session != this || host == null) return;
                    host.publishScreensaver(KEY, "Album", html);
                    status("Connected · " + photos.size() + " photos. Select Album (Synology Photos) in Screensaver mode.", false);
                }
                lastPhoto = photo.id;
                hasPhoto = true;
                failures = 0;
                retryAt = 0;
                nextSlide = System.nanoTime() + interval;
                if (index == 0) order();
            } catch (Exception error) {
                synchronized (SynologyPhotosPlugin.this) {
                    if (session != this || host == null || Thread.currentThread().isInterrupted()) return;
                    String message = error instanceof java.io.IOException ? error.getMessage() : "Unable to render this photo. Try another album or report this issue.";
                    status(message + (hasPhoto ? " Keeping the last photo." : " Retrying automatically."), true);
                    if (!hasPhoto) host.publishScreensaver(KEY, "Album", PhotoHtml.message("Album unavailable. Check the connection status in Plugin Manager → Synology Photos."));
                }
                failures++;
                // Try the next broken thumbnail; reconnect after an entire failed pass.
                if (updating || failures >= photos.size()) {
                    reconnect = true;
                    retryAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                } else {
                    retryAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
                    nextSlide = 0;
                }
            }
        }

        private void order() {
            if (!shuffle) return;
            Collections.shuffle(photos);
            if (photos.size() > 1 && photos.get(0).id == lastPhoto) Collections.swap(photos, 0, 1);
        }
    }
}
