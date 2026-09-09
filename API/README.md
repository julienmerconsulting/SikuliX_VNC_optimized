# OculiX API

The Java library behind OculiX: the top-level elements — `Screen`, `Region`,
`Pattern`, `Match`, `Image`, `Location` — and the methods that search for
images on screen and act on what they find with mouse and keyboard.

Use it as a Maven dependency in any JVM project. It is a library, not an
executable: scripting lives in the IDE module.

```xml
<dependency>
    <groupId>io.github.oculix-org</groupId>
    <artifactId>oculixapi</artifactId>
    <version>4.0.0</version>
</dependency>
```

**Java 17 or later, 64-bit.** Every module of OculiX compiles with
`<release>17</release>`.

```java
import org.sikuli.script.*;

Screen s = new Screen();
s.click("file_menu.png");
s.wait("save_dialog.png", 10);
String text = s.text();
```

The `org.sikuli.*` package namespace is kept on purpose: OculiX is the
continuation of SikuliX, and existing code compiles unchanged.

## Natives

Nothing to install by hand. The API resolves its natives as ordinary Maven
dependencies and extracts them to a per-user cache on first use:

| Library | Provider | Version |
| --- | --- | --- |
| OpenCV | [Apertix](https://github.com/oculix-org/Apertix) | 4.10.0-3 |
| Tesseract + Leptonica + 5 language models | [Legerix](https://github.com/oculix-org/Legerix) | 5.5.0-8 |

Windows, macOS (Intel and Apple Silicon) and Linux (x86_64 and aarch64, two
glibc tiers) are covered. PaddleOCR is available as an opt-in HTTP service for
multilingual and CJK workloads.

## Beyond the local screen

- **VNC** — `VNCScreen` is a `Screen`: the same `find`, `click`, `text` and
  `wait` on a remote machine, with no local display. Thread-safe parallel
  sessions, full X keysym mapping.
- **SSH tunnels** — `SSHTunnel`, with embedded JSch: open a tunnel from Java
  alone, no shell wrapper and no WSL.
- **Android** — `ADBScreen` and `ADBDevice` over USB or Wi-Fi, with a
  persistent adb shell for input. No Appium, no XPath, no accessibility API.

## Documentation

Full API guides at [oculix.org](https://oculix.org). Javadoc is published with
each release on [Maven Central](https://central.sonatype.com/namespace/io.github.oculix-org).
