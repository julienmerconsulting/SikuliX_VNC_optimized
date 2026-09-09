<div align="center">

<img src="IDE/src/main/resources/icons/gecko_cyclope_hero.png" width="240" alt="OculiX"/>

# OculiX

**The open-source reference for visual automation.**
*Twenty years of lineage. MIT-licensed. Trusted in production by 149 organizations worldwide.*

[![Java](https://img.shields.io/badge/Java-17%2B-orange?style=flat-square&logo=openjdk)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)](LICENSE)
[![OpenSSF Best Practices](https://www.bestpractices.dev/projects/12878/badge)](https://www.bestpractices.dev/projects/12878)
[![Version](https://img.shields.io/badge/Version-4.0.0-blue?style=flat-square)](https://github.com/oculix-org/Oculix/releases)
[![Docs](https://img.shields.io/badge/Docs-oculix.org-blueviolet?style=flat-square)](https://oculix.org)
[![Maven Central](https://img.shields.io/badge/Maven%20Central-io.github.oculix--org-blue?style=flat-square&logo=apachemaven)](https://central.sonatype.com/namespace/io.github.oculix-org)

[**Get started**](https://oculix.org/getting-started/installation/) · [**Documentation**](https://oculix.org) · [**Enterprise**](https://oculix.org/support/enterprise/)

</div>

---

## Trusted in production

| 149 | 14k+ | 3.4k | MIT |
|---|---|---|---|
| enterprise organizations | clones every two weeks | Maven downloads / quarter | open source, forever |

---

## What OculiX is

OculiX drives any graphical interface by **what it looks like** — not by accessibility hooks, DOM selectors, or fragile XPaths. You point at what you see on screen, OculiX does the rest.

It works where selector-based tools cannot: native desktop applications, virtualized desktops (Citrix, RDP, VNC), Canvas/WebGL-rendered UIs, multi-application workflows, and any environment where what reaches the user is, ultimately, pixels.

You don't need to be a developer to use it. If you can take a screenshot, you can write your first OculiX script. If you *are* a developer, you get a full JVM scripting environment on top.

---

## A first script

```python
# Open the app, export today's report, save it — every morning at 8 AM.
from sikuli import *

click("file_menu.png")
click("export_to_csv.png")
wait("save_dialog.png", 10)
click("filename_field.png")
type("report_today.csv")
click("save_button.png")
```

Full installation, first-script tutorial, and tour of the IDE at [**oculix.org/getting-started**](https://oculix.org/getting-started/installation/).

---

## What it brings

<table>
<tr>
<td width="50%">

**Pixel-perfect matching**
OpenCV-based template matching with DPI awareness, similarity tuning, sub-pixel reliability. Five matching strategies cascaded before FindFailed.

</td>
<td width="50%">

**OCR out of the box**
Tesseract embedded via Legerix, no manual install. PaddleOCR available as opt-in HTTP server for multilingual and CJK workloads.

</td>
</tr>
<tr>
<td width="50%">

**Cross-platform**
Windows, macOS, Linux. One script runs everywhere — no platform-specific selectors. Native libraries pre-built and bundled.

</td>
<td width="50%">

**Jython scripting**
Python 2.7 syntax with full JVM interop. Drop-in compatible with legacy SikuliX scripts. JRuby, Robot Framework, and PowerShell runners also supported.

</td>
</tr>
<tr>
<td width="50%">

**Full VNC stack**
Connect to any remote machine without a local display. `VNCScreen`, `VNCRobot`, complete X keysym mapping, thread-safe parallel sessions.

</td>
<td width="50%">

**Native SSH tunneling**
`SSHTunnel` utility with embedded JSch. Open tunnels from Java alone — no shell wrapper, no WSL.

</td>
</tr>
<tr>
<td width="50%">

**Android via ADB**
Full ADB integration over WiFi or USB. No Appium, no XPath, no accessibility API. Validated on Android 12+.

</td>
<td width="50%">

**Auditable execution**
Optional MCP module: Ed25519-signed, SHA-256-chained JSONL audit journal. Every action independently verifiable.

</td>
</tr>
</table>

---

## The modules

| Module | Artifact | What it is |
|---|---|---|
| **API** | `oculixapi` | The library: `Screen`, `Region`, `Pattern`, `Match`, and everything that finds and acts. Also VNC, SSH tunnelling and Android. |
| **IDE** | `oculixide` | The editor: capture, inline image thumbnails, run, and the script runners. |
| **Reporter** | `oculixreporter` | Opt-in single-file HTML reports with embedded screenshots, failure diagnosis and flaky detection. |
| **MCP** | `oculix-mcp-server` | The same tools over Model Context Protocol, with a signed append-only audit journal. |

Natives come from two sibling projects: [Apertix](https://github.com/oculix-org/Apertix) for OpenCV, [Legerix](https://github.com/oculix-org/Legerix) for Tesseract, Leptonica and the bundled language models.

---

## How it compares

OculiX sits in the **process automation** space alongside RPA and visual automation tools.

| Capability | OculiX | UiPath | Eggplant |
|---|---|---|---|
| License & cost | **MIT — free forever** | Commercial — per bot / per user | Commercial — enterprise license |
| Self-hosted, fully local | **100% local, no cloud needed** | Hybrid (Studio local, Orchestrator cloud) | On-prem available |
| Cross-platform | **Windows · macOS · Linux** | Studio is Windows-only | Windows · macOS · Linux |
| Approach | Pure visual — sees the screen | UI selectors (UIA/HTML) + visual fallback | Visual + IDE |
| Embedded OCR | **Tesseract bundled** | Multiple providers via marketplace | Built-in |
| Scripting language | Jython (Python 2.7 + JVM) | VB.NET · C# · Python (limited) | SenseTalk |
| Best fit | Anyone — non-tech users to enterprise teams | Enterprise RPA at scale | Visual test for regulated industries |

*Comparison based on each vendor's public documentation as of 2026. OculiX is independent and unaffiliated with UiPath (UiPath, Inc.) or Eggplant (Keysight Technologies).*

---

## Quick install

**Prerequisite:** Java 17 or later — [Eclipse Temurin](https://adoptium.net) or [Azul Zulu](https://www.azul.com/downloads/).

**Maven:**

```xml
<dependency>
    <groupId>io.github.oculix-org</groupId>
    <artifactId>oculixapi</artifactId>
    <version>4.0.0</version>
</dependency>
```

**From source:**

```bash
git clone https://github.com/oculix-org/Oculix.git
cd Oculix
mvn clean install -DskipTests
```

Detailed setup at [**oculix.org/getting-started/installation**](https://oculix.org/getting-started/installation/).

---

## Adopted by teams at

IBM · Qualcomm · Tencent · Alibaba · Kakao · Deutsche Bank · PepsiCo · Dassault Systèmes · Kaspersky · Synopsys · Neo4j · Zscaler · Jack Henry · RSA Security · Bill.com · University of Alberta

*Organizations with verified OculiX downloads on Maven Central. Names listed for informational purposes only — no endorsement, partnership, or affiliation is implied. Full list on the [adoption showcase](https://oculix.org/showcase/).*

---

## Where to next

| Need | Where |
|---|---|
| Install OculiX | [Get started](https://oculix.org/getting-started/installation/) |
| Your first script | [Hello-world tutorial](https://oculix.org/getting-started/first-script/) |
| OCR & text recognition | [OCR guide](https://oculix.org/guides/ocr/) |
| Bug reports & questions | [GitHub Issues](https://github.com/oculix-org/Oculix/issues) · [Discussions](https://github.com/oculix-org/Oculix/discussions) |
| Security vulnerability | [Private advisory](https://github.com/oculix-org/Oculix/security/advisories/new) — see [SECURITY.md](SECURITY.md) |
| Enterprise support | [Work with us](https://oculix.org/support/enterprise/) |

---

## Community tools & integrations

People are building things around OculiX. If your project deserves a mention here, open a Discussion or a PR — the bar is *"useful to someone in the OculiX ecosystem"*, not *"officially endorsed"*.

| Project | Author | What it brings |
|---|---|---|
| [**oculix-vscode**](https://github.com/MiguelDomingues/oculix-vscode) | [@MiguelDomingues](https://github.com/MiguelDomingues) | VS Code extension : region capture, inline image previews replacing PNG filenames in code, click-to-set target offsets, interactive `cv2.matchTemplate` testing, script runner. Consumes OculiX as a runtime JAR — the right architectural model for community tooling. |

---

## Contributing

Pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for the workflow, code style, and review expectations. Substantive changes are discussed in advance via an issue when possible.

Visible at [Contributors](https://github.com/oculix-org/Oculix/graphs/contributors).

---

## License & lineage

OculiX is released under the [MIT License](LICENSE). It is the active continuation of the Sikuli (Tom Yeh, MIT UIST 2009) and SikuliX (Raimund Hocke, 2010–2025) lineage. The `org.sikuli.*` package namespace and historical prose are preserved as a matter of respect to the foundations.

🦎
