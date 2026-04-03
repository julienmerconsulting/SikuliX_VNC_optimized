# CDC — Apertix Apple Silicon (M1/M2/M3) Support

## Contexte

Raimund Hocke a remonté un `UnsatisfiedLinkError` sur OpenCV lors de l'exécution d'un script OculiX sur Mac M1 (macOS 26, Java 22). Le problème : Apertix ne fournit que des binaires OpenCV `x86-64`. Il n'y a aucun binaire `arm64/aarch64` pour macOS ni Linux.

**Côté OculiX, c'est déjà fait :**
- `Commons.java` détecte `aarch64`/`arm64` via `runningArm64()`
- `loadOpenCV()` cherche `darwin-aarch64/libopencv_java4100.dylib` sur M1
- Les assembly configs incluent déjà tout `/nu/pattern/opencv/osx/**`
- jnativehook upgradé en 2.2.2 (arm64 macOS inclus)
- Les `sikulixcontent` sont corrigés (430 → 4100)

**Ce CDC concerne uniquement le repo Apertix** — compiler et packager les natifs OpenCV manquants.

---

## Rappel : structure actuelle du JAR Apertix

Apertix utilise **JNA** pour le chargement natif. `OpenCV.loadLocally()` délègue à :
```java
NativeLibrary library = NativeLibrary.getInstance(Core.NATIVE_LIBRARY_NAME);
System.load(library.getFile().getAbsolutePath());
```

JNA résout automatiquement le binaire en cherchant dans des répertoires qui suivent sa propre convention :
- `darwin/` (macOS x86-64)
- `darwin-aarch64/` (macOS ARM64/M1)
- `linux-x86-64/` (Linux Intel)
- `linux-aarch64/` (Linux ARM)
- `win32-x86-64/` (Windows 64-bit)

**Aucune modification de `OpenCV.loadLocally()` n'est nécessaire.** JNA gère la détection d'architecture.

```
src/main/resources/
├── darwin/
│   └── libopencv_java490.dylib          ← macOS x86_64, version 4.9.0 (OBSOLETE)
├── linux-aarch64/
│   └── README.md                         ← PAS de binaire
├── linux-x86-64/
│   └── libopencv_java490.so             ← version 4.9.0 (OBSOLETE)
├── win32-x86/
│   └── opencv_java490.dll               ← version 4.9.0 (OBSOLETE)
└── win32-x86-64/
    ├── opencv_java490.dll               ← version 4.9.0 (OBSOLETE)
    └── opencv_java4100.dll              ← seul binaire 4.10.0
```

**Problème critique de version :** `Core.NATIVE_LIBRARY_NAME` vaut `opencv_java4100` (4.10.0)
mais les binaires macOS et Linux sont en 4.9.0 (`libopencv_java490`). JNA cherche `opencv_java4100`
et ne trouve pas → `UnsatisfiedLinkError`. Seul Windows x86-64 fonctionne.

## Structure cible du JAR Apertix

```
src/main/resources/
├── darwin/
│   └── libopencv_java4100.dylib          ← macOS x86_64 (recompiler)
├── darwin-aarch64/
│   └── libopencv_java4100.dylib          ← macOS ARM64/M1 (P0 - recompiler)
├── linux-x86-64/
│   └── libopencv_java4100.so             ← recompiler
├── linux-aarch64/
│   └── libopencv_java4100.so             ← P2 optionnel
├── win32-x86-64/
│   └── opencv_java4100.dll               ← existe déjà
└── win32-x86/
    └── (supprimer ou recompiler)
```

Les anciens binaires 4.9.0 doivent être supprimés pour éviter toute confusion.

---

## Étape 1 — Nettoyer les anciens binaires 4.9.0

Supprimer tous les fichiers `*opencv_java490*` du répertoire resources.
Ils ne sont plus utilisés depuis que `Core.NATIVE_LIBRARY_NAME` pointe vers `opencv_java4100`.

---

## Étape 2 — Compiler OpenCV 4.10.0 pour macOS arm64

### Prérequis (sur un Mac M1/M2/M3)

```bash
# Xcode command line tools
xcode-select --install

# CMake
brew install cmake

# JDK 17+ (Azul Zulu arm64 ou Temurin arm64)
# Vérifier que c'est bien le JDK arm64 :
java -XshowSettings:all 2>&1 | grep "os.arch"
# Doit afficher : os.arch = aarch64

# Cloner OpenCV
git clone https://github.com/opencv/opencv.git
cd opencv
git checkout 4.10.0
```

### Build

