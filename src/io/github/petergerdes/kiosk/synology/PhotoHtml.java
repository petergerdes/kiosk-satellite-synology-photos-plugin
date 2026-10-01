// SPDX-License-Identifier: GPL-3.0-only
package io.github.petergerdes.kiosk.synology;

import android.util.Base64;
import java.util.Collections;
import java.util.Map;

/** Embed both sides of a transition: KS recreates the document on each slide. */
final class PhotoHtml {
    static final int MAX_IMAGE_BYTES = 190_000;

    static String photo(byte[] bytes, String mime, boolean fill) {
        return photo(null, new SynologyClient.Image(bytes, mime),
            Collections.singletonMap("fit", fill ? "Fill screen" : "Fit whole photo"));
    }

    static String photo(SynologyClient.Image previous, SynologyClient.Image image, Map<String, Object> settings) {
        String transition = "Slide".equals(settings.get("transition")) ? "slide"
            : "None".equals(settings.get("transition")) ? "none" : "fade";
        boolean motion = "Ken Burns".equals(settings.get("motion"));
        double duration = number(settings.get("transitionSeconds"), 1, 0.2, 3);
        double interval = number(settings.get("intervalSeconds"), 30, 5, 300);
        String outgoing = previous == null || "none".equals(transition) ? ""
            : "<div class=\"previous\" aria-hidden=\"true\">" + img(previous, "") + "</div>";
        String body = "<main class=\"" + transition + (motion ? " motion" : "") + "\" style=\"--transition:"
            + duration + "s;--interval:" + interval + "s\">" + outgoing
            + "<div class=\"incoming\">" + img(image, "current") + "</div></main>"
            + "<script>const images=document.querySelectorAll('img');"
            + "function start(){for(let i=0;i<images.length;i++)if(!images[i].complete||!images[i].naturalWidth)return;"
            + "document.querySelector('main').classList.add('ready')}"
            + "for(let i=0;i<images.length;i++)images[i].addEventListener('load',start,{once:true});start();</script>";
        return document(body, "Fill screen".equals(settings.get("fit")));
    }

    private static String img(SynologyClient.Image image, String id) {
        if (image.bytes.length > MAX_IMAGE_BYTES) throw new IllegalArgumentException("Photo exceeds renderer budget");
        if (!image.mime.matches("image/(jpeg|png|webp)")) throw new IllegalArgumentException("Unsupported image type");
        return "<img" + (id.isEmpty() ? "" : " id=\"" + id + "\"") + " alt=\"Album photo\" src=\"data:"
            + image.mime + ";base64," + Base64.encodeToString(image.bytes, Base64.NO_WRAP) + "\">";
    }

    private static double number(Object value, double fallback, double min, double max) {
        double number = value instanceof Number ? ((Number) value).doubleValue() : fallback;
        return Double.isFinite(number) ? Math.max(min, Math.min(max, number)) : fallback;
    }

    static String message(String text) {
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return document("<p>" + escaped + "</p>", false);
    }

    private static String document(String body, boolean fill) {
        return "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#000;color:#ddd}"
            + "body{display:flex;align-items:center;justify-content:center;font:20px sans-serif;text-align:center}"
            + "main{position:relative;width:100%;height:100%;overflow:hidden}"
            + ".previous,.incoming{position:absolute;top:0;left:0;width:100%;height:100%;background:#000}"
            + ".incoming{opacity:0}.ready .incoming{opacity:1}"
            + "img{width:100%;height:100%;object-fit:" + (fill ? "cover" : "contain") + "}"
            + ".ready.fade .incoming{animation:fade-in var(--transition) ease-in-out both}"
            + ".ready.slide .incoming{animation:slide-in var(--transition) ease-in-out both}"
            + ".ready.motion .incoming img{animation:ken-burns var(--interval) linear both}"
            + ".motion .previous img{transform:scale(1.12) translate(1%,-1%)}"
            + "@keyframes fade-in{from{opacity:0}to{opacity:1}}"
            + "@keyframes slide-in{from{transform:translateX(100%)}to{transform:translateX(0)}}"
            + "@keyframes ken-burns{from{transform:scale(1.02) translate(-1%,1%)}to{transform:scale(1.12) translate(1%,-1%)}}"
            + "@media(prefers-reduced-motion:reduce){.ready .incoming,.ready.motion .incoming img{animation:none}"
            + ".motion .previous img{transform:none}}"
            + "p{padding:24px;max-width:36em}</style></head><body>" + body + "</body></html>";
    }
}
