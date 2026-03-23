## OculiX 3.0.0 - Première release officielle

Fork actif de SikuliX1 (archivé le 3 mars 2026). Visual automation for the real world.

### Ajouts majeurs
- **VNC full stack** : VNCScreen, VNCRobot, VNCClient, VNCFrameBuffer, VNCClipboard, XKeySym (2200+ key definitions)
- **SSH natif** : SSHTunnel via jcraft/jsch embarqué, zéro dépendance externe
- **Android ADB** : ADBScreen, ADBRobot, ADBDevice, ADBClient, ADBTest via jadb embarqué
- **OCR multi-engine** : PaddleOCREngine (629 lignes), TesseractEngine, OCREngine pluggable
- **Multi-language runners** : Jython, JRuby, Python, PowerShell, AppleScript, Robot Framework, Network, Server
- **VNC fixes** : Raw encoding, ZRLE/Tight corruption fix, SetPixelFormat negotiation, VncAuth only

### Dépendances
- **Apertix 4.10.0-0** : remplacement d'openpnp/opencv 4.5.4 par OpenCV 4.10.0 compilé from scratch (Windows x86-64)
- JNA migration complète (compatible JDK 16+, zéro --add-opens)

### Build
- Java 17+, Maven multimodule
- Artifacts : **oculixapi-3.0.0.jar** + **oculixide-3.0.0.jar**

### Base
- Fork de RaiMan/SikuliX1 @ MIT License