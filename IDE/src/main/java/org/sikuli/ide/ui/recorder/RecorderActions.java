/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui.recorder;

import org.sikuli.script.*;
import org.sikuli.support.recorder.PatternValidator;

import javax.swing.*;
import java.io.File;

class RecorderActions {

  private final RecorderAssistant assistant;
  private final RecorderWorkflow workflow;
  private final RecorderCodeGen codeGen;
  private final RecorderAppScope appScope;
  private final RecorderImagePicker imagePicker;
  private final RecorderCodePreview codePreview;
  private final File screenshotDir;
  private final java.util.List<String> capturedImages;

  RecorderActions(RecorderAssistant assistant, RecorderWorkflow workflow, RecorderCodeGen codeGen,
                  RecorderAppScope appScope, RecorderImagePicker imagePicker,
                  RecorderCodePreview codePreview, File screenshotDir, java.util.List<String> capturedImages) {
    this.assistant = assistant;
    this.workflow = workflow;
    this.codeGen = codeGen;
    this.appScope = appScope;
    this.imagePicker = imagePicker;
    this.codePreview = codePreview;
    this.screenshotDir = screenshotDir;
    this.capturedImages = capturedImages;
  }

  void handleImageCapture(String actionType) {
    if (!workflow.startCapture(actionType)) return;
    if (appScope.warnIfNoApp(assistant)) { workflow.reset(); return; }

    java.util.List<String> options = new java.util.ArrayList<>();
    options.add("Capture screen");
    options.add("Browse file...");
    if (!capturedImages.isEmpty()) {
      options.add("Use existing image");
    }

    int choice = JOptionPane.showOptionDialog(assistant,
        "Choose image source for: " + actionType,
        actionType,
        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
        null, options.toArray(), options.get(0));

    if (choice < 0) {
      workflow.reset();
      return;
    }
    String selected = (String) options.get(choice);

    if ("Browse file...".equals(selected)) {
      String imagePath = imagePicker.browseImage();
      if (imagePath == null) { workflow.reset(); return; }
      finishImageCapture(actionType, imagePath);
      return;
    }
    if ("Use existing image".equals(selected)) {
      String imagePath = imagePicker.pickFromLibrary();
      if (imagePath == null) { workflow.reset(); return; }
      finishImageCapture(actionType, imagePath);
      return;
    }

    assistant.hideForCapture();

    new Thread(() -> {
      ScreenImage capture = new Screen().userCapture("Select region for " + actionType);

      SwingUtilities.invokeLater(() -> {
        assistant.showAfterCapture();

        if (capture == null) {
          workflow.reset();
          return;
        }

        try {
          String defaultName = actionType + "_" + System.currentTimeMillis();
          String imageName = JOptionPane.showInputDialog(assistant,
              "Name this image:", defaultName);
          if (imageName == null || imageName.trim().isEmpty()) imageName = defaultName;
          imageName = imageName.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_");
          if (!imageName.endsWith(".png")) imageName += ".png";

          String imagePath = capture.save(screenshotDir.getAbsolutePath(), imageName);
          if (imagePath == null) {
            workflow.reset();
            RecorderNotifications.error("Failed to save captured image");
            return;
          }
          capturedImages.add(imagePath);

          finishImageCapture(actionType, imagePath);

        } catch (Exception ex) {
          workflow.reset();
          RecorderNotifications.error("Action failed: " + ex.getMessage());
        }
      });
    }).start();
  }

  private void finishImageCapture(String actionType, String imagePath) {
    workflow.onCaptureComplete();
    boolean scoped = appScope.isAppScoped();
    String varName = appScope.getAppVarName();

    try {
      Pattern pattern = new Pattern(imagePath);

      PatternValidator.ValidationResult result = null;
      try {
        java.awt.image.BufferedImage candidate =
            javax.imageio.ImageIO.read(new File(imagePath));
        if (candidate != null) {
          result = PatternValidator.validate(
              new Screen().capture().getImage(), candidate);
        }
      } catch (Exception | UnsatisfiedLinkError ignored) {
      }

      if (result != null) {
        if (result.warning == PatternValidator.Warning.AMBIGUOUS) {
          pattern = pattern.similar((float) result.suggestedSimilarity);
          RecorderNotifications.warning(
              "Pattern matches " + result.matchCount + " locations. Similarity raised to " + result.suggestedSimilarity);
        } else if (result.warning == PatternValidator.Warning.COLOR_DEPENDENT) {
          RecorderNotifications.warning("Pattern depends on colors. May break with theme changes.");
        } else if (result.warning == PatternValidator.Warning.TOO_SMALL) {
          RecorderNotifications.warning("Pattern too small. Consider capturing a larger area.");
        } else if (result.matchCount > 0) {
          RecorderNotifications.success("Pattern validated (score: " + String.format("%.2f", result.bestScore) + ")");
        }
      }

      String code = codeGen.generateImageCode(actionType, pattern);
      codeGen.addMultilineActionCode(code, scoped, varName);

      if ("click".equals(actionType) || "doubleClick".equals(actionType) || "rightClick".equals(actionType)) {
        codeGen.generateVanish(assistant, pattern, scoped, varName);
      }

      workflow.onActionComplete();

    } catch (Exception ex) {
      workflow.reset();
      RecorderNotifications.error("Action failed: " + ex.getMessage());
    }
  }

