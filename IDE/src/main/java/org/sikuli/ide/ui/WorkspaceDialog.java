/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui;

import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Modal dialog for creating a new workspace.
 * Collects name, description, author, then lets the user choose a directory.
 * Creates a workspace.json file in the selected directory.
 */
public class WorkspaceDialog extends JDialog {

  private JTextField nameField;
  private JTextField descriptionField;
  private JTextField authorField;
  private boolean confirmed = false;
  private File workspaceDir;

  public WorkspaceDialog(Frame parent) {
    super(parent, "New Workspace", true);
    setSize(420, 300);
    setLocationRelativeTo(parent);
    setResizable(false);
    buildUI();
  }

  private void buildUI() {
    JPanel content = new JPanel(new MigLayout("wrap 2, insets 20, gap 8", "[right, 100]10[grow, fill, 250]", ""));
    content.setBackground(UIManager.getColor("Panel.background"));

    // Title
    JLabel title = new JLabel("Create Workspace");
    title.setFont(UIManager.getFont("h2.font"));
    content.add(title, "span 2, align center, gapbottom 12");

    // Name
    content.add(new JLabel("Name *"));
    nameField = new JTextField();
    nameField.setToolTipText("Workspace name (required)");
    content.add(nameField);

    // Description
    content.add(new JLabel("Description"));
    descriptionField = new JTextField();
    descriptionField.setToolTipText("Short description (optional)");
    content.add(descriptionField);

    // Author
    content.add(new JLabel("Author"));
    authorField = new JTextField(System.getProperty("user.name"));
    authorField.setToolTipText("Author name (optional)");
    content.add(authorField);

    // Buttons
    JPanel buttons = new JPanel(new MigLayout("insets 0", "push[]8[]", ""));
    buttons.setOpaque(false);

    JButton cancelBtn = new JButton("Cancel");
    cancelBtn.addActionListener(e -> {
      confirmed = false;
      dispose();
    });
    buttons.add(cancelBtn);

    JButton okBtn = new JButton("Create");
    okBtn.addActionListener(e -> onCreateClicked());
    okBtn.putClientProperty("JButton.buttonType", "default");
    buttons.add(okBtn);

    content.add(buttons, "span 2, align right, gaptop 12");

    setContentPane(content);

    // Enter key = Create
    getRootPane().setDefaultButton(okBtn);
  }

  private void onCreateClicked() {
    String name = nameField.getText().trim();
    if (name.isEmpty()) {
      nameField.requestFocus();
      nameField.setBorder(BorderFactory.createLineBorder(new Color(0xFF, 0x6B, 0x6B), 2));
      return;
    }

    // Choose directory
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Choose workspace directory");
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setAcceptAllFileFilterUsed(false);

    int result = chooser.showOpenDialog(this);
    if (result != JFileChooser.APPROVE_OPTION) {
      return;
    }

    workspaceDir = chooser.getSelectedFile();

    // Create workspace.json
    try {
      File wsFile = new File(workspaceDir, "workspace.json");
      String json = buildWorkspaceJson(name);
      try (FileWriter writer = new FileWriter(wsFile)) {
        writer.write(json);
      }
      confirmed = true;
      dispose();
    } catch (Exception ex) {
      JOptionPane.showMessageDialog(this,
          "Error creating workspace: " + ex.getMessage(),
          "Error", JOptionPane.ERROR_MESSAGE);
    }
  }

  private String buildWorkspaceJson(String name) {
    String desc = descriptionField.getText().trim();
    String author = authorField.getText().trim();
    String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"name\": \"").append(escapeJson(name)).append("\",\n");
    sb.append("  \"description\": \"").append(escapeJson(desc)).append("\",\n");
    sb.append("  \"author\": \"").append(escapeJson(author)).append("\",\n");
    sb.append("  \"created\": \"").append(date).append("\",\n");
    sb.append("  \"scripts\": []\n");
    sb.append("}\n");
    return sb.toString();
  }

  private String escapeJson(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  public boolean isConfirmed() {
    return confirmed;
  }

  public File getWorkspaceDir() {
    return workspaceDir;
  }

  public String getWorkspaceName() {
    return nameField.getText().trim();
  }

  /**
   * Shows the dialog and returns the workspace directory if confirmed, null otherwise.
   */
  public static File showDialog(Frame parent) {
    WorkspaceDialog dialog = new WorkspaceDialog(parent);
    dialog.setVisible(true);
    if (dialog.isConfirmed()) {
      return dialog.getWorkspaceDir();
    }
    return null;
  }
}
