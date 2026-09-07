/*
 * Copyright (c) 2010-2020, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.ide;

import org.sikuli.basics.Debug;
import org.sikuli.basics.PreferencesUser;
import org.sikuli.basics.Settings;
import org.sikuli.script.Image;
import org.sikuli.script.Key;
import org.sikuli.script.ScreenImage;
import org.sikuli.support.Commons;
import org.sikuli.support.devices.ScreenDevice;
import org.sikuli.support.ide.SikuliIDEI18N;
import org.sikuli.util.EventObserver;
import org.sikuli.util.EventSubject;
import org.sikuli.util.OverlayCapturePrompt;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

class ButtonCapture extends ButtonOnToolbar implements Cloneable, EventObserver {

  private static final String me = "ButtonCapture: ";
  protected EditorPane _codePane;
  private boolean captureCancelled = false;
  private EditorPatternLabel _lbl = null;
  private String givenName = "";

  public static boolean debugTrace = true;

  public ButtonCapture() {
    super();
    buttonText = SikulixIDE._I("btnCaptureLabel");
    buttonHint = captureHint();
    iconFile = "/icons/sxcapture-x.png";
    init();
  }

  private static String captureHint() {
    PreferencesUser pref = PreferencesUser.get();
    String strHotkey = Key.convertKeyToText(
        pref.getCaptureHotkey(), pref.getCaptureHotkeyModifiers());
    return SikulixIDE._I("btnCaptureHint", strHotkey);
  }

  /**
   * Re-render the tooltip after the capture hotkey has been rebound — otherwise the
   * button keeps advertising the previous combination until the IDE is restarted.
   */
  void refreshTooltip() {
    buttonHint = captureHint();
    setToolTipText(buttonHint);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    Debug.log(3, "ButtonCapture: capture started");
    captureWithAutoDelay();
  }

  public void captureWithAutoDelay() {
    PreferencesUser pref = PreferencesUser.get();
    int delay = (int) (pref.getCaptureDelay() * 1000.0) + 1;
    capture(delay);
  }

  ScreenImage sImgNonLocal = null;

  public void capture(int delay) {
    // No active script context — the Welcome tab is showing, or every script tab is
    // closed. There is then nowhere to save the image and no line to name it from.
    // Bail out *before* hiding the IDE: otherwise the window vanishes, capture dies
    // on an NPE deep in getLineTextAtCaret(), and the IDE is left hidden with nothing
    // on screen to explain it. Reachable from the toolbar button and, more easily,
    // from the capture hotkey.
    if (SikulixIDE.get().getActiveContext() == null) {
      Debug.error("ButtonCapture: no script open — nothing to capture into");
      JOptionPane.showMessageDialog(SikulixIDE.get(),
          SikuliIDEI18N._I("msgCaptureNoScript"),
          SikuliIDEI18N._I("dlgCaptureNoScript"),
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }

    if (SikulixIDE.notHidden()) {
      delay = Math.max(delay, 500);
      SikulixIDE.doHide();
    }

    givenName = SikulixIDE.get().getImageNameFromLine();

    Commons.pause(delay);
    OverlayCapturePrompt.capturePrompt(this, SikulixIDE._I("captureOverlayPrompt"));

//TODO capture on Android
//    defaultScreen = SikulixIDE.getDefaultScreen();
//    if (defaultScreen == null) {
//      Screen.doPrompt("Select an image", this);
//    } else {
//      if (HelpDevice.isAndroid(defaultScreen) && Sikulix.popAsk("Android capture")) {
//        new Thread() {
//          @Override
//          public void run() {
//            sImgNonLocal = (ScreenImage) defaultScreen.action("userCapture");
//            ButtonCapture.this.update((EventSubject) null);
//          }
//        }.start();
//      } else {
//        ButtonCapture.this.update((EventSubject) null);
//      }
//    }
  }

  @Override
  public void update(EventSubject event) {
    BufferedImage capturedImage = null;
    BufferedImage screenShot = null;
    OverlayCapturePrompt ocp = (OverlayCapturePrompt) event;

    if (!ocp.isCanceled()) {
      Debug.log(3, "ButtonCapture: finished");
      capturedImage = ocp.getSelectionImage();
      if (capturedImage != null) {
        screenShot = ocp.getOriginal();
      }
    } else {
      Debug.log(3, "ButtonCapture: cancelled");
    }

    ocp.close();

    if (capturedImage != null) {
      if (givenName.isEmpty()) {
        final PreferencesUser prefs = PreferencesUser.get();
        int naming = prefs.getAutoNamingMethod();
        if (naming == PreferencesUser.AUTO_NAMING_TIMESTAMP) {
          givenName = Settings.getTimestamp();
        } else if (naming == PreferencesUser.AUTO_NAMING_OCR) {
          givenName = PatternPaneNaming.getFilenameFromImage(capturedImage);
          if (givenName == null || givenName.isEmpty()) {
            givenName = Settings.getTimestamp();
          }
        } else {
          // AUTO_NAMING_OFF: run OCR silently for a suggested name, then pop the
          // input dialog with it pre-filled so the user can accept or edit it.
          String nameOCR = "";
          try {
            nameOCR = PatternPaneNaming.getFilenameFromImage(capturedImage);
          } catch (Exception e) {
            // OCR failure is silent — fall back to a blank suggestion
          }
          givenName = askForScreenshotName(
              (nameOCR == null || nameOCR.isEmpty()) ? "noname" : nameOCR);
          if (givenName == null || givenName.isEmpty()) {
            givenName = Settings.getTimestamp();
          }
        }
      }
      SikulixIDE.PaneContext context = SikulixIDE.get().getActiveContext();
      final File imgFile = new File(context.getImageFolder(), givenName + ".png");
      try {
        org.sikuli.support.FileManager.writePngWithDpi(capturedImage, imgFile);
        if (context.getScreenshotFolder().exists()) {
          org.sikuli.support.FileManager.writePngWithDpi(screenShot, new File(context.getScreenshotFolder(), givenName + ".png"));
        }
      } catch (IOException e) {
      }
      if (context.getShowThumbs()) {
        context.insertImageButton(imgFile);
      } else {
        context.getPane().insertString("\"" + givenName + ".png\"");
      }
    }
    ScreenDevice.closeCapturePrompts();
    SikulixIDE.showAgain();
  }

  //<editor-fold defaultstate="collapsed" desc="RaiMan not used">
  /*public boolean hasNext() {
   * return false;
   * }*/
  /*public CaptureButton getNextDiffButton() {
   * return null;
   * }*/
  /*public void setParentPane(SikuliPane parent) {
   * _codePane = parent;
   * }*/
  /*public void setDiffMode(boolean flag) {
   * }*/
  /*public void setSrcElement(Element elmLine) {
   * _line = elmLine;
   * }*/
  //</editor-fold>

  /**
   * Asks for the filename to save a capture under.
   *
   * <p>Built by hand rather than through {@code JOptionPane.showInputDialog(parent, …)},
   * which cannot be made reliably visible here. Two things work against it:
   *
   * <ul>
   * <li>{@link #capture(int)} calls {@code SikulixIDE.doHide()} first and
   *     {@code showAgain()} only runs after this prompt returns, so the main IDE
   *     window — the parent the convenience method would use — is <em>hidden</em>
   *     while the prompt is up, leaving the dialog without a visible anchor.</li>
   * <li>The Preferences window is a separate top-level frame, outside that owner
   *     hierarchy. JOptionPane dialogs are application-modal, so with Preferences
   *     open the prompt blocks it while rendering <em>behind</em> it. The IDE then
   *     looks frozen for no visible reason and there is no way to recover except
   *     finding the hidden dialog.</li>
   * </ul>
   *
   * <p>Owning the dialog to the active window keeps it above whatever the user is
   * actually looking at, and always-on-top guarantees it regardless. Easy to hit now
   * that the Hotkeys tab invites testing the capture hotkey from inside Preferences.
   *
   * @param suggestedName value the input starts pre-filled and selected with; the
   *                      OCR-derived suggestion when AUTO_NAMING_OFF found text,
   *                      otherwise "noname"
   * @return the chosen name, or null if cancelled
   */
  private static String askForScreenshotName(String suggestedName) {
    Window owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    if (owner == null || !owner.isShowing()) {
      owner = SikulixIDE.get();
    }
    JOptionPane pane = new JOptionPane(
        SikuliIDEI18N._I("msgEnterScreenshotFilename"),
        JOptionPane.PLAIN_MESSAGE,
        JOptionPane.OK_CANCEL_OPTION,
        null, null, null);
    pane.setWantsInput(true);
    pane.setInitialSelectionValue(suggestedName);
    JDialog dialog = pane.createDialog(owner, SikuliIDEI18N._I("dlgEnterScreenshotFilename"));
    dialog.setAlwaysOnTop(true);
    pane.selectInitialValue();
    dialog.setVisible(true); // modal — blocks here until dismissed
    dialog.dispose();
    Object value = pane.getInputValue();
    if (value == null || value == JOptionPane.UNINITIALIZED_VALUE) {
      return null;
    }
    return value.toString();
  }

  private boolean replaceButton(Element src, String imgFullPath) {
    if (captureCancelled) {
      if (_codePane.context.getShowThumbs() && PreferencesUser.get().getPrefMoreImageThumbs()
          || !_codePane.context.getShowThumbs()) {
        return true;
      }
    }
    int start = src.getStartOffset();
    int end = src.getEndOffset();
    int old_sel_start = _codePane.getSelectionStart(),
        old_sel_end = _codePane.getSelectionEnd();
    try {
      StyledDocument doc = (StyledDocument) src.getDocument();
      String text = doc.getText(start, end - start);
      Debug.log(3, text);
      for (int i = start; i < end; i++) {
        Element elm = doc.getCharacterElement(i);
        if (elm.getName().equals(StyleConstants.ComponentElementName)) {
          AttributeSet attr = elm.getAttributes();
          Component com = StyleConstants.getComponent(attr);
          boolean isButton = com instanceof ButtonCapture;
          boolean isLabel = com instanceof EditorPatternLabel;
          if (isButton || isLabel && ((EditorPatternLabel) com).isCaptureButton()) {
            Debug.log(5, "button is at " + i);
            int oldCaretPos = _codePane.getCaretPosition();
            _codePane.select(i, i + 1);
            if (!_codePane.context.getShowThumbs()) {
              _codePane.insertString((new EditorPatternLabel(_codePane, imgFullPath, true)).toString());
            } else {
              if (PreferencesUser.get().getPrefMoreImageThumbs()) {
                com = new EditorPatternButton(_codePane, imgFullPath);
              } else {
                if (captureCancelled) {
                  com = new EditorPatternLabel(_codePane, "");
                } else {
                  com = new EditorPatternLabel(_codePane, imgFullPath, true);
                }
              }
              _codePane.insertComponent(com);
            }
            _codePane.setCaretPosition(oldCaretPos);
            break;
          }
        }
      }
    } catch (BadLocationException ble) {
      Debug.error(me + "Problem inserting Button!\n%s", ble.getMessage());
    }
    _codePane.select(old_sel_start, old_sel_end);
    _codePane.requestFocus();
    return true;
  }

  protected void insertAtCursor(EditorPane pane, Image capturedImage) {
    if (!pane.context.getShowThumbs()) {
      pane.insertString("\"" + capturedImage.getName() + "\"");
    } else {
      if (PreferencesUser.get().getPrefMoreImageThumbs()) {
        EditorPatternButton comp = EditorPatternButton.createFromImage(pane, capturedImage, null);
        if (comp != null) {
          pane.insertComponent(comp);
        }
      } else {
        EditorPatternLabel label = new EditorPatternLabel(pane, capturedImage.fileName(), true);
        pane.insertComponent(label);
      }
    }
//TODO set Caret
    pane.requestFocus();
  }

  @Override
  public String toString() {
    return "\"__CLICK-TO-CAPTURE__\"";
  }
}
