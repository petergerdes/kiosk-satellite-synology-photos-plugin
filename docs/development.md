# Development and releases

## Requirements

- Python 3.9 or newer.
- JDK 17 or newer. Set `JAVA_HOME` if the Java tools are not on your `PATH`.
- For ZIP builds: an Android SDK with a numbered platform and build-tools containing `d8`. Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`).

For example:

```sh
export JAVA_HOME=/path/to/jdk
export ANDROID_HOME=/path/to/android-sdk
sdkmanager 'platforms;android-35' 'build-tools;35.0.0'
python3 tools/test.py
python3 tools/build.py --android-platform 35
```

Without `--android-platform`, the builder selects the highest installed numeric platform. The package still targets a minimum Android API of 24. SDK interfaces are compiled separately and excluded from the plugin's DEX files. Android supplies `org.json` and `android.util.Base64`; there are no third-party runtime dependencies.

The JVM test runner downloads a pinned `org.json` test-only jar from Maven Central into ignored `.cache/`, verifies its SHA-256, and uses test substitutes for Android's Base64 and bitmap APIs (JDK ImageIO supplies the test codec). After the first run, the test dependency can be used offline. Neither the jar nor the substitutes are packaged in the plugin.

## Code

| File | Responsibility |
| --- | --- |
| `src/.../SynologyClient.java` | Sharing-link parsing, sharing login, per-client cookies, paginated photo listing and bounded thumbnail downloads. |
| `src/.../SynologyPhotosPlugin.java` | KS lifecycle, cancellable worker, visibility events, slideshow ordering, retries and status. |
| `src/.../PhotoHtml.java` | Responsive HTML with embedded photos, fade/slide transitions and Ken Burns motion. |
| `src/.../PhotoEncoder.java` | Android bitmap decoding, downsampling and JPEG compression to fit the renderer budget. |
| `kiosk-satellite-plugin.json` | Identity, capabilities, setup settings and refresh command. |
| `sdk/src/` | Official compile-time SDK interfaces. |
| `tools/` | Build, test, manifest and package validation tools. |

The sharing login uses `SYNO.Core.Sharing.Login` at the Photos application's `webapi/entry.cgi`, without the sharing-routing header. Album calls use the shared-link route's `webapi/entry.cgi`, the `X-SYNO-SHARING` header and the sharing session cookie. Listing uses `SYNO.Foto.Browse.Item` v1 in pages of 100; thumbnails use `SYNO.Foto.Thumbnail` v2. API string arguments are JSON-quoted and then form-encoded in the POST body, including the login password. No account login, writing, downloading originals, or global TLS/cookie configuration is implemented.

KS blocks external requests inside a screensaver WebView. The worker therefore fetches thumbnails and publishes inline HTML using `publishScreensaver`. Each network image is limited to 8 MiB. Android's bitmap codec checks dimensions, downsamples when the longest side exceeds 1920 pixels, and tries JPEG quality levels 85, 70, 55 and 40 if needed. Each display image is capped at 190,000 bytes. Two images plus Base64 and markup stay below KS's 512 KiB HTML limit.

KS recreates the document on every publication. For fade and slide transitions, the worker includes the previous image and the next image in the new document. CSS animates the incoming layer once both images have loaded; opaque black layer backgrounds prevent a previous landscape photo from showing through a new portrait photo's margins. Ken Burns pans and zooms the incoming image over the configured photo interval. The previous image holds its final motion position, and the renderer respects `prefers-reduced-motion`. Recreating the WebView may still cause a brief flash on some devices. There is no JavaScript bridge, local HTTP server, or persistent offline photo cache.

The NAS provides the list sorted by date taken, oldest first. **Newest first** reverses that list when it is refreshed. **Shuffle** randomizes each pass and prevents an immediate repeat across passes. The `order` select replaces the old `shuffle` boolean, which KS drops on update; the new default is Shuffle. Existing connection, timing and layout settings keep their original keys and types.

All network work runs off the serialized SDK callback worker. Reconfiguration cancels the previous task and disconnects its active request. A session identity check prevents late responses from publishing into a new configuration or a stopped plugin. Screen-off and other known screensaver modes pause rotation. The SDK has no initial active-mode snapshot or foreground event; when enabling an already-active screensaver, the plugin may prepare slides until it receives a view event. The host still owns visibility and input handling.

Keep the plugin ID `synology-photos`, renderer key `album`, and command ID `refresh` stable after publication. The saved screensaver mode is `plugin:synology-photos:album`.

## Verification

`python3 tools/test.py` starts a fake NAS on an ephemeral loopback port. It checks URL validation, form encoding (including non-ASCII passwords), sharing cookies and headers, mixed photo/video pagination, oversized thumbnail fallback, two-image renderer budgets, photo ordering, effect configuration, setup status, idle/screen-off behavior, retaining the current photo on failure, reconnection, and stale-response cancellation. The upstream build-tool checks also run. CI runs these tests for pushes and pull requests.

Generate synthetic effect previews without using private photos:

```sh
python3 tools/test.py --preview
python3 -m http.server 8765 --bind 127.0.0.1 --directory .cache/preview
```

Open `http://localhost:8765/fade.html`, `slide.html`, or `none.html` to inspect each transition combined with Ken Burns. The preview fixtures are generated with JDK graphics and stay outside the package.

