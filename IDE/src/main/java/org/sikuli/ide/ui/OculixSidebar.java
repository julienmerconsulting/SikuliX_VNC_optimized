/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;
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
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
public class OculixSidebar extends JPanel {

  private boolean collapsed = false;
  private int collapsedWidth = 50;

  // Navigation items
  private SidebarItem navFile;
  private SidebarItem navEdit;
  private SidebarItem navRun;
  private SidebarItem navView;
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
    // Sans-serif bold (RaiMan-style 2010), wrapped in a soft rounded card so
    // the brand block reads as a unit even in OculiX Light where the cyan
    // accents lose contrast against the paper bg.
    JPanel wordmarkCard = new WordmarkCard();
    JLabel logoLine1 = new JLabel("OculiX");
    logoLine1.setFont(OculixFonts.uiBold(26));
    logoLine1.setForeground(UIManager.getColor("Label.foreground"));
    wordmarkCard.add(logoLine1, "aligny baseline");

    JLabel logoLine2 = new JLabel("IDE");
    logoLine2.setFont(applyTracking(OculixFonts.mono(11), 0.18f).deriveFont(Font.BOLD));
    logoLine2.setForeground(OculixColors.OX_CYAN_500);
    wordmarkCard.add(logoLine2, "aligny baseline, gapleft 4");

    mainPanel.add(wordmarkCard, "growx, gapbottom 8");

    // ── Hero script card ──
    // Soft cyan-tinted card with a glowing border. When no script is open
    // the card collapses to a single muted "no script" line.
    heroCardRef = new HeroScriptCard();
    projectName = heroCardRef.nameLabel;
    projectPath = heroCardRef.pathLabel;
    projectImages = heroCardRef.metaLabel;
    mainPanel.add(heroCardRef, "growx, gaptop 0, gapbottom 8");

    // ── SCRIPT section ──
    // Section header values come from _I() — addSectionHeader uppercases
    // the result so locales whose translation didn't preserve the CAPS
    // (e.g. zh_CN, ja) still render the consistent kicker style.
    addSectionHeader(_I("sidebarSectionScript"));
    navFile = new SidebarItem(_I("menuFile"), null);
    navFile.setIcon(new FlatSVGIcon("icons/sidebar/folder.svg", 18, 18));
    navFile.setMnemonic(java.awt.event.KeyEvent.VK_F);
    mainPanel.add(navFile);
    navEdit = new SidebarItem(_I("menuEdit"), null);
    navEdit.setIcon(new FlatSVGIcon("icons/sidebar/pencil.svg", 18, 18));
    navEdit.setMnemonic(java.awt.event.KeyEvent.VK_E);
    mainPanel.add(navEdit);
    navRun = new SidebarItem(_I("menuRun"), null);
    navRun.setIcon(new FlatSVGIcon("icons/sidebar/play.svg", 18, 18));
    navRun.setMnemonic(java.awt.event.KeyEvent.VK_R);
    mainPanel.add(navRun);
    // View sits where the classic menu bar had it, between Run and Tools.
    // It went missing when the menu bar was hidden behind the sidebar, and
    // with it the only visible way to toggle the inline image thumbnails.
    navView = new SidebarItem(_I("menuView"), null);
    navView.setIcon(new FlatSVGIcon("icons/sidebar/eye.svg", 18, 18));
    navView.setMnemonic(java.awt.event.KeyEvent.VK_V);
    mainPanel.add(navView);

    // ── TOOLS section ──
    addSectionHeader(_I("sidebarSectionTools"));
    navTools = new SidebarItem(_I("menuTool"), null);
    navTools.setIcon(new FlatSVGIcon("icons/sidebar/wrench.svg", 18, 18));
    navTools.setMnemonic(java.awt.event.KeyEvent.VK_T);
    mainPanel.add(navTools);

