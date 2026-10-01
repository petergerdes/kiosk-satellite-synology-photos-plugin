// SPDX-License-Identifier: GPL-3.0-only
package io.github.petergerdes.kiosk.synology;

import android.util.Base64;

/** A single embedded photo: the KS renderer deliberately cannot reach the NAS. */
final class PhotoHtml {
    static final int MAX_IMAGE_BYTES = 380_000;

    static String photo(byte[] bytes, String mime, boolean fill) {
        if (bytes.length > MAX_IMAGE_BYTES) throw new IllegalArgumentException("Photo exceeds renderer budget");
        if (!mime.matches("image/(jpeg|png|webp)")) throw new IllegalArgumentException("Unsupported image type");
        return document("<img alt=\"Album photo\" src=\"data:" + mime + ";base64,"
            + Base64.encodeToString(bytes, Base64.NO_WRAP) + "\">", fill);
    }

    static String message(String text) {
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return document("<p>" + escaped + "</p>", false);
    }

    private static String document(String body, boolean fill) {
        return "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#000;color:#ddd}"
            + "body{display:flex;align-items:center;justify-content:center;font:20px sans-serif;text-align:center}"
            + "img{width:100%;height:100%;object-fit:" + (fill ? "cover" : "contain") + "}"
            + "p{padding:24px;max-width:36em}</style></head><body>" + body + "</body></html>";
    }
}
