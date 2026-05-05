/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import net.miginfocom.swing.MigLayout;
import org.sikuli.ide.theme.OculixColors;
import org.sikuli.ide.theme.OculixFonts;
import org.sikuli.support.Commons;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.util.HashMap;
import java.util.Map;

/**
 * Welcome panel displayed when no script is open.
 *
 * <p>Layout (top → bottom, all left-aligned in a centered column):
 * <ol>
 *   <li>Eyebrow kicker — VISUAL AUTOMATION · v3.0.3-rc4 in JetBrains Mono cyan</li>
 *   <li>Hero quote — RaiMan's original SikuliX1 description, in Fraunces italic</li>
 *   <li>Attribution line — "— RaiMan, SikuliX1" in small italic</li>
 *   <li>OculiX-adds box — what this fork brings on top (VNC, Modern Recorder,
 *       bundled natives), framed as "extension", not "replacement"</li>
 *   <li>Primary CTAs — New script (cyan) + Open script (ghost) + shortcuts</li>
 *   <li>Secondary grid — workspace + recorder + capture, mono shortcut hints</li>
 *   <li>Footer — version · license · external links</li>
 * </ol>
 *
 * <p>Background: subtle radial gradient haze (violet upper-left, cyan upper-right)
 * painted in {@link #paintComponent(Graphics)}. Cached to a BufferedImage on
 * resize so we don't repaint the gradient on every event.
 */
public class WelcomeTab extends JPanel {

  private final ActionListener onNew;
  private final ActionListener onOpen;
  private final ActionListener onNewWorkspace;
  private final ActionListener onOpenWorkspace;

  private java.awt.image.BufferedImage hazeCache;
  private java.awt.image.BufferedImage geckoCache;
  private static java.awt.image.BufferedImage geckoSource;

  public WelcomeTab(ActionListener onNew, ActionListener onOpen,
                    ActionListener onNewWorkspace, ActionListener onOpenWorkspace) {
    this.onNew = onNew;
    this.onOpen = onOpen;
    this.onNewWorkspace = onNewWorkspace;
    this.onOpenWorkspace = onOpenWorkspace;
    setLayout(new MigLayout("fill, wrap 1", "[center]", "push[]push"));
    setOpaque(true);
    // Welcome is the brand surface: always navy + cyan/violet haze, even when
    // the user picks the OculiX Light theme elsewhere. The home page is the
    // brand statement — it doesn't follow the chrome theme.
    setBackground(OculixColors.OX_INK_900);
    buildUI();
  }

