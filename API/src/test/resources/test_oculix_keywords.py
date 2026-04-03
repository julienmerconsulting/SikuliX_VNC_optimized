from org.sikuli.script import OculixKeywords, Screen, Region, App
from org.sikuli.basics import Settings
from com.sikulix.ocr import PaddleOCREngine
import jarray
import os, time, subprocess

s = Screen()
tmpdir = os.getcwd()

print("=== TEST OCULIX KEYWORDS - INTEGRATION ===")
print("Screen: " + str(s.getBounds()))
print("Working dir: " + tmpdir)
passed = 0
failed = 0
skipped = 0

def test(name, func):
    global passed, failed
    try:
        func()
        print("[OK]   " + name)
        passed += 1
    except Exception as e:
        print("[FAIL] " + name + " : " + str(e))
        failed += 1

def skip(name, reason):
    global skipped
    print("[SKIP] " + name + " : " + reason)
    skipped += 1

def intArray(lst):
    return jarray.array(lst, 'i')

# ============================================================
# PREPARATION
# ============================================================
App.open("notepad.exe")
time.sleep(2)
window = App.focusedWindow()
print("Notepad window: " + str(window))

# Type text into Notepad
window.type("OCULIX TEST ALPHA BETA GAMMA")
time.sleep(1)

# Create OculixKeywords bound to the Notepad window
kw = OculixKeywords(window)

# Capture a reference image: top-left corner of the window (title bar)
refRegion = Region(window.getX(), window.getY(), 80, 30)
simg = s.capture(refRegion)
smallPath = simg.getFile(tmpdir, "ref_small")
print("Ref image: " + smallPath)

# Capture a "not wanted" image: bottom-right of screen (taskbar)
sw = s.getW()
sh = s.getH()
nwRegion = Region(sw - 50, sh - 30, 40, 20)
nwSimg = s.capture(nwRegion)
nwPath = nwSimg.getFile(tmpdir, "not_wanted")
print("Not-wanted image: " + nwPath)
print("")

# ============================================================
# GROUPE 1 : Metriques
# ============================================================
print("--- Metriques ---")
kw.setRegion(window)

def test_getMatchScore():
    score = kw.getMatchScore(smallPath)
    assert score > 0.5, "Score trop bas: " + str(score)

def test_imageCount():
    count = kw.imageCount(smallPath)
    assert count >= 1, "Attendu >= 1, obtenu: " + str(count)

test("getMatchScore", test_getMatchScore)
test("imageCount", test_imageCount)

# ============================================================
# GROUPE 2 : Regions etendues (calcul pur, pas d'ecran)
# ============================================================
print("--- Regions etendues ---")

def test_extended_below():
    r = kw.getExtendedRegionFromRegion(intArray([100, 100, 50, 30]), "below", 2)
    assert r[0] == 100 and r[1] == 130 and r[2] == 50 and r[3] == 60, str(r)

def test_extended_above():
    r = kw.getExtendedRegionFromRegion(intArray([100, 200, 50, 30]), "above", 1)
    assert r[0] == 100 and r[1] == 170 and r[2] == 50 and r[3] == 30, str(r)

def test_extended_right():
    r = kw.getExtendedRegionFromRegion(intArray([100, 100, 50, 30]), "right", 3)
    assert r[0] == 150 and r[2] == 150, str(r)

def test_extended_left():
    r = kw.getExtendedRegionFromRegion(intArray([200, 100, 50, 30]), "left", 2)
    assert r[0] == 100 and r[2] == 100, str(r)

def test_extended_original():
    r = kw.getExtendedRegionFromRegion(intArray([100, 100, 50, 30]), "original", 1)
    assert r[0] == 100 and r[1] == 100 and r[2] == 50 and r[3] == 30, str(r)

def test_jumpTo_below():
    r = kw.fromRegionJumpTo(intArray([100, 100, 50, 30]), "below", 2, 10)
    assert r[1] == 180, "Expected y=180, got " + str(r[1])

def test_jumpTo_right():
    r = kw.fromRegionJumpTo(intArray([100, 100, 50, 30]), "right", 1, 5)
    assert r[0] == 155, "Expected x=155, got " + str(r[0])

test("extended below", test_extended_below)
test("extended above", test_extended_above)
test("extended right", test_extended_right)
test("extended left", test_extended_left)
test("extended original", test_extended_original)
test("jumpTo below", test_jumpTo_below)
test("jumpTo right", test_jumpTo_right)

# ============================================================
# GROUPE 3 : ROI management
# ============================================================
print("--- ROI ---")

def test_setRoi():
    kw.setRoi(50, 50, 400, 300)
    r = kw.getRegion()
    assert r.getW() == 400 and r.getH() == 300, "ROI not set correctly"
    kw.setRegion(window)  # restore

