# Contributing

Issues and pull requests are welcome. Please read the [development guide](docs/development.md) and run `python3 tools/test.py` before submitting a change. Changes to runtime or packaging code should also pass `python3 tools/build.py`.

For a bug report, describe the expected behavior, what happened and the plugin status. Include DSM, Synology Photos, Kiosk Satellite and Android versions. Redact sharing tokens, passwords, personal hostnames and images. A sanitized API response is helpful for compatibility issues; never post session cookies.

Keep the setup short, preserve the stable plugin and renderer IDs, and avoid new runtime dependencies where the Android or Java APIs cover the need. Add a focused test when changing protocol behavior or lifecycle logic.

Contributions are licensed under the project's Apache-2.0 license. Third-party source must include its required license and attribution. Synology Photos' undocumented API may need version-specific investigation; do not claim compatibility solely from the fake-NAS tests.
