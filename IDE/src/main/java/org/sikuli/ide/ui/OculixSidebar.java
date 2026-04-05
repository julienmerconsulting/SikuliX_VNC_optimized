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

import static org.sikuli.support.ide.SikuliIDEI18N._I;

/**
 * Vertical sidebar replacing the classic JMenuBar + JToolBar.
 * Contains action buttons (Run, Stop, Capture, Record) and navigation
 * items (File, Edit, Tools, Help) with popup submenus.
 */
public class OculixSidebar extends JPanel {

  private boolean collapsed = false;
  private int expandedWidth = 200;
  private int collapsedWidth = 50;

  // Action buttons
  private SidebarItem btnRun;
  private SidebarItem btnRunSlow;
  private SidebarItem btnCapture;
  private SidebarItem btnRecord;

  // Navigation items
  private SidebarItem navFile;
  private SidebarItem navEdit;
  private SidebarItem navTools;
  private SidebarItem navHelp;

  // Submenus
  private SidebarSubmenu fileSubmenu;
  private SidebarSubmenu editSubmenu;
  private SidebarSubmenu toolsSubmenu;
  private SidebarSubmenu helpSubmenu;

  // Theme toggle
  private SidebarItem btnTheme;
  private boolean isDark = true;

  // Version label
  private JLabel versionLabel;

  // Panels for layout
  private JPanel topSection;
  private JPanel bottomSection;
  private JPanel footerSection;

  public OculixSidebar() {
    setLayout(new MigLayout("wrap 1, insets 8, fill", "[fill]", "[]0[]push[]0[]"));
    setPreferredSize(new Dimension(expandedWidth, 0));
    setMinimumSize(new Dimension(collapsedWidth, 0));
    putClientProperty(FlatClientProperties.STYLE, "background: darken(@background, 3%)");

    initSections();
    addResizeHandle();
  }

  private void initSections() {
    // Top section: logo + action buttons
    topSection = new JPanel(new MigLayout("wrap 1, insets 4, gap 2", "[fill]"));
    topSection.setOpaque(false);

    JLabel logo = new JLabel("OculiX");
    logo.setFont(UIManager.getFont("h3.font"));
    logo.setHorizontalAlignment(SwingConstants.CENTER);
    logo.setBorder(BorderFactory.createEmptyBorder(8, 0, 12, 0));
    topSection.add(logo);

    topSection.add(new JSeparator());

    add(topSection, "growx");

    // Bottom section: navigation items
    bottomSection = new JPanel(new MigLayout("wrap 1, insets 4, gap 2", "[fill]"));
    bottomSection.setOpaque(false);
    add(bottomSection, "growx");

    // Footer section: theme toggle + version
    footerSection = new JPanel(new MigLayout("wrap 1, insets 4, gap 2", "[fill]"));
    footerSection.setOpaque(false);
    add(footerSection, "growx, dock south");
  }

  /**
   * Initializes the action buttons section.
   * Must be called after SikulixIDE creates its button actions.
   */
  public void initActionButtons(ActionListener runAction, ActionListener runSlowAction,
                                 ActionListener captureAction, ActionListener recordAction) {
    btnRun = new SidebarItem("Run",
        loadIcon("/icons/run_big_green.png", 18), runAction);
    btnRun.setToolTipText(_I("btnRunLabel"));
    btnRun.setMnemonic(java.awt.event.KeyEvent.VK_R);
    topSection.add(btnRun);

    btnRunSlow = new SidebarItem("Run Slow",
        loadIcon("/icons/runviz.png", 18), runSlowAction);
    btnRunSlow.setToolTipText(_I("btnRunSlowMotionLabel"));
    topSection.add(btnRunSlow);

    btnCapture = new SidebarItem("Capture",
        loadIcon("/icons/capture-small.png", 18), captureAction);
    btnCapture.setToolTipText(_I("btnCaptureLabel"));
    topSection.add(btnCapture);

    btnRecord = new SidebarItem("Record",
        loadIcon("/icons/record.png", 18), recordAction);
    btnRecord.setToolTipText(_I("btnRecordLabel"));
    topSection.add(btnRecord);

    topSection.add(new JSeparator());
  }

