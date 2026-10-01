# Security

Do not post live sharing links, passwords, cookies or private photos in public issues. If one has been exposed, revoke the album link in Synology Photos and create a new one.

Report sensitive vulnerabilities through the repository's GitHub **Security → Report a vulnerability** flow when available. For other reports, open an issue containing only a general description; arrange private reproduction details before sharing them.

This initial release uses standard HTTPS verification, bounded downloads and no redirect following. It does not bypass certificate errors. KS SDK 1 persists the sharing link and optional password as ordinary settings, with no plugin-level secure credential store. Protect KS Remote Admin and settings exports.
