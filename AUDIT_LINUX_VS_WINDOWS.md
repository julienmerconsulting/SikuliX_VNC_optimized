# Audit: Linux Runtime vs Windows Runtime — Oculix / SikuliX

**Date**: 2026-03-22
**Scope**: Read-only analysis — no files modified
**Objective**: Identify everything missing or broken on Linux compared to Windows

---

## 1. OPENCV NATIVE LIBRARIES

### 1.1 What is bundled (sikulixcontent manifests)

| Platform | Manifest file | Bundled libraries |
|----------|--------------|-------------------|
| **Windows** | `sikulixlibs/windows/libs64/sikulixcontent` | `JIntellitype.dll` (local) + `opencv_java454.dll` (from openpnp JAR, **v4.5.4**) |
| **Linux** | `sikulixlibs/linux/libs64/sikulixcontent` | `libJXGrabKey.so` (local) + `libopencv_java430.so` (from openpnp JAR, **v4.3.0**) |
| **macOS** | `sikulixlibs/mac/libs64/sikulixcontent` | `libMacUtil.dylib` (local) + `libopencv_java430.dylib` (from openpnp JAR, v4.3.0) |

### 1.2 OpenCV version mismatch

| | Windows | Linux |
|---|---------|-------|
| **OpenCV Java binding version** | **4.5.4** (`opencv_java454.dll`) | **4.3.0** (`libopencv_java430.so`) |
| **Maven dependency (pom.xml)** | `org.openpnp:opencv:4.5.4-0` | Same dependency, but openpnp 4.5.4-0 bundles 4.3.0 for Linux |

**Impact**: Linux is 2 minor versions behind. API differences between 4.3.0 and 4.5.4 may cause subtle runtime failures if newer features are used.

### 1.3 Supporting C++ runtime libraries

| Library | Windows | Linux |
|---------|---------|-------|
| `libgcc_s_seh-1.dll` / equivalent | Bundled (76 KB) in `Support/nativeCode/windows/` | **NOT bundled** — relies on system `libgcc_s.so` |
| `libstdc++-6.dll` / equivalent | Bundled (931 KB) | **NOT bundled** — relies on system `libstdc++.so` |
| `libwinpthread-1.dll` / equivalent | Bundled (91 KB) | **NOT bundled** — relies on system `libpthread.so` |

### 1.4 OpenCV module dependencies (system-level on Linux)

The `libopencv_java430.so` binding is a monolithic JNI library. However, on Linux it dynamically links against system OpenCV shared libraries. `LinuxSupport.java` explicitly checks for:

| Module | Required | Detected via |
|--------|----------|-------------|
| `libopencv_core.so.*` | YES | `ldconfig -p` |
| `libopencv_imgproc.so.*` | YES | `ldconfig -p` |
| `libopencv_highgui.so.*` | YES | `ldconfig -p` |
| `libopencv_imgcodecs.so.*` | Used in code (`Finder.java`) | Not explicitly checked |

**Windows**: All modules statically linked into `opencv_java454.dll` — fully self-contained.
**Linux**: Requires `apt install libopencv-dev` or equivalent. Without it, `System.load()` fails with `UnsatisfiedLinkError`.

### 1.5 What is needed to make Linux equivalent

| Action | Effort |
|--------|--------|
| Upgrade openpnp dependency or embed a statically-linked `libopencv_java454.so` for Linux | **Medium** |
| Bundle C++ runtime libs (`libstdc++`, `libgcc_s`) in the Linux JAR | **Low** |
| Add `imgcodecs` to `LinuxSupport.java` detection | **Low** |
| Add fallback for containers/environments where `ldconfig` is unavailable | **Medium** |

---

## 2. TESSERACT / OCR

### 2.1 Windows status

| Component | Status |
|-----------|--------|
| **Tesseract native DLLs** | Bundled via Tess4J 4.5.4 Maven artifact (JNA-based, includes `libtesseract.dll` + `libleptonica.dll` for Windows x64) |
| **Tessdata** | `eng.traineddata` bundled in `src/main/resources/tessdataSX/` — extracted at runtime to `SikulixTesseract/tessdata` |
| **Initialization** | `TextRecognizer.java` → `new Tesseract1()` — Tess4J handles native loading via JNA automatically |
| **Result** | **Works out of the box** |

### 2.2 Linux status

