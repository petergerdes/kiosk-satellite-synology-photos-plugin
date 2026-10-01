# Synology Photos screensaver for Kiosk Satellite

Display photos from one Synology Photos album on your [Kiosk Satellite](https://github.com/jxlarrea/kiosk-satellite) kiosk. **Paste an album sharing link, optionally enter its password, and choose the screensaver.** No NAS account credentials, API keys, album IDs, Home Assistant integration, or extra server are needed.

The plugin is open source under [GPL-3.0](LICENSE). It uses Kiosk Satellite's official [SDK 1 and plugin template](https://github.com/jxlarrea/kiosk-satellite-plugin-hello-world). This is an independent community project, unaffiliated with Synology or Kiosk Satellite.

**Initial release:** tested against SDK 1, a simulated NAS, and a real password-protected Synology Photos album. All 18 photos, including Live Photos, were fetched and encoded on an Android API 36.1 emulator, and the HTML renderer was checked in a browser. Installation and display inside the actual Kiosk Satellite app still need a device check. Synology Photos uses an undocumented API that may change between versions. See [verification](docs/development.md#verification).

## What you need

- Kiosk Satellite with SDK 1 plugin and screensaver support, on Android 7.0 or newer.
- A Synology NAS running DSM 7 and the **Synology Photos** package. Photo Station and Moments are different applications and are not supported.
- A shareable album containing photos, with access set to **anyone with the link**. Password-protected sharing links are supported by the implementation. Links requiring a DSM account login are not supported.
- A direct NAS address reachable from the Android kiosk. A local connection is enough; you do not need to expose the NAS to the internet.

## Install

1. Open **Settings → Plugin Manager** on the kiosk, or **Plugin Manager** in Kiosk Satellite's Remote Admin.
2. Turn on **Enable Plugins**.
3. Choose **Add plugin**, paste `https://github.com/petergerdes/kiosk-satellite-synology-photos-plugin`, and choose **Preview**.
4. Review the source and this README, choose **Trust and install**, then enable **Synology Photos**.

Kiosk Satellite installs from the latest stable GitHub release. The release must have its ZIP, manifest and checksum uploaded by the included GitHub Actions workflow. Source code alone is not installable. If no completed release is available yet, use the [local build instructions](docs/development.md).

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
| Shuffle photos | On | Randomize each pass; avoid repeating the last photo immediately at the next pass. |
| Photo layout | Fit whole photo | Keep the entire photo on black, or choose **Fill screen** to crop the edges. |
| Check for album changes | 15 minutes | Refresh the album list every 1–120 minutes while the slideshow is active. |
| Reconnect and refresh album | Action | Recheck access and load a fresh photo immediately. Assign it to a gesture, drawer shortcut, or Home Assistant button through KS if desired. |

The plugin checks access and loads one photo after setup or reconnection. After that, it advances and refreshes while the screensaver is active and the screen is on; known other screensaver modes pause it. KS keeps control of idle activation, schedules, brightness, wake detection, touch dismissal, widgets, and Now Playing layout. Fit and fill adapt to the available viewport, including portrait screens.

Videos are skipped; Live Photos are displayed as still images. The plugin asks Synology for large (`xl`) image thumbnails, which also allows the NAS to convert formats such as HEIC into displayable previews. Android downsamples oversized dimensions and compresses large thumbnails into JPEG to fit the renderer's budget, retaining as much detail as possible. If that fails, it tries `m` and then `sm`. It supports JPEG, PNG and WebP thumbnails, up to 1920 pixels on the longest side after processing, and albums below 50,000 total items.

## Troubleshooting

Read the plugin's status in **Plugin Manager → Synology Photos**. The Remote Admin Overview also shows an album status tile.

| Status or symptom | What to check |
| --- | --- |
| Invalid sharing link | Paste the whole link, including `http://` or `https://` and the final sharing token. A normal album page is not a sharing link. |
| Synology API error | Confirm sharing is enabled for anyone with the link, the link has not expired, and the album password is correct. Synology returns version-dependent error codes; the status includes the code without exposing the server response. |
| Hostname cannot be resolved / cannot connect / timeout | Open the same link on the kiosk. Check Wi-Fi, DNS, NAS address, port, firewall and reverse-proxy routing. Requests have two-second connection and read timeouts; slow remote links may need a faster route. |
| HTTPS certificate could not be verified | Use a trusted certificate and its matching hostname. Self-signed or mismatched certificates are not bypassed. HTTP can be used on a trusted local network if the host app permits it, but sends the link, password and photos unencrypted. |
| Redirect / web page / invalid JSON | Use the final direct Photos URL. Configure the proxy to forward the Photos `webapi/entry.cgi` routes as well as the web UI. API requests do not follow redirects. |
| No photos with thumbnails | Add photos to the selected album, wait for Synology to finish indexing and thumbnail generation, then run **Reconnect and refresh album**. Video-only albums cannot be displayed. |
| NAS temporarily unavailable | The last loaded photo remains visible. Broken thumbnails are skipped; connection failures retry after a failed pass, with a 30-second delay before reconnecting. Automatic retries with a cached photo resume while the slideshow is active. |
| Screensaver is black / mode is missing | Enable both Plugin Manager and this plugin, confirm it connects, and select **Album (Synology Photos)**. Check your KS build supports plugin screensavers. |

When reporting an issue, include your DSM, Synology Photos, Kiosk Satellite and Android versions, plus the status text. **Remove sharing tokens, passwords, photos, hostnames and personal data from logs or screenshots.**

## Privacy and storage

The Java plugin talks directly to the configured NAS. It does not send photos or credentials to this project, analytics services, or a separate server. GitHub is used by KS to install and check plugin releases.

A sharing link grants access to its album. Treat its token and optional password as secrets. **SDK 1 has no secret/password setting type:** both values are ordinary KS settings, visible in the plugin settings and potentially included in KS exports or backups. Do not enter your DSM password. Protect access to Remote Admin and your backups. Revoke the link in Synology Photos when it is no longer needed.

Sharing sessions and photo metadata are held in memory. The current image is embedded as image data in the isolated KS screensaver document; the NAS URL, password and cookie are not included in that document. The plugin creates no photo cache on disk and provides no persistent offline library. Disabling the plugin stops requests and removes its renderer; a new session needs the NAS again. KS itself manages saved settings and their removal on uninstall.

Plugins execute trusted code inside the KS app and have its Android permissions. Manifest capabilities describe the host API calls this plugin uses (`screensaver` and `host.read`); they are not a security sandbox.

## Build, contribute and publish

See [development and release instructions](docs/development.md), [contributing](CONTRIBUTING.md), and [security reporting](SECURITY.md). Bug reports and pull requests are welcome.

```sh
python3 tools/test.py
python3 tools/build.py
```

The build produces `dist/synology-photos-0.1.0.zip`, its SHA-256 checksum and the release manifest. Python 3, JDK 17+ and an Android SDK are required for a build; tests need only Python and Java.

The SDK and build tools are adapted from the [official plugin template](https://github.com/jxlarrea/kiosk-satellite-plugin-hello-world) and retain their [Apache-2.0 license](sdk/LICENSE). See [NOTICE](NOTICE) for attribution. Synology API behavior was researched using the [shared-album client implementation](https://github.com/Caleb9/synology-photos-slideshow/blob/main/synology_photos_client.py) and [Synology Photos API observations](https://github.com/zeichensatz/SynologyPhotosAPI). No code from those Synology clients is bundled.
