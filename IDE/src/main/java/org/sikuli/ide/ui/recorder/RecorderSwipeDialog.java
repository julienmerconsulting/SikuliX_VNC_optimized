/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui.recorder;

import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Modal dialog for configuring a Swipe action over a captured zone.
 * Shows the captured image, 4 direction arrows, and a distance slider.
 * Generates a 2-line Jython snippet using dragDrop().
 */
public class RecorderSwipeDialog extends JDialog {

  private final BufferedImage capture;
  private final String imageName;

  private JToggleButton btnUp, btnDown, btnLeft, btnRight;
  private JSlider distanceSlider;
  private JLabel distanceLabel;
  private ImagePanel imagePanel;
  private JLabel previewLabel;
  private JButton okBtn, cancelBtn;

  private String direction = "Down";
  private int distance = 200;
  private int startOffsetX = 0;
  private int startOffsetY = 0;
  private String result = null;
  private JLabel startLabel;

  public RecorderSwipeDialog(Dialog parent, BufferedImage capture, String imagePath) {
    super(parent, "Swipe Configuration", true);
    this.capture = capture;
    this.imageName = new File(imagePath).getName();
    setResizable(false);
    buildUI();
    updatePreview();
    pack();
    setLocationRelativeTo(parent);
  }

  private void buildUI() {
    JPanel content = new JPanel(new MigLayout("wrap 1, insets 16, gap 8", "[grow, fill]", ""));
    content.setBackground(UIManager.getColor("Panel.background"));

    // Image preview with swipe arrow overlay
    JLabel lblImg = new JLabel("Swipe zone (click to set start position, default is center)");
    lblImg.setFont(UIManager.getFont("small.font"));
    lblImg.setForeground(UIManager.getColor("Label.disabledForeground"));
    content.add(lblImg);

    imagePanel = new ImagePanel();
    int minW = 380, minH = 240, maxW = 1200, maxH = 800;
    int imgW = capture.getWidth(), imgH = capture.getHeight();
    double scale;
    if (imgW < minW || imgH < minH) {
      scale = Math.max((double) minW / imgW, (double) minH / imgH);
    } else if (imgW > maxW || imgH > maxH) {
      scale = Math.min((double) maxW / imgW, (double) maxH / imgH);
    } else {
      scale = 1.0;
    }
    imagePanel.setPreferredSize(new Dimension((int) (imgW * scale), (int) (imgH * scale)));
    imagePanel.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1));
    content.add(imagePanel, "align center");

    startLabel = new JLabel("Start: center of pattern");
    startLabel.setFont(UIManager.getFont("small.font"));
    startLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
    content.add(startLabel);

    content.add(new JSeparator(), "growx, gaptop 4");

    // Direction buttons
    JLabel lblDir = new JLabel("Direction");
    lblDir.setFont(UIManager.getFont("small.font"));
    lblDir.setForeground(UIManager.getColor("Label.disabledForeground"));
    content.add(lblDir);

    JPanel dirPad = new JPanel(new MigLayout("insets 0, gap 6", "[grow, center]", ""));
    dirPad.setOpaque(false);

    JPanel arrows = new JPanel(new GridLayout(3, 3, 4, 4));
    arrows.setOpaque(false);
    btnUp    = makeDirBtn("\u25B2", "Up");
    btnDown  = makeDirBtn("\u25BC", "Down");
    btnLeft  = makeDirBtn("\u25C0", "Left");
    btnRight = makeDirBtn("\u25B6", "Right");
    ButtonGroup g = new ButtonGroup();
    g.add(btnUp); g.add(btnDown); g.add(btnLeft); g.add(btnRight);
    btnDown.setSelected(true);

    arrows.add(new JLabel()); arrows.add(btnUp);   arrows.add(new JLabel());
    arrows.add(btnLeft);      arrows.add(new JLabel()); arrows.add(btnRight);
    arrows.add(new JLabel()); arrows.add(btnDown); arrows.add(new JLabel());
    dirPad.add(arrows);
    content.add(dirPad);

    // Distance slider
    JLabel lblDist = new JLabel("Distance");
    lblDist.setFont(UIManager.getFont("small.font"));
    lblDist.setForeground(UIManager.getColor("Label.disabledForeground"));
    content.add(lblDist, "gaptop 6");

    distanceSlider = new JSlider(20, 1000, distance);
    distanceSlider.setMajorTickSpacing(200);
    distanceSlider.setMinorTickSpacing(50);
    distanceSlider.setPaintTicks(true);
    distanceSlider.addChangeListener(e -> {
      distance = distanceSlider.getValue();
      updatePreview();
      imagePanel.repaint();
    });
    content.add(distanceSlider, "growx");

    distanceLabel = new JLabel(distance + " px");
    distanceLabel.setFont(UIManager.getFont("small.font"));
    distanceLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
    content.add(distanceLabel, "align right");

    content.add(new JSeparator(), "growx, gaptop 4");

    // Preview
    JLabel lblPrev = new JLabel("Generated code");
    lblPrev.setFont(UIManager.getFont("small.font"));
    lblPrev.setForeground(UIManager.getColor("Label.disabledForeground"));
    content.add(lblPrev);

    previewLabel = new JLabel(" ");
    previewLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    previewLabel.setForeground(new Color(0x00, 0xA8, 0x9D));
    previewLabel.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1),
        BorderFactory.createEmptyBorder(8, 10, 8, 10)));
    content.add(previewLabel, "growx");

    // Buttons
    JPanel buttons = new JPanel(new MigLayout("insets 0, gap 8", "push[][]", ""));
    buttons.setOpaque(false);
    cancelBtn = new JButton("Cancel");
    cancelBtn.addActionListener(e -> { result = null; dispose(); });
    okBtn = new JButton("OK");
    okBtn.putClientProperty("JButton.buttonType", "default");
    okBtn.addActionListener(e -> { result = buildCode(); dispose(); });
    buttons.add(cancelBtn);
    buttons.add(okBtn);
    content.add(buttons, "gaptop 8, align right");

    setContentPane(content);
    getRootPane().setDefaultButton(okBtn);
  }

  private JToggleButton makeDirBtn(String symbol, String dir) {
    JToggleButton b = new JToggleButton(symbol);
    b.setFont(new Font(Font.DIALOG, Font.BOLD, 16));
    b.setPreferredSize(new Dimension(48, 40));
    b.addActionListener(e -> {
      direction = dir;
      updatePreview();
      imagePanel.repaint();
    });
    return b;
  }

  /**
   * Returns the two generated lines (one per entry), or null if cancelled.
   */
  public String[] getResultLines() {
    if (result == null) return null;
    return result.split("\n");
  }

  public String getResult() { return result; }

  private String buildCode() {
    String method;
    switch (direction) {
      case "Up":    method = "above";  break;
      case "Left":  method = "left";   break;
      case "Right": method = "right";  break;
      default:      method = "below";  break;
    }
    String startExpr;
    if (startOffsetX == 0 && startOffsetY == 0) {
      startExpr = "find(\"" + imageName + "\").getCenter()";
    } else {
      startExpr = "find(\"" + imageName + "\").getCenter().offset(" + startOffsetX + ", " + startOffsetY + ")";
    }
    return "_start = " + startExpr + "\n"
         + "dragDrop(_start, _start." + method + "(" + distance + "))";
  }

  private void updatePreview() {
    previewLabel.setText("<html>" + buildCode().replace("\n", "<br>") + "</html>");
    distanceLabel.setText(distance + " px");
    if (startOffsetX == 0 && startOffsetY == 0) {
      startLabel.setText("Start: center of pattern");
    } else {
      startLabel.setText("Start: offset (" + startOffsetX + ", " + startOffsetY + ") from center");
    }
  }

  /**
   * Disable direction buttons that would send the swipe off the pattern.
   * If the start is in the left quarter, LEFT is disabled, etc.
   * Auto-switches to a valid direction if the current one gets disabled.
   */
  private void refreshDirectionAvailability() {
    int imgW = capture.getWidth();
    int imgH = capture.getHeight();
    int quarterW = imgW / 4;
    int quarterH = imgH / 4;

    boolean canLeft  = startOffsetX > -quarterW;
    boolean canRight = startOffsetX <  quarterW;
    boolean canUp    = startOffsetY > -quarterH;
    boolean canDown  = startOffsetY <  quarterH;

    btnLeft.setEnabled(canLeft);
    btnRight.setEnabled(canRight);
    btnUp.setEnabled(canUp);
    btnDown.setEnabled(canDown);

    // If the current direction got disabled, auto-pick a valid one
    boolean currentValid =
        ("Left".equals(direction)  && canLeft)  ||
        ("Right".equals(direction) && canRight) ||
        ("Up".equals(direction)    && canUp)    ||
        ("Down".equals(direction)  && canDown);
    if (!currentValid) {
      if (canDown)       { direction = "Down";  btnDown.setSelected(true);  }
      else if (canUp)    { direction = "Up";    btnUp.setSelected(true);    }
      else if (canRight) { direction = "Right"; btnRight.setSelected(true); }
      else if (canLeft)  { direction = "Left";  btnLeft.setSelected(true);  }
    }
  }

  private class ImagePanel extends JPanel {

    private double scale;
    private int drawOffsetX, drawOffsetY, drawWidth, drawHeight;

    ImagePanel() {
      setBackground(Color.DARK_GRAY);
      setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

      java.awt.event.MouseAdapter handler = new java.awt.event.MouseAdapter() {
        @Override public void mousePressed(java.awt.event.MouseEvent e) { setStart(e.getX(), e.getY()); }
        @Override public void mouseDragged(java.awt.event.MouseEvent e) { setStart(e.getX(), e.getY()); }
      };
      addMouseListener(handler);
      addMouseMotionListener(handler);
    }

    private void setStart(int px, int py) {
      // Clamp to image area
      if (drawWidth <= 0 || drawHeight <= 0) return;
      px = Math.max(drawOffsetX, Math.min(drawOffsetX + drawWidth, px));
      py = Math.max(drawOffsetY, Math.min(drawOffsetY + drawHeight, py));

      int imgCx = drawOffsetX + drawWidth / 2;
      int imgCy = drawOffsetY + drawHeight / 2;
      startOffsetX = (int) Math.round((px - imgCx) / scale);
      startOffsetY = (int) Math.round((py - imgCy) / scale);
      refreshDirectionAvailability();
      updatePreview();
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (capture == null) return;

      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
          RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
          RenderingHints.VALUE_ANTIALIAS_ON);

      int panelW = getWidth(), panelH = getHeight();
      int imgW = capture.getWidth(), imgH = capture.getHeight();
      scale = Math.min((double) panelW / imgW, (double) panelH / imgH);
      drawWidth  = (int) (imgW * scale);
      drawHeight = (int) (imgH * scale);
      drawOffsetX = (panelW - drawWidth) / 2;
      drawOffsetY = (panelH - drawHeight) / 2;

      g2.drawImage(capture, drawOffsetX, drawOffsetY, drawWidth, drawHeight, null);

      // Starting point: center + user offset (in screen coordinates)
      int sx = drawOffsetX + drawWidth / 2 + (int) (startOffsetX * scale);
      int sy = drawOffsetY + drawHeight / 2 + (int) (startOffsetY * scale);

      // Arrow direction scaled to display, capped so it stays inside
      int maxReach = (int) (Math.min(drawWidth, drawHeight) * 0.45);
      int displayDistance = (int) Math.min(maxReach, distance * scale);
      int dx = 0, dy = 0;
      switch (direction) {
        case "Up":    dy = -displayDistance; break;
        case "Down":  dy =  displayDistance; break;
        case "Left":  dx = -displayDistance; break;
        case "Right": dx =  displayDistance; break;
      }

      Color teal = new Color(0x00, 0xA8, 0x9D);
      g2.setColor(teal);
      g2.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g2.drawLine(sx, sy, sx + dx, sy + dy);
      drawArrowHead(g2, sx, sy, sx + dx, sy + dy);

      // Starting dot
      g2.fillOval(sx - 5, sy - 5, 10, 10);
      g2.dispose();
    }

    private void drawArrowHead(Graphics2D g2, int x1, int y1, int x2, int y2) {
      int size = 10;
      double angle = Math.atan2(y2 - y1, x2 - x1);
      int ax1 = (int) (x2 - size * Math.cos(angle - Math.PI / 6));
      int ay1 = (int) (y2 - size * Math.sin(angle - Math.PI / 6));
      int ax2 = (int) (x2 - size * Math.cos(angle + Math.PI / 6));
      int ay2 = (int) (y2 - size * Math.sin(angle + Math.PI / 6));
      int[] xs = {x2, ax1, ax2};
      int[] ys = {y2, ay1, ay2};
      g2.fillPolygon(xs, ys, 3);
    }
  }
}
