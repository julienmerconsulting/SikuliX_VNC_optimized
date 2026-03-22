# Rapport d'audit — `org.sikuli.android`

## 1. ADBClient

### CRITIQUE

| # | Description |
|---|-------------|
| C1 | **NPE dans `getDevice(int id)` (ligne 95)** — `jadb.getDevices()` est appelé sans vérifier que `jadb != null`. Si `init()` n'a pas été appelé ou a échoué, `jadb` reste `null` → NPE immédiat. La méthode publique `getDevice(int)` est accessible sans passer par `init()`. |
| C2 | **Exception avalée silencieusement (ligne 103-104)** — Le `catch (Exception e)` dans `getDevice(int)` est entièrement vide. Si le device se déconnecte pendant l'énumération, aucune erreur n'est signalée, et `null` est retourné sans diagnostic. |

### MAJEUR

| # | Description |
|---|-------------|
| M1 | **Variables statiques sans synchronisation** — `jadb`, `device`, `isAdbAvailable`, `shouldStopServer` sont des champs statiques mutables accédés par `init()`, `reset()`, `getDevice()` sans aucune synchronisation. Deux threads appelant `init()` en parallèle peuvent provoquer un double lancement du serveur ADB ou une corruption d'état. |
| M2 | **`reset()` ne remet pas `isAdbAvailable` à `false`** (ligne 108-121) — Après `reset()`, `isAdbAvailable` reste `true` alors que `jadb` et `device` sont `null`. Tout code testant `isAdbAvailable` (ex: `SikulixIDE.androidSupport()`) croit ADB disponible alors que la connexion est détruite. |
| M3 | **`adbExec` public mutable** (ligne 30) — Champ public non-final, modifiable de l'extérieur à tout moment, ce qui peut corrompre le chemin ADB entre `init()` et `reset()`. |
| M4 | **Pas de timeout sur `p.waitFor()`** (ligne 116) — L'appel `Runtime.exec("adb kill-server").waitFor()` bloque indéfiniment si le processus ADB ne termine pas. |

### MINEUR

| # | Description |
|---|-------------|
| m1 | **`isValid()` et `hasDevices()` sont des méthodes d'instance sur une classe à état entièrement statique** (lignes 152-158) — Incohérence : elles testent des champs statiques via une instance, ce qui est trompeur. |
| m2 | **TODO abandonné** (ligne 150) — `//TODO: get device by id` jamais implémenté. |
| m3 | **`isAdbAvailable` est un flag public** jamais réinitialisé proprement → code mort potentiel si on s'y fie après un `reset()`. |

---

## 2. ADBDevice

### CRITIQUE

| # | Description |
|---|-------------|
| C1 | **NPE dans `getDisplayDimension()` → crash garanti en mode paysage/erreur** (lignes 320-335) — Si le pattern regex ne matche pas dans le dump (format changé sur Android récent, ou device en paysage), `dim` reste `null`. La méthode retourne `null`, et `getBounds()` (ligne 155) appelle `dim.getWidth()` → **NPE non rattrapé**. Ce crash se propage à `ADBScreen.init()`, `toString()`, et toute capture d'écran. |
| C2 | **`captureDeviceScreenMat` : busy-wait infini (ligne 201)** — `while (deviceOut.available() < 12);` est un spin-lock sans timeout ni condition de sortie. Si le device se déconnecte ou si le flux ne renvoie jamais 12 octets, le thread est bloqué à 100% CPU pour toujours. |
| C3 | **Rejet d'images valides en mode rotation (lignes 209-212)** — La condition `!((currentW == devW && currentH == devH) || (currentH == devW && currentW == devH))` renvoie `null` si les dimensions ne correspondent pas exactement aux dimensions initiales. Mais `devW`/`devH` sont mis en cache au premier appel de `getBounds()` et ne sont jamais rafraîchis. Si le device change d'orientation après l'init, **toutes les captures échouent silencieusement** en retournant `null`. |
| C4 | **Fuite de ressource Mat OpenCV (ligne 198/267-276)** — `new Mat()` et `new Mat(actH, actW, ...)` sont créés mais jamais `.release()`. Les `Mat` temporaires dans `matsOrg`/`matsImage` ne sont jamais libérés. En boucle de capture, c'est une fuite mémoire native progressive. |

