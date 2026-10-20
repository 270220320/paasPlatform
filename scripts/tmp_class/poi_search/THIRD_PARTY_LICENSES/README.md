Flight Hub SDK — Third-Party Notices
=====================================

This SDK includes the following third-party open source components, redistributed
as native binary shared libraries under jar://lib/{linux-x86_64,macosx-arm64}/.
Each component's full license text is in the correspondingly named file next to
this NOTICE.

Component               Version    License          License file
---------               -------    -------          ------------
OpenSSL                 1.1.1n     Apache 2.0*      OpenSSL-LICENSE
                        (Linux)                     (see file for OpenSSL/SSLeay
                                                    dual-license notice used up to 1.1.1)
OpenSSL                 3.x        Apache 2.0       OpenSSL-LICENSE
                        (macOS,                     (macOS bundle uses OpenSSL 3.x
                        随包分发)                    which is Apache 2.0 only)
libwebsockets           v4.x       MIT (+ retained  libwebsockets-LICENSE
                        (v12 SONAME) BSD/Zlib bits;
                                    see LICENSE)
paho-mqtt-c             1.3.14     EPL 2.0 or       paho-mqtt-c-LICENSE
                        (async SSL) EDL 1.0 (dual)

Additional notes
----------------
- OpenSSL 1.1.1 (Linux side) was originally distributed under the dual OpenSSL
  License / SSLeay License. The full text of that dual license is preserved in
  OpenSSL-LICENSE for compliance. OpenSSL 3.x (macOS bundle) is Apache 2.0 only.
- libwebsockets (warmcat) modern releases are MIT-licensed (with a few
  retained BSD/Zlib/etc. fragments documented in libwebsockets-LICENSE).
  Older historical LGPL builds are not what this SDK ships. Source:
  https://libwebsockets.org/ / https://github.com/warmcat/libwebsockets
- paho-mqtt-c is dual-licensed under EPL 2.0 and EDL 1.0. See
  paho-mqtt-c-LICENSE for the upstream notice plus both full texts; you may
  pick either.
- Volc RTC (VolcEngineRTC / ByteRTC series) and Agora RTC — **removed from
  this build** (C-01, ES320 v3.6 second-round audit). FHSDK realtime paths
  converged to WebRTC and DRTC. Related native libraries (`libVolcEngineRTC`,
  `libbytertc_*`, `libbytenn`, `libagora_rtc_sdk`, `libagora-fdkaac`,
  `libaosl`, and the transitively bundled `libRTCFFmpeg`) are no longer
  packaged. The `LGPL-2.1-LICENSE` file may still be present for future
  restoration reference and does not currently apply to any shipped binary.

DJI Proprietary Native Components
----------------------------------

The following native libraries are DJI proprietary components, redistributed
as part of this SDK per DJI's own terms (not subject to third-party licensing):

- `libdrtc_device_sdk` (Linux) — DJI DRTC device SDK
- `libdrpc` (Linux) — DJI DRTC RPC

Redistribution of these components requires no third-party consent; DJI
retains all rights.

Statically linked / code-embedded components
--------------------------------------------

The following components are statically linked or code-embedded inside the JNI
library and their licenses are provided here for full transparency, even though
they do not produce separate .so/.dylib files:

- AIGCJson (header-only JSON mapper) — MIT-style, header library
- h264_analyze — see h264_analyze upstream LICENSE
- plog (header-only logger) — **MIT License** (Copyright (c) Sergey Podobry;
  see plog-LICENSE for full text). Note: some older audit references list
  plog as MPL 2.0, which is out-of-date — the upstream project has been
  MIT-licensed for several releases. MIT imposes no copyleft or source-
  disclosure obligation; retaining the copyright notice is sufficient.
- aws-cpp-sdk-s3 — Apache 2.0
- libcurl — MIT/X derivative

For any question regarding the third-party redistribution please contact the
Flight Hub SDK team.