| Component | Status |
|-----------|--------|
| **Tesseract native .so** | **NOT bundled**. Tess4J on Linux requires system-installed `libtesseract.so` |
| **Leptonica** | **NOT bundled**. Transitive system dependency |
| **Tessdata** | Same `eng.traineddata` bundled — extraction works identically |
| **Detection** | `LinuxSupport.java` checks `ldconfig -p` for `libtesseract.so.*` |
| **Result** | **Fails unless `apt install libtesseract-dev libleptonica-dev` is run** |

### 2.3 Assembly exclusions

Both `makeapi-win.xml` and `makeapi-lux.xml` exclude `**/tessdata/**` from the fat JAR. The runtime extraction from `/tessdataSX/` is the same on both platforms — this part works.

### 2.4 What is needed

| Action | Effort |
|--------|--------|
| Bundle a statically-compiled `libtesseract.so` + `libleptonica.so` for Linux x64 in the JAR | **High** (requires cross-compilation or CI with Linux builder) |
| Alternatively: document the system dependency clearly + add a startup check with actionable error message | **Low** |
| Add Tesseract version detection (4.x vs 5.x API differences) | **Medium** |

---

## 3. libsExport / RunTime MECHANISM

### 3.1 Platform detection (`RunTime.java:840-862`)

```
os.name → lowercase
  starts with "windows" → runningWindows, sysName="windows"
  starts with "mac"     → runningMac,     sysName="mac"
  starts with "linux"   → runningLinux,   sysName="linux"
```

Path constructed: `/sikulixlibs/{sysName}/libs{javaArch}/` (e.g., `/sikulixlibs/linux/libs64/`)

### 3.2 Export flow comparison

| Step | Windows | Linux |
|------|---------|-------|
| 1. Read manifest | `sikulixcontent` from JAR | Same |
| 2. Extract libs to disk | To `{appDataPath}/SikulixLibs/` | Same |
| 3. Modify system PATH | **YES** — `WinUtil.setEnv("PATH", ...)` prepends libs folder | **NO** — no equivalent |
| 4. Track loaded libs | **YES** — `libsLoaded` HashMap prevents double-loading | **NO** — `if (!runningLinux)` **skips tracking** (line 1247) |
| 5. Load library | `System.load(absolutePath)` | Same |
| 6. TODO/incomplete code | None | `//TODO Linux libs handling` (line 1264) |

### 3.3 Critical divergences

1. **No PATH manipulation on Linux**: Windows uses `WinUtil.setEnv("PATH")` to make extracted DLLs discoverable. Linux has **no equivalent** — no `LD_LIBRARY_PATH` manipulation. This means transitive native dependencies of the extracted `.so` files cannot be found.

2. **No load tracking on Linux**: The `libsLoaded` HashMap is bypassed on Linux (`if (!runningLinux)`). Every call to `loadLibrary()` re-attempts `System.load()`, which causes `UnsatisfiedLinkError` on second load of the same lib.

3. **`libsProvided` is commented out** (lines 1394-1400): The mechanism to copy pre-provided Linux libs (`LinuxSupport.copyProvidedLibs()`) exists but is disabled.

4. **Java 9+ ClassLoader.usr_paths** (lines 1404-1407): TODO comment, never implemented. On Java 9+, the reflection-based path manipulation doesn't work.

### 3.4 What is needed

| Action | Effort |
|--------|--------|
| Set `LD_LIBRARY_PATH` or use `System.setProperty("java.library.path")` before loading | **Medium** |
| Enable load tracking for Linux (remove the `!runningLinux` guard) | **Low** |
| Uncomment and test `libsProvided` support | **Medium** |
| Fix Java 9+ compatibility for library path manipulation | **High** |

---

## 4. VISIONPROXY / JNI

### 4.1 Status per platform

| | Windows | Linux | macOS |
|---|---------|-------|-------|
| **Pre-built binary** | Not needed (uses pure Java Finder2) | **NOT bundled** | Not needed |
| **Native utility lib** | `WinUtil.dll` (bundled, 20 KB) | No equivalent | `libMacUtil.dylib` (bundled, 15 KB) |
| **Image matching** | Pure Java via OpenCV (Finder2 class) | Same Java code, but depends on system OpenCV | Same |
| **VisionProxy** | Not used | Source bundled in `/srcnativelibs/Vision/`, compiled at runtime by `LinuxSupport.buildVision()` | Not used |

