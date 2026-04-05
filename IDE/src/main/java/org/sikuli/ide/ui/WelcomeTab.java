/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import net.miginfocom.swing.MigLayout;
import org.sikuli.support.Commons;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;

/**
 * Welcome panel displayed when no script is open.
 * Shows quick actions (New, Open), status info, and branding.
 */
public class WelcomeTab extends JPanel {

  private ActionListener onNew;
  private ActionListener onOpen;
  private ActionListener onNewWorkspace;
  private ActionListener onOpenWorkspace;

  public WelcomeTab(ActionListener onNew, ActionListener onOpen,
                     ActionListener onNewWorkspace, ActionListener onOpenWorkspace) {
    this.onNew = onNew;
    this.onOpen = onOpen;
    this.onNewWorkspace = onNewWorkspace;
    this.onOpenWorkspace = onOpenWorkspace;
    setLayout(new MigLayout("fill, wrap 1", "[center]", "push[]20[]20[]20[]push"));
    setOpaque(true);
    setBackground(UIManager.getColor("Panel.background"));
    buildUI();
  }

  private void buildUI() {
    // Logo and title
    JPanel headerPanel = new JPanel(new MigLayout("wrap 1, insets 0", "[center]"));
    headerPanel.setOpaque(false);

    URL iconUrl = getClass().getResource("/icons/sikulix-red.png");
    if (iconUrl != null) {
      ImageIcon logo = new ImageIcon(iconUrl);
      int targetWidth = 280;
      int targetHeight = (int) ((double) logo.getIconHeight() / logo.getIconWidth() * targetWidth);
      Image scaled = logo.getImage().getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
      JLabel logoLabel = new JLabel(new ImageIcon(scaled));
      headerPanel.add(logoLabel);
    }

    JLabel title = new JLabel("OculiX IDE");
    title.setFont(UIManager.getFont("h1.font"));
    headerPanel.add(title);

    JLabel subtitle = new JLabel("Visual Automation — Powered by SikuliX");
    subtitle.setFont(UIManager.getFont("h4.font"));
    subtitle.setForeground(UIManager.getColor("Label.disabledForeground"));
    headerPanel.add(subtitle);

    add(headerPanel);

    // Quick Start section
    JPanel quickStartPanel = new JPanel(new MigLayout("wrap 1, insets 20 40 20 40, gap 8", "[fill, 300]"));
    quickStartPanel.setOpaque(false);
    quickStartPanel.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground"), 1),
        BorderFactory.createEmptyBorder(16, 16, 16, 16)));

    JLabel quickStartLabel = new JLabel("Quick Start");
    quickStartLabel.setFont(UIManager.getFont("h3.font"));
    quickStartPanel.add(quickStartLabel, "gapbottom 8");

    quickStartPanel.add(createActionLink("\uD83D\uDCC4  New Script", "Ctrl+N", onNew));
    quickStartPanel.add(createActionLink("\uD83D\uDCC2  Open Script", "Ctrl+O", onOpen));
    quickStartPanel.add(new JSeparator(), "growx, gaptop 8, gapbottom 8");
    quickStartPanel.add(createActionLink("\uD83D\uDCC1  New Workspace", "", onNewWorkspace));
    quickStartPanel.add(createActionLink("\uD83D\uDCC2  Open Workspace", "", onOpenWorkspace));

    add(quickStartPanel);

    // Version footer
    JLabel versionFooter = new JLabel("v" + Commons.getSXVersionShort() + " — MIT License");
    versionFooter.setFont(UIManager.getFont("small.font"));
    versionFooter.setForeground(UIManager.getColor("Label.disabledForeground"));
    add(versionFooter);
  }

  private JPanel createActionLink(String text, String shortcut, ActionListener action) {
    JPanel link = new JPanel(new MigLayout("insets 6 12 6 12, fill", "[]push[]"));
    link.setOpaque(false);
    link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    JLabel label = new JLabel(text);
    label.setFont(UIManager.getFont("defaultFont").deriveFont(14.0f));
    link.add(label);

    JLabel shortcutLabel = new JLabel(shortcut);
    shortcutLabel.setFont(UIManager.getFont("small.font"));
    shortcutLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
    link.add(shortcutLabel);

    link.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        link.setOpaque(true);
        link.setBackground(UIManager.getColor("List.selectionBackground"));
        link.repaint();
      }

      @Override
      public void mouseExited(MouseEvent e) {
        link.setOpaque(false);
        link.repaint();
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        if (action != null) {
          action.actionPerformed(null);
        }
      }
    });

    return link;
  }
}