### MAJEUR

| # | Description |
|---|-------------|
| M1 | **Singleton `adbDevice` cassé par `init(int id)` (lignes 88-99)** — La surcharge `init(int id)` crée un `new ADBDevice()` local qui **masque** le champ statique `adbDevice` (variable locale du même nom). Le singleton statique n'est jamais mis à jour, mais le nouveau device retourné n'est pas non plus tracké. Appeler `reset()` ensuite ne nettoie pas ce device fantôme. |
| M2 | **Injection de commande shell via `input(String text)` (ligne 500)** — `device.executeShell("input text ", text)` passe le texte utilisateur directement dans la commande shell. Un texte contenant `;rm -rf /` ou `$(...)` serait exécuté sur le device. Le `Bash.quote()` de jadb ne s'applique qu'aux `args` varargs, pas au texte concaténé dans le premier argument `command`. |
| M3 | **`captureDeviceScreenMat` retourne une `Mat` vide en cas d'exception** (ligne 282) — Si une exception est levée dans le try-catch (lignes 279-281), `matImage` est retourné en tant que `Mat()` vide (0x0) et non `null`. Le code appelant vérifie `matImage != null` mais pas `.empty()`, ce qui provoque des `BufferedImage` de taille 0 ou des erreurs OpenCV en aval. |
| M4 | **`inputKeyEvent` : format de log incorrect (ligne 450)** — `log(-1, "inputKeyEvent: %d did not work: %s", e.getMessage())` utilise `%d` pour formater `e.getMessage()` (un String) car le paramètre `key` est manquant dans les arguments → `IllegalFormatConversionException` à l'exécution, masquant l'erreur réelle. |
| M5 | **`tap()` : commande shell malformée (ligne 456)** — `device.executeShell("input tap", x, y)` envoie `"input tap"` comme un seul mot de commande. Selon `buildCmdLine()` de jadb, le résultat est `"input tap 'x' 'y'"` — cela fonctionne par chance car le shell interprète l'espace dans le premier argument, mais c'est fragile. Même problème pour `swipe()` (ligne 464) et `input()` (ligne 500). |
| M6 | **`typeChar` non thread-safe (ligne 490-493)** — `typeChar()` modifie `textBuffer` sans synchronisation alors que `typeStarts()` et `typeEnds()` sont `synchronized`. Un appel concurrent à `typeChar` pendant `typeEnds` peut perdre des caractères. |
| M7 | **Concaténation de String dans une boucle** (ligne 492) — `textBuffer += character` dans une boucle de frappe crée un nouvel objet String à chaque caractère. |

### MINEUR

| # | Description |
|---|-------------|
| m1 | **Bloc statique commenté (lignes 33-37)** — Chargement OpenCV commenté, déplacé dans `init()` mais le bloc mort reste. |
| m2 | **`isMulti` jamais utilisé (ligne 50)** — Flag déclaré, jamais lu ni écrit. Code mort. |
| m3 | **`execADB()` (lignes 296-318) jamais appelée** — Méthode privée complète avec ProcessBuilder, jamais référencée nulle part. Code mort complet. |
| m4 | **`printDump()` et `printDump(String)` (lignes 363-381)** — Écriture directe sur `System.out` et dans un fichier. Utilitaires de debug laissés en production. |
| m5 | **Import inutilisé** — `java.util.Map` (ligne 27) importé pour `execADB()` qui est du code mort. |

---

## 3. ADBScreen

### CRITIQUE