### 4.2 Linux VisionProxy compilation

`LinuxSupport.java` implements an on-demand build system:

1. Checks system for OpenCV + Tesseract via `ldconfig -p`
2. Extracts C/C++ sources from `/srcnativelibs/Vision/` in JAR
3. Extracts headers from `/srcnativelibs/Include/OpenCV/` and `/srcnativelibs/Include/Tesseract/`
4. Runs a generated build script (`/Support/Linux/runBuild`)
5. Produces `libVisionProxy.so`

**Requirements**: `gcc`, `g++`, `make`, OpenCV dev headers, Tesseract dev headers, JDK with `jni.h`.

### 4.3 JNI methods

| Platform | Library | JNI methods |
|----------|---------|-------------|
| Windows | `WinUtil.dll` | 10 methods: switchApp, openApp, closeApp, bringWindowToFront, getHwnd, getRegion, getFocusedRegion |
| macOS | `libMacUtil.dylib` | 7 methods: bringWindowToFront, openApp, getPID, getRegion, getFocusedRegion, isAxEnabled, openAxSetting |
| Linux | `libJXGrabKey.so` | X11 keyboard grabbing only — **no window management equivalent** |

### 4.4 What is needed

| Action | Effort |
|--------|--------|
| Pre-compile `libVisionProxy.so` in CI and bundle it (eliminate runtime compilation) | **Medium** |
| Create a `LinuxUtil.so` equivalent for window management (xdotool/wmctrl JNI bridge) | **High** |
| Ensure `libJXGrabKey.so` works on Wayland (currently X11-only) | **High** |

---

## 5. ADB MODULE

### 5.1 Platform-specific code

The ADB module is **almost entirely platform-agnostic**. Only one platform check exists:

```java
// ADBClient.java:39-41
if (RunTime.get().runningWindows) {
    adbExec += ".exe";
}
```

### 5.2 ADB binary resolution

| Priority | Source | Path |
|----------|--------|------|
| 1 | Bundled extensions | `{fSikulixExtensions}/android/platform-tools/adb[.exe]` |
| 2 | Environment variable | `sikulixadb` (env var or Java property) |
| 3 | Working directory | `{workDir}/platform-tools/adb` |
| 4 | JADB fallback | `$ANDROID_HOME/platform-tools/adb` or just `adb` on PATH |

**ADB binary is NOT bundled** in the repository. Users must provide it on all platforms.

### 5.3 Linux vs Windows comparison

| Aspect | Windows | Linux |
|--------|---------|-------|
| Binary discovery | `.exe` suffix added | No suffix, otherwise identical |
| Socket connection | localhost:5037 (standard) | Same |
| Shell commands | `Runtime.exec(String[])` | Same |
| USB device access | Standard ADB driver | May require udev rules |
| Server management | `adb kill-server` via `Runtime.exec` | Same |

### 5.4 What is needed

| Action | Effort |
|--------|--------|
| Nothing platform-specific — ADB works identically | **None** |
| (Optional) Check `ANDROID_HOME` / `ANDROID_SDK_ROOT` in `ADBClient` directly | **Low** |
| (Optional) Auto-detect udev issues on Linux and provide guidance | **Low** |

---

## 6. BUILD SYSTEM

### 6.1 Maven profiles (API/pom.xml + IDE/pom.xml)

| Profile ID | Assembly descriptor | Purpose |
|------------|-------------------|---------|
| `complete-jar` | `makeapi.xml` / `makeide.xml` | Multi-platform fat JAR (all platforms, excludes 32-bit) |
| `complete-win-jar` | `makeapi-win.xml` / `makeide-win.xml` | Windows-only JAR |
| `complete-mac-jar` | `makeapi-mac.xml` / `makeide-mac.xml` | macOS-only JAR |
| `complete-lux-jar` | `makeapi-lux.xml` / `makeide-lux.xml` | Linux-only JAR |
| `sign` | — | GPG signing |
| `build-source` | — | Source JAR |
| `build-docs` | — | Javadoc JAR |
| `maven-release` | — | Sonatype/Nexus deployment |

### 6.2 Assembly descriptor comparison

