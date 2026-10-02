# Synology Photos screensaver for Kiosk Satellite

Display photos from one Synology Photos album on your [Kiosk Satellite](https://github.com/jxlarrea/kiosk-satellite) kiosk. **Paste an album sharing link, optionally enter its password, and choose the screensaver.** No NAS account credentials, API keys, album IDs, Home Assistant integration, or extra server are needed.

The plugin is open source under [GPL-3.0](LICENSE). This is an independent community project, unaffiliated with Synology or Kiosk Satellite.

## What you need

- Kiosk Satellite with SDK 1 plugin and screensaver support, on Android 7.0 or newer.
- A Synology NAS running DSM 7 and the **Synology Photos** package. Photo Station and Moments are different applications and are not supported.
- A shareable album containing photos, with access set to **anyone with the link**. Password-protected sharing links are supported. Links requiring a DSM account login are not supported.
- A direct NAS address reachable from the Android kiosk. A local connection is enough; you do not need to expose the NAS to the internet.

## Install

1. Open **Settings → Plugin Manager** on the kiosk, or **Plugin Manager** in Kiosk Satellite's Remote Admin.
2. Turn on **Enable Plugins**.
3. Choose **Add plugin**, paste `https://github.com/petergerdes/kiosk-satellite-synology-photos-plugin`, and choose **Preview**.
4. Review the source and this README, choose **Trust and install**, then enable **Synology Photos**.

Kiosk Satellite installs the latest stable [GitHub release](https://github.com/petergerdes/kiosk-satellite-synology-photos-plugin/releases/latest). For manual ZIP installation or local builds, see the [development guide](docs/development.md#install-a-local-build).

## Connect your album

1. In **Synology Photos → Albums**, open the album you want to display.
2. Open its sharing settings, enable the sharing link, and grant access to **anyone with the link**. A view-only sharing permission is sufficient. You can add an album-sharing password. Avoid an expiration date if you want it to keep working unattended. Synology's [sharing guide](https://kb.synology.com/en-us/DSM/help/SynologyPhotos/sharing?version=7) covers the available sharing controls.
3. Copy the full sharing link. It usually looks like:

   ```text
   https://nas.example.com/photo/mo/sharing/ALBUM_TOKEN
   https://nas.example.com:5001/mo/sharing/ALBUM_TOKEN
   ```

4. In **Plugin Manager → Synology Photos**, paste it into **Album sharing link**. If applicable, enter **Album password (optional)**. This is the password for the link, **not your NAS account password**. Settings save automatically; the connection check starts immediately.
5. Wait for **Connected · N photos** in the plugin status.
6. In **Screensaver → Screensaver mode**, choose **Album (Synology Photos)**. Start the screensaver from the kiosk menu or wait for your configured idle timeout. You can also select this mode in a screensaver schedule.

Use a link that opens on the kiosk without signing in to DSM. If a copied link uses a remote hostname that is unavailable locally, you can replace its hostname and port with your reachable NAS address, keeping the sharing path and token. The replacement hostname must match the HTTPS certificate. Custom Photos aliases and reverse-proxy prefixes are accepted when the full link ends in `/sharing/TOKEN` or `/mo/sharing/TOKEN`.

Short links and QuickConnect landing pages are not resolved automatically. Open them in a browser and copy the final direct NAS sharing URL. QuickConnect relay transport is not supported. A local NAS URL, a VPN-accessible URL, or a direct reverse-proxy URL is the intended connection method.

## Slideshow settings

| Setting | Default | Behavior |
| --- | --- | --- |
| Time per photo | 30 seconds | Choose 5–300 seconds. |
| Photo order | Shuffle | Choose **Shuffle**, **Oldest first**, or **Newest first**, using the date each photo was taken. Shuffle avoids repeating the last photo immediately at the next pass. |
| Transition | Fade | Choose **None**, **Fade**, or **Slide** between photos. |
| Transition duration | 1 second | Choose 0.2–3 seconds for fades and slides. |
| Photo motion | None | Choose **Ken Burns** for a slow pan and zoom while each photo is displayed. Works with any transition. |
| Photo layout | Fit whole photo | Keep the entire photo on black, or choose **Fill screen** to crop the edges. |
| Check for album changes | 15 minutes | Refresh the album list every 1–120 minutes while the slideshow is active. |
| Reconnect and refresh album | Action | Recheck access and load a fresh photo immediately. Assign it to a gesture, drawer shortcut, or Home Assistant button through KS if desired. |

The slideshow advances and checks for album changes while the screensaver is active and the screen is on. Kiosk Satellite keeps control of idle activation, schedules, brightness, wake detection, touch dismissal, widgets, and Now Playing layout. Photos adapt to the available space, including portrait screens.

For a gentle slideshow, choose **Fade** with **Ken Burns**. For still photos that change immediately, set both **Transition** and **Photo motion** to **None**. Ken Burns zooms slightly into each photo, so some edges may be cropped even with **Fit whole photo**. Effects respect the display's reduced-motion preference when available.

When updating from 0.1.0, **Photo order** replaces the **Shuffle photos** toggle and starts at **Shuffle**. Choose **Oldest first** to keep the previous non-shuffled behavior. Your album connection and other compatible settings are retained.

Live Photos are displayed as still images; videos are skipped. The slideshow requests Synology's largest available preview, including previews for formats such as HEIC. Photos retain up to 1920 pixels on the longest side, with high-quality WebP compression and gradual resizing when needed to fit the screensaver. The current photo gets priority within the image budget; the outgoing photo is reduced only when a transition needs more space. With **Transition → None**, the current photo gets the entire budget. Previews that already fit are displayed unchanged. Albums must contain fewer than 50,000 total items.

For the sharpest display, choose **Fit whole photo** and **Photo motion → None** to avoid enlarging cropped or zoomed photos. **Transition → None** allows the most image detail. The plugin status shows the displayed resolution, such as **1707 × 1280 px**. The source preview's resolution still limits the detail available; this plugin does not download full-resolution originals.

## Troubleshooting

**The screen goes black before a fade or slide.** Update Kiosk Satellite to **2026.10.1 or newer**. Its maintainer fixed the WebView replacement that caused the gap, and the fix has been confirmed on a kiosk. See [issue #782](https://github.com/jxlarrea/kiosk-satellite/issues/782) and the [renderer fix guide](docs/host-renderer-fix.md) for background.

Read the plugin's status in **Plugin Manager → Synology Photos**. The Remote Admin Overview also shows an album status tile.

| Status or symptom | What to check |
| --- | --- |
| “GitHub denied the request or its request limit was reached” when adding the repository | GitHub may have temporarily limited requests from your network. Wait and retry, or connect the kiosk through another network. Manual ZIP installation may still work. See [GitHub's rate-limit documentation](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api). |
| Invalid sharing link | Paste the whole link, including `http://` or `https://` and the final sharing token. A normal album page is not a sharing link. |
| Synology API error | Confirm sharing is enabled for anyone with the link, the link has not expired, and the album password is correct. Include the displayed error code when reporting an issue. |
| Hostname cannot be resolved / cannot connect / timeout | Open the same link on the kiosk. Check Wi-Fi, DNS, NAS address, port, firewall and reverse-proxy routing. Slow remote connections may need a faster route. |
| HTTPS certificate could not be verified | Use a trusted certificate and its matching hostname. Self-signed or mismatched certificates are not bypassed. HTTP can be used on a trusted local network if the host app permits it, but sends the link, password and photos unencrypted. |
| Redirect / web page / invalid JSON | Use the final direct Photos URL. Configure the proxy to forward the Photos `webapi/entry.cgi` routes as well as the web UI. API requests do not follow redirects. |
| No photos with thumbnails | Add photos to the selected album, wait for Synology to finish indexing and thumbnail generation, then run **Reconnect and refresh album**. Video-only albums cannot be displayed. |
| Photos look pixelated or soft | Update the plugin to 0.2.2 or newer and use **Reconnect and refresh album**. Check the pixel dimensions in the plugin status. **Fit whole photo** and **Photo motion → None** avoid enlargement; **Transition → None** provides the largest image budget. If dimensions remain small, check Synology thumbnail generation and compare the album in Synology Photos. |
| NAS temporarily unavailable | The last loaded photo remains visible. The plugin retries automatically while the slideshow is active. Use **Reconnect and refresh album** to check immediately. |
| Screensaver is black / mode is missing | Enable both Plugin Manager and this plugin, confirm it connects, and select **Album (Synology Photos)**. Check your KS build supports plugin screensavers. |

When reporting an issue, include your DSM, Synology Photos, Kiosk Satellite and Android versions, plus the status text. **Remove sharing tokens, passwords, photos, hostnames and personal data from logs or screenshots.**

## Privacy and storage

Photos are fetched directly from your NAS. Photos and credentials are not sent to this project or an analytics service. Kiosk Satellite uses GitHub to install and check plugin releases.

A sharing link grants access to its album. Treat the link and optional password as secrets. **Both are saved as ordinary Kiosk Satellite settings and are visible in the plugin settings.** Protect access to Remote Admin and any backups containing these settings. Do not enter your DSM password. Revoke the link in Synology Photos when it is no longer needed.

The plugin keeps photos in memory and creates no photo cache on disk. A photo already loaded can remain visible during a connection interruption, but starting the plugin again requires access to the NAS. Disabling it stops requests and removes its screensaver. Kiosk Satellite removes the plugin's saved settings on uninstall.

Install only plugins you trust: they run inside Kiosk Satellite with its Android permissions.

## Contributing

Bug reports and pull requests are welcome. See the [contributing guide](CONTRIBUTING.md), [development and release instructions](docs/development.md), and [security reporting guidance](SECURITY.md).

The SDK and build tools are adapted from the [official plugin template](https://github.com/jxlarrea/kiosk-satellite-plugin-hello-world) and retain their [Apache-2.0 license](sdk/LICENSE). See [NOTICE](NOTICE) for attribution.
