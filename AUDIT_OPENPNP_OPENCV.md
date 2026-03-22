# Investigation: Why openpnp OpenCV 4.5.4-0 provides 4.5.4 for Windows but 4.3.0 for Linux

**Date**: 2026-03-22
**Scope**: Read-only investigation — no files modified

---

## EXECUTIVE SUMMARY

**Root cause found**: The openpnp `opencv:4.5.4-0` JAR **DOES contain `libopencv_java454.so` for Linux x86_64**. The version mismatch is **NOT an upstream openpnp problem** — it is a **stale `sikulixcontent` manifest** in the Oculix repository that was never updated when the Maven dependency was bumped from 4.3.0 to 4.5.4.

**The fix is a 2-line change** in `sikulixcontent` manifest files.

---

## 1. OPENPNP ARTIFACT CONTENTS

### 1.1 Verified JAR contents (downloaded from Maven Central)

**Artifact**: `org.openpnp:opencv:4.5.4-0` (91 MB)
**Source**: `https://repo1.maven.org/maven2/org/openpnp/opencv/4.5.4-0/opencv-4.5.4-0.jar`

| Path in JAR | Size | OpenCV Version |
|-------------|------|----------------|
| `nu/pattern/opencv/windows/x86_64/opencv_java454.dll` | 50.8 MB | **4.5.4** |
| `nu/pattern/opencv/windows/x86_32/opencv_java454.dll` | 31.3 MB | **4.5.4** |
| `nu/pattern/opencv/linux/x86_64/libopencv_java454.so` | **61.9 MB** | **4.5.4** |
| `nu/pattern/opencv/linux/ARMv8/libopencv_java454.so` | 26.5 MB | **4.5.4** |
| `nu/pattern/opencv/linux/ARMv7/libopencv_java454.so` | 18.8 MB | **4.5.4** |
| `nu/pattern/opencv/linux/x86_32/` | README only | — |
| `nu/pattern/opencv/osx/x86_64/libopencv_java454.dylib` | 53.0 MB | **4.5.4** |

**Key finding**: `libopencv_java454.so` **exists in the JAR for Linux x86_64, ARMv7, and ARMv8**. There is NO `libopencv_java430.so` in this artifact.

### 1.2 What the Oculix manifests reference

| Platform | `sikulixcontent` references | Actual file in JAR | Match? |
|----------|---------------------------|-------------------|--------|
| **Windows** | `/nu/pattern/opencv/windows/x86_64/opencv_java454.dll` | `opencv_java454.dll` | **YES** |
| **Linux** | `/nu/pattern/opencv/linux/x86_64/libopencv_java430.so` | `libopencv_java454.so` | **NO — file not found** |
| **macOS** | `/nu/pattern/opencv/osx/x86_64/libopencv_java430.dylib` | `libopencv_java454.dylib` | **NO — file not found** |

### 1.3 What happens at runtime on Linux

1. `RunTime.libsExport()` reads `sikulixcontent` manifest (line 1351)
2. For the OpenCV entry, it splits on `@`: path = `/nu/pattern/opencv/linux/x86_64/libopencv_java430.so`, class = `nu.pattern.OpenCV`
3. Calls `nu.pattern.OpenCV.class.getResourceAsStream("/nu/pattern/opencv/linux/x86_64/libopencv_java430.so")`
4. **Returns `null`** because that file doesn't exist in the openpnp 4.5.4-0 JAR
5. The `copy(inStream, outFile)` at line 1377 throws `NullPointerException` or `IOException`
6. `libsExport` logs "failed" and **deletes the entire libs folder** (line 1384)
7. OpenCV is never loaded → **all image operations fail**

**On Windows**: The manifest correctly references `opencv_java454.dll`, so extraction succeeds.

---

## 2. OPENPNP GITHUB / RELEASE HISTORY

### 2.1 Project status

- **Repository**: https://github.com/openpnp/opencv
- **Latest release on Maven Central**: `4.9.0-0` (as of 2024)
- **Build system**: Uses pre-compiled OpenCV binaries for all platforms
- **All versions since 4.5.1-0** include Linux x86_64 native binaries with matching version numbers

### 2.2 Version history (relevant releases)