| Exclusion | `makeapi-win.xml` | `makeapi-lux.xml` | `makeapi.xml` (all) |
|-----------|-------------------|-------------------|---------------------|
| 32-bit Windows | `**/windows/x86_32/**` | — | `**/windows/x86_32/**` |
| All Windows | — | `**/windows/**` | — |
| Linux | `**/linux/**` | — | — |
| macOS | `**/osx/**` | `**/osx/**` | — |
| 32-bit Linux | — | — | `**/linux/x86_32/**` |
| Tessdata | `**/tessdata/**` | `**/tessdata/**` | `**/tessdata/**` |
| JRuby | Excluded | Excluded | Excluded |
| PDFBox | Excluded | Excluded | Excluded |

### 6.3 What is NOT in the build for Linux

| Missing element | Description |
|-----------------|-------------|
| **No native compilation step** | No `maven-nar-plugin`, no `maven-native-plugin`, no CMake integration. Windows DLLs are pre-built and committed. Linux `.so` files are either from openpnp JAR or must be compiled at runtime. |
| **No CI/CD for Linux native builds** | `.travis.yml` reference in pom.xml (`env.TRAVIS_BUILD_NUMBER`) but no actual CI config found |
| **No Docker-based build** | No Dockerfile for reproducible Linux builds |
| **No system dependency check at build time** | Build succeeds even if Linux system libs are missing — failure deferred to runtime |
| **No static linking profile** | No profile to produce a self-contained Linux JAR with statically linked OpenCV/Tesseract |
| **Support/nativeCode has no Linux directory** | `Support/nativeCode/windows/` exists with pre-built DLLs + source. `Support/nativeCode/mac/` exists with source + dylib. **No `Support/nativeCode/linux/` directory.** |

### 6.4 What is needed

| Action | Effort |
|--------|--------|
| Add `Support/nativeCode/linux/` with pre-built `libVisionProxy.so` | **Medium** |
| Add a Maven profile that triggers native compilation on Linux (e.g., via `exec-maven-plugin`) | **Medium** |
| Create a Dockerfile for reproducible Linux builds with all native deps | **Medium** |
| Add a `static-linux` profile that bundles statically-linked `.so` files | **High** |
| Add build-time validation that required system libs exist (`enforcer-plugin`) | **Low** |

---

## SUMMARY MATRIX

| Area | Windows Status | Linux Status | Gap Severity |
|------|---------------|-------------|-------------|
| **OpenCV JNI binding** | v4.5.4, self-contained | v4.3.0, needs system libs | **HIGH** |
| **OpenCV modules** | All statically linked | Requires system `libopencv-dev` | **HIGH** |
| **C++ runtime** | Bundled | System-dependent | **MEDIUM** |
| **Tesseract** | Via Tess4J (self-contained) | Requires system `libtesseract-dev` | **HIGH** |
| **Tessdata** | Bundled `eng.traineddata` | Same — works | **NONE** |
| **RunTime PATH setup** | `WinUtil.setEnv("PATH")` | No `LD_LIBRARY_PATH` setup | **HIGH** |
| **Library load tracking** | HashMap prevents double-load | Tracking disabled | **MEDIUM** |
| **VisionProxy** | Not needed (Finder2) | Source-only, runtime compile | **MEDIUM** |
| **Window management JNI** | `WinUtil.dll` (10 methods) | `libJXGrabKey.so` (keyboard only) | **HIGH** |
| **ADB** | Works | Works (identical) | **NONE** |
| **Build profiles** | `complete-win-jar` works | `complete-lux-jar` exists but incomplete | **HIGH** |
| **Native code directory** | `Support/nativeCode/windows/` | **Does not exist** | **HIGH** |

---

## TOP 5 ACTIONS TO ACHIEVE LINUX PARITY

| Priority | Action | Effort | Impact |
|----------|--------|--------|--------|
| **1** | Bundle statically-linked `libopencv_java454.so` (same version as Windows) | Medium | Eliminates system OpenCV dependency |
| **2** | Bundle `libtesseract.so` + `libleptonica.so` or document required packages with startup validation | Medium-High | Eliminates OCR failure on fresh installs |
| **3** | Implement `LD_LIBRARY_PATH` manipulation in `RunTime.java` (mirror Windows PATH logic) | Medium | Fixes transitive native lib discovery |
| **4** | Enable library load tracking on Linux (remove `!runningLinux` guard) | Low | Prevents double-load crashes |
| **5** | Create `Support/nativeCode/linux/` with pre-built binaries + CI pipeline | Medium | Brings Linux build infrastructure to parity |