    // ── STATUS section ──
    // Three glowing dots + label + value (right-aligned). Each dot is a
    // GlowingDot painted with a translucent radial halo so it reads "alive".
    addSectionHeader(_I("sidebarSectionStatus"));
    JPanel statusPanel = new JPanel(new MigLayout("wrap 3, insets 0 2 0 2, gap 4 6", "[14!][grow][]", ""));
    statusPanel.setOpaque(false);
    paddleDot = new GlowingDot(OculixColors.OX_RED_500);
    statusPanel.add(paddleDot);
    ocrPaddleStatus = makeStatusLabel("PaddleOCR");
    statusPanel.add(ocrPaddleStatus, "growx");
    paddleValue = makeStatusValue(_I("sidebarStatusOffline"));
    statusPanel.add(paddleValue, "align right");
    tesseractDot = new GlowingDot(OculixColors.OX_LIME_400);
    statusPanel.add(tesseractDot);
    ocrTesseractStatus = makeStatusLabel("Tesseract");
    statusPanel.add(ocrTesseractStatus, "growx");
    tesseractValue = makeStatusValue(_I("sidebarStatusBuiltIn"));
    statusPanel.add(tesseractValue, "align right");
    javaDot = new GlowingDot(OculixColors.OX_CYAN_500);
    statusPanel.add(javaDot);
    javaInfo = makeStatusLabel("Java");
    statusPanel.add(javaInfo, "growx");
    javaValue = makeStatusValue(System.getProperty("java.version"));
    statusPanel.add(javaValue, "align right");
    mainPanel.add(statusPanel, "gapbottom 8");

    // ── LAST RUN section ──
    addSectionHeader(_I("sidebarSectionLastRun"));
    LastRunCard lastRunCard = new LastRunCard();
    lastRunResult = lastRunCard.resultLabel;
    lastRunDuration = lastRunCard.durationLabel;
    lastRunTime = lastRunCard.timeLabel;
    mainPanel.add(lastRunCard, "growx, gapbottom 6");

    // ── HELP section ──
    addSectionHeader(_I("sidebarSectionHelp"));
    navHelp = new SidebarItem(_I("menuHelp"), null);
    navHelp.setIcon(new FlatSVGIcon("icons/sidebar/circle-help.svg", 18, 18));
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
   *
   * <p>Top padding is generous (24px) so the sidebar spreads out and fills
   * the available height instead of clustering all sections at the top of
   * the column with a long blank gap below.
   */
  private void addSectionHeader(String text) {
    JLabel header = new JLabel(text == null ? null : text.toUpperCase(java.util.Locale.ROOT));
    header.setFont(applyTracking(OculixFonts.mono(10), 0.18f).deriveFont(Font.BOLD));
    header.setForeground(OculixColors.OX_INK_300);
    header.setBorder(BorderFactory.createEmptyBorder(24, 2, 8, 0));
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
      projectName.setText(_I("sidebarNoScript"));
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
    // Singular vs plural via two distinct keys; MessageFormat handles the
    // {0,number,integer} interpolation per locale (e.g. some locales prefer
    // grouping with a non-breaking-space).
    String imgKey = imgCount == 1 ? "sidebarImageCountSingular" : "sidebarImageCountPlural";
    projectImages.setText(_I(imgKey, imgCount));
  }

