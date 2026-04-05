/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

import static org.sikuli.support.ide.SikuliIDEI18N._I;

/**
 * Vertical sidebar with navigation submenus and live info panels.
 */
public class OculixSidebar extends JPanel {

  private boolean collapsed = false;
  private int collapsedWidth = 50;

  // Navigation items
  private SidebarItem navFile;
  private SidebarItem navEdit;
  private SidebarItem navRun;
  private SidebarItem navTools;
  private SidebarItem navHelp;

  // Theme toggle
  private SidebarItem btnTheme;
  private boolean isDark = true;

  // Version label
  private JLabel versionLabel;

  // Info panels
  private JLabel projectName;
  private JLabel projectPath;
  private JLabel projectImages;
  private JLabel ocrPaddleStatus;
  private JLabel ocrTesseractStatus;
  private JLabel javaInfo;
  private JLabel lastRunResult;
  private JLabel lastRunTime;
  private JLabel lastRunDuration;

  // Layout panels
  private JPanel mainPanel;
  private JPanel footerPanel;

  public OculixSidebar() {
    setLayout(new BorderLayout());
    setPreferredSize(new Dimension(200, 0));
    setMinimumSize(new Dimension(200, 0));
    putClientProperty(FlatClientProperties.STYLE, "background: darken(@background, 3%)");

    mainPanel = new JPanel(new MigLayout("wrap 1, insets 8 10 8 10, gap 0", "[fill, grow]", ""));
    mainPanel.setOpaque(false);

    footerPanel = new JPanel(new MigLayout("wrap 1, insets 4 10 8 10, gap 2", "[fill, grow]", ""));
    footerPanel.setOpaque(false);

    JScrollPane scroll = new JScrollPane(mainPanel);
    scroll.setBorder(null);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.getVerticalScrollBar().setUnitIncrement(12);
    scroll.setOpaque(false);
    scroll.getViewport().setOpaque(false);

    add(scroll, BorderLayout.CENTER);
    add(footerPanel, BorderLayout.SOUTH);

    addResizeHandle();
  }