| # | Description |
|---|-------------|
| C1 | **NPE dans le constructeur si aucun device (lignes 72-77 + 86-94)** — `ADBScreen("")` appelle `ADBDevice.init("")` qui peut retourner `null`. Ensuite `init()` vérifie `device != null`, mais le constructeur **ne lance pas d'exception** si `device == null`. L'objet `ADBScreen` est créé avec `bounds = null`, `robot = null`. Tout appel ultérieur à `getBounds()`, `capture()`, `getDeviceDescription()` → **NPE**. Le `start()` gère ce cas, mais `new ADBScreen()` (appelé directement dans `ADBTest` et `SikulixIDE`) ne le gère pas. |
| C2 | **`userCapture` : busy-wait avec `this.wait(0.1f)` (ligne 236)** — `this.wait()` est `Object.wait()` qui attend une notification, PAS un délai en secondes flottant. Un `float` est casté en `long` (0L), ce qui signifie attente infinie ou retour immédiat selon l'implémentation. Si c'est `Region.wait(float)` (recherche d'image), c'est un usage détourné qui fait une FindFailed silencieuse à chaque itération. Dans les deux cas, le comportement est imprévisible. |

### MAJEUR

| # | Description |
|---|-------------|
| M1 | **Singleton statique `screen` + instances multiples** (lignes 42-57, 68-84) — `start()` gère un singleton statique, mais les constructeurs publics `ADBScreen()` et `ADBScreen(int)` créent des instances indépendantes sans mettre à jour le singleton. `stop()` ne nettoie que le singleton, pas les instances créées par constructeur direct. |
| M2 | **`bounds` jamais rafraîchi** — Les dimensions sont capturées une seule fois dans `init()`. Rotation du device → les dimensions sont obsolètes, les captures sont clippées ou rejetées. |
| M3 | **`lastScreenImage` non thread-safe (ligne 29)** — Accédé en écriture par `capture()` et `userCapture()`, en lecture par `getLastScreenImageFromScreen()`, sans synchronisation. |
| M4 | **`waitForScreenshot` codé en dur à 300** (ligne 35) — C'est le nombre d'itérations dans `userCapture`, pas un timeout réel. La durée dépend du comportement indéterminé de `this.wait(0.1f)`. |

### MINEUR

| # | Description |
|---|-------------|
| m1 | **Bloc commenté `getScreenWithDevice`** (lignes 104-115) — Code mort abandonné. |
| m2 | **`isFake` jamais utilisé (ligne 26)** — Flag statique déclaré, jamais lu. |
| m3 | **`captureObserver` jamais utilisé (ligne 215)** — Déclaré, jamais assigné ni lu. |
| m4 | **`waitPrompt` écrit mais jamais lu de manière utile** (lignes 32, 160, 222) — Assigné dans `update()` et `userCapture()` mais la boucle de `userCapture` ne le vérifie pas. |
| m5 | **`promptMsg` jamais utilisé (ligne 34)** — La chaîne par défaut est déclarée mais `prompt.prompt(msg)` utilise le paramètre `msg`. |

---

## 4. ADBTest

### MAJEUR

| # | Description |
|---|-------------|
| M1 | **NPE si `userCapture` retourne `null` (lignes 75-76, 121-122)** — `sIMg.getFile(...)` et `new Image(sIMg)` sont appelés sans vérifier que `sIMg != null`. Si l'utilisateur annule la capture ou si le timeout est atteint → NPE. |
| M2 | **`ideTest` : fuite de `ADBScreen` (lignes 147-149)** — `ADBScreen.stop()` puis `ADBScreen.start()` puis `adbScreen.getDevice().printDump()` puis `ADBScreen.stop()`. La première `stop()` détruit le device, la deuxième `start()` en crée un nouveau, mais si `printDump()` échoue, la deuxième `stop()` n'est pas dans un finally. |
| M3 | **`isDisplayOn()` peut retourner `null`** (ligne 59) — `adbs.getDevice().isDisplayOn()` retourne `Boolean` (objet), et le test `if (!adbs.getDevice().isDisplayOn())` provoque un unboxing NPE si le retour est `null`. C'est exactement le cas quand le pattern n'est pas trouvé dans le dump. |

### MINEUR

| # | Description |
|---|-------------|
| m1 | **`runTests` flag inutile (ligne 31)** — Toujours `true`, jamais modifié. Vestige de développement. |
| m2 | **Dépendance à l'UI Swing** — `Sikulix.popup()` / `popAsk()` / `popError()` bloque le thread en attendant l'interaction utilisateur. Pas de timeout. |

---

## 5. ADBRobot

### MAJEUR

| # | Description |
|---|-------------|
| M1 | **`getColorAt()` NPE (lignes 211-213)** — `captureScreen()` peut retourner `null` (si device déconnecté). `image.getImage().getRGB(0,0)` → NPE. |
| M2 | **`waitForIdle()` crée un `java.awt.Robot` local** (lignes 217-222) — Instancie un robot desktop pour un device Android. Non seulement inutile, mais lance une `AWTException` en environnement headless. |
| M3 | **`typeStarts()` boucle infinie** (lignes 120-122) — `while (!device.typeStarts()) { RunTime.pause(1); }` boucle indéfiniment si `typing` est déjà `true` et que `typeEnds()` n'est jamais appelé (ex: exception dans le flux de frappe). |

---

## 6. Dépendances externes — JADB & ADB

| Sévérité | Description |
|----------|-------------|
| **CRITIQUE** | **JADB 1.1.0-SNAPSHOT (2018) embarqué en source** — Aucune dépendance Maven, le code est copié directement. Pas de mise à jour possible via le build. La version upstream a évolué significativement depuis. |
| **CRITIQUE** | **Chaque appel `executeShell`/`execute` ouvre une nouvelle socket TCP** vers le daemon ADB (via `getTransport()` → `createTransport()` → `new Socket()`). Les sockets ne sont jamais fermées côté `Transport` quand on utilise `executeShell` avec le retour `InputStream` — la fermeture dépend du consommateur. |
| **MAJEUR** | **Aucune vérification que `adb` est exécutable** — Le code tente `Runtime.exec()` et attrape l'exception, mais ne vérifie ni les permissions ni la version d'ADB. Android 10+ nécessite ADB >= 29. |
| **MAJEUR** | **Pas de gestion du protocole ADB v2** — Android 10+ utilise des features protocolaires (checksum désactivé, TLS auth) que JADB 2018 ne supporte pas. |
| **MINEUR** | **`Subprocess.java`** dans jadb utilise `Runtime.exec(String)` (parsing shell-dépendant) au lieu de `Runtime.exec(String[])`. |

---

## 7. Compatibilité Android moderne (10+)

| Sévérité | Description |
|----------|-------------|
| **CRITIQUE** | **Android 10+ : `screencap` sans `-p` retourne du format RAW RGBA** — Le code attend ce format (vérifie le byte 8 == 0x01). Mais sur certains devices Android 12+, le format du header peut différer, ou `screencap` peut nécessiter des permissions supplémentaires. |
| **MAJEUR** | **Wireless debugging (Android 11+)** — Le code ne supporte que la connexion USB (localhost:5037). Pas de support pour le pairing WiFi ni l'authentification par code. |
| **MAJEUR** | **Scoped storage (Android 10+)** — Les commandes `push`/`pull` de jadb ne tiennent pas compte des restrictions d'accès au stockage. |
| **MAJEUR** | **`mDefaultViewport` pattern** (ADBDevice:324-325) — Le format de `dumpsys display` a changé sur Android 12+ (renommé en `mDisplayInfo`). Le regex ne matchera plus → `getDisplayDimension()` retourne `null` → crash en cascade. |

---

## Résumé des compteurs

| Classe | CRITIQUE | MAJEUR | MINEUR |
|--------|----------|--------|--------|
| ADBClient | 2 | 4 | 3 |
| ADBDevice | 4 | 7 | 5 |
| ADBScreen | 2 | 4 | 5 |
| ADBTest | 0 | 3 | 2 |
| ADBRobot | 0 | 3 | 0 |
| JADB/Compat | 2 | 4 | 1 |
| **Total** | **10** | **25** | **16** |

## Top 3 des problèmes les plus bloquants

1. **`getDisplayDimension()` retourne `null` sur Android 12+** → NPE en cascade, aucune capture possible
2. **Busy-wait infini dans `captureDeviceScreenMat`** → thread bloqué si le device se déconnecte
3. **Aucune gestion de la rotation** — les dimensions sont cachées au premier appel et jamais rafraîchies