  /**
   * Updates the last run result panel.
   */
  public void updateLastRun(int exitCode, long durationMs) {
    if (exitCode == 0) {
      lastRunResult.setText(_I("sidebarRunPassed"));
      lastRunResult.setForeground(OculixColors.OX_LIME_400);
    } else {
      lastRunResult.setText(_I("sidebarRunFailed", exitCode));
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
    paddleValue.setText(paddleOk ? _I("sidebarStatusOk") : _I("sidebarStatusOffline"));

    // Tesseract is always bundled inside the IDE jar
    tesseractDot.setColor(OculixColors.OX_LIME_400);
    tesseractValue.setText(_I("sidebarStatusBuiltIn"));

    javaValue.setText(System.getProperty("java.version"));
  }

  // ── Navigation wiring ──

  private SidebarSubmenu fileSub, editSub, runSub, viewSub, toolsSub, helpSub;

  public void initNavigation(SidebarSubmenu fileSub, SidebarSubmenu editSub,
                              SidebarSubmenu runSub, SidebarSubmenu viewSub,
                              SidebarSubmenu toolsSub, SidebarSubmenu helpSub) {
    this.fileSub = fileSub;
    this.editSub = editSub;
    this.runSub = runSub;
    this.viewSub = viewSub;
    this.toolsSub = toolsSub;
    this.helpSub = helpSub;
    navFile.addActionListener(e -> fileSub.showBelow(navFile));
    navEdit.addActionListener(e -> editSub.showBelow(navEdit));
    navRun.addActionListener(e -> runSub.showBelow(navRun));
    navView.addActionListener(e -> viewSub.showBelow(navView));
    navTools.addActionListener(e -> toolsSub.showBelow(navTools));
    navHelp.addActionListener(e -> helpSub.showBelow(navHelp));
  }

  // Popup submenus live outside the visible window tree until clicked, so
  // FlatLaf.updateUI() does not reach them. Call updateComponentTreeUI on
  // each after a theme swap so the next popup render picks up the new LaF
  // instead of showing old Dark colors on a Light theme (and vice-versa).
  public void refreshSubmenuLaF() {
    for (SidebarSubmenu sub : new SidebarSubmenu[]{fileSub, editSub, runSub, viewSub, toolsSub, helpSub}) {
      if (sub != null) SwingUtilities.updateComponentTreeUI(sub);
    }
  }

  // ── Footer ──

  private LanguagePicker languagePicker;

  public void initFooter(String version, ActionListener themeAction) {
    footerPanel.add(new JSeparator(), "growx, gapbottom 6");

    // Language picker — custom-painted pill that mirrors the theme switch
    // visuals (same rounded outline, same brand-cyan accent, same mono
    // font) so the footer reads as one coherent control surface in both
    // Dark and Light themes. Click opens a popup with the 25 available
    // locales rendered in their own language ("Deutsch", "Français",
    // "日本語", "Русский", ...).
    languagePicker = new LanguagePicker(PreferencesUser.get().getLocale(),
        newLocale -> changeLocale(newLocale));
    footerPanel.add(languagePicker, "align center, gapbottom 4");

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

  /**
   * Persists the new locale + reloads the SikuliIDEI18N bundle, then
   * surfaces a "restart for full effect" toast on the IDE message panel.
   * The Welcome tab + Sidebar section headers will pick up the new
   * locale on the next IDE launch (Java Swing components cache strings
   * at construction time, so a partial live-refresh is unreliable
   * across the whole IDE).
   */
  private void changeLocale(java.util.Locale newLocale) {
    try {
      PreferencesUser prefs = PreferencesUser.get();
      prefs.setLocale(newLocale);
      prefs.store();
      org.sikuli.support.ide.SikuliIDEI18N.setLocale(newLocale);
      // Update the picker label to the freshly-selected locale so the
      // user sees feedback even before restart. setSelectedLocale (not
      // setLocale) — the latter is inherited from java.awt.Component
      // and renaming avoids a weak-access override compile error.
      if (languagePicker != null) languagePicker.setSelectedLocale(newLocale);

      // User-facing notice that a restart is required for the full UI
      // to switch (Welcome tab + Sidebar labels are constructed once,
      // can't refresh in-place without rebuilding the whole tree).
      // The popup itself uses _I() with the freshly-set locale, so the
      // user sees the new translation as proof the change took effect.
      String displayName = newLocale.getDisplayName(newLocale);
      if (displayName == null || displayName.isEmpty()) {
        displayName = newLocale.toLanguageTag();
      }
      // Capitalize first letter so 'français' / 'deutsch' look right.
      displayName = Character.toUpperCase(displayName.charAt(0)) + displayName.substring(1);

      JOptionPane.showMessageDialog(
          SwingUtilities.getWindowAncestor(this),
          _I("i18nLanguageChangedBody", displayName),
          _I("i18nLanguageChangedTitle"),
          JOptionPane.INFORMATION_MESSAGE);
    } catch (Exception ex) {
      System.err.println("[i18n] failed to change locale: " + ex);
    }
  }

  private void toggleTheme() {
    isDark = !isDark;
    try {
      // Persist the new theme BEFORE installing the LaF so any component
      // that reads PreferencesUser.getIdeTheme() during the swap (e.g.
      // EditorConsolePane.applyThemeColors) sees the right value already.
      PreferencesUser prefs = PreferencesUser.get();
      prefs.setIdeTheme(isDark ? PreferencesUser.THEME_DARK : PreferencesUser.THEME_LIGHT);
      prefs.store();
      if (isDark) OculixDarkLaf.setup(); else OculixLightLaf.setup();
      FlatLaf.updateUI();
      // Force a full LaF propagation on every visible top-level window,
      // including detached frames (Preferences, More options, splash).
      // FlatLaf.updateUI() walks the JFrame tree but stale references on
      // some windows can keep the old LaF until the next focus event.
      for (java.awt.Window w : java.awt.Window.getWindows()) {
        if (w.isDisplayable()) SwingUtilities.updateComponentTreeUI(w);
      }
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
   * Wordmark card: soft rounded container around "OculiX IDE". Subtle ink-700
   * fill in dark mode, paper-200 fill in light, both with a 1px ink-500 /
   * paper-400 border. Gives the brand block presence without screaming.
   */
  static class WordmarkCard extends JPanel {
    WordmarkCard() {
      super(new MigLayout("insets 10 14 10 14, gap 4", "push[][]push", "[]"));
      setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int w = getWidth();
      int h = getHeight();
      int arc = 12;
      boolean dark = isDarkLaf();
      g2.setColor(dark
          ? OculixColors.withAlpha(OculixColors.OX_INK_700, 180)
          : new Color(0xEE, 0xF2, 0xFA));    // paper-200
      g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
      g2.setColor(dark ? OculixColors.OX_INK_500 : new Color(0xBC, 0xC7, 0xE2)); // paper-400
      g2.setStroke(new BasicStroke(1f));
      g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
      g2.dispose();
      super.paintComponent(g);
    }
  }

  /** True when the active LaF is the OculiX Dark theme. */
  private static boolean isDarkLaf() {
    String name = UIManager.getLookAndFeel().getName();
    return name != null && name.toLowerCase(java.util.Locale.ROOT).contains("dark");
  }

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

      nameLabel = new JLabel(_I("sidebarNoScript"));
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

      resultLabel = new JLabel(_I("sidebarRunNotYet"));
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
      // Pill-switch labels — uppercased for the brand kicker style even
      // when the locale's translation didn't preserve CAPS.
      String labelDark = _I("sidebarThemeDark").toUpperCase();
      String labelLight = _I("sidebarThemeLight").toUpperCase();
      g2.setColor(dark ? OculixColors.OX_INK_100 : OculixColors.OX_INK_400);
      int wDark = fm.stringWidth(labelDark);
      g2.drawString(labelDark, (half - wDark) / 2, h / 2 + fm.getAscent() / 2 - 2);
      g2.setColor(dark ? OculixColors.OX_INK_400 : OculixColors.OX_INK_100);
      int wLight = fm.stringWidth(labelLight);
      g2.drawString(labelLight, half + (half - wLight) / 2, h / 2 + fm.getAscent() / 2 - 2);

      g2.dispose();
    }
  }

  /**
   * Custom-painted language picker, visually mirrors {@link ThemePillSwitch}:
   * same rounded outline, same brand-cyan accent on hover, same JetBrains
   * Mono 10 typography. Click opens a {@link JPopupMenu} with all available
   * locales rendered in their own language ({@code Locale.getDisplayName(loc)}),
   * so a Russian user sees "Русский" and a Japanese user sees "日本語" — no
   * need to know the locale code.
   *
   * <p>Locales kept here in sync with {@code scripts/translate-bundles.py}'s
   * LOCALE_MAP so this list updates whenever a new bundle is staged. The
   * Tamil bundle ({@code ta_IN}) is included even though our Google
   * Translate pipeline doesn't auto-translate it: RaiMan's hand-translated
   * IDE_ta_IN.properties is preserved as-is, so users can still pick it.
   */
  static class LanguagePicker extends JComponent {
    /** Locales selectable by the user. Mirror of LOCALE_MAP in the
     *  translate-bundles.py script + en_US source + ta_IN RaiMan-curated. */
    private static final java.util.List<java.util.Locale> AVAILABLE = java.util.List.of(
        new java.util.Locale("en", "US"),
        new java.util.Locale("fr"),
        new java.util.Locale("de"),
        new java.util.Locale("es"),
        new java.util.Locale("it"),
        new java.util.Locale("nl"),
        new java.util.Locale("pt", "BR"),
        new java.util.Locale("pl"),
        new java.util.Locale("ru"),
        new java.util.Locale("uk"),
        new java.util.Locale("bg"),
        new java.util.Locale("ca"),
        new java.util.Locale("da"),
        new java.util.Locale("sv"),
        new java.util.Locale("tr"),
        new java.util.Locale("he"),
        new java.util.Locale("ar"),
        new java.util.Locale("ja"),
        new java.util.Locale("ko"),
        new java.util.Locale("zh", "CN"),
        new java.util.Locale("zh", "TW"),
        new java.util.Locale("ta", "IN"),
        new java.util.Locale("hi"),
        new java.util.Locale("bn"),
        new java.util.Locale("te")
    );

    private java.util.Locale current;
    private boolean hover;
    private final java.util.function.Consumer<java.util.Locale> onChange;

    LanguagePicker(java.util.Locale initial, java.util.function.Consumer<java.util.Locale> onChange) {
      this.current = initial != null ? initial : java.util.Locale.ENGLISH;
      this.onChange = onChange;
      setPreferredSize(new Dimension(132, 26));
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      addMouseListener(new MouseAdapter() {
        @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
        @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
        @Override public void mouseClicked(MouseEvent e) { showPopup(); }
      });
    }

    void setSelectedLocale(java.util.Locale loc) {
      this.current = loc;
      repaint();
    }

    /**
     * Builds the popup lazily on each click so the labels reflect the
     * latest installed locales (in case bundles get added at runtime via
     * the future extensions system). 25 locales would overflow a single
     * vertical popup on smaller screens, so we group them by region:
     * Latin Europe, Slavic / Eastern Europe, Middle East, East Asia,
     * Indian subcontinent. Each region opens as a sub-menu on hover.
     *
     * <p>The font for each item is the JVM's logical "Dialog" font —
     * Java's font-rendering pipeline auto-falls-back through the OS
     * font registry (Segoe UI on Windows, Helvetica on macOS, DejaVu on
     * Linux) so CJK / Arabic / Hebrew / Devanagari / Tamil / Bengali /
     * Telugu / Cyrillic glyphs render correctly. Without this, the
     * brand-set OculixFonts (Latin-only Inter clones) would show empty
     * boxes for every non-Latin script.
     */
    private void showPopup() {
      JPopupMenu menu = new JPopupMenu();
      Font itemFont = new Font(Font.DIALOG, Font.PLAIN, 12);

      // Region grouping — keeps the top-level popup short and lets
      // users scan by language family instead of a flat 25-row list.
      // Region grouping. Each submenu carries an SVG icon (Apple Silicon
      // Swing renders Unicode flag emojis as tofu; SVG bypasses the font
      // fallback entirely).
      addRegionSubmenu(menu, itemFont, _I("languageRegionLatinEurope"),
          new FlatSVGIcon("icons/menu/globe-europe.svg", 16, 16),
          "en", "en_US", "fr", "de", "es", "it", "nl", "pt", "pt_BR",
          "sv", "da", "ca");
      addRegionSubmenu(menu, itemFont, _I("languageRegionSlavicEurope"),
          new FlatSVGIcon("icons/menu/globe-europe.svg", 16, 16),
          "pl", "ru", "uk", "bg", "tr");
      addRegionSubmenu(menu, itemFont, _I("languageRegionMiddleEast"),
          new FlatSVGIcon("icons/menu/globe-europe.svg", 16, 16),
          "he", "ar");
      addRegionSubmenu(menu, itemFont, _I("languageRegionEastAsia"),
          new FlatSVGIcon("icons/menu/globe-asia.svg", 16, 16),
          "ja", "ko", "zh", "zh_CN", "zh_TW");
      addRegionSubmenu(menu, itemFont, _I("languageRegionIndianSubcontinent"),
          new FlatSVGIcon("icons/menu/globe-asia.svg", 16, 16),
          "hi", "bn", "te", "ta", "ta_IN");

      // Allow selecting any AVAILABLE locale that wasn't covered above
      // (defensive — none today, but keeps the picker honest if locales
      // get added without a region tag).
      java.util.Set<String> covered = new java.util.HashSet<>();
      for (java.util.Locale loc : AVAILABLE) covered.add(localeKey(loc));
      // (no-op for now; the regions above already cover AVAILABLE)

      menu.show(this, 0, getHeight());
    }

    /** Build one sub-menu of locales picked by language code from
     *  {@link #AVAILABLE}. The active locale is marked with bold + brand
     *  green rather than a Unicode bullet (● renders as tofu on macOS
     *  Apple Silicon Swing — same class as the emoji glyphs). */
    private void addRegionSubmenu(JPopupMenu parent, Font font,
                                  String label, javax.swing.Icon icon,
                                  String... codes) {
      JMenu region = new JMenu(label);
      region.setFont(font);
      if (icon != null) region.setIcon(icon);
      java.util.Set<String> wanted = new java.util.HashSet<>(java.util.Arrays.asList(codes));
      for (java.util.Locale loc : AVAILABLE) {
        if (!wanted.contains(localeKey(loc))) continue;
        String displayLabel = loc.getDisplayName(loc);
        if (displayLabel == null || displayLabel.isEmpty()) {
          displayLabel = loc.toLanguageTag();
        } else {
          displayLabel = Character.toUpperCase(displayLabel.charAt(0))
              + displayLabel.substring(1);
        }
        boolean isActive = loc.equals(current)
            || (loc.getLanguage().equals(current.getLanguage())
                && java.util.Objects.equals(loc.getCountry(), current.getCountry()));
        JMenuItem item = new JMenuItem(displayLabel);
        item.setFont(isActive ? font.deriveFont(Font.BOLD) : font);
        if (isActive) item.setForeground(OculixColors.OX_LIME_400);
        final java.util.Locale picked = loc;
        item.addActionListener(e -> {
          if (onChange != null) onChange.accept(picked);
        });
        region.add(item);
      }
      if (region.getItemCount() > 0) parent.add(region);
    }

    /** Compose a stable key for matching a Locale against the region
     *  string codes (handles language-only and language_COUNTRY forms). */
    private static String localeKey(java.util.Locale loc) {
      String c = loc.getCountry();
      return (c == null || c.isEmpty()) ? loc.getLanguage()
          : loc.getLanguage() + "_" + c;
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

      int w = getWidth();
      int h = getHeight();
      int arc = h;

      // Outer pill — brand-cyan accent on hover, ink-500 otherwise.
      // Mirrors ThemePillSwitch's hover affordance.
      g2.setColor(hover
          ? OculixColors.OX_CYAN_500
          : OculixColors.withAlpha(OculixColors.OX_INK_500, 200));
      g2.setStroke(new BasicStroke(1f));
      g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

      // Subtle hover fill so the pill reads as "interactive" without
      // shouting like a primary CTA.
      if (hover) {
        g2.setColor(OculixColors.withAlpha(OculixColors.OX_CYAN_500, 30));
        g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
      }

      // Compose: SVG globe icon + locale code (uppercase, ASCII-safe).
      // The former "🌐 + code + ▾" glyph string rendered as tofu on macOS
      // Apple Silicon Swing; SVG bypasses the font fallback entirely.
      String code = current.getCountry().isEmpty()
          ? current.getLanguage().toUpperCase(java.util.Locale.ROOT)
          : current.getLanguage().toUpperCase(java.util.Locale.ROOT)
              + "-" + current.getCountry().toUpperCase(java.util.Locale.ROOT);

      g2.setFont(applyTracking(OculixFonts.mono(10), 0.16f).deriveFont(Font.BOLD));
      FontMetrics fm = g2.getFontMetrics();
      g2.setColor(hover ? OculixColors.OX_INK_100 : OculixColors.OX_INK_300);

      int iconSize = 14;
      int iconGap = 6;
      int textW = fm.stringWidth(code);
      int totalW = iconSize + iconGap + textW;
      int startX = (w - totalW) / 2;
      int iconY = (h - iconSize) / 2;
      int textY = h / 2 + fm.getAscent() / 2 - 2;

      FlatSVGIcon globe = new FlatSVGIcon("icons/menu/globe-meridians.svg", iconSize, iconSize);
      globe.paintIcon(this, g2, startX, iconY);
      g2.drawString(code, startX + iconSize + iconGap, textY);

      g2.dispose();
    }
  }
}