For SDK compatibility with a local application checkout:

```sh
python3 tools/check-sdk.py /path/to/kiosk-satellite
```

For an optional live test, create an ignored private file such as `.cache/live-config.json` containing `albumUrl` and `albumPassword` (both strings). Run:

```sh
python3 tools/test.py --live-config .cache/live-config.json
```

This checks login, listing and the first thumbnail using the JVM test codec. To check every photo with the actual Android bitmap APIs, start a developer emulator, get its serial from `adb devices`, and run:

```sh
python3 tools/test-android.py .cache/live-config.json --serial emulator-5554
```

The Android test builds a temporary DEX harness, runs it using the emulator's shell `app_process`, and removes its private files from the emulator afterward. It saves a private first-photo preview under ignored `.cache/preview/android.html`. This does not install or test the KS app. Delete the private config and previews after testing; they must not be published.

Verification performed on 2026-10-01:

| Check | Result |
| --- | --- |
| SDK compatibility | Interfaces and license match KS commit `15dd10e971efa0ada0e206b813aaa687ce19a91d`. SDK/tooling adapted from template commit `8a070aca0815346fc2e68b29ed52e07c240d480e`. |
| Fake-NAS protocol and lifecycle tests | Passed, including more than 100 mixed items, password encoding, size fallback, screen-off and cancellation. |
| Real password-protected album | Login and listing passed; all 18 photos, including Live Photos, fetched using the production client. DSM and Photos package version numbers were not supplied. |
| Android native codec | Passed on an Android API 36.1 arm64 emulator; all 18 processed images stayed within the HTML budget. |
| Browser renderer | Embedded image decoded at 1707 × 1280; landscape and portrait layouts fit their viewport without overflow. |
| KS app installation and physical kiosk | Not yet tested. |

Before claiming full device compatibility, complete this checklist on a real NAS and kiosk:

- [ ] Install the release through **Add plugin** and confirm the release preview.
- [ ] Connect an unprotected shared album and confirm its first photo displays.
- [ ] Connect a password-protected shared album, including a password containing punctuation.
- [ ] Try an album from Personal Space and an album containing Shared Space photos.
- [ ] Verify an album with more than 100 items, mixed photos/videos, portrait photos and HEIC photos with generated thumbnails.
- [ ] Check fit/fill, shuffled rotation, scheduled activation, stock overlays, touch dismissal, screen-off and wake.
- [ ] Add/remove a photo and verify a refresh updates the list.
- [ ] Disconnect/reconnect the NAS, revoke the link, and check status and recovery.
- [ ] Disable/re-enable and update the plugin while a request is active.

Record DSM, Synology Photos, KS and Android versions with the result. Do not commit live sharing links, credentials or private images. The initial release's live checks cover the NAS protocol and Android codec; its full kiosk checklist remains open.

## Install a local build

On the kiosk or Remote Admin, open **Plugin Manager → Developer Tools → Install from ZIP**, choose the ZIP in `dist/`, review and trust it, then enable it. A local ZIP cannot replace a GitHub-installed copy; uninstall that copy first, which removes its settings. Local-to-local updates preserve compatible settings and restore the plugin's enabled state.

## Publish on GitHub

KS requires release assets uploaded by `github-actions[bot]`, with GitHub's asset digest matching the accompanying checksum. Do not manually upload local build assets or use a personal token for the asset upload.

1. Commit the source, manifest, README and workflow to a **public** repository.
2. Run tests and build locally. Push the commit.
3. Create and push a version tag, then publish a stable release:

   ```sh
   git tag v0.1.0
   git push origin v0.1.0
   gh release create v0.1.0 --title 'Synology Photos 0.1.0' --notes-file /path/to/release-notes.md
   ```

4. Wait for the **Release** workflow to pass. It checks out the tag, tests, builds with Android platform 35, takes the version from the tag, and uses `github.token` to attach:

   ```text
   kiosk-satellite-plugin.json
   synology-photos-0.1.0.zip
   synology-photos-0.1.0.zip.sha256
   ```

5. Confirm all three assets appear before sharing the repository URL. KS excludes draft and prerelease releases from normal installation.

To retry a failed build, run the Release workflow manually on the existing release tag. Publish a new version when changing package contents. Tagged documentation is what users see during installation; edits on the main branch do not change an existing release's preview.

Local builds may override the package version without editing the source manifest:

```sh
python3 tools/build.py --version 0.2.0
```