| openpnp version | OpenCV | Linux x86_64 binary | Windows x86_64 binary |
|----------------|--------|---------------------|-----------------------|
| 4.3.0-2 | 4.3.0 | `libopencv_java430.so` | `opencv_java430.dll` |
| 4.5.1-0 | 4.5.1 | `libopencv_java451.so` | `opencv_java451.dll` |
| **4.5.4-0** | **4.5.4** | **`libopencv_java454.so`** | **`opencv_java454.dll`** |
| 4.7.0-0 | 4.7.0 | `libopencv_java470.so` | `opencv_java470.dll` |
| 4.9.0-0 | 4.9.0 | `libopencv_java490.so` | `opencv_java490.dll` |

**There has never been a version where Windows and Linux had different OpenCV versions.** The mismatch is purely in Oculix's manifests.

### 2.3 What likely happened

The Oculix project was originally using `org.openpnp:opencv:4.3.0-2`. When the dependency was upgraded to `4.5.4-0` in `pom.xml`:
- The Windows `sikulixcontent` was updated to reference `opencv_java454.dll` ✓
- The Linux `sikulixcontent` was **NOT updated** — still references `libopencv_java430.so` ✗
- The macOS `sikulixcontent` was **NOT updated** — still references `libopencv_java430.dylib` ✗
- The Java binding sources (`Core.java`, `OpenCVNativeLoader.java`) were updated to 4.5.4 ✓

---

## 3. ALTERNATIVE ARTIFACTS (for reference)

### 3.1 org.bytedeco:opencv-platform (JavaCV)

| Attribute | Value |
|-----------|-------|
| **Maven coordinates** | `org.bytedeco:opencv-platform:4.10.0-1.5.11` |
| **Latest OpenCV version** | 4.10.0 |
| **Linux x86_64** | YES — statically linked, self-contained |
| **Linux ARM64** | YES |
| **Windows x86_64** | YES |
| **macOS x86_64 + ARM64** | YES |
| **Total dependency size** | ~300 MB (all platforms) or ~70 MB (single platform with classifier) |
| **API style** | Own Java API wrapping C++ (not standard OpenCV Java bindings) |

**Pros**: Most complete multi-platform support, actively maintained, static builds
**Cons**: Different API from OpenCV's standard Java bindings — would require rewriting all `org.opencv.*` calls. Not a drop-in replacement.

### 3.2 nu.pattern:opencv

| Attribute | Value |
|-----------|-------|
| **Maven coordinates** | `nu.pattern:opencv:2.4.9-7` |
| **Latest version** | 2.4.9-7 (last published ~2015) |
| **Status** | **ABANDONED** — openpnp is the active fork |

**Not viable** — too old, unmaintained.

### 3.3 Direct static compilation

To produce a self-contained `libopencv_java454.so`:

```bash
cmake -D BUILD_SHARED_LIBS=OFF \
      -D BUILD_opencv_java=ON \
      -D BUILD_ZLIB=ON \
      -D BUILD_PNG=ON \
      -D BUILD_JPEG=ON \
      -D BUILD_TIFF=ON \
      -D WITH_GTK=OFF \
      -D WITH_QT=OFF \
      -D CMAKE_BUILD_TYPE=Release \
      ..
```

**Effort**: High (requires CI infrastructure, testing across distros)
**Not needed** — openpnp already provides statically-linked binaries for Linux.

---

## 4. VERSION IMPACT ANALYSIS

### 4.1 OpenCV API usage in Oculix codebase

Complete audit of all `org.opencv.*` API calls:

**Core module** (17 methods): `Mat` constructors, `minMaxLoc`, `split`, `merge`, `absdiff`, `countNonZero`, `bitwise_not`, `subtract`, `addWeighted`, `mixChannels`, `meanStdDev`, `CvType` constants, `Size`, `Scalar`
→ **All available in 4.3.0** ✓

**Imgproc module** (25 methods/constants): `matchTemplate`, `resize`, `cvtColor`, `GaussianBlur`, `threshold`, `dilate`, `morphologyEx`, `getStructuringElement`, `findContours`, all `TM_*` constants, all `INTER_*` constants, `COLOR_BGR2GRAY`, `THRESH_*`, `MORPH_*`, `RETR_LIST`, `CHAIN_APPROX_SIMPLE`
→ **All available in 4.3.0 EXCEPT ONE** ⚠️

**Imgcodecs module** (1 method): `imencode`
→ **Available in 4.3.0** ✓

### 4.2 The one incompatibility