  private void buildUI() {
    // Centered column of content with a max width for readability
    JPanel column = new JPanel(new MigLayout(
        "wrap 1, insets 32 48 32 48, gap 0", "[grow, fill, 600!]", ""));
    column.setOpaque(false);

    // ── Eyebrow ──
    JLabel eyebrow = new JLabel(("VISUAL AUTOMATION · v" + Commons.getSXVersionShort()).toUpperCase());
    eyebrow.setFont(applyTracking(OculixFonts.mono(11), 0.20f).deriveFont(Font.BOLD));
    eyebrow.setForeground(OculixColors.OX_CYAN_500);
    column.add(eyebrow, "gapbottom 18");

    // ── Hero quote (RaiMan's words from the SikuliX1 README) ──
    String hero = "<html><div style='line-height:1.15'>"
        + "SikuliX automates anything you<br>"
        + "see on the screen."
        + "</div></html>";
    JLabel heroLabel = new JLabel(hero);
    heroLabel.setFont(OculixFonts.display(40));
    heroLabel.setForeground(UIManager.getColor("Label.foreground"));
    column.add(heroLabel, "gapbottom 12");

    String body = "<html><div style='width:560px; line-height:1.5'>"
        + "It uses image recognition powered by OpenCV to identify GUI components, "
        + "and acts on them with mouse and keyboard. Handy when there is no easy "
        + "access to a GUI's internals or to the source code."
        + "</div></html>";
    JLabel bodyLabel = new JLabel(body);
    bodyLabel.setFont(OculixFonts.ui(14));
    bodyLabel.setForeground(OculixColors.OX_INK_200);
    column.add(bodyLabel, "gapbottom 6");

    JLabel attribution = new JLabel("— RaiMan, SikuliX1");
    attribution.setFont(OculixFonts.ui(12).deriveFont(Font.ITALIC));
    attribution.setForeground(OculixColors.OX_INK_400);
    column.add(attribution, "gapbottom 24");

    // ── OculiX-adds box ──
    column.add(new OculixAddsBox(), "growx, gapbottom 28");

    // ── Primary CTAs ──
    JPanel primaryCtas = new JPanel(new MigLayout("insets 0, gap 12", "[]12[]push"));
    primaryCtas.setOpaque(false);
    primaryCtas.add(new HeroButton("+  New script", "Ctrl+N", true, onNew));
    primaryCtas.add(new HeroButton("↗  Open script", "Ctrl+O", false, onOpen));
    column.add(primaryCtas, "gapbottom 18");

    // ── Secondary grid ──
    JPanel secondary = new JPanel(new MigLayout("insets 0, wrap 2, gap 6 24", "[grow, fill][grow, fill]", ""));
    secondary.setOpaque(false);
    secondary.add(new SecondaryRow("⊞  New workspace", "Ctrl+Shift+N", onNewWorkspace));
    secondary.add(new SecondaryRow("↓  Open workspace", "Ctrl+Shift+O", onOpenWorkspace));
    column.add(secondary, "gapbottom 32");

    // ── Footer ──
    JPanel footer = new JPanel(new MigLayout("insets 0, gap 14", "[]14[]14[]push[]14[]14[]"));
    footer.setOpaque(false);
    footer.add(footerText("v" + Commons.getSXVersionShort()));
    footer.add(footerSep());
    footer.add(footerText("MIT"));
    footer.add(footerSep());
    footer.add(footerText("fork of SikuliX1"));
    footer.add(footerLink("Docs", "https://github.com/oculix-org/Oculix/wiki"));
    footer.add(footerLink("Release notes", "https://github.com/oculix-org/Oculix/releases"));
    footer.add(footerLink("github.com/oculix-org", "https://github.com/oculix-org/Oculix"));
    column.add(footer, "growx");

    add(column);
  }

  // ── Footer helpers ──────────────────────────────────────────────

  private static JLabel footerText(String s) {
    JLabel l = new JLabel(s);
    l.setFont(OculixFonts.mono(11));
    l.setForeground(OculixColors.OX_INK_400);
    return l;
  }

  private static JLabel footerSep() {
    JLabel l = new JLabel("·");
    l.setFont(OculixFonts.mono(11));
    l.setForeground(OculixColors.OX_INK_500);
    return l;
  }

  private static JLabel footerLink(String label, String url) {
    JLabel l = new JLabel(label + " ↗");
    l.setFont(OculixFonts.mono(11));
    l.setForeground(OculixColors.OX_CYAN_300);
    l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    l.addMouseListener(new MouseAdapter() {
      @Override public void mouseClicked(MouseEvent e) {
        try { Desktop.getDesktop().browse(java.net.URI.create(url)); } catch (Exception ignored) {}
      }
    });
    return l;
  }

  private static Font applyTracking(Font base, float tracking) {
    Map<TextAttribute, Object> attrs = new HashMap<>();
    attrs.put(TextAttribute.TRACKING, tracking);
    return base.deriveFont(attrs);
  }

