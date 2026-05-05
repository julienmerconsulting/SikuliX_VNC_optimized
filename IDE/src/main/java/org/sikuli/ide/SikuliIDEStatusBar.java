/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide;

import java.awt.*;
import javax.swing.*;

import net.miginfocom.swing.MigLayout;
import org.sikuli.support.Commons;
import org.sikuli.support.ide.SikuliIDEI18N;

import java.util.Date;

class SikuliIDEStatusBar extends JPanel {

  private JLabel _lblMsg;
  private JLabel _lblCaretPos;
  private JLabel _lblOcrStatus;
  private String currentContentType = SikuliIDEI18N._I("statusbar.contentTypeUnknown");
  private int currentRow;
  private int currentCol;
  private long starting;

  public SikuliIDEStatusBar() {
    setLayout(new MigLayout("insets 2 8 2 8, fill", "[]push[]12[]12[]", "[]"));
    setPreferredSize(new Dimension(10, 22));

    _lblMsg = new JLabel();
    _lblMsg.setFont(UIManager.getFont("Label.font").deriveFont(11.0f));

    _lblOcrStatus = new JLabel();
    _lblOcrStatus.setFont(UIManager.getFont("Label.font").deriveFont(11.0f));
    checkOcrStatus();

    _lblCaretPos = new JLabel();
    _lblCaretPos.setFont(UIManager.getFont("Label.font").deriveFont(11.0f));

    setCaretPosition(1, 1);
    resetMessage();

    add(_lblMsg);
    add(_lblOcrStatus);
    add(new JSeparator(SwingConstants.VERTICAL), "growy");
    add(_lblCaretPos);
  }

  public void setType(String contentType) {
    if (contentType == null) {
      return;
    }
    currentContentType = contentType.replaceFirst(".*?\\/", "");
    if (currentContentType.equals("null")) {
      currentContentType = SikuliIDEI18N._I("statusbar.contentTypeText");
    }
    setCaretPosition(-1, 0);
  }

  public void setCaretPosition(int row, int col) {
    if (row > -1) {
      currentRow = row;
      currentCol = col;
    }
    _lblCaretPos.setText(SikuliIDEI18N._I("statusbar.caretFormat", currentContentType, currentRow, currentCol));
    if (starting > 0 && new Date().getTime() - starting > 3000) {
      resetMessage();
    }
  }

  public void setMessage(String text) {
    _lblMsg.setText(text);
    repaint();
    starting = new Date().getTime();
  }

  public void resetMessage() {
    String message = "OculiX " + Commons.getSXVersionShort() + "  \u2502  Java " + Commons.getJavaVersion();
    setMessage(message);
    starting = 0;
  }

  private void checkOcrStatus() {
    try {
      Class<?> engineClass = Class.forName("com.sikulix.ocr.PaddleOCREngine");
      Object engine = engineClass.getDeclaredConstructor().newInstance();
      boolean available = (boolean) engineClass.getMethod("isAvailable").invoke(engine);
      if (available) {
        _lblOcrStatus.setText("\u2B24 PaddleOCR");
        _lblOcrStatus.setForeground(new Color(0x3D, 0xDB, 0xA4)); // green
      } else {
        _lblOcrStatus.setText("\u2B24 PaddleOCR");
        _lblOcrStatus.setForeground(new Color(0xFF, 0x6B, 0x6B)); // red
      }
    } catch (Exception e) {
      _lblOcrStatus.setText("\u2B24 PaddleOCR");
      _lblOcrStatus.setForeground(UIManager.getColor("Label.disabledForeground"));
    }
  }

  public void refreshOcrStatus() {
    checkOcrStatus();
  }
}
