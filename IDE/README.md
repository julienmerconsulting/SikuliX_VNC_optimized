# OculiX IDE

The graphical environment for writing and running OculiX scripts: capture a
region of the screen, drop the image straight into the code, run it, watch it
work. Images appear inline in the editor as thumbnails rather than as file
names, which is what makes a visual script readable.

It also runs scripts from the command line.

**Java 17 or later, 64-bit.** Use [Eclipse Temurin](https://adoptium.net) or
[Azul Zulu](https://www.azul.com/downloads/).

## Running it

The jars are platform-specific, since each one carries the natives of its own
platform:

| Platform | Jar |
| --- | --- |
| Windows | `oculixide-4.0.0-complete-win.jar` |
| macOS | `oculixide-4.0.0-complete-mac.jar` |
| Linux | `oculixide-4.0.0-complete-lux.jar` |

```bash
java -jar oculixide-4.0.0-complete-win.jar
```

Double-clicking works too. Build one yourself with the matching profile:

```bash
mvn -pl IDE -am package -DskipTests -Pcomplete-win-jar
```

## What is inside

- **Jython 2.7.4**, bundled: Python 2.7 syntax with full JVM interop, and
  drop-in compatibility with existing SikuliX scripts.
- **JRuby 9.4.14.0**, bundled. The 9.4 line is pinned on purpose: JRuby 10
  requires Java 21, and OculiX targets 17.
- **Other runners**: Robot Framework, PowerShell, AppleScript, plain text,
  and `.skl` / `.zip` script bundles.
- **OpenCV 4.10**, via [Apertix](https://github.com/oculix-org/Apertix), for
  template matching.
- **Tesseract 5.5 and Leptonica 1.87**, with five `tessdata_fast` language
  models, via [Legerix](https://github.com/oculix-org/Legerix). Nothing to
  install: no `apt install tesseract-ocr`, no `brew install tesseract`.
  PaddleOCR can be plugged in as an opt-in HTTP service for CJK and
  multilingual work.

## Around the editor

- **Sidebar** with File, Edit, Run, View and Tools, the open scripts, the
  status of the OCR engines and the last run.
- **Image thumbnails** in the code, with a click for rename, optimize and
  pattern promotion, and a similarity badge on patterns.
- **Preferences** with a Hotkeys tab: the quick-capture and the stop/abort
  hotkeys are both rebindable, each with its own enable switch, and a
  validator refuses a binding that cannot work.
- **Themes**, dark and light, and the interface translated into 23 locales.

Defaults: `Alt+Shift+2` captures, `Alt+Shift+C` aborts a running script. Both
are global, so they work while a script has the focus.

## Remote and mobile

The IDE drives what the API drives: a VNC target through `VNCScreen`, with SSH
tunnelling from Java alone, and an Android device through `ADBScreen` over USB
or Wi-Fi. Neither is suspended, both are supported.

Full documentation at [oculix.org](https://oculix.org).
