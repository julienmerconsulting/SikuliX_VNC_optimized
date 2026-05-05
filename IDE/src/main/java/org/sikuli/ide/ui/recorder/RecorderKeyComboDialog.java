/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui.recorder;

import net.miginfocom.swing.MigLayout;
import org.sikuli.support.ide.SikuliIDEI18N;

import javax.swing.*;
import java.awt.*;

/**
 * Modal dialog to build a key combination with modifier checkboxes
 * and a key dropdown. Generates a type(Key.X, KeyModifier.Y + ...) expression.
 */
public class RecorderKeyComboDialog extends JDialog {

  private static final String[] SPECIAL_KEYS = {
      "ENTER", "TAB", "ESC", "SPACE", "BACKSPACE", "DELETE",
      "UP", "DOWN", "LEFT", "RIGHT",
      "HOME", "END", "PAGE_UP", "PAGE_DOWN", "INSERT",
      "F1", "F2", "F3", "F4", "F5", "F6",
      "F7", "F8", "F9", "F10", "F11", "F12"
  };

  private JCheckBox cbCtrl, cbAlt, cbShift, cbMeta;
  private JComboBox<String> keyCombo;
  private JTextField charField;
  private JRadioButton rbSpecial, rbChar;
  private JLabel previewLabel;
  private JButton okBtn, cancelBtn;

  private String result = null;

  public RecorderKeyComboDialog(Dialog parent) {
    super(parent, SikuliIDEI18N._I("recorder.keyCombo.title"), true);
    setSize(400, 320);
    setLocationRelativeTo(parent);
    setResizable(false);
    buildUI();
    updatePreview();
  }

  private void buildUI() {
    JPanel content = new JPanel(new MigLayout("wrap 1, insets 16, gap 8", "[grow, fill]", ""));
    content.setBackground(UIManager.getColor("Panel.background"));

    // Modifiers
    JLabel lblMod = new JLabel(SikuliIDEI18N._I("recorder.keyCombo.lblModifiers"));
    lblMod.setFont(UIManager.getFont("small.font"));
    lblMod.setForeground(UIManager.getColor("Label.disabledForeground"));
    content.add(lblMod);

    JPanel modPanel = new JPanel(new MigLayout("insets 0, gap 6", "[][][][]"));
    modPanel.setOpaque(false);
    cbCtrl  = new JCheckBox("Ctrl");
    cbAlt   = new JCheckBox("Alt");
    cbShift = new JCheckBox("Shift");
    cbMeta  = new JCheckBox("Meta");
    cbCtrl.addActionListener(e -> updatePreview());
    cbAlt.addActionListener(e -> updatePreview());
    cbShift.addActionListener(e -> updatePreview());
    cbMeta.addActionListener(e -> updatePreview());
    modPanel.add(cbCtrl);
    modPanel.add(cbAlt);
    modPanel.add(cbShift);
    modPanel.add(cbMeta);
    content.add(modPanel);

    content.add(new JSeparator(), "growx, gaptop 4");

    // Key selector
    JLabel lblKey = new JLabel(SikuliIDEI18N._I("recorder.keyCombo.lblKey"));
    lblKey.setFont(UIManager.getFont("small.font"));
    lblKey.setForeground(UIManager.getColor("Label.disabledForeground"));
    content.add(lblKey, "gaptop 4");

    rbSpecial = new JRadioButton(SikuliIDEI18N._I("recorder.keyCombo.rbSpecial"));
    rbSpecial.setSelected(true);
    rbChar = new JRadioButton(SikuliIDEI18N._I("recorder.keyCombo.rbChar"));
    ButtonGroup group = new ButtonGroup();
    group.add(rbSpecial);
    group.add(rbChar);

    JPanel specialRow = new JPanel(new MigLayout("insets 0, gap 6", "[][grow, fill]"));
    specialRow.setOpaque(false);
    specialRow.add(rbSpecial);
    keyCombo = new JComboBox<>(SPECIAL_KEYS);
    keyCombo.addActionListener(e -> updatePreview());
    specialRow.add(keyCombo, "growx");
    content.add(specialRow);

    JPanel charRow = new JPanel(new MigLayout("insets 0, gap 6", "[][grow, fill]"));
    charRow.setOpaque(false);
    charRow.add(rbChar);
    charField = new JTextField("a", 4);
    charField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
      public void insertUpdate(javax.swing.event.DocumentEvent e)  { updatePreview(); }
      public void removeUpdate(javax.swing.event.DocumentEvent e)  { updatePreview(); }
      public void changedUpdate(javax.swing.event.DocumentEvent e) { updatePreview(); }
    });
    charRow.add(charField, "growx");
    content.add(charRow);

    rbSpecial.addActionListener(e -> { updateEnabledState(); updatePreview(); });
    rbChar.addActionListener(e -> { updateEnabledState(); updatePreview(); });
    updateEnabledState();

    content.add(new JSeparator(), "growx, gaptop 4");

    // Preview
    JLabel lblPreview = new JLabel(SikuliIDEI18N._I("recorder.keyCombo.lblPreview"));
    lblPreview.setFont(UIManager.getFont("small.font"));
    lblPreview.setForeground(UIManager.getColor("Label.disabledForeground"));
    content.add(lblPreview, "gaptop 4");

    previewLabel = new JLabel(" ");
    previewLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
    previewLabel.setForeground(new Color(0x00, 0xA8, 0x9D));
    previewLabel.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1),
        BorderFactory.createEmptyBorder(8, 10, 8, 10)));
    content.add(previewLabel, "growx");

    // Buttons
    JPanel buttons = new JPanel(new MigLayout("insets 0, gap 8", "push[][]", ""));
    buttons.setOpaque(false);
    cancelBtn = new JButton(SikuliIDEI18N._I("recorder.keyCombo.btnCancel"));
    cancelBtn.addActionListener(e -> { result = null; dispose(); });
    okBtn = new JButton(SikuliIDEI18N._I("recorder.keyCombo.btnOk"));
    okBtn.putClientProperty("JButton.buttonType", "default");
    okBtn.addActionListener(e -> { result = buildCode(); dispose(); });
    buttons.add(cancelBtn);
    buttons.add(okBtn);
    content.add(buttons, "gaptop 8, align right");

    setContentPane(content);
    getRootPane().setDefaultButton(okBtn);
  }

  private String buildKey() {
    if (rbSpecial.isSelected()) {
      return "Key." + (String) keyCombo.getSelectedItem();
    }
    String c = charField.getText();
    if (c == null || c.isEmpty()) return "''";
    return "'" + c.charAt(0) + "'";
  }

  private String buildCode() {
    StringBuilder mods = new StringBuilder();
    if (cbCtrl.isSelected())  mods.append(mods.length() > 0 ? " + " : "").append("Key.CTRL");
    if (cbAlt.isSelected())   mods.append(mods.length() > 0 ? " + " : "").append("Key.ALT");
    if (cbShift.isSelected()) mods.append(mods.length() > 0 ? " + " : "").append("Key.SHIFT");
    if (cbMeta.isSelected())  mods.append(mods.length() > 0 ? " + " : "").append("Key.META");

    String key = buildKey();
    if (mods.length() == 0) {
      return "type(" + key + ")";
    }
    return "type(" + key + ", " + mods + ")";
  }

  private void updatePreview() {
    previewLabel.setText(buildCode());
  }

  private void updateEnabledState() {
    boolean special = rbSpecial.isSelected();
    keyCombo.setEnabled(special);
    charField.setEnabled(!special);
  }

  public String getResult() {
    return result;
  }
}
