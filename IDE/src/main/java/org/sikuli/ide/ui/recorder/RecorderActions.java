/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui.recorder;

import org.sikuli.script.*;
import org.sikuli.support.ide.SikuliIDEI18N;
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

    String optCapture = SikuliIDEI18N._I("recorder.picker.optCaptureScreen");
    String optBrowse = SikuliIDEI18N._I("recorder.picker.optBrowseFile");
    String optExisting = SikuliIDEI18N._I("recorder.picker.optExisting");

    java.util.List<String> options = new java.util.ArrayList<>();
    options.add(optCapture);
    options.add(optBrowse);
    if (!capturedImages.isEmpty()) {
      options.add(optExisting);
    }

    int choice = JOptionPane.showOptionDialog(assistant,
        SikuliIDEI18N._I("recorder.picker.chooseSource", actionType),
        actionType,
        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
        null, options.toArray(), options.get(0));

    if (choice < 0) {
      workflow.reset();
      return;
    }
    String selected = (String) options.get(choice);

    if (optBrowse.equals(selected)) {
      String imagePath = imagePicker.browseImage();
      if (imagePath == null) { workflow.reset(); return; }
      finishImageCapture(actionType, imagePath);
      return;
    }
    if (optExisting.equals(selected)) {
      String imagePath = imagePicker.pickFromLibrary();
      if (imagePath == null) { workflow.reset(); return; }
      finishImageCapture(actionType, imagePath);
      return;
    }

    assistant.hideForCapture();

    new Thread(() -> {
      ScreenImage capture = new Screen().userCapture(
          SikuliIDEI18N._I("recorder.picker.regionForPurpose", actionType));

      SwingUtilities.invokeLater(() -> {
        assistant.showAfterCapture();

        if (capture == null) {
          workflow.reset();
          return;
        }

        try {
          String defaultName = actionType + "_" + System.currentTimeMillis();
          String imageName = JOptionPane.showInputDialog(assistant,
              SikuliIDEI18N._I("recorder.picker.namePrompt"), defaultName);
          if (imageName == null || imageName.trim().isEmpty()) imageName = defaultName;
          imageName = imageName.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_");
          if (!imageName.endsWith(".png")) imageName += ".png";

          String imagePath = capture.save(screenshotDir.getAbsolutePath(), imageName);
          if (imagePath == null) {
            workflow.reset();
            RecorderNotifications.error(SikuliIDEI18N._I("recorder.actions.failedToSaveCapture"));
            return;
          }
          capturedImages.add(imagePath);

          finishImageCapture(actionType, imagePath);

        } catch (Exception ex) {
          workflow.reset();
          RecorderNotifications.error(SikuliIDEI18N._I("recorder.actions.actionFailed", ex.getMessage()));
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
          RecorderNotifications.warning(SikuliIDEI18N._I(
              "recorder.actions.patternAmbiguous",
              result.matchCount, result.suggestedSimilarity));
        } else if (result.warning == PatternValidator.Warning.COLOR_DEPENDENT) {
          RecorderNotifications.warning(SikuliIDEI18N._I("recorder.actions.patternColor"));
        } else if (result.warning == PatternValidator.Warning.TOO_SMALL) {
          RecorderNotifications.warning(SikuliIDEI18N._I("recorder.actions.patternTooSmall"));
        } else if (result.matchCount > 0) {
          RecorderNotifications.success(SikuliIDEI18N._I(
              "recorder.actions.patternValidated",
              String.format("%.2f", result.bestScore)));
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
      RecorderNotifications.error(SikuliIDEI18N._I("recorder.actions.actionFailed", ex.getMessage()));
    }
  }

  void handleDragDrop() {
    if (appScope.warnIfNoApp(assistant)) return;
    if (!workflow.startDragDrop()) return;

    pickImageAsync(SikuliIDEI18N._I("recorder.actions.dragSource"), sourcePath -> {
      if (sourcePath == null) { workflow.reset(); return; }
      workflow.advanceDragDrop();

      pickImageAsync(SikuliIDEI18N._I("recorder.actions.dropDest"), destPath -> {
        if (destPath == null) { workflow.reset(); return; }
        try {
          Pattern sourcePattern = new Pattern(sourcePath);
          Pattern destPattern = new Pattern(destPath);
          String code = codeGen.getGenerator().dragDrop(sourcePattern, destPattern);
          codeGen.addActionCode(code, appScope.isAppScoped(), appScope.getAppVarName());
          workflow.onActionComplete();
          RecorderNotifications.success(SikuliIDEI18N._I("recorder.actions.dragDropRecorded"));
        } catch (Exception ex) {
          workflow.reset();
          RecorderNotifications.error(SikuliIDEI18N._I("recorder.actions.dragDropFailed", ex.getMessage()));
        }
      });
    });
  }

  /**
   * Mirror handleImageCapture's source-selection flow but expose the result
   * via a callback so callers (notably handleDragDrop's two-step source/dest
   * pickers) can chain them without blocking the EDT.
   *
   * <p>Capture path: assistant.hideForCapture(), userCapture on a worker
   * thread, naming + save back on the EDT, callback with the saved path.
   * Browse / library paths stay synchronous (no userCapture, no focus
   * issue) and just invoke the callback inline.
   *
   * <p>Replaces the previous synchronous imagePicker.pickImage() chain
   * which blocked the EDT during userCapture and prevented the Windows
   * capture overlay from receiving drag events.
   */
  private void pickImageAsync(String purpose, java.util.function.Consumer<String> callback) {
    String optCapture = SikuliIDEI18N._I("recorder.picker.optCaptureScreen");
    String optBrowse = SikuliIDEI18N._I("recorder.picker.optBrowseFile");
    String optExisting = SikuliIDEI18N._I("recorder.picker.optExisting");

    java.util.List<String> options = new java.util.ArrayList<>();
    options.add(optCapture);
    options.add(optBrowse);
    if (!capturedImages.isEmpty()) {
      options.add(optExisting);
    }
    int choice = JOptionPane.showOptionDialog(assistant,
        SikuliIDEI18N._I("recorder.picker.chooseSource", purpose),
        purpose,
        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
        null, options.toArray(), options.get(0));
    if (choice < 0) { callback.accept(null); return; }
    String selected = (String) options.get(choice);

    if (optBrowse.equals(selected)) {
      callback.accept(imagePicker.browseImage());
      return;
    }
    if (optExisting.equals(selected)) {
      callback.accept(imagePicker.pickFromLibrary());
      return;
    }

    assistant.hideForCapture();
    new Thread(() -> {
      ScreenImage capture = new Screen().userCapture(
          SikuliIDEI18N._I("recorder.picker.regionForPurpose", purpose));
      SwingUtilities.invokeLater(() -> {
        assistant.showAfterCapture();
        if (capture == null) { callback.accept(null); return; }
        try {
          String defaultName = purpose.replaceAll("\\s+", "_").toLowerCase()
              + "_" + System.currentTimeMillis();
          String imageName = JOptionPane.showInputDialog(assistant,
              SikuliIDEI18N._I("recorder.picker.namePrompt"), defaultName);
          if (imageName == null || imageName.trim().isEmpty()) imageName = defaultName;
          imageName = imageName.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_");
          if (!imageName.endsWith(".png")) imageName += ".png";
          String imagePath = capture.save(screenshotDir.getAbsolutePath(), imageName);
          if (imagePath == null) {
            callback.accept(null);
            RecorderNotifications.error(SikuliIDEI18N._I("recorder.actions.failedToSaveCapture"));
            return;
          }
          capturedImages.add(imagePath);
          callback.accept(imagePath);
        } catch (Exception ex) {
          callback.accept(null);
          RecorderNotifications.error(SikuliIDEI18N._I("recorder.actions.actionFailed", ex.getMessage()));
        }
      });
    }, "RecorderDragDrop-" + purpose).start();
  }

  void handleSwipe() {
    if (appScope.warnIfNoApp(assistant)) return;
    if (!workflow.startCapture("swipe")) return;

    assistant.hideForCapture();

    new Thread(() -> {
      ScreenImage capture = new Screen().userCapture(
          SikuliIDEI18N._I("recorder.picker.regionForSwipe"));

      SwingUtilities.invokeLater(() -> {
        assistant.showAfterCapture();

        if (capture == null) {
          workflow.reset();
          return;
        }

        try {
          String defaultName = "swipe_zone_" + System.currentTimeMillis();
          String imageName = JOptionPane.showInputDialog(assistant,
              SikuliIDEI18N._I("recorder.picker.zoneNamePrompt"), defaultName);
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
            RecorderNotifications.success(SikuliIDEI18N._I("recorder.actions.swipeRecorded"));
          }
          workflow.onActionComplete();
        } catch (Exception ex) {
          workflow.reset();
          RecorderNotifications.error(SikuliIDEI18N._I("recorder.actions.swipeFailed", ex.getMessage()));
        }
      });
    }, "RecorderSwipe").start();
  }

  void handleWheelCapture() {
    if (appScope.warnIfNoApp(assistant)) return;
    if (!workflow.startCapture("wheel")) return;

    assistant.hideForCapture();

    new Thread(() -> {
      ScreenImage capture = new Screen().userCapture(
          SikuliIDEI18N._I("recorder.picker.regionForWheel"));

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
          RecorderNotifications.error(SikuliIDEI18N._I("recorder.actions.wheelFailed", ex.getMessage()));
        }
      });
    }, "RecorderWheel").start();
  }

  void handleTextAction(String actionType) {
    if (appScope.warnIfNoApp(assistant)) return;
    if (!workflow.startTextInput()) return;

    String label;
    switch (actionType) {
      case "textClick":  label = SikuliIDEI18N._I("recorder.actions.textClickPrompt"); break;
      case "textWait":   label = SikuliIDEI18N._I("recorder.actions.textWaitPrompt"); break;
      case "textExists": label = SikuliIDEI18N._I("recorder.actions.textExistsPrompt"); break;
      default:           label = SikuliIDEI18N._I("recorder.actions.textPrompt"); break;
    }

    String text = JOptionPane.showInputDialog(assistant, label,
        SikuliIDEI18N._I("recorder.actions.textTitle"),
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

    String text = JOptionPane.showInputDialog(assistant,
        SikuliIDEI18N._I("recorder.actions.typePrompt"),
        SikuliIDEI18N._I("recorder.actions.typeTitle"),
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

    String seconds = JOptionPane.showInputDialog(assistant,
        SikuliIDEI18N._I("recorder.actions.pausePrompt"),
        SikuliIDEI18N._I("recorder.actions.pauseTitle"),
        JOptionPane.PLAIN_MESSAGE);
    if (seconds != null && !seconds.isEmpty()) {
      try {
        int s = Integer.parseInt(seconds.trim());
        codePreview.addLine("sleep(" + s + ")");
        RecorderNotifications.warning(SikuliIDEI18N._I("recorder.actions.pauseFragile"));
      } catch (NumberFormatException ex) {
        RecorderNotifications.error(SikuliIDEI18N._I("recorder.actions.invalidNumber", seconds));
      }
    }
    workflow.onActionComplete();
  }
}
