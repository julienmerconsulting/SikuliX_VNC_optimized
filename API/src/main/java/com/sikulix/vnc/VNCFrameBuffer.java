/*
 * Copyright (c) 2017 - sikulix.com - MIT license
 */
package com.sikulix.vnc;

import com.tigervnc.rfb.PixelBuffer;
import com.tigervnc.rfb.PixelFormat;
import com.tigervnc.rfb.Rect;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.*;
import java.nio.ByteOrder;

/**
 * An off-screen frame buffer that can be used to capture screen contents.
 */
class VNCFrameBuffer extends PixelBuffer {
  private final Object imageLock = new Object();
  private BufferedImage image;
  private DataBuffer db;
  private PixelFormat pixelFormat = null;
  private ColorModel colorModel = null;

  public DataBuffer getDB() {
    return db;
  }

  public PixelFormat getPixelFormat() {
    return pixelFormat;
  }

  public VNCFrameBuffer(int width, int height, PixelFormat serverPF) {
    PixelFormat nativePF = this.getNativePF();
    if (nativePF.depth > serverPF.depth) {
      pixelFormat = serverPF;
    } else {
      pixelFormat = nativePF;
    }
    this.setPF(pixelFormat);
    colorModel = this.cm;
    this.resize(width, height);
  }

  public void resize(int width, int height) {
    if (width != this.width() || height != this.height()) {
      this.width_ = width;
      this.height_ = height;
      this.createImage(width, height);
    }
  }

  private PixelFormat getNativePF() {
    return new PixelFormat(
            32,
            24,
            ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN,
            true,
            255,
            255,
            255,
            16,
            8,
            0
    );
  }

  private void createImage(int width, int height) {
    synchronized (imageLock) {
      if (width != 0 && height != 0) {
        WritableRaster raster = colorModel.createCompatibleWritableRaster(width, height);
        this.image = new BufferedImage(colorModel, raster, false, null);
        this.db = raster.getDataBuffer();
      }
    }
  }

  public void fillRect(int x, int y, int w, int h, int pixelValue) {
    synchronized (imageLock) {
      Graphics2D g2d = this.image.createGraphics();
      switch (this.format.depth) {
        case 24:
          g2d.setColor(new Color(pixelValue));
          g2d.fillRect(x, y, w, h);
          break;
        default:
          g2d.setColor(new Color(0xff000000 | colorModel.getRed(pixelValue) << 16 |
                  colorModel.getGreen(pixelValue) << 8 | colorModel.getBlue(pixelValue)));
          g2d.fillRect(x, y, w, h);
      }

      g2d.dispose();
    }
  }

  public void imageRect(int x, int y, int w, int h, Object p) {
    if (p instanceof Image) {
      Image img = (Image) p;
      synchronized (imageLock) {
        Graphics2D g2d = this.image.createGraphics();
        g2d.drawImage(img, x, y, w, h, null);
        g2d.dispose();
      }
      img.flush();
    } else {
      synchronized (imageLock) {
        SampleModel sampleModel = this.image.getSampleModel();
        if (sampleModel.getTransferType() == DataBuffer.TYPE_BYTE) {
          byte[] byteData = new byte[((int[]) p).length];

          for (int i = 0; i < byteData.length; ++i) {
            byteData[i] = (byte) ((int[]) p)[i];
          }

          p = byteData;
        }

        sampleModel.setDataElements(x, y, w, h, p, this.db);
      }
    }

  }

  public void copyRect(int dx, int dy, int w, int h, int sx, int sy) {
    synchronized (imageLock) {
      Graphics2D g2d = this.image.createGraphics();
      g2d.copyArea(sx, sy, w, h, dx - sx, dy - sy);
      g2d.dispose();
    }
  }

  public BufferedImage getImage(int x, int y, int w, int h) {
    BufferedImage i;
    synchronized (imageLock) {
      i = new BufferedImage(image.getColorModel(), image.getColorModel().createCompatibleWritableRaster(w, h), false, null);
      Graphics2D g2d = i.createGraphics();
      g2d.drawImage(image, 0, 0, w, h, x, y, x + w, y + h, null);
      g2d.dispose();
    }
    return i;
  }
}