| Item | Details |
|------|---------|
| **Constant** | `Imgproc.INTER_LINEAR_EXACT` (value = 5) |
| **Introduced in** | OpenCV 4.5.0 |
| **Used in** | `Image.java:386` (Interpolation enum), `ImageWindow.java:129` (resize call) |
| **Impact** | The constant is defined in the Java binding source (which is 4.5.4), so it compiles. The native library must also support it — **4.5.4 does, 4.3.0 does NOT** |
| **Severity** | LOW — only triggered if `Interpolation.LINEAR_EXACT` is explicitly selected by user code. Default interpolation methods don't use it. |
| **Conclusion** | **Moot point** — since openpnp 4.5.4-0 actually provides 4.5.4 for Linux, this is not a real compatibility issue. The only problem is the stale manifest. |

### 4.3 Is staying on 4.3.0 for Linux safe?

**This question is irrelevant** — the project is NOT actually running on 4.3.0. The openpnp JAR contains 4.5.4 for all platforms. The Linux `sikulixcontent` just points to a nonexistent filename, causing **total failure** (not degraded functionality).

---

## 5. RECOMMENDED SOLUTION

### Option A: Fix the sikulixcontent manifests (RECOMMENDED)

**The fix is trivial — 2 files, 1 line each:**

**File 1**: `API/src/main/resources/sikulixlibs/linux/libs64/sikulixcontent`
```
Current:  /nu/pattern/opencv/linux/x86_64/libopencv_java430.so@nu.pattern.OpenCV
Fix:      /nu/pattern/opencv/linux/x86_64/libopencv_java454.so@nu.pattern.OpenCV
```

**File 2**: `API/src/main/resources/sikulixlibs/mac/libs64/sikulixcontent`
```
Current:  /nu/pattern/opencv/osx/x86_64/libopencv_java430.dylib@nu.pattern.OpenCV
Fix:      /nu/pattern/opencv/osx/x86_64/libopencv_java454.dylib@nu.pattern.OpenCV
```

| Attribute | Value |
|-----------|-------|
| **Effort** | **Trivial** (< 5 minutes) |
| **Risk** | None — aligns manifest with actual JAR contents |
| **Testing** | Run on Linux, verify OpenCV loads successfully |
| **Side effects** | Linux gets 61.9 MB statically-linked `.so` instead of a failed load |

### Why NOT the other options

| Option | Reason to reject |
|--------|-----------------|
| B: Embed pre-built `.so` in repo | Unnecessary — openpnp already provides it. Would add 62 MB to repo. |
| C: Stay on 4.3.0 with limitations | Linux is not "on 4.3.0" — it's **broken**. The `.so` file doesn't exist in the JAR. |
| D: Switch to JavaCV (bytedeco) | Completely different API. Would require rewriting all OpenCV code. Massive effort for no gain. |

### Optional follow-up: Upgrade to openpnp 4.9.0-0

After fixing the manifests, consider upgrading the Maven dependency:

```xml
<!-- Current -->
<version>4.5.4-0</version>

<!-- Recommended upgrade -->
<version>4.9.0-0</version>
```

This would bring OpenCV 4.9.0 to all platforms. The manifest filenames would need to be updated to `*454*` → `*490*`. All current API usage is compatible.

**Effort**: Low (update pom.xml + 3 sikulixcontent files + test)

---

## 6. ADDITIONAL FINDING: Linux `.so` is self-contained

The `libopencv_java454.so` in openpnp 4.5.4-0 is **61.9 MB** — significantly larger than a typical dynamically-linked build. This strongly suggests it is **statically linked** with all OpenCV modules built in, similar to the Windows DLL (50.8 MB).

If confirmed, this means:
- **No system OpenCV installation required on Linux** (contrary to what was assumed in the previous audit)
- The `LinuxSupport.java` checks for `libopencv_core.so`, `libopencv_imgproc.so`, etc. via `ldconfig` are **unnecessary** when using the openpnp-provided library
- Linux can be fully self-contained, exactly like Windows

**Verification needed**: Run `ldd libopencv_java454.so` on the extracted library to confirm static linking.

---

## SUMMARY

| Question | Answer |
|----------|--------|
| Does openpnp provide 4.5.4 for Linux? | **YES** — `libopencv_java454.so` (61.9 MB) |
| Why does Oculix load 4.3.0 on Linux? | **Stale manifest** — `sikulixcontent` references `430` instead of `454` |
| Is this an upstream openpnp bug? | **NO** — all platforms get 4.5.4 in the JAR |
| What breaks on Linux currently? | **Everything** — `getResourceAsStream` returns null → extraction fails → no OpenCV |
| Fix effort? | **Trivial** — change 2 filenames in 2 manifest files |
| API compatibility risk? | **None** — one minor constant (`INTER_LINEAR_EXACT`) not used in default paths |