  /**
   * Initializes the full sidebar layout: logo, project info, nav, status, last run, help.
   */
  public void initNavItems() {
    // ── Logo Box ──
    JPanel logoBox = new JPanel(new MigLayout("wrap 1, insets 10 6 10 6, gap 0", "[center, fill]"));
    logoBox.setOpaque(true);
    logoBox.setBackground(UIManager.getColor("Panel.background"));
    logoBox.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1));

    JLabel logoLine1 = new JLabel("OculiX");
    logoLine1.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 28));
    logoLine1.setForeground(UIManager.getColor("Label.foreground"));
    logoLine1.setHorizontalAlignment(SwingConstants.CENTER);
    logoBox.add(logoLine1);

    JLabel logoLine2 = new JLabel("I D E");
    logoLine2.setFont(new Font("SansSerif", Font.PLAIN, 14));
    logoLine2.setForeground(UIManager.getColor("Label.disabledForeground"));
    logoLine2.setHorizontalAlignment(SwingConstants.CENTER);
    logoBox.add(logoLine2);

    mainPanel.add(logoBox, "growx, gapbottom 6");

    // ── Project Info Panel ──
    JPanel projectPanel = createInfoPanel();
    projectName = new JLabel("\u2014 No script open");
    projectName.setFont(UIManager.getFont("defaultFont").deriveFont(Font.BOLD, 12f));
    projectPanel.add(projectName, "span 2");
    projectPath = new JLabel("");
    projectPath.setFont(UIManager.getFont("small.font"));
    projectPath.setForeground(UIManager.getColor("Label.disabledForeground"));
    projectPanel.add(projectPath, "span 2");
    projectImages = new JLabel("");
    projectImages.setFont(UIManager.getFont("small.font"));
    projectImages.setForeground(UIManager.getColor("Label.disabledForeground"));
    projectPanel.add(projectImages, "span 2");
    mainPanel.add(projectPanel, "gaptop 4, gapbottom 6");

    // ── SCRIPT section ──
    addSectionHeader("SCRIPT");
    navFile = new SidebarItem(_I("menuFile") + "  \u25B8",
        loadIcon("/icons/insert-image-icon.png", 16));
    navFile.setMnemonic(java.awt.event.KeyEvent.VK_F);
    mainPanel.add(navFile);
    navEdit = new SidebarItem(_I("menuEdit") + "  \u25B8", null);
    navEdit.setMnemonic(java.awt.event.KeyEvent.VK_E);
    mainPanel.add(navEdit);
    navRun = new SidebarItem(_I("menuRun") + "  \u25B8",
        loadIcon("/icons/run_big_green.png", 16));
    navRun.setMnemonic(java.awt.event.KeyEvent.VK_R);
    mainPanel.add(navRun);

    // ── TOOLS section ──
    addSectionHeader("TOOLS");
    navTools = new SidebarItem(_I("menuTool") + "  \u25B8",
        loadIcon("/icons/capture-small.png", 16));
    navTools.setMnemonic(java.awt.event.KeyEvent.VK_T);
    mainPanel.add(navTools);

    // ── STATUS section ──
    addSectionHeader("STATUS");
    JPanel statusPanel = createInfoPanel();
    ocrPaddleStatus = createStatusLabel("\u2B24 PaddleOCR", statusPanel);
    ocrTesseractStatus = createStatusLabel("\u2B24 Tesseract", statusPanel);
    javaInfo = createStatusLabel("", statusPanel);
    mainPanel.add(statusPanel, "gapbottom 6");

    // ── LAST RUN section ──
    addSectionHeader("LAST RUN");
    JPanel lastRunPanel = createInfoPanel();
    lastRunResult = new JLabel("\u2014 Not run yet");
    lastRunResult.setFont(UIManager.getFont("defaultFont").deriveFont(Font.BOLD, 11f));
    lastRunPanel.add(lastRunResult, "span 2");
    lastRunDuration = new JLabel("");
    lastRunDuration.setFont(UIManager.getFont("small.font"));
    lastRunDuration.setForeground(UIManager.getColor("Label.disabledForeground"));
    lastRunPanel.add(lastRunDuration, "span 2");
    lastRunTime = new JLabel("");
    lastRunTime.setFont(UIManager.getFont("small.font"));
    lastRunTime.setForeground(UIManager.getColor("Label.disabledForeground"));
    lastRunPanel.add(lastRunTime, "span 2");
    mainPanel.add(lastRunPanel, "gapbottom 6");

    // ── HELP section ──
    addSectionHeader("HELP");
    navHelp = new SidebarItem(_I("menuHelp") + "  \u25B8", null);
    navHelp.setMnemonic(java.awt.event.KeyEvent.VK_H);
    mainPanel.add(navHelp);

    // Initial status check
    refreshOcrStatus();
  }

  private JPanel createInfoPanel() {
    JPanel panel = new JPanel(new MigLayout("wrap 2, insets 6, gap 2", "[fill, grow]", ""));
    panel.setOpaque(true);
    panel.setBackground(UIManager.getColor("Panel.background"));
    panel.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground"), 1),
        BorderFactory.createEmptyBorder(4, 6, 4, 6)));
    return panel;
  }

  private JLabel createStatusLabel(String text, JPanel parent) {
    JLabel label = new JLabel(text);
    label.setFont(UIManager.getFont("small.font"));
    parent.add(label, "span 2");
    return label;
  }

  private void addSectionHeader(String text) {
    JLabel header = new JLabel(text);
    header.setFont(UIManager.getFont("small.font"));
    header.setForeground(UIManager.getColor("Label.disabledForeground"));
    header.setBorder(BorderFactory.createEmptyBorder(10, 4, 3, 0));
    mainPanel.add(header);
  }

  // ── Public API for live updates ──

  /**
   * Updates the project info panel when a script is opened.
   */
  public void updateProjectInfo(String name, File scriptDir) {
    if (name == null || name.isEmpty()) {
      projectName.setText("\u2014 No script open");
      projectPath.setText("");
      projectImages.setText("");
      return;
    }
    projectName.setText("\uD83D\uDCC4 " + name);
    projectPath.setText(scriptDir != null ? truncatePath(scriptDir.getAbsolutePath(), 25) : "");
    projectPath.setToolTipText(scriptDir != null ? scriptDir.getAbsolutePath() : "");

    // Count images in script bundle
    int imgCount = 0;
    if (scriptDir != null && scriptDir.exists()) {
      File[] pngs = scriptDir.listFiles((dir, fn) -> fn.endsWith(".png"));
      if (pngs != null) imgCount = pngs.length;
    }
    projectImages.setText("\uD83D\uDDBC " + imgCount + " image" + (imgCount != 1 ? "s" : ""));
  }

  /**
   * Updates the last run result panel.
   */
  public void updateLastRun(int exitCode, long durationMs) {
    if (exitCode == 0) {
      lastRunResult.setText("\u2705 Passed");
      lastRunResult.setForeground(new Color(0x3D, 0xDB, 0xA4));
    } else {
      lastRunResult.setText("\u274C Failed (code " + exitCode + ")");
      lastRunResult.setForeground(new Color(0xFF, 0x6B, 0x6B));
    }
    lastRunDuration.setText("\u23F1 " + String.format("%.1fs", durationMs / 1000.0));
    lastRunTime.setText(new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new java.util.Date()));
  }

  /**
   * Refreshes OCR engine status indicators.
   */
  public void refreshOcrStatus() {
    // PaddleOCR
    boolean paddleOk = false;
    try {
      Class<?> engineClass = Class.forName("com.sikulix.ocr.PaddleOCREngine");
      Object engine = engineClass.getDeclaredConstructor().newInstance();
      paddleOk = (boolean) engineClass.getMethod("isAvailable").invoke(engine);
    } catch (Exception ignored) {}
    ocrPaddleStatus.setText("\u2B24 PaddleOCR" + (paddleOk ? "  OK" : ""));
    ocrPaddleStatus.setForeground(paddleOk ? new Color(0x3D, 0xDB, 0xA4) : new Color(0xFF, 0x6B, 0x6B));

    // Tesseract (always bundled)
    ocrTesseractStatus.setText("\u2B24 Tesseract  built-in");
    ocrTesseractStatus.setForeground(new Color(0x3D, 0xDB, 0xA4));

    // Java info
    javaInfo.setText("\u2615 Java " + System.getProperty("java.version"));
    javaInfo.setForeground(UIManager.getColor("Label.disabledForeground"));
  }

  // ── Navigation wiring ──

  public void initNavigation(SidebarSubmenu fileSub, SidebarSubmenu editSub,
                              SidebarSubmenu runSub, SidebarSubmenu toolsSub,
                              SidebarSubmenu helpSub) {
    navFile.addActionListener(e -> fileSub.showBelow(navFile));
    navEdit.addActionListener(e -> editSub.showBelow(navEdit));
    navRun.addActionListener(e -> runSub.showBelow(navRun));
    navTools.addActionListener(e -> toolsSub.showBelow(navTools));
    navHelp.addActionListener(e -> helpSub.showBelow(navHelp));
  }

  // ── Footer ──

  public void initFooter(String version, ActionListener themeAction) {
    footerPanel.add(new JSeparator(), "growx, gapbottom 4");
    btnTheme = new SidebarItem("\u263C  Dark / Light", null, e -> {
      toggleTheme();
      if (themeAction != null) themeAction.actionPerformed(e);
    });
    footerPanel.add(btnTheme);
    versionLabel = new JLabel("v" + version);
    versionLabel.setFont(UIManager.getFont("small.font"));
    versionLabel.setHorizontalAlignment(SwingConstants.CENTER);
    versionLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
    footerPanel.add(versionLabel);
  }

  private void toggleTheme() {
    isDark = !isDark;
    try {
      if (isDark) FlatDarkLaf.setup(); else FlatLightLaf.setup();
      FlatLaf.updateUI();
    } catch (Exception ex) {}
  }

  public boolean isDarkTheme() { return isDark; }

  // ── Collapse ──

  public void toggleCollapsed() {
    collapsed = !collapsed;
    for (Component c : mainPanel.getComponents()) {
      if (c instanceof SidebarItem) ((SidebarItem) c).setCollapsed(collapsed);
      if (c instanceof JLabel && !(c instanceof SidebarItem)) c.setVisible(!collapsed);
      if (c instanceof JPanel && c != mainPanel) c.setVisible(!collapsed);
    }
    for (Component c : footerPanel.getComponents()) {
      if (c instanceof SidebarItem) ((SidebarItem) c).setCollapsed(collapsed);
    }
    versionLabel.setVisible(!collapsed);
    revalidate();
    repaint();
  }

  public boolean isCollapsed() { return collapsed; }

  private void addResizeHandle() {
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) toggleCollapsed();
      }
    });
  }

  private String truncatePath(String path, int maxLen) {
    if (path.length() <= maxLen) return path;
    return "..." + path.substring(path.length() - maxLen + 3);
  }

  private ImageIcon loadIcon(String path, int size) {
    try {
      java.net.URL url = getClass().getResource(path);
      if (url != null) {
        ImageIcon icon = new ImageIcon(url);
        Image img = icon.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(img);
      }
    } catch (Exception e) {}
    return null;
  }
}