  void handleDragDrop() {
    if (appScope.warnIfNoApp(assistant)) return;
    if (!workflow.startDragDrop()) return;

    String sourcePath = imagePicker.pickImage("Drag SOURCE");
    if (sourcePath == null) { workflow.reset(); return; }
    workflow.advanceDragDrop();

    String destPath = imagePicker.pickImage("Drop DESTINATION");
    if (destPath == null) { workflow.reset(); return; }

    try {
      Pattern sourcePattern = new Pattern(sourcePath);
      Pattern destPattern = new Pattern(destPath);
      String code = codeGen.getGenerator().dragDrop(sourcePattern, destPattern);
      codeGen.addActionCode(code, appScope.isAppScoped(), appScope.getAppVarName());
      workflow.onActionComplete();
      RecorderNotifications.success("Drag & Drop recorded");
    } catch (Exception ex) {
      workflow.reset();
      RecorderNotifications.error("Drag & Drop failed: " + ex.getMessage());
    }
  }

  void handleSwipe() {
    if (appScope.warnIfNoApp(assistant)) return;
    if (!workflow.startCapture("swipe")) return;

    assistant.hideForCapture();

    new Thread(() -> {
      ScreenImage capture = new Screen().userCapture("Select region for swipe");

      SwingUtilities.invokeLater(() -> {
        assistant.showAfterCapture();

        if (capture == null) {
          workflow.reset();
          return;
        }

        try {
          String defaultName = "swipe_zone_" + System.currentTimeMillis();
          String imageName = JOptionPane.showInputDialog(assistant,
              "Name this zone:", defaultName);
          if (imageName == null || imageName.trim().isEmpty()) imageName = defaultName;
          imageName = imageName.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_");
          if (!imageName.endsWith(".png")) imageName += ".png";

          String imagePath = capture.save(screenshotDir.getAbsolutePath(), imageName);
          capturedImages.add(imagePath);

          RecorderSwipeDialog dialog = new RecorderSwipeDialog(
              assistant, capture.getImage(), imagePath);
          dialog.setVisible(true);
          String[] lines = dialog.getResultLines();

          if (lines != null) {
            boolean scoped = appScope.isAppScoped();
            String varName = appScope.getAppVarName();
            for (String line : lines) codeGen.addActionCode(line, scoped, varName);
            RecorderNotifications.success("Swipe recorded");
          }
          workflow.onActionComplete();
        } catch (Exception ex) {
          workflow.reset();
          RecorderNotifications.error("Swipe failed: " + ex.getMessage());
        }
      });
    }, "RecorderSwipe").start();
  }

  void handleWheelCapture() {
    if (appScope.warnIfNoApp(assistant)) return;
    if (!workflow.startCapture("wheel")) return;

    assistant.hideForCapture();

    new Thread(() -> {
      ScreenImage capture = new Screen().userCapture("Select region for wheel action");

      SwingUtilities.invokeLater(() -> {
        assistant.showAfterCapture();

        if (capture == null) {
          workflow.reset();
          return;
        }

        try {
          String imagePath = capture.save(screenshotDir.getAbsolutePath());
          capturedImages.add(imagePath);

          RecorderWheelDialog dialog = new RecorderWheelDialog(
              assistant, capture.getImage(), imagePath);
          dialog.setVisible(true);
          String code = dialog.getResult();

          if (code != null) {
            codeGen.addActionCode(code, appScope.isAppScoped(), appScope.getAppVarName());
          }
          workflow.onActionComplete();
        } catch (Exception ex) {
          workflow.reset();
          RecorderNotifications.error("Wheel failed: " + ex.getMessage());
        }
      });
    }, "RecorderWheel").start();
  }

  void handleTextAction(String actionType) {
    if (appScope.warnIfNoApp(assistant)) return;
    if (!workflow.startTextInput()) return;

    String label;
    switch (actionType) {
      case "textClick":  label = "Text to click on:"; break;
      case "textWait":   label = "Text to wait for:"; break;
      case "textExists": label = "Text to check:"; break;
      default:           label = "Text:"; break;
    }

    String text = JOptionPane.showInputDialog(assistant, label, "Text Action",
        JOptionPane.PLAIN_MESSAGE);
    if (text != null && !text.trim().isEmpty()) {
      codeGen.addActionCode(codeGen.generateTextCode(actionType, text.trim()),
          appScope.isAppScoped(), appScope.getAppVarName());
    }
    workflow.onActionComplete();
  }

  void handleTypeText() {
    if (appScope.warnIfNoApp(assistant)) return;
    if (!workflow.startTextInput()) return;

    String text = JOptionPane.showInputDialog(assistant, "Text to type:", "Type Text",
        JOptionPane.PLAIN_MESSAGE);
    if (text != null && !text.isEmpty()) {
      String code = codeGen.getGenerator().typeText(text, new String[0]);
      codeGen.addActionCode(code, appScope.isAppScoped(), appScope.getAppVarName());
    }
    workflow.onActionComplete();
  }

  void handleKeyCombo() {
    if (appScope.warnIfNoApp(assistant)) return;
    if (!workflow.startKeyComboCApture()) return;

    RecorderKeyComboDialog dialog = new RecorderKeyComboDialog(assistant);
    dialog.setVisible(true);
    String combo = dialog.getResult();
    if (combo != null && !combo.isEmpty()) {
      codeGen.addActionCode(combo, appScope.isAppScoped(), appScope.getAppVarName());
    }
    workflow.onActionComplete();
  }

  void handlePause() {
    if (!workflow.startPauseInput()) return;

    String seconds = JOptionPane.showInputDialog(assistant, "Pause duration (seconds):",
        "Pause", JOptionPane.PLAIN_MESSAGE);
    if (seconds != null && !seconds.isEmpty()) {
      try {
        int s = Integer.parseInt(seconds.trim());
        codePreview.addLine("sleep(" + s + ")");
        RecorderNotifications.warning("Fixed delays are fragile. Prefer Wait Image when possible.");
      } catch (NumberFormatException ex) {
        RecorderNotifications.error("Invalid number: " + seconds);
      }
    }
    workflow.onActionComplete();
  }
}