  // ── Background haze paint ────────────────────────────────────────

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    int w = getWidth();
    int h = getHeight();
    if (w <= 0 || h <= 0) return;
    if (hazeCache == null || hazeCache.getWidth() != w || hazeCache.getHeight() != h) {
      hazeCache = renderHaze(w, h);
      geckoCache = renderGecko(w, h);
    }
    g.drawImage(hazeCache, 0, 0, null);
    if (geckoCache != null) {
      g.drawImage(geckoCache, 0, 0, null);
    }
  }

  /**
   * Loads the gecko mascot once per JVM, then composes it on the right edge
   * of the panel at 28% alpha — fills the empty space without competing
   * with the hero text. Cached at panel size, regenerated only on resize.
   */
  private java.awt.image.BufferedImage renderGecko(int w, int h) {
    if (geckoSource == null) {
      try {
        java.net.URL url = WelcomeTab.class.getResource("/icons/gecko_cyclope.png");
        if (url != null) geckoSource = javax.imageio.ImageIO.read(url);
      } catch (Exception ignored) {
        return null;
      }
    }
    if (geckoSource == null) return null;

    java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(
        w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = out.createGraphics();
    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    // Target ~62% of panel height, capped at 580px so it doesn't scream on
    // big screens. Anchored on the right edge with a comfortable margin.
    int targetH = Math.min((int) (h * 0.62), 580);
    double scale = (double) targetH / geckoSource.getHeight();
    int targetW = (int) (geckoSource.getWidth() * scale);
    int x = w - targetW + targetW / 6;       // bleed slightly off the right edge
    int y = (h - targetH) / 2;

    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.28f));
    g2.drawImage(geckoSource, x, y, targetW, targetH, null);
    g2.dispose();
    return out;
  }

  private static java.awt.image.BufferedImage renderHaze(int w, int h) {
    java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
        w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = img.createGraphics();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    // Violet haze upper-left
    g2.setPaint(new RadialGradientPaint(
        new Point(w / 4, h / 4),
        Math.max(w, h) * 0.55f,
        new float[]{0f, 1f},
        new Color[]{
            OculixColors.withAlpha(OculixColors.OX_VIOLET_500, 70),
            OculixColors.withAlpha(OculixColors.OX_VIOLET_500, 0)
        }));
    g2.fillRect(0, 0, w, h);

    // Cyan haze upper-right
    g2.setPaint(new RadialGradientPaint(
        new Point((int) (w * 0.78), h / 4),
        Math.max(w, h) * 0.5f,
        new float[]{0f, 1f},
        new Color[]{
            OculixColors.withAlpha(OculixColors.OX_CYAN_500, 60),
            OculixColors.withAlpha(OculixColors.OX_CYAN_500, 0)
        }));
    g2.fillRect(0, 0, w, h);

    g2.dispose();
    return img;
  }

  // ── Inner: OculiX-adds box ──────────────────────────────────────

  private static class OculixAddsBox extends JPanel {
    OculixAddsBox() {
      super(new MigLayout("wrap 1, insets 16 18 16 18, gap 6", "[grow, fill]", ""));
      setOpaque(false);

      JLabel header = new JLabel(applyTracking(OculixFonts.mono(10), 0.18f) == null ? "" : "WHAT OCULIX ADDS");
      header.setFont(applyTracking(OculixFonts.mono(10), 0.18f).deriveFont(Font.BOLD));
      header.setForeground(OculixColors.OX_INK_300);
      add(header, "gapbottom 6");

      add(bullet("VNC remote screens",
          "mainframes (3270/5250), AS/400, Citrix sessions, datacenter machines without local desktop access"));
      add(bullet("Modern Recorder",
          "visual capture with multi-language script generation (Python, Java, Robot Framework)"));
      add(bullet("Bundled OCR & OpenCV",
          "works on any Linux distro, no apt install required"));
    }

    private static JComponent bullet(String title, String desc) {
      JPanel row = new JPanel(new MigLayout("insets 0, gap 8", "[12!][grow, fill]", ""));
      row.setOpaque(false);
      JLabel dot = new JLabel("•");
      dot.setFont(OculixFonts.uiBold(13));
      dot.setForeground(OculixColors.OX_CYAN_500);
      row.add(dot, "aligny top");
      JLabel text = new JLabel("<html><b style='color:#E6EAFB'>" + title + "</b>"
          + " <span style='color:#B9C2E8'>— " + desc + "</span></html>");
      text.setFont(OculixFonts.ui(12));
      row.add(text, "growx");
      return row;
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int w = getWidth();
      int h = getHeight();
      int arc = 10;
      g2.setColor(OculixColors.withAlpha(OculixColors.OX_INK_700, 110));
      g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
      g2.setColor(OculixColors.withAlpha(OculixColors.OX_INK_500, 200));
      g2.setStroke(new BasicStroke(1f));
      g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
      g2.dispose();
      super.paintComponent(g);
    }
  }

  // ── Inner: HeroButton (primary CTA) ─────────────────────────────

  private static class HeroButton extends JPanel {
    private final boolean primary;
    private boolean hover;

    HeroButton(String label, String shortcut, boolean primary, ActionListener action) {
      super(new MigLayout("insets 10 16 10 16, gap 12", "[]push[]"));
      this.primary = primary;
      setOpaque(false);
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

      JLabel l = new JLabel(label);
      l.setFont(OculixFonts.uiBold(13));
      l.setForeground(primary ? new Color(0x00131F) : OculixColors.OX_INK_100);
      add(l);

      JLabel s = new JLabel(shortcut);
      s.setFont(OculixFonts.mono(10));
      s.setForeground(primary ? OculixColors.withAlpha(new Color(0x00131F), 180) : OculixColors.OX_INK_400);
      add(s);

      addMouseListener(new MouseAdapter() {
        @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
        @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
        @Override public void mouseClicked(MouseEvent e) {
          if (action != null) action.actionPerformed(null);
        }
      });
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int w = getWidth();
      int h = getHeight();
      int arc = h;
      if (primary) {
        g2.setColor(hover ? OculixColors.OX_CYAN_300 : OculixColors.OX_CYAN_500);
        g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
        // Subtle glow
        for (int i = 0; i < 3; i++) {
          g2.setColor(OculixColors.withAlpha(OculixColors.OX_CYAN_500, 40 - i * 12));
          g2.setStroke(new BasicStroke(1f + i * 0.6f));
          g2.drawRoundRect(-i, -i, w - 1 + 2 * i, h - 1 + 2 * i, arc + 2 * i, arc + 2 * i);
        }
      } else {
        g2.setColor(hover
            ? OculixColors.withAlpha(OculixColors.OX_INK_500, 80)
            : OculixColors.withAlpha(OculixColors.OX_INK_700, 60));
        g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
        g2.setColor(OculixColors.withAlpha(OculixColors.OX_INK_500, 200));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
      }
      g2.dispose();
      super.paintComponent(g);
    }
  }

  // ── Inner: Secondary action row ─────────────────────────────────

  private static class SecondaryRow extends JPanel {
    private boolean hover;

    SecondaryRow(String label, String shortcut, ActionListener action) {
      super(new MigLayout("insets 8 12 8 12, gap 10", "[]push[]"));
      setOpaque(false);
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

      JLabel l = new JLabel(label);
      l.setFont(OculixFonts.ui(13));
      l.setForeground(OculixColors.OX_INK_100);
      add(l);

      JLabel s = new JLabel(shortcut);
      s.setFont(OculixFonts.mono(10));
      s.setForeground(OculixColors.OX_INK_400);
      add(s);

      addMouseListener(new MouseAdapter() {
        @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
        @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
        @Override public void mouseClicked(MouseEvent e) {
          if (action != null) action.actionPerformed(null);
        }
      });
    }

    @Override
    protected void paintComponent(Graphics g) {
      if (hover) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(OculixColors.withAlpha(OculixColors.OX_INK_700, 120));
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
        g2.dispose();
      }
      super.paintComponent(g);
    }
  }
}
