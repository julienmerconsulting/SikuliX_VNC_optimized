/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Workspace explorer showing open scripts as visual cards.
 * Each card represents a script (.sikuli bundle or temp script).
 * Clicking a card activates the corresponding editor tab.
 */
public class ScriptExplorer extends JPanel {

  private JPanel cardContainer;
  private JLabel titleLabel;
  private JLabel emptyLabel;
  private JScrollPane scrollPane;
  private final List<ScriptCard> cards = new ArrayList<>();
  private int activeIndex = -1;

  // Callbacks
  private ActionListener onCardSelected;
  private ActionListener onCardRenamed;

  public ScriptExplorer() {
    setLayout(new BorderLayout());
    setMinimumSize(new Dimension(160, 0));
    setPreferredSize(new Dimension(200, 0));

    // Header
    JPanel header = new JPanel(new MigLayout("insets 6 10 6 10, fill", "[grow]", "[]"));
    header.setOpaque(false);
    titleLabel = new JLabel("Workspace");
    titleLabel.setFont(UIManager.getFont("defaultFont").deriveFont(Font.BOLD, 12f));
    header.add(titleLabel);
    add(header, BorderLayout.NORTH);

    // Card container (scrollable)
    cardContainer = new JPanel(new MigLayout("wrap 1, insets 4 6 4 6, gap 4", "[fill, grow]", ""));
    cardContainer.setOpaque(false);

    scrollPane = new JScrollPane(cardContainer);
    scrollPane.setBorder(null);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.getVerticalScrollBar().setUnitIncrement(12);
    scrollPane.setOpaque(false);
    scrollPane.getViewport().setOpaque(false);

    // Empty state
    emptyLabel = new JLabel("<html><center>No scripts open<br><br><span style='font-size:9px'>Use File \u25B8 New<br>to create a script</span></center></html>");
    emptyLabel.setFont(UIManager.getFont("defaultFont").deriveFont(11f));
    emptyLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
    emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);

