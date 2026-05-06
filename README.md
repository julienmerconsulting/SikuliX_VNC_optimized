<div align="center">

<img src="IDE/src/main/resources/icons/gecko_cyclope_hero.png" width="300" alt="OculiX"/>

# OculiX

**Visual automation for the real world.**

[![Java](https://img.shields.io/badge/Java-11%2B-orange?style=flat-square&logo=openjdk)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)](LICENSE)
[![Fork of](https://img.shields.io/badge/Fork%20of-SikuliX1%20%28archived%29-lightgrey?style=flat-square)](https://github.com/RaiMan/SikuliX1)
[![Maintained](https://img.shields.io/badge/Maintained-Yes-brightgreen?style=flat-square)](https://github.com/julienmerconsulting/OculiX)
[![Changes](https://img.shields.io/badge/Changes%20vs%20SikuliX-123%2C728%20insertions-blue?style=flat-square)]()
[![Version](https://img.shields.io/badge/Version-3.0.2-blue?style=flat-square)]()

<br/>

*From the Latin **oculi** — eyes.*  
*Because if you can see it, you can automate it.*

<br/>

**Desktops &nbsp;·&nbsp; POS terminals &nbsp;·&nbsp; Kiosks &nbsp;·&nbsp; SCO machines &nbsp;·&nbsp; Android devices &nbsp;·&nbsp; VNC remote systems &nbsp;·&nbsp; Legacy apps**

<br/>

> **SikuliX1 was archived on March 3, 2026. OculiX is its active continuation.**  
> 511 files changed. 123,728 insertions. Built for production — not for demos.

</div>

---
## ⚔️ Why OculiX beats everything else

<img src="https://community.appinventor.mit.edu/uploads/default/original/3X/2/a/2ad031bc25a55c4d3f55ff5ead8b2de63cdf28bf.png" width="20"/> **Google Vision API?**  
Tells you what's on screen. Can't click it.

<img src="https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTBdcgkdcDy5z4PYGx_kDJB1AvvC_x1pCBbbQ&s" width="20"/>  **GPT-4o vision?**  
Describes your UI beautifully. Can't touch it.

<img src="https://avatars.githubusercontent.com/u/192012301?s=200&v=4" width="20"/>  **Browser Use / Claude Computer Use?**  
Browser only. Needs a DOM. Fails on native apps.

<img src="https://www.logiciels.pro/wp-content/uploads/2025/03/appium-avis-prix-fonctionnalites-alternatives.webp" width="20"/> **Appium?**  
Requires accessibility, XPath, a server… and a PhD.

<img src="https://raw.githubusercontent.com/RaiMan/SikuliX1/master/Support/sikulix-red.png" width="50"/> **SikuliX?**  
Archived. Dead. March 3, 2026.

---

👁️⚡ **OculiX:**
- Sees the screen. Clicks it. Types. Swipes. Scrolls.
- Desktop. Android. VNC remote. Kiosk. POS. Legacy 1998 apps.
- No API. No DOM. No selectors. No cloud. No bullshit.

**If a human can see it — OculiX can automate it.**

💰 **Price: €0. Forever.**


## ⚡ What OculiX adds over SikuliX

<table>
<tr>
<td width="50%">

### 🖥️ Full VNC stack
Connect to any remote machine — no local display required.  
`VNCScreen`, `VNCRobot`, `VNCClient`, `VNCFrameBuffer`, `VNCClipboard`.  
Complete keyboard mapping via `XKeySym` (2200+ key definitions).  
Thread-safe parallel sessions via `ThreadLocalSecurityClient`.

</td>
<td width="50%">

### 🔐 Native SSH — zero external deps
`jcraft/jsch` is embedded directly in the JAR.  
Open SSH tunnels from Java. No WSL. No shell wrapper. No sshpass.  
`SSHTunnel` available as a standalone utility in `com.sikulix.util`.

</td>
</tr>
<tr>
<td width="50%">

### 📱 Android automation via ADB
Full ADB integration: `ADBClient`, `ADBDevice`, `ADBRobot`, `ADBScreen`.  
Control Android devices with the same API as desktop screens.  
WiFi or USB. No Appium. No XPath. No accessibility required.  
Powered by `jadb` — embedded, no external tools required.  
**Validated on Android 12+ (Samsung, 1080x2400).**

</td>
<td width="50%">

### 🧠 PaddleOCR integration
Find and click on any text visible on screen — desktop or Android.  
`PaddleOCREngine` + `PaddleOCRClient` — full JSON parsing, bbox extraction, confidence scoring.  
Works on any app, any language, any resolution.  
No DOM. No selectors. Just text.

</td>
</tr>
<tr>
<td width="50%">

### 🏃 Multi-language script runners
Run automation scripts in: **Jython, JRuby, Python, PowerShell, AppleScript, Robot Framework**.  
Network runner for distributed execution.  
Server runner for headless CI/CD integration.

</td>
<td width="50%">

### 🔧 Native OS libraries — bundled
**Windows:** `WinUtil.dll` + `WinUtil.cc` — native Windows interaction, precompiled and bundled.  
**macOS:** `MacUtil.m` — native macOS support.  
**Linux:** `LinuxSupport.java` — 421 lines of Linux-specific handling.  
No manual native lib setup on any platform.

</td>
</tr>
<tr>
<td width="50%">

### 👁️ Apertix — OpenCV 4.10.0 custom build
OculiX depends on [Apertix](https://github.com/julienmerconsulting/Apertix), a custom fork of `openpnp/opencv`.  
Full JNA migration — no `System.loadLibrary` conflicts.  
OpenCV 4.10.0 compiled from scratch on Windows x86-64 MSVC.  
Drop-in replacement for `org.openpnp:opencv`.

</td>
<td width="50%">

### 🏗️ Production build system
Platform-specific `makeapi` scripts for Windows, macOS, Linux.  
TigerVNC deployed as a proper Maven dependency.  
`maven-deploy-API` for reproducible CI builds.  
IntelliJ run configurations included out of the box.

</td>
</tr>
</table>

---

## 🚀 Getting started

**Prerequisite: Java 11+**  
→ [Eclipse Temurin](https://adoptium.net) &nbsp;|&nbsp; [Azul Zulu](https://www.azul.com/downloads/?package=jdk#download-openjdk)

**Build from source:**

```bash
git clone https://github.com/julienmerconsulting/OculiX.git
cd OculiX
mvn clean install -DskipTests
```

**Maven (local install):**

```xml
<dependency>
    <groupId>io.github.oculix-org</groupId>
    <artifactId>oculixapi</artifactId>
    <version>3.0.2</version>
</dependency>
```

**Required dependency — Apertix (OpenCV 4.10.0):**

```xml
<dependency>
  <groupId>io.github.julienmerconsulting.apertix</groupId>
  <artifactId>opencv</artifactId>
  <version>4.10.0-0</version>
</dependency>
```

→ [Apertix releases](https://github.com/julienmerconsulting/Apertix/releases)

---

## 🖥️ VNC — automate remote machines

```java
// Connexion directe à une machine distante via VNC
VNCScreen vnc = VNCScreen.start("192.168.1.10", 5900, "", 1920, 1080);
vnc.click("validate_button.png");
vnc.type("1234");
vnc.waitVanish("loading_spinner.png", 10);
vnc.stop();
```

---

## 🔐 SSH tunnel — from Java, no shell

```java
// Tunnel SSH natif — jcraft/jsch embarqué
SSHTunnel tunnel = new SSHTunnel("user", "remote-host", 22, "password");
tunnel.open(5900, "localhost", 5900);

// Puis connexion VNC sur le port local tunnelé
VNCScreen vnc = VNCScreen.start("localhost", 5900, "", 1920, 1080);
```

---

## 📱 Android — ADB WiFi, no Appium

```java
// Connexion ADB WiFi
ADBScreen android = ADBScreen.start("/path/to/adb");
System.out.println(android.getW() + "x" + android.getH()); // ex: 1080x2400

// Capture écran
ScreenImage img = android.capture();

// Clic sur image
android.click(new Pattern("accept_button.png").similar(0.7f));

// Clic sur texte via OCR PaddleOCR
PaddleOCREngine ocr = new PaddleOCREngine();
String json = ocr.recognize("/tmp/screen.png");
int[] coords = ocr.findTextCoordinates(json, "Validate");
if (coords != null) {
    int cx = coords[0] + coords[2] / 2;
    int cy = coords[1] + coords[3] / 2;
    android.getDevice().tap(cx, cy);
}
```

> Works on any Android app — no accessibility API, no XPath, no DOM.  
> Validated on Android 12+ via WiFi (ADB pairing).

---

## 🧠 PaddleOCR — find text anywhere

```java
PaddleOCREngine ocr = new PaddleOCREngine(); // localhost:5000 par défaut
String json = ocr.recognize("/path/to/screenshot.png");

// Trouver un texte et obtenir ses coordonnées
int[] coords = ocr.findTextCoordinates(json, "Submit");
// coords = {x, y, width, height}

// Tous les textes détectés avec leur confiance
Map<String, Double> results = ocr.parseTextWithConfidence(json);
// {"Submit" -> 0.9997, "Cancel" -> 0.9981, ...}
```

---

## 🏃 Script runners — run anything

```java
// Jython
Runner.run("myscript.py", new String[]{});

// PowerShell
Runner.run("myscript.ps1", new String[]{});

// Robot Framework
Runner.run("test.robot", new String[]{});
```

---

## 📦 What's inside

| Component | Status | Details |
|---|---|---|
| Core API — Screen, Region, Pattern, Match | ✅ Active | Extended from SikuliX 2.0.5 |
| VNCScreen + VNCRobot + VNCClient | ✅ Active | 1000+ lines reworked, production-tested |
| VNCFrameBuffer + VNCClipboard + XKeySym | ✅ Active | Full remote control stack |
| SSH tunnel — jcraft/jsch | ✅ Embedded | No external SSH dependency |
| Android ADB — ADBScreen + jadb | ✅ Active | Validated Android 12+ WiFi |
| PaddleOCR — text detection + click | ✅ Active | Full JSON parsing, bbox, confidence |
| Apertix — OpenCV 4.10.0 JNA | ✅ Active | Custom build, no loadLibrary conflict |
| Script runners — Jython, JRuby, Python, PS, Robot | ✅ Active | Multi-language automation |
| Native libs — Windows DLL + macOS + Linux | ✅ Bundled | No manual setup |
| TigerVNC Maven dependency | ✅ Active | Reproducible builds |
| SikuliX IDE | 🔧 Maintained | No new features planned |
| Operix — Python wrapper | 🔬 Coming soon | See roadmap |

---

## 🗺️ Roadmap

- [ ] **Fat JAR** — one file, no manual classpath management
- [ ] **Maven Central** publication
- [ ] **Operix** — Python wrapper for OculiX via py4j  
  *Write your automation scripts in Python, powered by OculiX under the hood.*  
  *Same API. No Java knowledge required.*
- [ ] **Linux / macOS Apertix binaries** — cross-platform OpenCV builds
- [ ] **OculiX simplified API** — single entry point, zero internal knowledge required
- [ ] **Appium driver** — register OculiX as a Selenium Grid node

---

## 🤝 Contributing

Issues, bug reports, pull requests — all welcome.

OculiX targets production QA engineers and automation specialists working on environments without API access — desktops, kiosks, POS terminals, legacy software, Android devices.

If you were using SikuliX and looking for a continuation — **you found it.**

- **Bug reports / feature requests** → [Issues](../../issues)
- **Bug fixes** → Pull request against `master`
- **Major changes** → Open an issue first

---

## 🌐 Ecosystem

```
Apertix      → OpenCV 4.10.0 custom build (fork openpnp/opencv)
    ↓
OculiX       → visual automation engine (fork SikuliX1) — v3.0.2
    ↓
Operix       → Python wrapper via py4j (coming soon)
```

| Project | Repo | Status |
|---|---|---|
| Apertix | [julienmerconsulting/Apertix](https://github.com/julienmerconsulting/Apertix) | ✅ v4.10.0-0 |
| OculiX | [julienmerconsulting/OculiX](https://github.com/julienmerconsulting/OculiX) | ✅ v3.0.2 |
| Operix | coming soon | 🔬 In design |

---

## 👤 Maintainer

<table>
<tr>
<td width="60">
<img src="https://avatars.githubusercontent.com/julienmerconsulting" width="56" style="border-radius:50%"/>
</td>
<td>

**Julien MER** — QA Architect · 20+ years in defense, biotech, aerospace, retail  
Katalon Top Partner Europe &nbsp;|&nbsp; JMer Consulting  
Newsletter [Bonnes Pratiques QA](https://www.linkedin.com/newsletters/bonnes-pratiques-qa-6878703775620636672) — 3574 abonnés  
[Clean QA Academy](https://qa-julienmer-course.pages.dev/) — 100% free QA training

</td>
</tr>
</table>

---

## 📄 License

MIT — same as the original SikuliX project.

Original project: [RaiMan/SikuliX1](https://github.com/RaiMan/SikuliX1) (archived March 2026)  
Original author: Raimund Hocke — all credits for the foundational work.