```bash
mkdir build-mac-arm64 && cd build-mac-arm64

cmake \
  -DCMAKE_OSX_ARCHITECTURES=arm64 \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=ON \
  -DBUILD_opencv_java=ON \
  -DBUILD_opencv_python3=OFF \
  -DBUILD_TESTS=OFF \
  -DBUILD_PERF_TESTS=OFF \
  -DBUILD_EXAMPLES=OFF \
  -DBUILD_DOCS=OFF \
  -DWITH_IPP=OFF \
  -DJAVA_AWT_INCLUDE_PATH="$JAVA_HOME/include" \
  -DJAVA_AWT_LIBRARY="$JAVA_HOME/lib/libawt.dylib" \
  -DJAVA_INCLUDE_PATH="$JAVA_HOME/include" \
  -DJAVA_INCLUDE_PATH2="$JAVA_HOME/include/darwin" \
  -DJAVA_JVM_LIBRARY="$JAVA_HOME/lib/server/libjvm.dylib" \
  ..

make -j$(sysctl -n hw.ncpu)
```

### Résultat attendu

```
build-mac-arm64/lib/libopencv_java4100.dylib
```

Vérifier l'architecture :
```bash
file lib/libopencv_java4100.dylib
# Doit afficher : Mach-O 64-bit dynamically linked shared library arm64

lipo -info lib/libopencv_java4100.dylib
# Doit afficher : Non-fat file: lib/libopencv_java4100.dylib is architecture: arm64
```

### Copier dans Apertix (convention JNA)

```bash
mkdir -p src/main/resources/darwin-aarch64/
cp build-mac-arm64/lib/libopencv_java4100.dylib \
   src/main/resources/darwin-aarch64/
```

---

## Étape 3 — Compiler OpenCV 4.10.0 pour macOS x86-64

Même machine (cross-compile depuis M1) ou sur un Mac Intel :

```bash
mkdir build-mac-x64 && cd build-mac-x64

cmake \
  -DCMAKE_OSX_ARCHITECTURES=x86_64 \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=ON \
  -DBUILD_opencv_java=ON \
  -DBUILD_opencv_python3=OFF \
  -DBUILD_TESTS=OFF \
  -DBUILD_PERF_TESTS=OFF \
  -DBUILD_EXAMPLES=OFF \
  -DBUILD_DOCS=OFF \
  -DWITH_IPP=OFF \
  -DJAVA_AWT_INCLUDE_PATH="$JAVA_HOME/include" \
  -DJAVA_AWT_LIBRARY="$JAVA_HOME/lib/libawt.dylib" \
  -DJAVA_INCLUDE_PATH="$JAVA_HOME/include" \
  -DJAVA_INCLUDE_PATH2="$JAVA_HOME/include/darwin" \
  -DJAVA_JVM_LIBRARY="$JAVA_HOME/lib/server/libjvm.dylib" \
  ..

make -j$(sysctl -n hw.ncpu)
```

```bash
mkdir -p src/main/resources/darwin/
cp build-mac-x64/lib/libopencv_java4100.dylib \
   src/main/resources/darwin/
```

---

## Étape 4 — Compiler OpenCV 4.10.0 pour Linux x86-64

Sur une machine Linux ou dans un conteneur Docker :

```bash
# Prérequis
sudo apt-get install -y build-essential cmake openjdk-17-jdk libgtk-3-dev

mkdir build-linux-x64 && cd build-linux-x64

cmake \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=ON \
  -DBUILD_opencv_java=ON \
  -DBUILD_opencv_python3=OFF \
  -DBUILD_TESTS=OFF \
  -DBUILD_PERF_TESTS=OFF \
  -DBUILD_EXAMPLES=OFF \
  -DBUILD_DOCS=OFF \
  ..

make -j$(nproc)
```

```bash
mkdir -p src/main/resources/linux-x86-64/
cp build-linux-x64/lib/libopencv_java4100.so \
   src/main/resources/linux-x86-64/
```

---

## Étape 5 — CI/CD (GitHub Actions)

### Workflow pour builder les 4 targets

