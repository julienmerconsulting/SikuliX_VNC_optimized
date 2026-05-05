/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui.recorder;

import org.sikuli.script.Screen;
import org.sikuli.script.ScreenImage;
import org.sikuli.support.ide.SikuliIDEI18N;

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
    String optCapture = SikuliIDEI18N._I("recorder.picker.optCaptureScreen");
    String optBrowse = SikuliIDEI18N._I("recorder.picker.optBrowseFile");
    String optExisting = SikuliIDEI18N._I("recorder.picker.optExisting");

    java.util.List<String> options = new java.util.ArrayList<>();
    options.add(optCapture);
    options.add(optBrowse);
    if (!capturedImages.isEmpty()) {
      options.add(optExisting);
    }

    int choice = JOptionPane.showOptionDialog(parent,
        SikuliIDEI18N._I("recorder.picker.chooseSource", purpose),
        purpose,
        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
        null, options.toArray(), options.get(0));

    if (choice < 0) return null;
    String selected = (String) options.get(choice);

    if (optCapture.equals(selected)) {
      parent.setAlwaysOnTop(false);
      try {
        return captureImage(purpose);
      } finally {
        parent.setAlwaysOnTop(true);
      }
    }
    if (optBrowse.equals(selected)) {
      return browseImage();
    }
    if (optExisting.equals(selected)) {
      return pickFromLibrary();
    }
    return null;
  }

  String captureImage(String purpose) {
    parent.setVisible(false);
    parent.getOwner().setVisible(false);
    final ScreenImage[] captured = new ScreenImage[1];
    try {
      captured[0] = new Screen().userCapture(SikuliIDEI18N._I("recorder.picker.regionForPurpose", purpose));
    } finally {
      parent.getOwner().setVisible(true);
      parent.setVisible(true);
    }
    if (captured[0] == null) return null;

    try {
      String defaultName = purpose.replaceAll("\\s+", "_").toLowerCase() + "_" + System.currentTimeMillis();
      String imageName = JOptionPane.showInputDialog(parent,
          SikuliIDEI18N._I("recorder.picker.namePrompt"), defaultName);
      if (imageName == null || imageName.trim().isEmpty()) imageName = defaultName;
      imageName = imageName.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_");
      if (!imageName.endsWith(".png")) imageName += ".png";

      String path = captured[0].save(screenshotDir.getAbsolutePath(), imageName);
      if (path != null) capturedImages.add(path);
      return path;
    } catch (Exception ex) {
      RecorderNotifications.error(SikuliIDEI18N._I("recorder.picker.failedToSave", ex.getMessage()));
      return null;
    }
  }

  String browseImage() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle(SikuliIDEI18N._I("recorder.picker.fileChooserTitle"));
    chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
        SikuliIDEI18N._I("recorder.picker.fileFilterDesc"), "png", "jpg", "jpeg", "gif"));
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
        RecorderNotifications.error(SikuliIDEI18N._I("recorder.picker.failedToImport", ex.getMessage()));
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
        SikuliIDEI18N._I("recorder.picker.libraryPrompt"),
        SikuliIDEI18N._I("recorder.picker.libraryTitle"),
        JOptionPane.PLAIN_MESSAGE, null, names, names[names.length - 1]);
    if (chosen == null) return null;
    return capturedImages.stream()
        .filter(p -> new File(p).getName().equals(chosen))
        .findFirst().orElse(null);
  }
}
