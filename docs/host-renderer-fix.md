# Black gap before photo transitions

The released plugin's transition works inside its HTML document. Kiosk Satellite destroys that document's native WebView on every `publishScreensaver` update because `_Document` is keyed by the entire renderer map. The replacement WebView starts on black, decodes the previous photo, and then runs the fade to the next photo. This explains the visible sequence of black → previous photo → next photo.

The SDK exposes no method to update a running document. Changing the plugin's CSS, using an asset renderer, or disabling transitions cannot prevent the WebView replacement. This needs an application change; the patch below is **not a plugin update**.

## Proposed Kiosk Satellite patch

The fix is also committed in [our Kiosk Satellite fork](https://github.com/petergerdes/kiosk-satellite/commit/716329b951488f2a7afa746ce52c71a676ba23ce), on branch `fix/plugin-screensaver-black-flash`. On October 1, 2026, GitHub rejected upstream PR creation: [Kiosk Satellite restricts new pull requests](https://github.com/jxlarrea/kiosk-satellite/pulls), and the repository API reports `has_pull_requests: false`. No upstream PR was created. We submitted [issue #782](https://github.com/jxlarrea/kiosk-satellite/issues/782) instead, explaining the bug and linking the proposed fix for the maintainer to review.

[Download the patch](patches/kiosk-satellite-retain-inline-webview.patch). It applies to Kiosk Satellite commit `15dd10e971efa0ada0e206b813aaa687ce19a91d`.

The patch retains the native WebView for inline HTML updates. Its outer document creates a hidden replacement iframe and retains the displayed iframe until the new one has loaded and reached two animation frames. It then reveals the replacement and retires the old frame. Rapid updates discard superseded frames. No JavaScript bridge or network access is added, and the plugin iframe retains its `allow-scripts` sandbox without same-origin access. Asset entry/origin/options changes still recreate the document. A new inline publication can recreate a crashed renderer.

The existing plugin can use this behavior without SDK or setting changes. Initial screensaver activation still needs to load a WebView.

Apply in a Kiosk Satellite checkout, using the absolute path to the downloaded patch:

```sh
git apply --check /path/to/kiosk-satellite-retain-inline-webview.patch
git apply /path/to/kiosk-satellite-retain-inline-webview.patch
cd app
flutter analyze lib/ui/plugin_screensaver.dart
flutter test test/plugin_screensaver_test.dart
```

This patch requires a rebuilt Kiosk Satellite application or an upstream release incorporating it. Installing another Synology Photos plugin ZIP does not apply it.

## Browser regression check

From this plugin repository, with Dart installed and the patch applied to a Kiosk Satellite checkout:

```sh
python3 tools/test-host-renderer.py /path/to/kiosk-satellite
python3 -m http.server 8765 --bind 127.0.0.1 --directory .cache/host-renderer-check
```

Open `http://localhost:8765/`. The check generates the wrapper from the actual patched Dart document builder. It verifies that the current frame survives loading, the replacement waits for paint, stale updates cannot replace the latest frame, retired frames are removed, and the plugin cannot read the host document. Fixtures use synthetic content and stay outside the plugin package.

These browser checks passed, including the plugin's actual synthetic fade HTML with both images decoded and the fade at its midpoint. The patch also passes Dart formatting and applies cleanly to the referenced upstream commit. Flutter analysis, widget tests, and a full Kiosk Satellite device test have **not** been run: Flutter is not installed in the development environment used for this patch. An upstream build and device verification are required before calling the application fix released.
