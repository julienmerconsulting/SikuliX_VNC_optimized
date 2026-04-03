from org.sikuli.script import OculixKeywords, Screen, Region, App, Pattern, OCR, TextRecognizer
from org.sikuli.basics import Settings
import jarray
import os, time, subprocess

s = Screen()
tmpdir = os.getcwd()

print("=== TEST OCULIX KEYWORDS - INTEGRATION ===")
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
    """Convert a Python list to a Java int[]"""
    return jarray.array(lst, 'i')

# --- Preparation ---
App.open("notepad.exe")
time.sleep(2)
window = App.focusedWindow()
kw = OculixKeywords(window)

window.type("OCULIX TEST ALPHA BETA GAMMA")
time.sleep(1)

# Capture a reference image from the notepad window
refRegion = Region(window.getX() + 10, window.getY() + 10, 80, 40)
simg = s.capture(refRegion)
smallPath = simg.getFile(tmpdir, "ref_small")
print("Reference image saved: " + smallPath)

# Capture a "not wanted" image from a corner of the screen
nwRegion = Region(1900, 1060, 10, 10)
nwImg = s.capture(nwRegion)
nwPath = nwImg.getFile(tmpdir, "not_wanted")
print("Not-wanted image saved: " + nwPath)

# === GROUPE 1 : Metriques ===
def test_getMatchScore():
    score = kw.getMatchScore(smallPath)
    assert score > 0.5, "Score trop bas: " + str(score)

def test_imageCount():
    count = kw.imageCount(smallPath)
    assert count >= 1, "Count trop bas: " + str(count)

test("getMatchScore", test_getMatchScore)
test("imageCount", test_imageCount)

# === GROUPE 2 : Regions etendues (calcul pur, zero ecran) ===
def test_extended_below():
    result = kw.getExtendedRegionFromRegion(intArray([100, 100, 50, 30]), "below", 2)
    assert result[0] == 100 and result[1] == 130 and result[2] == 50 and result[3] == 60

def test_extended_right():
    result = kw.getExtendedRegionFromRegion(intArray([100, 100, 50, 30]), "right", 3)
    assert result[0] == 150 and result[2] == 150

def test_jumpTo():
    result = kw.fromRegionJumpTo(intArray([100, 100, 50, 30]), "below", 2, 10)
    assert result[1] == 180, "Expected 180, got " + str(result[1])

test("extended below", test_extended_below)
test("extended right", test_extended_right)
test("fromRegionJumpTo", test_jumpTo)

# === GROUPE 3 : ROI ===
def test_setRoi():
    kw.setRoi(window.getX(), window.getY(), 500, 500)
    assert kw.getRegion().getW() == 500
    kw.setRegion(window)

def test_resetRoi():
    kw.setRoi(10, 10, 100, 100)
    kw.resetRoi()
    assert kw.getRegion().getW() > 100
    kw.setRegion(window)

test("setRoi", test_setRoi)
test("resetRoi", test_resetRoi)

# === GROUPE 4 : clickText OCR ===
# Probe OCR availability before running OCR tests
Settings.OcrTextSearch = True
Settings.OcrTextRead = True

textArea = Region(window.getX() + 5, window.getY() + 85, window.getW() - 30, window.getH() - 120)
print("OCR debug - textArea: " + str(textArea))

ocrWorks = False
try:
    ocrResult = textArea.text()
    print("OCR sees: [" + str(ocrResult).strip() + "]")
    if ocrResult and len(ocrResult.strip()) > 0:
        ocrWorks = True
    else:
        print("OCR returned empty - trying with larger region and wait...")
        time.sleep(1)
        # Try full screen OCR to check if Tesseract works at all
        fullOcr = s.text()
        print("Full screen OCR: [" + str(fullOcr)[:80] + "...]")
        if fullOcr and len(fullOcr.strip()) > 0:
            ocrWorks = True
            textArea = window  # fallback to full window
except Exception as e:
    print("OCR probe failed: " + str(e))

if ocrWorks:
    kw.setRegion(textArea)
    test("clickText OCULIX", lambda: kw.clickText("OCULIX"))
    time.sleep(0.3)
    kw.setRegion(textArea)
    test("regionClickText ALPHA", lambda: kw.regionClickText("ALPHA"))
    time.sleep(0.3)
else:
    skip("clickText OCULIX", "Tesseract OCR not available or not detecting text")
    skip("regionClickText ALPHA", "Tesseract OCR not available or not detecting text")

# === GROUPE 5 : Clicks coordonnees ===
kw.setRegion(window)

def test_clickRegion():
    kw.clickRegion(window.getX() + 100, window.getY() + 100, 50, 30)

def test_clickOnRegion():
    r = Region(window.getX() + 100, window.getY() + 100, 50, 30)
    kw.clickOnRegion(r)

test("clickRegion", test_clickRegion)
test("clickOnRegion", test_clickOnRegion)

# === GROUPE 6 : waitForImage ===
kw.setRegion(window)

def test_waitForImage():
    m = kw.waitForImage(smallPath, nwPath, 5)
    assert m is not None, "waitForImage returned None"

test("waitForImage", test_waitForImage)

# === GROUPE 7 : Highlights ===
def test_highlightRoi():
    kw.setRoi(window.getX() + 50, window.getY() + 50, 300, 200)
    kw.highlightRoi(1)
    kw.setRegion(window)

def test_highlightCount():
    count = kw.getHighlightCount()
    assert count == 0, "Expected 0, got " + str(count)

test("highlightRoi", test_highlightRoi)
test("highlightCount", test_highlightCount)

# === GROUPE 8 : Timeout ===
def test_timeout():
    kw.setTimeout(5.0)
    assert kw.getTimeout() == 5.0, "Timeout not set"
    kw.setTimeout(3.0)

test("setTimeout", test_timeout)

# === Cleanup ===
time.sleep(1)
subprocess.call(["taskkill", "/F", "/IM", "notepad.exe"])

print("")
print("=== RESULTATS ===")
print("Passes:  " + str(passed))
print("Fails:   " + str(failed))
print("Skipped: " + str(skipped))
print("Total:   " + str(passed + failed + skipped))
