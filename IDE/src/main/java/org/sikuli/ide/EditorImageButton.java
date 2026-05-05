/*
 * Copyright (c) 2010-2020, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.ide;

import org.apache.commons.io.FilenameUtils;
import org.sikuli.basics.PreferencesUser;
import org.sikuli.support.ide.IButton;
import org.sikuli.script.Location;
import org.sikuli.script.Pattern;
import org.sikuli.support.Commons;
import org.sikuli.support.RunTime;
import org.sikuli.support.gui.SXDialog;
import org.sikuli.support.gui.SXDialogPaneImage;
import org.sikuli.support.gui.SXDialogPaneImageMenu;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class EditorImageButton extends JButton implements ActionListener, Serializable, MouseListener {

  Map<String, Object> options;

  public Map<String, Object> getOptions() {
    return options;
  }

  public String getFilename() {
    return ((File) options.get(IButton.FILE)).getAbsolutePath();
  }

  int MAXHEIGHT = getThumbHeight();

  private static int getThumbHeight() {
    try {
      return PreferencesUser.get().getDefaultThumbHeight();
    } catch (Exception e) {
      return 50; // fallback
    }
  }

  BufferedImage thumbnail;

  public BufferedImage getThumbnail() {
    return thumbnail;
  }

  public EditorImageButton() {
  }

  public EditorImageButton(Map<String, Object> options) {
    this.options = options;
    thumbnail = createThumbnailImage((File) this.options.get(IButton.FILE), MAXHEIGHT);

    init();
  }

  public EditorImageButton(File imgFile) {
    thumbnail = createThumbnailImage(imgFile, MAXHEIGHT);
    options = new HashMap<>();
    options.put(IButton.FILE, imgFile);
    options.put(IButton.TEXT, "\"" + imgFile.getName() + "\"");

    init();
  }

  public EditorImageButton(Pattern pattern) {
    thumbnail = createThumbnailImage(pattern, MAXHEIGHT);
    options = new HashMap<>();
    File imgFile = pattern.getImage().file();
    options.put(IButton.FILE, imgFile);
    options.put(IButton.TEXT, "\"" + imgFile.getName() + "\"");
    options.put(IButton.PATT, pattern);

    init();
  }

  private void init() {
    setIcon(new ImageIcon(thumbnail));
    setButtonText();

    setMargin(new Insets(0, 0, 0, 0));
    setBorderPainted(false);
    setContentAreaFilled(false);
    setOpaque(false);
    setFocusPainted(false);
    setCursor(new Cursor(Cursor.HAND_CURSOR));
    addActionListener(this);
    addMouseListener(this);
  }

  // When the LaF changes (theme toggle), the new ButtonUI resets contentAreaFilled
  // and opaque back to defaults, which makes FlatLightLaf paint an opaque white
  // background over the icon. Keep the button fully transparent so only the cached
  // thumbnail + custom paint() overlay are rendered (issue #165).
  @Override
  public void updateUI() {
    super.updateUI();
    if (thumbnail != null) {
      setIcon(new ImageIcon(thumbnail));
    }
    setMargin(new Insets(0, 0, 0, 0));
    setBorderPainted(false);
    setContentAreaFilled(false);
    setOpaque(false);
    setFocusPainted(false);
  }

  // Build a fresh clone of this button carrying the same visible state but
  // with a fresh ButtonUI installed under the active LaF. Called by
  // EditorPane.afterThemeChange() to replace every embedded image button
  // after a theme toggle so the Flat ButtonUI defaults don't leave the
  // thumbnails invisible or un-clickable.
  public EditorImageButton cloneForRefresh(EditorPane pane) {
    if (options == null) return null;
    Object patt = options.get(IButton.PATT);
    if (patt instanceof Pattern) return new EditorImageButton((Pattern) patt);
    Object file = options.get(IButton.FILE);
    if (file instanceof File) return new EditorImageButton((File) file);
    return null;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    final EditorImageButton source = (EditorImageButton) e.getSource();
    handlePopup(null);
  }

  private boolean closeIfVisible(SXDialog popup) {
    if (popup != null && popup.isVisible()) {
      popup.closeCancel();
      return true;
    }
    return false;
  }

  SXDialogPaneImageMenu popmenu = null;

  private void handlePopup(MouseEvent me) {
    if (closeIfVisible(popmenu)) {
      return;
    }
    closeIfVisible(popwin);
    if (me == null) {
      handlePreview();
    } else {
      Point where = getLocationOnScreen();
      where.y += MAXHEIGHT + 10;

      popmenu = new SXDialogPaneImageMenu(where,
          new String[]{"image"}, options.get(IButton.FILE), this);
      popmenu.run();
    }
  }

  private SXDialogPaneImage popwin = null;

  private void handlePreview() {
    Point where = getLocationOnScreen();
    popwin = new SXDialogPaneImage(where, new String[]{"image"}, options.get(IButton.FILE), this);
    popwin.run();
  }

  @Override
  public Point getLocationOnScreen() {
    return super.getLocationOnScreen();
  }

  @Override
  public void mouseEntered(MouseEvent me) {
    showPreviewPopup(true);
  }

  @Override
  public void mouseExited(MouseEvent me) {
    showPreviewPopup(false);
  }

  private JFrame previewPopup;
  private JLabel previewModifierLabel;

  /**
   * Recreate the SikuliX 2.0.5 hover preview: a borderless always-on-top
   * window that shows the captured image at full size with the parsed
   * Pattern signature underneath. Invoked on mouse-entered, hidden on
   * mouse-exited. The signature line is re-extracted from the document
   * every time the popup is shown so it stays accurate when the user
   * edits chained {@code .similar()} or {@code .targetOffset()} arguments.
   */
  private void showPreviewPopup(boolean show) {
    if (!show) {
      if (previewPopup != null) {
        previewPopup.setVisible(false);
      }
      return;
    }
    if (options == null || options.get(IButton.FILE) == null) {
      return;
    }
    File imgFile = (File) options.get(IButton.FILE);
    BufferedImage img;
    try {
      img = ImageIO.read(imgFile);
    } catch (IOException e) {
      return;
    }
    if (img == null) {
      return;
    }
    if (previewPopup == null) {
      previewPopup = new JFrame();
      previewPopup.setAlwaysOnTop(true);
      previewPopup.setUndecorated(true);
      previewPopup.setResizable(false);
      previewPopup.setFocusableWindowState(false);
      previewPopup.setBackground(Color.WHITE);
      Container p = previewPopup.getContentPane();
      p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
      p.setBackground(Color.WHITE);
      JLabel imgLbl = new JLabel(new ImageIcon(img));
      imgLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
      imgLbl.setBorder(BorderFactory.createLineBorder(new Color(0, 128, 128), 1));
      p.add(imgLbl);
      previewModifierLabel = new JLabel(" ");
      previewModifierLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
      previewModifierLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
      previewModifierLabel.setForeground(Color.DARK_GRAY);
      p.add(previewModifierLabel);
    }
    previewModifierLabel.setText(buildSignatureFromContext());
    previewPopup.pack();
    Point btn = getLocationOnScreen();
    Rectangle screen = getGraphicsConfiguration().getBounds();
    Point at = new Point();
    if (btn.y - screen.y < screen.height / 2) {
      at.y = btn.y + getHeight() + 3;
    } else {
      at.y = btn.y - 3 - previewPopup.getHeight();
    }
    if (btn.x - screen.x < screen.width / 2) {
      at.x = btn.x;
    } else {
      at.x = btn.x - previewPopup.getWidth() + getWidth();
    }
    previewPopup.setLocation(at);
    previewPopup.setVisible(true);
  }

  /**
   * Reconstruct the {@code Pattern("name.png").similar(X).targetOffset(X,Y)}
   * call from the source code surrounding this button, so the hover preview
   * stays informative when the original Pattern() has chained modifiers.
   * Falls back to bare {@code Pattern("name.png")} when the button is not
   * yet attached to a JTextPane (palette / drag preview / detached state).
   */
  private String buildSignatureFromContext() {
    String name = info();
    String sig = "Pattern(\"" + name + ".png\")";
    Container ancestor = SwingUtilities.getAncestorOfClass(JTextPane.class, this);
    if (!(ancestor instanceof JTextPane)) {
      return sig;
    }
    JTextPane pane = (JTextPane) ancestor;
    javax.swing.text.StyledDocument doc = pane.getStyledDocument();
    int myOffset = -1;
    int i = 0;
    while (i < doc.getLength()) {
      javax.swing.text.Element el = doc.getCharacterElement(i);
      Component comp = javax.swing.text.StyleConstants.getComponent(el.getAttributes());
      if (comp == this) {
        myOffset = el.getStartOffset();
        break;
      }
      i = Math.max(el.getEndOffset(), i + 1);
    }
    if (myOffset < 0) {
      return sig;
    }
    String after;
    try {
      javax.swing.text.Element para = doc.getParagraphElement(myOffset);
      int from = myOffset + 1;
      int len = Math.max(0, para.getEndOffset() - from);
      after = doc.getText(from, len);
    } catch (javax.swing.text.BadLocationException e) {
      return sig;
    }
    StringBuilder sb = new StringBuilder(sig);
    java.util.regex.Matcher mSim = SIMILAR_PAT.matcher(after);
    if (mSim.find()) {
      sb.append(".similar(").append(mSim.group(1)).append(")");
    }
    java.util.regex.Matcher mOff = OFFSET_PAT.matcher(after);
    if (mOff.find()) {
      sb.append(".targetOffset(")
          .append(mOff.group(1)).append(",").append(mOff.group(2)).append(")");
    }
    return sb.toString();
  }

  private static final java.util.regex.Pattern SIMILAR_PAT =
      java.util.regex.Pattern.compile("\\.similar\\s*\\(\\s*([0-9]*\\.?[0-9]+)\\s*\\)");
  private static final java.util.regex.Pattern OFFSET_PAT =
      java.util.regex.Pattern.compile("\\.targetOffset\\s*\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\)");

  @Override
  public void mousePressed(MouseEvent me) {
    if (me.isPopupTrigger()) {
      handlePopup(me);
    }
  }

  @Override
  public void mouseReleased(MouseEvent me) {
    if (me.isPopupTrigger()) {
      handlePopup(me);
    }
  }

  @Override
  public void mouseClicked(MouseEvent me) {
  }

  private BufferedImage createThumbnailImage(Pattern pattern, int maxHeight) {
    //TODO Pattern thumbnail
    return createThumbnailImage(pattern.getImage().file(), maxHeight);
  }

  private BufferedImage createThumbnailImage(File imgFile, int maxHeight) {
    BufferedImage img = null;
    try {
      img = ImageIO.read(imgFile);
    } catch (IOException e) {
    }
    if (img == null) {
      try {
        img = ImageIO.read(SikulixIDE.class.getResource("/icons/sxcapture.png"));
      } catch (Exception e) {
        RunTime.terminate(999, "EditorImageButton: createThumbnailImage: possible? %s", e.getMessage());
      }
    }
    int w = img.getWidth();
    int h = img.getHeight();
    if (maxHeight == 0 || maxHeight >= h) {
      return img;
    }
    float _scale = (float) maxHeight / h;
    w *= _scale;
    h *= _scale;
    h = (int) h;
    // Use ARGB so the thumbnail has an alpha channel and survives LaF changes
    // (opaque TYPE_INT_RGB caused icons to vanish visually after a theme toggle - issue #165).
    BufferedImage thumb = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = thumb.createGraphics();
    g2d.drawImage(img, 0, 0, w, h, null);
    g2d.dispose();
    return thumb;
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);
    Graphics2D g2d = (Graphics2D) g;
    g2d.setColor(new Color(0, 128, 128, 128));
    g2d.drawRoundRect(3, 3, getWidth() - 7, getHeight() - 7, 5, 5);
  }

  @Override
  public String toString() {
    if (options == null || options.get(IButton.TEXT) == null) {
      return "";
    }
    return (String) options.get(IButton.TEXT);
  }

  public String info() {
    if (options == null || options.get(IButton.FILE) == null) {
      return "";
    }
    final String name = FilenameUtils.getBaseName(((File) options.get(IButton.FILE)).getAbsolutePath());
    return String.format("%s", name);
  }

  void setButtonText() {
    if (options != null && options.get(IButton.FILE) != null) {
      setToolTipText(((File) options.get(IButton.FILE)).getName());
    } else {
      setToolTipText(info());
    }
  }

  public static void renameImage(String name, Map<String, Object> options) {
    Commons.startLog(3, "renameImage: called name=%s keys=%s", name, options.keySet());
    if (name == null || name.trim().isEmpty()) {
      Commons.startLog(3, "renameImage: empty name, skip");
      return;
    }
    // The dialog (SXDialogPaneImage) builds its own options map using the
    // string "image" as key (see SXDialog.setOptions + the call site at
    // EditorImageButton:171 which passes new String[]{"image"} as the key
    // array). EditorImageButton internally uses IButton.FILE = "FILE".
    // Try both so we work regardless of which caller invoked us.
    File oldFile = (File) options.get("image");
    if (oldFile == null) {
      oldFile = (File) options.get(IButton.FILE);
    }
    if (oldFile == null) {
      Commons.error("renameImage: no source file in options (keys=%s)",
          options.keySet());
      return;
    }
    Commons.startLog(3, "renameImage: oldFile=%s exists=%s", oldFile, oldFile.exists());
    if (!oldFile.exists()) {
      Commons.error("renameImage: source file does not exist on disk: %s", oldFile);
      return;
    }
    String newBaseName = name.trim();
    if (!newBaseName.toLowerCase().endsWith(".png")
        && !newBaseName.toLowerCase().endsWith(".jpg")
        && !newBaseName.toLowerCase().endsWith(".jpeg")) {
      newBaseName += "." + FilenameUtils.getExtension(oldFile.getName()).toLowerCase();
    }
    File newFile = new File(oldFile.getParentFile(), newBaseName);
    Commons.startLog(3, "renameImage: %s -> %s", oldFile.getName(), newFile.getName());
    if (newFile.equals(oldFile)) {
      Commons.startLog(3, "renameImage: same name, skip");
      return;
    }
    boolean overwritten = newFile.exists();
    try {
      java.nio.file.Files.move(oldFile.toPath(), newFile.toPath(),
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      Commons.startLog(3, "renameImage: move OK, newFile exists=%s", newFile.exists());
    } catch (IOException e) {
      Commons.error("renameImage: cannot move %s -> %s: %s",
          oldFile, newFile, e.getMessage());
      return;
    }
    // Update the dialog's own map (caller may inspect it after this returns).
    options.put(IButton.FILE, newFile);
    options.put("image", newFile);
    // The dialog stashed the originating EditorImageButton instance in its
    // options under "parm1" (cf. SXDialogPaneImage construction at line 171
    // and SXDialog.setOptions filling unnamed parms as "parmN"). The button's
    // own internal `options` map is a SEPARATE Map from the dialog's — without
    // mirroring the rename onto it, the next save would still serialize the
    // old filename and the tooltip would still show the old name.
    Object btnObj = options.get("parm1");
    if (btnObj instanceof EditorImageButton) {
      EditorImageButton btn = (EditorImageButton) btnObj;
      btn.options.put(IButton.FILE, newFile);
      // IButton.TEXT carries the textual form ("filename.png" with quotes)
      // that the button serializes back to the script when the document is
      // saved. Without this update the next File ▸ Save writes the OLD name
      // even though the .png on disk is now the NEW name.
      btn.options.put(IButton.TEXT, "\"" + newFile.getName() + "\"");
      btn.setButtonText();
      btn.repaint();
      Commons.startLog(3, "renameImage: button instance updated (tooltip + serialization)");
    } else {
      Commons.startLog(3, "renameImage: no button instance in options.parm1 (kept dialog-only update)");
    }
    SikulixIDE.get().reparseOnRenameImage(oldFile.getAbsolutePath(),
        newFile.getAbsolutePath(), overwritten);
    Commons.startLog(3, "renameImage: reparseOnRenameImage called");
  }

  //imgBtn.setImage(filename);
  public void setImage(String fname) {

  }

  //imgBtn.setParameters(
  //						_screenshot.isExact(), _screenshot.getSimilarity(),
  //						_screenshot.getNumMatches()));
  public boolean setParameters(boolean exact, double sim, int numM) {
    return true;
  }

  //imgBtn.setTargetOffset(_tarOffsetPane.getTargetOffset()))
  public boolean setTargetOffset(Location offset) {
    return true;
  }

  //imgBtn.getWindow()
  public PatternWindow getWindow() {
    return null;
  }

  //imgBtn.resetParameters()
  public void resetParameters() {

  }
}
