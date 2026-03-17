from com.sikulix.ocr import PaddleOCREngine
import time

region = capture()

t1 = time.time()
paddle = PaddleOCREngine()
result_paddle = paddle.recognize(region)
t2 = time.time()
print("=== PADDLE ({:.3f}s) ===".format(t2-t1))
print(result_paddle)
