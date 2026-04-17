/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui.recorder;

import org.sikuli.script.Screen;
import org.sikuli.script.ScreenImage;

import javax.swing.*;
import java.io.File;
import java.util.List;

class RecorderImagePicker {

  private final JDialog parent;
  private final File screenshotDir;
  private final List<String> capturedImages;

  RecorderImagePicker(JDialog parent, File screenshotDir, List<String> capturedImages) {
    this.parent = parent;
    this.screenshotDir = screenshotDir;
    this.capturedImages = capturedImages;
  }

  String pickImage(String purpose) {
    java.util.List<String> options = new java.util.ArrayList<>();
    options.add("Capture screen");
    options.add("Browse file...");
    if (!capturedImages.isEmpty()) {
      options.add("Use existing image");
    }

    int choice = JOptionPane.showOptionDialog(parent,
        "Choose image source for: " + purpose,
        purpose,
        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
        null, options.toArray(), options.get(0));

    if (choice < 0) return null;
    String selected = (String) options.get(choice);

    if ("Capture screen".equals(selected)) {
      parent.setAlwaysOnTop(false);
      try {
        return captureImage(purpose);
      } finally {
        parent.setAlwaysOnTop(true);
      }
    }
    if ("Browse file...".equals(selected)) {
      return browseImage();
    }
    if ("Use existing image".equals(selected)) {
      return pickFromLibrary();
    }
    return null;
  }

  String captureImage(String purpose) {
    parent.setVisible(false);
    parent.getOwner().setVisible(false);
    final ScreenImage[] captured = new ScreenImage[1];
    try {
      captured[0] = new Screen().userCapture("Select region for " + purpose);
    } finally {
      parent.getOwner().setVisible(true);
      parent.setVisible(true);
    }
    if (captured[0] == null) return null;

    try {
      String defaultName = purpose.replaceAll("\\s+", "_").toLowerCase() + "_" + System.currentTimeMillis();
      String imageName = JOptionPane.showInputDialog(parent, "Name this image:", defaultName);
      if (imageName == null || imageName.trim().isEmpty()) imageName = defaultName;
      imageName = imageName.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_");
      if (!imageName.endsWith(".png")) imageName += ".png";

      String path = captured[0].save(screenshotDir.getAbsolutePath(), imageName);
      if (path != null) capturedImages.add(path);
      return path;
    } catch (Exception ex) {
      RecorderNotifications.error("Failed to save: " + ex.getMessage());
      return null;
    }
  }

  String browseImage() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Select image file");
    chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
        "Image files (*.png, *.jpg, *.jpeg, *.gif)", "png", "jpg", "jpeg", "gif"));
    if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
      File f = chooser.getSelectedFile();
      try {
        File dest = new File(screenshotDir, f.getName());
        java.nio.file.Files.copy(f.toPath(), dest.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        String path = dest.getAbsolutePath();
        capturedImages.add(path);
        return path;
      } catch (Exception ex) {
        RecorderNotifications.error("Failed to import: " + ex.getMessage());
        return null;
      }
    }
    return null;
  }

  String pickFromLibrary() {
    if (capturedImages.isEmpty()) return null;
    String[] names = capturedImages.stream()
        .map(p -> new File(p).getName())
        .toArray(String[]::new);
    String chosen = (String) JOptionPane.showInputDialog(parent,
        "Choose image:", "Image Library",
        JOptionPane.PLAIN_MESSAGE, null, names, names[names.length - 1]);
    if (chosen == null) return null;
    return capturedImages.stream()
        .filter(p -> new File(p).getName().equals(chosen))
        .findFirst().orElse(null);
  }
}