```yaml
# .github/workflows/build-natives.yml
name: Build OpenCV Natives

on:
  workflow_dispatch:
  push:
    tags: ['v*']

jobs:
  build-mac-arm64:
    runs-on: macos-latest  # M1 depuis 2024 sur GitHub Actions
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: '17'
          architecture: 'aarch64'
      - name: Clone OpenCV 4.10.0
        run: |
          git clone --depth 1 --branch 4.10.0 https://github.com/opencv/opencv.git
      - name: Build
        run: |
          cd opencv && mkdir build && cd build
          cmake -DCMAKE_OSX_ARCHITECTURES=arm64 \
                -DCMAKE_BUILD_TYPE=Release \
                -DBUILD_SHARED_LIBS=ON \
                -DBUILD_opencv_java=ON \
                -DBUILD_opencv_python3=OFF \
                -DBUILD_TESTS=OFF -DBUILD_PERF_TESTS=OFF \
                -DBUILD_EXAMPLES=OFF -DBUILD_DOCS=OFF \
                -DWITH_IPP=OFF ..
          make -j$(sysctl -n hw.ncpu)
      - name: Package
        run: |
          mkdir -p natives/darwin-aarch64
          cp opencv/build/lib/libopencv_java4100.dylib natives/darwin-aarch64/
      - uses: actions/upload-artifact@v4
        with:
          name: opencv-osx-aarch64
          path: natives/

  build-mac-x64:
    runs-on: macos-13  # Intel runner
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: '17'
      - name: Clone OpenCV 4.10.0
        run: |
          git clone --depth 1 --branch 4.10.0 https://github.com/opencv/opencv.git
      - name: Build
        run: |
          cd opencv && mkdir build && cd build
          cmake -DCMAKE_OSX_ARCHITECTURES=x86_64 \
                -DCMAKE_BUILD_TYPE=Release \
                -DBUILD_SHARED_LIBS=ON \
                -DBUILD_opencv_java=ON \
                -DBUILD_opencv_python3=OFF \
                -DBUILD_TESTS=OFF -DBUILD_PERF_TESTS=OFF \
                -DBUILD_EXAMPLES=OFF -DBUILD_DOCS=OFF ..
          make -j$(sysctl -n hw.ncpu)
      - name: Package
        run: |
          mkdir -p natives/darwin
          cp opencv/build/lib/libopencv_java4100.dylib natives/darwin/
      - uses: actions/upload-artifact@v4
        with:
          name: opencv-osx-x86_64
          path: natives/

  build-linux-x64:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: '17'
      - name: Install deps
        run: sudo apt-get install -y build-essential cmake libgtk-3-dev
      - name: Clone OpenCV 4.10.0
        run: |
          git clone --depth 1 --branch 4.10.0 https://github.com/opencv/opencv.git
      - name: Build
        run: |
          cd opencv && mkdir build && cd build
          cmake -DCMAKE_BUILD_TYPE=Release \
                -DBUILD_SHARED_LIBS=ON \
                -DBUILD_opencv_java=ON \
                -DBUILD_opencv_python3=OFF \
                -DBUILD_TESTS=OFF -DBUILD_PERF_TESTS=OFF \
                -DBUILD_EXAMPLES=OFF -DBUILD_DOCS=OFF ..
          make -j$(nproc)
      - name: Package
        run: |
          mkdir -p natives/linux-x86-64
          cp opencv/build/lib/libopencv_java4100.so natives/linux-x86-64/
      - uses: actions/upload-artifact@v4
        with:
          name: opencv-linux-x86_64
          path: natives/

  package-jar:
    needs: [build-mac-arm64, build-mac-x64, build-linux-x64]
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/download-artifact@v4
        with:
          path: src/main/resources/nu/pattern/opencv/
          merge-multiple: true
      - uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: '17'
      - name: Build JAR
        run: mvn package -DskipTests
      - uses: actions/upload-artifact@v4
        with:
          name: apertix-opencv-4.10.0
          path: target/*.jar
```

---

## Étape 6 — Valider côté OculiX

Après publication du nouveau JAR Apertix avec les binaires arm64 :

1. Mettre à jour la version Apertix dans `OculiX/API/pom.xml` si elle change
2. Builder le jar OculiX Mac : `mvn package -Pcomplete-mac-jar`
3. Tester sur Mac M1 :
   ```bash
   java -jar oculixide-3.0.1-complete-mac.jar
   # Vérifier les logs :
   # [OculiX] Running on ARM64/Apple Silicon (aarch64)
   # [OculiX] Native library path: darwin-aarch64
   # OpenCV: loaded via Apertix (nu.pattern.OpenCV)
   ```
4. Tester l'exécution d'un script (le bug original de Raimund)
5. Tester le bouton Record (jnativehook)

---

## Résumé des priorités

| Priorité | Quoi | Effort |
|----------|------|--------|
| **P0** | Supprimer les anciens binaires 4.9.0 (libopencv_java490) | 10 min |
| **P0** | Compiler OpenCV 4.10.0 macOS arm64 → `darwin-aarch64/` | 1h (sur Mac M1) |
| **P0** | Compiler OpenCV 4.10.0 macOS x86-64 → `darwin/` | 1h |
| **P1** | Compiler OpenCV 4.10.0 Linux x86-64 → `linux-x86-64/` | 1h |
| **P1** | CI/CD GitHub Actions multi-platform | 2h |
| **P2** | Compiler Linux arm64 → `linux-aarch64/` | 1h |

---

## Problème connu non couvert par ce CDC

**librococoa.dylib** — le bridge Objective-C pour `App.focus()`, `App.open()` sur macOS. La version actuelle (rococoa 0.5) ne contient que des slices ppc_7400, i386, ppc64, x86_64. Pas d'arm64. Deux options :
- Recompiler rococoa avec arm64 (projet abandonné depuis longtemps)
- Remplacer par JNA direct vers les APIs Cocoa (NSRunningApplication est déjà wrappé en JNA dans OculiX)

Ce sujet mérite un CDC séparé.
