/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui.recorder;

import net.miginfocom.swing.MigLayout;
import org.sikuli.support.recorder.generators.ICodeGenerator;
import org.sikuli.support.recorder.generators.JavaCodeGenerator;
import org.sikuli.support.recorder.generators.JythonCodeGenerator;
import org.sikuli.support.recorder.generators.RobotFrameworkCodeGenerator;

import javax.swing.*;
import java.awt.*;

/**
 * Configuration dialog shown before the recorder opens.
 * Lets the user choose the target language and default settings.
 */
public class RecorderConfigDialog extends JDialog {

  private boolean confirmed = false;
  private JComboBox<String> languageCombo;

  private static final String[] LANGUAGES = {
      "Python (Jython)",
      "Java",
      "Robot Framework"
  };

  public RecorderConfigDialog(Frame parent) {
    super(parent, "Recorder Configuration", true);
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setSize(380, 180);
    setLocationRelativeTo(parent);
    setResizable(false);
    buildUI();
  }

  private void buildUI() {
    JPanel content = new JPanel(new MigLayout(
        "wrap 2, insets 16, gap 8", "[right][grow, fill]", ""));
    content.setBackground(UIManager.getColor("Panel.background"));

    content.add(new JLabel("Language:"));
    languageCombo = new JComboBox<>(LANGUAGES);
    languageCombo.setSelectedIndex(0);
    content.add(languageCombo);

    content.add(new JSeparator(), "span 2, growx, gaptop 8");

    JPanel buttons = new JPanel(new MigLayout("insets 0, gap 8", "[grow][grow]"));
    buttons.setOpaque(false);

    JButton btnStart = new JButton("Start Recording");
    btnStart.addActionListener(e -> {
      confirmed = true;
      dispose();
    });

    JButton btnCancel = new JButton("Cancel");
    btnCancel.addActionListener(e -> dispose());

    buttons.add(btnStart, "grow");
    buttons.add(btnCancel, "grow");
    content.add(buttons, "span 2, growx, gaptop 8");

    setContentPane(content);
  }

  public boolean isConfirmed() {
    return confirmed;
  }

  public ICodeGenerator getSelectedGenerator() {
    int idx = languageCombo.getSelectedIndex();
    switch (idx) {
      case 1:
        return new JavaCodeGenerator();
      case 2:
        return new RobotFrameworkCodeGenerator();
      default:
        return new JythonCodeGenerator();
    }
  }

  public String getSelectedLanguageName() {
    return (String) languageCombo.getSelectedItem();
  }

}
