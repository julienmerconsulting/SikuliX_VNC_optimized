/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;
import net.miginfocom.swing.MigLayout;
import org.sikuli.basics.PreferencesUser;
import org.sikuli.ide.theme.OculixColors;
import org.sikuli.ide.theme.OculixDarkLaf;
import org.sikuli.ide.theme.OculixFonts;
import org.sikuli.ide.theme.OculixLightLaf;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

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

  // Theme toggle - initialized from user preferences (persisted across sessions)
  private SidebarItem btnTheme; // legacy, kept null after pill switch refactor
  private ThemePillSwitch pillSwitch;
  private boolean isDark = PreferencesUser.THEME_DARK.equals(PreferencesUser.get().getIdeTheme());

  // Version label
  private JLabel versionLabel;

  // Hero script card (composite component for the project info panel)
  private HeroScriptCard heroCardRef;
  private JLabel projectName;
  private JLabel projectPath;
  private JLabel projectImages;

  // Status panel: glowing dot + label + right-aligned value, per row
  private GlowingDot paddleDot, tesseractDot, javaDot;
  private JLabel ocrPaddleStatus, ocrTesseractStatus, javaInfo;
  private JLabel paddleValue, tesseractValue, javaValue;

  // Last run card
  private JLabel lastRunResult;
  private JLabel lastRunTime;
  private JLabel lastRunDuration;

  // Layout panels
  private JPanel mainPanel;
  private JPanel footerPanel;

  public OculixSidebar() {
    setLayout(new BorderLayout());
    setPreferredSize(new Dimension(240, 0));
    setMinimumSize(new Dimension(240, 0));
    putClientProperty(FlatClientProperties.STYLE, "background: darken(@background, 3%)");

    mainPanel = new JPanel(new MigLayout("wrap 1, insets 8 14 8 14, gap 0", "[fill, grow]", ""));
    mainPanel.setOpaque(false);

    footerPanel = new JPanel(new MigLayout("wrap 1, insets 4 14 10 14, gap 6", "[fill, grow]", ""));
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
   * Initializes the full sidebar layout: wordmark, hero card, nav, status, last run, help.
   */
  public void initNavItems() {
    // ── Wordmark ──
    // "OculiX" in Fraunces italic black + "IDE" small mono cyan, baseline-aligned
    // on the same horizontal line. No box, no border — typography carries the
    // brand on its own.
    JPanel wordmarkRow = new JPanel(new MigLayout("insets 14 0 14 0, gap 4", "push[][]push", "[]"));
    wordmarkRow.setOpaque(false);

    JLabel logoLine1 = new JLabel("OculiX");
    logoLine1.setFont(OculixFonts.display(30));
    logoLine1.setForeground(UIManager.getColor("Label.foreground"));
    wordmarkRow.add(logoLine1, "aligny baseline");

    JLabel logoLine2 = new JLabel("IDE");
    logoLine2.setFont(applyTracking(OculixFonts.mono(11), 0.18f).deriveFont(Font.BOLD));
    logoLine2.setForeground(OculixColors.OX_CYAN_500);
    wordmarkRow.add(logoLine2, "aligny baseline, gapleft 2");

    mainPanel.add(wordmarkRow, "growx, gapbottom 8");

    // ── Hero script card ──
    // Soft cyan-tinted card with a glowing border. When no script is open
    // the card collapses to a single muted "no script" line.
    heroCardRef = new HeroScriptCard();
    projectName = heroCardRef.nameLabel;
    projectPath = heroCardRef.pathLabel;
    projectImages = heroCardRef.metaLabel;
    mainPanel.add(heroCardRef, "growx, gaptop 0, gapbottom 8");

    // ── SCRIPT section ──
    addSectionHeader("SCRIPT");
    navFile = new SidebarItem("\uD83D\uDCC1  " + _I("menuFile") + "  \u25B8", null);
    navFile.setMnemonic(java.awt.event.KeyEvent.VK_F);
    mainPanel.add(navFile);
    navEdit = new SidebarItem("\u270F\uFE0F  " + _I("menuEdit") + "  \u25B8", null);
    navEdit.setMnemonic(java.awt.event.KeyEvent.VK_E);
    mainPanel.add(navEdit);
    // Lime arrow on Ex\u00E9cuter only \u2014 the Run verb gets the brand color, no
    // decoration elsewhere. HTML lets us tint just the leading glyph.
    navRun = new SidebarItem(
        "<html><span style='color:#7DE356'>\u25B6</span>  " + _I("menuRun") + "  \u25B8</html>", null);
    navRun.setMnemonic(java.awt.event.KeyEvent.VK_R);
    mainPanel.add(navRun);

    // ── TOOLS section ──
    addSectionHeader("TOOLS");
    navTools = new SidebarItem("\uD83D\uDD27  " + _I("menuTool") + "  \u25B8", null);
    navTools.setMnemonic(java.awt.event.KeyEvent.VK_T);
    mainPanel.add(navTools);

    // ── STATUS section ──
    // Three glowing dots + label + value (right-aligned). Each dot is a
    // GlowingDot painted with a translucent radial halo so it reads "alive".
    addSectionHeader("STATUS");
    JPanel statusPanel = new JPanel(new MigLayout("wrap 3, insets 0 2 0 2, gap 4 6", "[14!][grow][]", ""));
    statusPanel.setOpaque(false);
    paddleDot = new GlowingDot(OculixColors.OX_RED_500);
    statusPanel.add(paddleDot);
    ocrPaddleStatus = makeStatusLabel("PaddleOCR");
    statusPanel.add(ocrPaddleStatus, "growx");
    paddleValue = makeStatusValue("offline");
    statusPanel.add(paddleValue, "align right");
    tesseractDot = new GlowingDot(OculixColors.OX_LIME_400);
    statusPanel.add(tesseractDot);
    ocrTesseractStatus = makeStatusLabel("Tesseract");
    statusPanel.add(ocrTesseractStatus, "growx");
    tesseractValue = makeStatusValue("built-in");
    statusPanel.add(tesseractValue, "align right");
    javaDot = new GlowingDot(OculixColors.OX_CYAN_500);
    statusPanel.add(javaDot);
    javaInfo = makeStatusLabel("Java");
    statusPanel.add(javaInfo, "growx");
    javaValue = makeStatusValue(System.getProperty("java.version"));
    statusPanel.add(javaValue, "align right");
    mainPanel.add(statusPanel, "gapbottom 8");

    // ── LAST RUN section ──
    addSectionHeader("LAST RUN");
    LastRunCard lastRunCard = new LastRunCard();
    lastRunResult = lastRunCard.resultLabel;
    lastRunDuration = lastRunCard.durationLabel;
    lastRunTime = lastRunCard.timeLabel;
    mainPanel.add(lastRunCard, "growx, gapbottom 6");

    // ── HELP section ──
    addSectionHeader("HELP");
    navHelp = new SidebarItem("\u2753  " + _I("menuHelp") + "  \u25B8", null);
    navHelp.setMnemonic(java.awt.event.KeyEvent.VK_H);
    mainPanel.add(navHelp);

    // Initial status check
    refreshOcrStatus();
  }

  /**
   * Returns a borderless transparent column panel — used for grouping related
   * status / last-run lines without the old "framed card" look.
   */
  private JPanel createInfoPanel() {
    JPanel panel = new JPanel(new MigLayout("wrap 2, insets 0 2 0 2, gap 2", "[fill, grow]", ""));
    panel.setOpaque(false);
    return panel;
  }

  private JLabel createStatusLabel(String text, JPanel parent) {
    JLabel label = new JLabel(text);
    label.setFont(OculixFonts.ui(12));
    parent.add(label, "span 2");
    return label;
  }

  /**
   * Section kicker: JetBrains Mono 10, uppercase, 0.18em letter-spacing,
   * ink-300. Replaces the older default-font small grey text.
   */
  private void addSectionHeader(String text) {
    JLabel header = new JLabel(text == null ? null : text.toUpperCase(java.util.Locale.ROOT));
    header.setFont(applyTracking(OculixFonts.mono(10), 0.18f).deriveFont(Font.BOLD));
    header.setForeground(OculixColors.OX_INK_300);
    header.setBorder(BorderFactory.createEmptyBorder(14, 2, 6, 0));
    mainPanel.add(header);
  }

  /**
   * Java's Font does not have a setLetterSpacing API; tracking is exposed via
   * a font attribute map. {@link TextAttribute#TRACKING} is in em units (0.1f
   * = 10% extra spacing between glyphs).
   */
  private static Font applyTracking(Font base, float tracking) {
    Map<TextAttribute, Object> attrs = new HashMap<>();
    attrs.put(TextAttribute.TRACKING, tracking);
    return base.deriveFont(attrs);
  }

  // ── Public API for live updates ──

  /**
   * Updates the project info panel when a script is opened.
   */
  public void updateProjectInfo(String name, File scriptDir) {
    boolean hasScript = name != null && !name.isEmpty();
    heroCardRef.setHasScript(hasScript);
    if (!hasScript) {
      projectName.setText("— No script open");
      projectPath.setText("");
      projectImages.setText("");
      return;
    }
    projectName.setText(name);
    projectPath.setText(scriptDir != null ? truncatePath(scriptDir.getAbsolutePath(), 30) : "");
    projectPath.setToolTipText(scriptDir != null ? scriptDir.getAbsolutePath() : "");

    int imgCount = 0;
    if (scriptDir != null && scriptDir.exists()) {
      File[] pngs = scriptDir.listFiles((dir, fn) -> fn.endsWith(".png"));
      if (pngs != null) imgCount = pngs.length;
    }
    projectImages.setText(imgCount + (imgCount == 1 ? " image" : " images") + "  ·  idle");
  }

  /**
   * Updates the last run result panel.
   */
  public void updateLastRun(int exitCode, long durationMs) {
    if (exitCode == 0) {
      lastRunResult.setText("Passed");
      lastRunResult.setForeground(OculixColors.OX_LIME_400);
    } else {
      lastRunResult.setText("Failed (code " + exitCode + ")");
      lastRunResult.setForeground(OculixColors.OX_RED_500);
    }
    lastRunDuration.setText(String.format("%.1fs", durationMs / 1000.0));
    lastRunTime.setText(new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()));
  }

  /**
   * Refreshes OCR engine status indicators.
   */
  public void refreshOcrStatus() {
    boolean paddleOk = false;
    try {
      Class<?> engineClass = Class.forName("com.sikulix.ocr.PaddleOCREngine");
      Object engine = engineClass.getDeclaredConstructor().newInstance();
      paddleOk = (boolean) engineClass.getMethod("isAvailable").invoke(engine);
    } catch (Exception ignored) {}
    paddleDot.setColor(paddleOk ? OculixColors.OX_LIME_400 : OculixColors.OX_RED_500);
    paddleValue.setText(paddleOk ? "OK" : "offline");

    // Tesseract is always bundled inside the IDE jar
    tesseractDot.setColor(OculixColors.OX_LIME_400);
    tesseractValue.setText("built-in");

    javaValue.setText(System.getProperty("java.version"));
  }

  // ── Navigation wiring ──

  private SidebarSubmenu fileSub, editSub, runSub, toolsSub, helpSub;

  public void initNavigation(SidebarSubmenu fileSub, SidebarSubmenu editSub,
                              SidebarSubmenu runSub, SidebarSubmenu toolsSub,
                              SidebarSubmenu helpSub) {
    this.fileSub = fileSub;
    this.editSub = editSub;
    this.runSub = runSub;
    this.toolsSub = toolsSub;
    this.helpSub = helpSub;
    navFile.addActionListener(e -> fileSub.showBelow(navFile));
    navEdit.addActionListener(e -> editSub.showBelow(navEdit));
    navRun.addActionListener(e -> runSub.showBelow(navRun));
    navTools.addActionListener(e -> toolsSub.showBelow(navTools));
    navHelp.addActionListener(e -> helpSub.showBelow(navHelp));
  }

  // Popup submenus live outside the visible window tree until clicked, so
  // FlatLaf.updateUI() does not reach them. Call updateComponentTreeUI on
  // each after a theme swap so the next popup render picks up the new LaF
  // instead of showing old Dark colors on a Light theme (and vice-versa).
  public void refreshSubmenuLaF() {
    for (SidebarSubmenu sub : new SidebarSubmenu[]{fileSub, editSub, runSub, toolsSub, helpSub}) {
      if (sub != null) SwingUtilities.updateComponentTreeUI(sub);
    }
  }

  // ── Footer ──

  public void initFooter(String version, ActionListener themeAction) {
    footerPanel.add(new JSeparator(), "growx, gapbottom 6");

    // Pill switch DARK | LIGHT — two segmented capsules sharing an outline.
    // Replaces the older text-button "Dark / Light" SidebarItem.
    pillSwitch = new ThemePillSwitch(isDark, e -> {
      toggleTheme();
      pillSwitch.setDark(isDark);
      if (themeAction != null) themeAction.actionPerformed(e);
    });
    footerPanel.add(pillSwitch, "align center");

    versionLabel = new JLabel("v" + version);
    versionLabel.setFont(OculixFonts.mono(10));
    versionLabel.setHorizontalAlignment(SwingConstants.CENTER);
    versionLabel.setForeground(OculixColors.OX_INK_400);
    footerPanel.add(versionLabel);
  }

  private void toggleTheme() {
    isDark = !isDark;
    try {
      if (isDark) OculixDarkLaf.setup(); else OculixLightLaf.setup();
      FlatLaf.updateUI();
      PreferencesUser prefs = PreferencesUser.get();
      prefs.setIdeTheme(isDark ? PreferencesUser.THEME_DARK : PreferencesUser.THEME_LIGHT);
      prefs.store();
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

  // ── Status row helpers ─────────────────────────────────────────

  private static JLabel makeStatusLabel(String text) {
    JLabel l = new JLabel(text);
    l.setFont(OculixFonts.ui(12));
    l.setForeground(UIManager.getColor("Label.foreground"));
    return l;
  }

  private static JLabel makeStatusValue(String text) {
    JLabel l = new JLabel(text);
    l.setFont(OculixFonts.mono(11));
    l.setForeground(OculixColors.OX_INK_300);
    return l;
  }

  // ── Inner components ───────────────────────────────────────────

  /**
   * Hero card showing the active script. Soft cyan-tinted background with a
   * glowing cyan border. When no script is open the card collapses to a
   * single muted line — same width, less content, no glow.
   */
  static class HeroScriptCard extends JPanel {
    final JLabel nameLabel;
    final JLabel pathLabel;
    final JLabel metaLabel;
    private boolean hasScript = false;

    HeroScriptCard() {
      super(new MigLayout("wrap 1, insets 12 14 12 14, gap 2", "[grow, fill]", ""));
      setOpaque(false);
      setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

      nameLabel = new JLabel("— No script open");
      nameLabel.setFont(OculixFonts.uiBold(13));
      nameLabel.setForeground(UIManager.getColor("Label.foreground"));
      add(nameLabel);

      pathLabel = new JLabel("");
      pathLabel.setFont(OculixFonts.mono(10));
      pathLabel.setForeground(OculixColors.OX_INK_300);
      add(pathLabel);

      metaLabel = new JLabel("");
      metaLabel.setFont(OculixFonts.mono(10));
      metaLabel.setForeground(OculixColors.OX_INK_400);
      add(metaLabel, "gaptop 4");
    }

    void setHasScript(boolean v) {
      this.hasScript = v;
      pathLabel.setVisible(v);
      metaLabel.setVisible(v);
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      // No bg paint when no script — keep the surface minimal.
      if (!hasScript) {
        super.paintComponent(g);
        return;
      }
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      int w = getWidth();
      int h = getHeight();
      int arc = 12;

      // Soft cyan-tinted gradient fill
      Color fillTop = new Color(0x1EA5FF, true) ;
      g2.setPaint(new GradientPaint(0, 0,
          OculixColors.withAlpha(OculixColors.OX_CYAN_500, 22),
          0, h,
          OculixColors.withAlpha(OculixColors.OX_CYAN_500, 8)));
      g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);

      // Outer glow — three concentric rounded rects with decreasing alpha
      for (int i = 0; i < 3; i++) {
        g2.setColor(OculixColors.withAlpha(OculixColors.OX_CYAN_500, 60 - i * 18));
        g2.setStroke(new BasicStroke(1f + i * 0.6f));
        g2.drawRoundRect(-i, -i, w - 1 + 2 * i, h - 1 + 2 * i, arc + 2 * i, arc + 2 * i);
      }
      // Crisp inner border
      g2.setStroke(new BasicStroke(1.2f));
      g2.setColor(OculixColors.withAlpha(OculixColors.OX_CYAN_500, 140));
      g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

      g2.dispose();
      super.paintComponent(g);
    }
  }

  /**
   * Tiny dot indicator (8px) painted with a translucent radial halo so it
   * reads "alive" on dark backgrounds. Color is mutable (e.g. PaddleOCR
   * goes from red→lime when it becomes available at runtime).
   */
  static class GlowingDot extends JComponent {
    private Color color;
    GlowingDot(Color color) {
      this.color = color;
      setPreferredSize(new Dimension(14, 14));
    }
    void setColor(Color c) {
      this.color = c;
      repaint();
    }
    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int w = getWidth();
      int h = getHeight();
      int cx = w / 2;
      int cy = h / 2;
      // Halo: 3 concentric circles with decreasing alpha
      for (int r = 7; r >= 5; r--) {
        g2.setColor(OculixColors.withAlpha(color, 18 + (7 - r) * 22));
        g2.fillOval(cx - r, cy - r, r * 2, r * 2);
      }
      // Solid dot
      g2.setColor(color);
      g2.fillOval(cx - 3, cy - 3, 6, 6);
      g2.dispose();
    }
  }

  /**
   * Last-run mini-card. Same visual language as the hero card (soft tint +
   * subtle border) but without the glow, since it's a status panel — not
   * the focus of attention.
   */
  static class LastRunCard extends JPanel {
    final JLabel resultLabel;
    final JLabel durationLabel;
    final JLabel timeLabel;

    LastRunCard() {
      super(new MigLayout("wrap 2, insets 8 12 8 12, gap 2 8", "[grow][]", ""));
      setOpaque(false);

      resultLabel = new JLabel("— Not run yet");
      resultLabel.setFont(OculixFonts.uiBold(12));
      resultLabel.setForeground(OculixColors.OX_INK_200);
      add(resultLabel, "growx");

      durationLabel = new JLabel("");
      durationLabel.setFont(OculixFonts.mono(10));
      durationLabel.setForeground(OculixColors.OX_INK_300);
      add(durationLabel, "align right");

      timeLabel = new JLabel("");
      timeLabel.setFont(OculixFonts.mono(10));
      timeLabel.setForeground(OculixColors.OX_INK_400);
      add(timeLabel, "span 2, growx");
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int w = getWidth();
      int h = getHeight();
      int arc = 8;
      g2.setColor(OculixColors.withAlpha(OculixColors.OX_INK_700, 120));
      g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
      g2.setColor(OculixColors.withAlpha(OculixColors.OX_INK_500, 180));
      g2.setStroke(new BasicStroke(1f));
      g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
      g2.dispose();
      super.paintComponent(g);
    }
  }

  /**
   * Pill switch with two segments DARK | LIGHT. Clicking the inactive segment
   * fires the action with the ID of that segment. Painted manually so the
   * switch matches the brand without depending on a toggle-button skin.
   */
  static class ThemePillSwitch extends JComponent {
    private boolean dark;
    private final java.awt.event.ActionListener onToggle;

    ThemePillSwitch(boolean dark, java.awt.event.ActionListener onToggle) {
      this.dark = dark;
      this.onToggle = onToggle;
      setPreferredSize(new Dimension(132, 26));
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      addMouseListener(new MouseAdapter() {
        @Override public void mouseClicked(MouseEvent e) {
          // Toggle on any click; the parent re-syncs setDark() afterwards.
          onToggle.actionPerformed(new java.awt.event.ActionEvent(
              ThemePillSwitch.this, java.awt.event.ActionEvent.ACTION_PERFORMED, "toggle"));
        }
      });
    }

    void setDark(boolean v) {
      this.dark = v;
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

      int w = getWidth();
      int h = getHeight();
      int arc = h;
      int half = w / 2;

      // Outer pill
      g2.setColor(OculixColors.withAlpha(OculixColors.OX_INK_500, 200));
      g2.setStroke(new BasicStroke(1f));
      g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

      // Active segment background
      g2.setColor(OculixColors.withAlpha(OculixColors.OX_CYAN_500, 60));
      if (dark) {
        g2.fillRoundRect(0, 0, half, h - 1, arc, arc);
        g2.setColor(OculixColors.OX_CYAN_500);
        g2.drawRoundRect(0, 0, half, h - 1, arc, arc);
      } else {
        g2.fillRoundRect(half, 0, w - half - 1, h - 1, arc, arc);
        g2.setColor(OculixColors.OX_CYAN_500);
        g2.drawRoundRect(half, 0, w - half - 1, h - 1, arc, arc);
      }

      // Labels
      g2.setFont(applyTracking(OculixFonts.mono(10), 0.16f).deriveFont(Font.BOLD));
      FontMetrics fm = g2.getFontMetrics();
      String labelDark = "DARK";
      String labelLight = "LIGHT";
      g2.setColor(dark ? OculixColors.OX_INK_100 : OculixColors.OX_INK_400);
      int wDark = fm.stringWidth(labelDark);
      g2.drawString(labelDark, (half - wDark) / 2, h / 2 + fm.getAscent() / 2 - 2);
      g2.setColor(dark ? OculixColors.OX_INK_400 : OculixColors.OX_INK_100);
      int wLight = fm.stringWidth(labelLight);
      g2.drawString(labelLight, half + (half - wLight) / 2, h / 2 + fm.getAscent() / 2 - 2);

      g2.dispose();
    }
  }
}