    add(emptyLabel, BorderLayout.CENTER);
  }

  public void setOnCardSelected(ActionListener listener) {
    this.onCardSelected = listener;
  }

  public void setWorkspaceName(String name) {
    titleLabel.setText(name != null ? name : "Workspace");
  }

  public void setOnCardRenamed(ActionListener listener) {
    this.onCardRenamed = listener;
  }

  /**
   * Rebuilds the card list from the given script contexts.
   * Call this whenever tabs change.
   */
  public void updateScripts(List<ScriptInfo> scripts, int selectedIndex) {
    cards.clear();
    cardContainer.removeAll();

    if (scripts.isEmpty()) {
      remove(scrollPane);
      remove(emptyLabel);
      add(emptyLabel, BorderLayout.CENTER);
      activeIndex = -1;
      revalidate();
      repaint();
      return;
    }

    remove(emptyLabel);
    remove(scrollPane);

    for (int i = 0; i < scripts.size(); i++) {
      ScriptInfo info = scripts.get(i);
      ScriptCard card = new ScriptCard(info, i);
      cards.add(card);
      cardContainer.add(card, "growx");
    }

    setActiveCard(selectedIndex);

    add(scrollPane, BorderLayout.CENTER);
    revalidate();
    repaint();
  }

  /**
   * Highlights the card at the given index.
   */
  public void setActiveCard(int index) {
    activeIndex = index;
    for (int i = 0; i < cards.size(); i++) {
      cards.get(i).setActive(i == index);
    }
  }

  /**
   * Updates a single card's info (e.g. after save/rename).
   */
  public void updateCard(int index, ScriptInfo info) {
    if (index >= 0 && index < cards.size()) {
      cards.get(index).updateInfo(info);
    }
  }

  // ── ScriptCard ──

  class ScriptCard extends JPanel {
    private final int index;
    private JLabel nameLabel;
    private JLabel infoLabel;
    private JLabel statusLabel;
    private boolean active = false;
    private final Color hoverBg;
    private final Color activeBg;
    private final Color normalBg;

    ScriptCard(ScriptInfo info, int index) {
      this.index = index;
      setLayout(new MigLayout("insets 8 10 8 10, gap 4", "[grow][]", "[]2[]"));
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

      normalBg = UIManager.getColor("Panel.background");
      hoverBg = UIManager.getColor("List.selectionInactiveBackground");
      activeBg = UIManager.getColor("List.selectionBackground");

      setOpaque(true);
      setBackground(normalBg);
      setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1),
          BorderFactory.createEmptyBorder(0, 0, 0, 0)));

      // Script icon + name
      nameLabel = new JLabel();
      nameLabel.setFont(UIManager.getFont("defaultFont").deriveFont(Font.BOLD, 12f));
      add(nameLabel, "growx");

      // Save status
      statusLabel = new JLabel();
      statusLabel.setFont(UIManager.getFont("small.font"));
      add(statusLabel, "wrap");

      // Image count
      infoLabel = new JLabel();
      infoLabel.setFont(UIManager.getFont("small.font"));
      infoLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
      add(infoLabel, "span 2");

      updateInfo(info);

      // Right-click context menu
      JPopupMenu popup = new JPopupMenu();
      JMenuItem renameItem = new JMenuItem("Rename");
      renameItem.addActionListener(ev -> {
        if (onCardRenamed != null) {
          String newName = JOptionPane.showInputDialog(ScriptExplorer.this,
              "New name:", info.name);
          if (newName != null && !newName.trim().isEmpty()) {
            onCardRenamed.actionPerformed(
                new java.awt.event.ActionEvent(ScriptCard.this, index, newName.trim()));
          }
        }
      });
      popup.add(renameItem);

      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (SwingUtilities.isRightMouseButton(e)) {
            popup.show(ScriptCard.this, e.getX(), e.getY());
          } else if (onCardSelected != null) {
            onCardSelected.actionPerformed(
                new java.awt.event.ActionEvent(ScriptCard.this, index, "select"));
          }
        }

        @Override
        public void mouseEntered(MouseEvent e) {
          if (!active) {
            setBackground(hoverBg);
          }
        }

        @Override
        public void mouseExited(MouseEvent e) {
          setBackground(active ? activeBg : normalBg);
        }
      });
    }

    void updateInfo(ScriptInfo info) {
      nameLabel.setText("\uD83D\uDCC4 " + info.name);
      nameLabel.setToolTipText(info.path);

      if (info.saved) {
        statusLabel.setText("\u2713");
        statusLabel.setForeground(new Color(0x3D, 0xDB, 0xA4));
        statusLabel.setToolTipText("Saved");
      } else {
        statusLabel.setText("*");
        statusLabel.setForeground(new Color(0xFF, 0xAA, 0x33));
        statusLabel.setToolTipText("Not saved");
      }

      String imgText = info.imageCount + " image" + (info.imageCount != 1 ? "s" : "");
      infoLabel.setText("\uD83D\uDDBC " + imgText);
    }

    void setActive(boolean active) {
      this.active = active;
      setBackground(active ? activeBg : normalBg);
      if (active) {
        nameLabel.setForeground(UIManager.getColor("List.selectionForeground"));
        infoLabel.setForeground(UIManager.getColor("List.selectionForeground"));
      } else {
        nameLabel.setForeground(UIManager.getColor("Label.foreground"));
        infoLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
      }
    }
  }

  // ── ScriptInfo (data class) ──

  public static class ScriptInfo {
    public final String name;
    public final String path;
    public final int imageCount;
    public final boolean saved;

    public ScriptInfo(String name, String path, int imageCount, boolean saved) {
      this.name = name;
      this.path = path;
      this.imageCount = imageCount;
      this.saved = saved;
    }

    public static ScriptInfo fromFolder(String name, File folder, boolean isTemp) {
      int imgCount = 0;
      String path = "";
      if (folder != null && folder.exists()) {
        File[] pngs = folder.listFiles((dir, fn) -> fn.endsWith(".png"));
        if (pngs != null) imgCount = pngs.length;
        path = folder.getAbsolutePath();
      }
      return new ScriptInfo(name, path, imgCount, !isTemp);
    }
  }
}