  /**
   * Initializes the navigation items with popup submenus.
   * File, Edit, Tools, Help — each opens a popup submenu on click.
   */
  public void initNavigation(SidebarSubmenu fileSub, SidebarSubmenu editSub,
                              SidebarSubmenu toolsSub, SidebarSubmenu helpSub) {
    this.fileSubmenu = fileSub;
    this.editSubmenu = editSub;
    this.toolsSubmenu = toolsSub;
    this.helpSubmenu = helpSub;

    navFile = createNavItem(_I("menuFile"), "\uD83D\uDCC1", fileSub);
    navFile.setMnemonic(java.awt.event.KeyEvent.VK_F);
    bottomSection.add(navFile);

    navEdit = createNavItem(_I("menuEdit"), "\u270F\uFE0F", editSub);
    navEdit.setMnemonic(java.awt.event.KeyEvent.VK_E);
    bottomSection.add(navEdit);

    navTools = createNavItem(_I("menuTool"), "\uD83D\uDD27", toolsSub);
    navTools.setMnemonic(java.awt.event.KeyEvent.VK_T);
    bottomSection.add(navTools);

    navHelp = createNavItem(_I("menuHelp"), "\u2753", helpSub);
    navHelp.setMnemonic(java.awt.event.KeyEvent.VK_H);
    bottomSection.add(navHelp);
  }

  private SidebarItem createNavItem(String text, String emoji, SidebarSubmenu submenu) {
    // Use a plain text icon as fallback since we don't have dedicated nav icons
    SidebarItem item = new SidebarItem(text, null);
    item.addActionListener(e -> submenu.showBelow(item));
    return item;
  }

  /**
   * Initializes the footer with theme toggle and version label.
   */
  public void initFooter(String version, ActionListener themeAction) {
    footerSection.add(new JSeparator());

    btnTheme = new SidebarItem("Dark / Light", null, e -> {
      toggleTheme();
      if (themeAction != null) {
        themeAction.actionPerformed(e);
      }
    });
    footerSection.add(btnTheme);

    versionLabel = new JLabel(version);
    versionLabel.setFont(UIManager.getFont("small.font"));
    versionLabel.setHorizontalAlignment(SwingConstants.CENTER);
    versionLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
    footerSection.add(versionLabel);
  }

  private void toggleTheme() {
    isDark = !isDark;
    try {
      if (isDark) {
        FlatDarkLaf.setup();
      } else {
        FlatLightLaf.setup();
      }
      FlatLaf.updateUI();
    } catch (Exception ex) {
      // Fallback: ignore theme switch failure
    }
  }

  public boolean isDarkTheme() {
    return isDark;
  }

  /**
   * Toggle between collapsed (icons only, 50px) and expanded (icons + labels, 180px) mode.
   */
  public void toggleCollapsed() {
    collapsed = !collapsed;
    setPreferredSize(new Dimension(collapsed ? collapsedWidth : expandedWidth, 0));

    // Update all SidebarItems
    for (Component c : topSection.getComponents()) {
      if (c instanceof SidebarItem) {
        ((SidebarItem) c).setCollapsed(collapsed);
      }
    }
    for (Component c : bottomSection.getComponents()) {
      if (c instanceof SidebarItem) {
        ((SidebarItem) c).setCollapsed(collapsed);
      }
    }
    for (Component c : footerSection.getComponents()) {
      if (c instanceof SidebarItem) {
        ((SidebarItem) c).setCollapsed(collapsed);
      }
    }

    // Hide/show version label and logo
    versionLabel.setVisible(!collapsed);
    Component logo = topSection.getComponent(0);
    if (logo instanceof JLabel) {
      ((JLabel) logo).setText(collapsed ? "OX" : "OculiX");
    }

    revalidate();
    repaint();
  }

  public boolean isCollapsed() {
    return collapsed;
  }

  private void addResizeHandle() {
    // Double-click on sidebar border toggles collapsed/expanded
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          toggleCollapsed();
        }
      }
    });
  }

  private ImageIcon loadIcon(String path, int size) {
    try {
      java.net.URL url = getClass().getResource(path);
      if (url != null) {
        ImageIcon icon = new ImageIcon(url);
        Image img = icon.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(img);
      }
    } catch (Exception e) {
      // ignore, return null
    }
    return null;
  }
}
