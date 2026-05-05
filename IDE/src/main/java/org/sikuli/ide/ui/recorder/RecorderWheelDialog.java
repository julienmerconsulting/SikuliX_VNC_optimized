/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui.recorder;

import net.miginfocom.swing.MigLayout;
import org.sikuli.support.ide.SikuliIDEI18N;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Modal dialog for configuring a Wheel action. Shows the captured image
 * with an interactive crosshair for offset selection, direction radio,
 * step count spinner and a live code preview.
 */
public class RecorderWheelDialog extends JDialog {

  private final BufferedImage capture;
  private final String imageName;

  private JRadioButton rbUp, rbDown;
  private JSpinner spinnerSteps;
  private ImagePanel imagePanel;
  private JLabel offsetLabel;
  private JLabel previewLabel;
  private JButton okBtn, cancelBtn;

  private int offsetX = 0;
  private int offsetY = 0;
  private String result = null;

  public RecorderWheelDialog(Dialog parent, BufferedImage capture, String imagePath) {
    super(parent, SikuliIDEI18N._I("recorder.wheel.title"), true);
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

    // Image preview with crosshair
    JLabel lblImg = new JLabel(SikuliIDEI18N._I("recorder.wheel.lblImg"));
    lblImg.setFont(UIManager.getFont("small.font"));
    lblImg.setForeground(UIManager.getColor("Label.disabledForeground"));
    content.add(lblImg);

    imagePanel = new ImagePanel();
    // Size the preview to match the captured image, with a minimum of 380x240
    // and a maximum of 1200x800. Small images are scaled up, large images are scaled down.
    int minW = 380;
    int minH = 240;
    int maxW = 1200;
    int maxH = 800;
    int imgW = capture.getWidth();
    int imgH = capture.getHeight();
    double scaleUp = Math.max((double) minW / imgW, (double) minH / imgH);
    double scaleDown = Math.min((double) maxW / imgW, (double) maxH / imgH);
    double scale;
    if (imgW < minW || imgH < minH) {
      scale = scaleUp; // Image too small, scale up
    } else if (imgW > maxW || imgH > maxH) {
      scale = scaleDown; // Image too big, scale down
    } else {
      scale = 1.0; // Image fits naturally
    }
    int previewW = (int) (imgW * scale);
    int previewH = (int) (imgH * scale);
    imagePanel.setPreferredSize(new Dimension(previewW, previewH));
    imagePanel.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1));
    content.add(imagePanel, "align center");

    offsetLabel = new JLabel(SikuliIDEI18N._I("recorder.wheel.offsetCentered"));
    offsetLabel.setFont(UIManager.getFont("small.font"));
    offsetLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
    content.add(offsetLabel);

    content.add(new JSeparator(), "growx, gaptop 4");

    // Direction
    JPanel dirPanel = new JPanel(new MigLayout("insets 0, gap 12", "[][]"));
    dirPanel.setOpaque(false);
    JLabel lblDir = new JLabel(SikuliIDEI18N._I("recorder.wheel.lblDir"));
    lblDir.setFont(UIManager.getFont("defaultFont"));
    dirPanel.add(lblDir);
    rbDown = new JRadioButton("Down", true);
    rbUp = new JRadioButton("Up");
    ButtonGroup grp = new ButtonGroup();
    grp.add(rbDown);
    grp.add(rbUp);
    rbDown.addActionListener(e -> updatePreview());
    rbUp.addActionListener(e -> updatePreview());
    JPanel radios = new JPanel(new MigLayout("insets 0, gap 8"));
    radios.setOpaque(false);
    radios.add(rbDown);
    radios.add(rbUp);
    dirPanel.add(radios);
    content.add(dirPanel);

    // Steps
    JPanel stepsPanel = new JPanel(new MigLayout("insets 0, gap 12", "[][60]"));
    stepsPanel.setOpaque(false);
    JLabel lblSteps = new JLabel(SikuliIDEI18N._I("recorder.wheel.lblSteps"));
    stepsPanel.add(lblSteps);
    spinnerSteps = new JSpinner(new SpinnerNumberModel(3, 1, 50, 1));
    spinnerSteps.addChangeListener(e -> updatePreview());
    stepsPanel.add(spinnerSteps);
    content.add(stepsPanel);

    content.add(new JSeparator(), "growx, gaptop 4");

    // Code preview
    JLabel lblPrev = new JLabel(SikuliIDEI18N._I("recorder.wheel.lblPreview"));
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
    cancelBtn = new JButton(SikuliIDEI18N._I("recorder.wheel.btnCancel"));
    cancelBtn.addActionListener(e -> { result = null; dispose(); });
    okBtn = new JButton(SikuliIDEI18N._I("recorder.wheel.btnOk"));
    okBtn.putClientProperty("JButton.buttonType", "default");
    okBtn.addActionListener(e -> { result = buildCode(); dispose(); });
    buttons.add(cancelBtn);
    buttons.add(okBtn);
    content.add(buttons, "gaptop 8, align right");

    setContentPane(content);
    getRootPane().setDefaultButton(okBtn);
  }

  private String buildCode() {
    int direction = rbDown.isSelected() ? 1 : -1;
    int steps = (Integer) spinnerSteps.getValue();
    String directionStr = direction > 0 ? "WHEEL_DOWN" : "WHEEL_UP";

    String patternExpr;
    if (offsetX == 0 && offsetY == 0) {
      patternExpr = "\"" + imageName + "\"";
    } else {
      patternExpr = "Pattern(\"" + imageName + "\").targetOffset(" + offsetX + ", " + offsetY + ")";
    }
    return "wheel(" + patternExpr + ", " + directionStr + ", " + steps + ")";
  }

  private void updatePreview() {
    previewLabel.setText(buildCode());
    if (offsetX == 0 && offsetY == 0) {
      offsetLabel.setText(SikuliIDEI18N._I("recorder.wheel.offsetCustomCentered", offsetX, offsetY));
    } else {
      offsetLabel.setText(SikuliIDEI18N._I("recorder.wheel.offsetCustom", offsetX, offsetY));
    }
  }

  public String getResult() {
    return result;
  }

  /**
   * Custom panel that displays the captured image and a movable crosshair.
   */
  private class ImagePanel extends JPanel {

    private int crosshairX, crosshairY;
    private double scale;
    private int drawOffsetX, drawOffsetY;
    private int drawWidth, drawHeight;

    ImagePanel() {
      setBackground(Color.DARK_GRAY);

      MouseAdapter handler = new MouseAdapter() {
        @Override public void mousePressed(MouseEvent e)  { updateCrosshair(e.getX(), e.getY()); }
        @Override public void mouseDragged(MouseEvent e)  { updateCrosshair(e.getX(), e.getY()); }
      };
      addMouseListener(handler);
      addMouseMotionListener(handler);
    }

    private void updateCrosshair(int px, int py) {
      // Clamp to image area
      px = Math.max(drawOffsetX, Math.min(drawOffsetX + drawWidth, px));
      py = Math.max(drawOffsetY, Math.min(drawOffsetY + drawHeight, py));
      crosshairX = px;
      crosshairY = py;

      // Convert screen coords to image offset relative to center
      if (drawWidth > 0 && drawHeight > 0 && capture != null) {
        int imgCx = drawOffsetX + drawWidth / 2;
        int imgCy = drawOffsetY + drawHeight / 2;
        offsetX = (int) Math.round((px - imgCx) / scale);
        offsetY = (int) Math.round((py - imgCy) / scale);
      }
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

      int panelW = getWidth();
      int panelH = getHeight();
      int imgW = capture.getWidth();
      int imgH = capture.getHeight();

      scale = Math.min((double) panelW / imgW, (double) panelH / imgH);
      drawWidth = (int) (imgW * scale);
      drawHeight = (int) (imgH * scale);
      drawOffsetX = (panelW - drawWidth) / 2;
      drawOffsetY = (panelH - drawHeight) / 2;

      g2.drawImage(capture, drawOffsetX, drawOffsetY, drawWidth, drawHeight, null);

      // If no crosshair set yet, place it at center
      if (crosshairX == 0 && crosshairY == 0) {
        crosshairX = drawOffsetX + drawWidth / 2;
        crosshairY = drawOffsetY + drawHeight / 2;
      }

      // Draw crosshair
      g2.setColor(new Color(0x00, 0xA8, 0x9D));
      g2.setStroke(new BasicStroke(2));
      g2.drawLine(crosshairX - 10, crosshairY, crosshairX + 10, crosshairY);
      g2.drawLine(crosshairX, crosshairY - 10, crosshairX, crosshairY + 10);
      g2.drawOval(crosshairX - 6, crosshairY - 6, 12, 12);

      g2.dispose();
    }
  }
}