def test_resetRoi():
    kw.setRoi(10, 10, 100, 100)
    kw.resetRoi()
    r = kw.getRegion()
    assert r.getW() > 100, "resetRoi: expected W>100, got " + str(r.getW())
    kw.setRegion(window)  # restore

def test_timeout():
    kw.setTimeout(7.5)
    assert kw.getTimeout() == 7.5, "Timeout not set"
    kw.setTimeout(3.0)  # restore

test("setRoi", test_setRoi)
test("resetRoi", test_resetRoi)
test("setTimeout", test_timeout)

# ============================================================
# GROUPE 4 : clickText OCR
# ============================================================
print("--- clickText OCR ---")
kw.setRegion(window)

# Probe PaddleOCR on the actual window
ocrReady = False
try:
    paddle = PaddleOCREngine()
    if paddle.isAvailable():
        testImg = s.capture(window)
        testPath = testImg.getFile(tmpdir, "debug_paddle")
        print("PaddleOCR: sending image " + testPath)
        rawJson = paddle.getClient().recognize(testPath)
        print("PaddleOCR raw response: " + str(rawJson)[:500])
        testTexts = paddle.getClient().recognizeAndParseTexts(testPath)
        print("PaddleOCR parsed texts: " + str(testTexts))
        if len(testTexts) > 0:
            kw.setOcrEngine(paddle)
            ocrReady = True
            print("OCR engine: PaddleOCR")
        else:
            print("PaddleOCR alive but detected 0 texts in window")
except Exception as e:
    print("PaddleOCR: " + str(e))

# Fallback: Tesseract
if not ocrReady:
    Settings.OcrTextSearch = True
    Settings.OcrTextRead = True
    try:
        ocrResult = window.text()
        if ocrResult and len(ocrResult.strip()) > 0:
            kw.setOcrEngine(None)
            ocrReady = True
            print("OCR engine: Tesseract, sees: [" + ocrResult.strip()[:50] + "]")
        else:
            print("Tesseract: empty result")
    except Exception as e:
        print("Tesseract: " + str(e))

if ocrReady:
    kw.setRegion(window)
    test("clickText OCULIX", lambda: kw.clickText("OCULIX"))
    time.sleep(0.3)
    kw.setRegion(window)
    test("regionClickText ALPHA", lambda: kw.regionClickText("ALPHA"))
    time.sleep(0.3)
else:
    skip("clickText OCULIX", "No OCR engine detected text")
    skip("regionClickText ALPHA", "No OCR engine detected text")

# ============================================================
# GROUPE 5 : Clicks par coordonnees
# ============================================================
print("--- Clicks coordonnees ---")
kw.setRegion(window)

def test_clickRegion():
    cx = window.getX() + window.getW() / 2
    cy = window.getY() + window.getH() / 2
    kw.clickRegion(cx, cy, 20, 20)

def test_clickOnRegion():
    cx = window.getX() + window.getW() / 2
    cy = window.getY() + window.getH() / 2
    r = Region(cx, cy, 20, 20)
    kw.clickOnRegion(r)

test("clickRegion", test_clickRegion)
test("clickOnRegion", test_clickOnRegion)

# ============================================================
# GROUPE 6 : waitForImage
# ============================================================
print("--- waitForImage ---")
kw.setRegion(window)

def test_waitForImage():
    m = kw.waitForImage(smallPath, nwPath, 5)
    assert m is not None, "waitForImage returned None"

test("waitForImage", test_waitForImage)

# ============================================================
# GROUPE 7 : Highlights
# ============================================================
print("--- Highlights ---")

def test_highlightRoi():
    kw.setRoi(window.getX() + 20, window.getY() + 20, 200, 100)
    kw.highlightRoi(1)
    time.sleep(1.2)
    kw.setRegion(window)

def test_highlightCount():
    assert kw.getHighlightCount() == 0, "Should be 0"

test("highlightRoi", test_highlightRoi)
test("highlightCount", test_highlightCount)

# ============================================================
# GROUPE 8 : Captures
# ============================================================
print("--- Captures ---")

def test_captureRoi():
    kw.setRoi(window.getX(), window.getY(), 200, 100)
    path = kw.captureRoi()
    assert path is not None and len(path) > 0, "captureRoi returned empty"
    kw.setRegion(window)

test("captureRoi", test_captureRoi)

# ============================================================
# CLEANUP
# ============================================================
time.sleep(0.5)
subprocess.call(["taskkill", "/F", "/IM", "notepad.exe"])

print("")
print("=== RESULTATS ===")
print("Passes:  " + str(passed))
print("Fails:   " + str(failed))
print("Skipped: " + str(skipped))
print("Total:   " + str(passed + failed + skipped))
