# AppleJr DNS Android (unofficial)

This is an Android implementation of the DNS-over-HTTPS part of the AppleJr iOS mobileconfig supplied by the user.

Default DoH endpoint:
https://novadns.novadev.vip/dns-query

Important:
- Android does not support iOS mobileconfig, IPA/ESign, or Apple's enterprise-certificate revocation model.
- This app does not reproduce iOS certificate anti-revoke behavior. It only reproduces DNS routing behavior.
- The app uses Android VpnService locally and routes DNS traffic only.
- Only one Android VPN service can be active at a time.
- The selected DNS resolver can observe DNS queries sent to it.
- TLS certificate verification is not disabled. If the DoH server certificate is invalid, the connection fails.

Modes:
1. Global: all Android system DNS queries are sent to the NovaDNS DoH endpoint.
2. AppleJr-only: only the domains present in the original AppleJr profile are sent to NovaDNS. Other queries use the current Wi-Fi/mobile DNS, with 1.1.1.1 only as a fallback if the network DNS cannot be determined.

Protocol scope:
- IPv4 UDP DNS captured through the local VPN.
- DoH POST with application/dns-message (RFC 8484 style).
- TCP DNS and IPv6 DNS inside the TUN loop are not implemented in v1.0.0.
- Apps that use their own encrypted DNS may bypass the Android system resolver.

Build:
- JDK 17
- Android SDK 35
- Gradle 8.9
- Android Gradle Plugin 8.7.3

Run:
gradle testDebugUnitTest assembleDebug

The debug APK is generated at:
app/build/outputs/apk/debug/app-debug.apk

The architecture was independently implemented with Android VpnService. AeroDNS (Apache-2.0) was reviewed as an open-source reference for the general DNS-only VPN pattern:
https://github.com/vmcsoft/aerodns
