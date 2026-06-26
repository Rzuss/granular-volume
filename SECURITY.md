# Security Policy

## Reporting a vulnerability

If you find a security issue in Granular Volume, please report it privately rather than opening a public issue. Use the GitHub "Report a vulnerability" feature on this repository, or email the maintainer. You can expect an acknowledgement within a few days.

## Scope and data handling

Granular Volume collects no personal data, makes no network requests, and contains no analytics or advertising SDKs. It operates entirely on device. The only sensitive surfaces are the system permissions it requests, which are documented in the README.

## Signing keys

The application signing keystore is intentionally excluded from this repository and must never be committed. Release artifacts are signed locally and through Google Play App Signing.
