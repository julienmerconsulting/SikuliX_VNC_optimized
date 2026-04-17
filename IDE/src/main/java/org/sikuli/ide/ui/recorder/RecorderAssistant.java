/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui.recorder;

import net.miginfocom.swing.MigLayout;
import org.sikuli.ide.SikulixIDE;
import org.sikuli.script.*;
import org.sikuli.support.recorder.PatternValidator;
import org.sikuli.support.recorder.generators.ICodeGenerator;
import org.sikuli.support.recorder.generators.JavaCodeGenerator;
import org.sikuli.support.recorder.generators.RobotFrameworkCodeGenerator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Non-modal floating dialog for the OculiX Modern Recorder.
 * Stays on top while the user interacts with the target application.
 * Coordinates with RecorderWorkflow for state management.
 */
public class RecorderAssistant extends JDialog {

  private final RecorderWorkflow workflow;
  private final RecorderCodePreview codePreview;
  private final ICodeGenerator codeGenerator;
  private File screenshotDir;

  private App currentApp = null;
  private String appVarName = null;

  // UI components
  private JLabel statusLabel;
  private JButton btnLaunchApp, btnCloseApp;
  private JCheckBox chkScopeToApp;
  private JButton btnClick, btnDblClick, btnRClick;
  private JButton btnTextClick, btnTextWait, btnTextExists;
  private JButton btnType, btnKeyCombo;
  private JButton btnDragDrop, btnSwipe, btnWheel, btnWait, btnPause;
  private JButton btnInsert, btnClear;

  // Library of captured images in this session
  private final java.util.List<String> capturedImages = new java.util.ArrayList<>();

  public RecorderAssistant(Frame parent, ICodeGenerator generator) {
    super(parent, "OculiX Modern Recorder (beta)", false);
    setSize(400, 680);
    setLocationRelativeTo(parent);
    setAlwaysOnTop(true);
    setType(Window.Type.UTILITY);
    setResizable(false);

    this.workflow = new RecorderWorkflow();
    this.codePreview = new RecorderCodePreview();
    this.codeGenerator = generator;

    if (codeGenerator instanceof RobotFrameworkCodeGenerator) {
      codePreview.addLine("*** Settings ***");
      codePreview.addLine("Library    SikuliLibrary");
      codePreview.addLine("Documentation    Recorded by OculiX Modern Recorder");
      codePreview.addLine("");
      codePreview.addLine("*** Test Cases ***");
      codePreview.addLine("Recorded Test");
    }

    // Create temp dir for screenshots
    try {
      screenshotDir = Files.createTempDirectory("oculix_recorder_").toFile();
      screenshotDir.deleteOnExit();
    } catch (IOException e) {
      screenshotDir = new File(System.getProperty("java.io.tmpdir"));
    }

    // Load OpenCV native lib once (like legacy Recorder.start())
    org.sikuli.support.Commons.loadOpenCV();

    buildUI();
    wireWorkflow();
    checkOcrStatus();

    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        workflow.dispose();
        cleanupTempDir();
        getOwner().setVisible(true); // Restore IDE on close
      }
    });

    RecorderNotifications.init(parent);

    // Hide IDE when recorder opens
    parent.setVisible(false);
  }

  private void buildUI() {
    JPanel content = new JPanel(new MigLayout(
        "wrap 1, insets 10, gap 6", "[grow, fill]", ""));
    content.setBackground(UIManager.getColor("Panel.background"));

    // ── Status bar ──
    statusLabel = new JLabel("\u2B24 Ready");
    statusLabel.setFont(UIManager.getFont("defaultFont").deriveFont(11f));
    statusLabel.setForeground(new Color(0x3D, 0xDB, 0xA4));
    content.add(statusLabel);

    content.add(new JSeparator(), "growx");

    // ── Launch app ──
    content.add(createSectionLabel("APPLICATION"));

    JPanel appRow = new JPanel(new MigLayout("insets 0, gap 4", "[grow][grow]"));
    appRow.setOpaque(false);
    btnLaunchApp = createActionButton("Launch App");
    btnCloseApp = createActionButton("Close App");
    btnCloseApp.setEnabled(false);
    appRow.add(btnLaunchApp, "grow");
    appRow.add(btnCloseApp, "grow");
    content.add(appRow);

    chkScopeToApp = new JCheckBox("Scope actions to this app", true);
    chkScopeToApp.setFont(UIManager.getFont("defaultFont").deriveFont(11f));
    chkScopeToApp.setOpaque(false);
    chkScopeToApp.setEnabled(false);
    content.add(chkScopeToApp);

    content.add(new JSeparator(), "growx, gaptop 4");

    // ── Image actions ──
    content.add(createSectionLabel("IMAGE ACTIONS"));

    JPanel imageRow1 = new JPanel(new MigLayout("insets 0, gap 4", "[grow][grow][grow]"));
    imageRow1.setOpaque(false);
    btnClick = createActionButton("Click");
    btnDblClick = createActionButton("DblClick");
    btnRClick = createActionButton("RClick");
    imageRow1.add(btnClick, "grow");
    imageRow1.add(btnDblClick, "grow");
    imageRow1.add(btnRClick, "grow");
    content.add(imageRow1);

    JPanel imageRow2 = new JPanel(new MigLayout("insets 0, gap 4", "[grow][grow][grow][grow]"));
    imageRow2.setOpaque(false);
    btnDragDrop = createActionButton("Drag&Drop");
    btnSwipe = createActionButton("Swipe");
    btnWheel = createActionButton("Wheel");
    btnWait = createActionButton("Wait");
    imageRow2.add(btnDragDrop, "grow");
    imageRow2.add(btnSwipe, "grow");
    imageRow2.add(btnWheel, "grow");
    imageRow2.add(btnWait, "grow");
    content.add(imageRow2);

    content.add(new JSeparator(), "growx, gaptop 4");

    // ── Text actions ──
    content.add(createSectionLabel("TEXT ACTIONS (OCR)"));

    JPanel textRow = new JPanel(new MigLayout("insets 0, gap 4", "[grow][grow][grow]"));
    textRow.setOpaque(false);
    btnTextClick = createActionButton("T.Click");
    btnTextWait = createActionButton("T.Wait");
    btnTextExists = createActionButton("T.Exists");
    textRow.add(btnTextClick, "grow");
    textRow.add(btnTextWait, "grow");
    textRow.add(btnTextExists, "grow");
    content.add(textRow);

    content.add(new JSeparator(), "growx, gaptop 4");

    // ── Keyboard ──
    content.add(createSectionLabel("KEYBOARD"));

    JPanel kbRow = new JPanel(new MigLayout("insets 0, gap 4", "[grow][grow]"));
    kbRow.setOpaque(false);
    btnType = createActionButton("Type");
    btnKeyCombo = createActionButton("Key Combo");
    kbRow.add(btnType, "grow");
    kbRow.add(btnKeyCombo, "grow");
    content.add(kbRow);

    // ── Other ──
    JPanel otherRow = new JPanel(new MigLayout("insets 0, gap 4", "[grow]"));
    otherRow.setOpaque(false);
    btnPause = createActionButton("Pause");
    otherRow.add(btnPause, "grow");
    content.add(otherRow);

    content.add(new JSeparator(), "growx, gaptop 4");

    // ── Code preview ──
    content.add(createSectionLabel("GENERATED CODE"));

    JScrollPane scrollPane = new JScrollPane(codePreview);
    scrollPane.setPreferredSize(new Dimension(0, 120));
    content.add(scrollPane, "grow, push");

    // ── Bottom buttons ──
    JPanel bottomRow = new JPanel(new MigLayout("insets 0, gap 6", "[grow][grow]"));
    bottomRow.setOpaque(false);
    btnInsert = new JButton("Insert & Close");
    btnInsert.addActionListener(e -> insertAndClose());
    btnClear = new JButton("Clear");
    btnClear.addActionListener(e -> codePreview.clear());
    bottomRow.add(btnInsert, "grow");
    bottomRow.add(btnClear, "grow");
    content.add(bottomRow, "gaptop 4");

    setContentPane(content);
  }

  private JLabel createSectionLabel(String text) {
    JLabel label = new JLabel(text);
    label.setFont(UIManager.getFont("small.font"));
    label.setForeground(UIManager.getColor("Label.disabledForeground"));
    return label;
  }

  private JButton createActionButton(String text) {
    JButton btn = new JButton(text);
    btn.setFont(UIManager.getFont("defaultFont").deriveFont(11f));
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return btn;
  }

  // ── Wire buttons to workflow ──

  private void wireWorkflow() {
    // State listener for UI updates
    workflow.addStateListener((oldState, newState) -> updateStatus(newState));

    // Launch / Close app
    btnLaunchApp.addActionListener(e -> handleLaunchApp());
    btnCloseApp.addActionListener(e -> handleCloseApp());
    chkScopeToApp.addActionListener(e -> {});

    // Image actions
    btnClick.addActionListener(e -> handleImageCapture("click"));
    btnDblClick.addActionListener(e -> handleImageCapture("doubleClick"));
    btnRClick.addActionListener(e -> handleImageCapture("rightClick"));
    btnWait.addActionListener(e -> handleImageCapture("wait"));
    btnWheel.addActionListener(e -> handleWheelCapture());
    btnDragDrop.addActionListener(e -> handleDragDrop());
    btnSwipe.addActionListener(e -> handleSwipe());

    // Text actions
    btnTextClick.addActionListener(e -> handleTextAction("textClick"));
    btnTextWait.addActionListener(e -> handleTextAction("textWait"));
    btnTextExists.addActionListener(e -> handleTextAction("textExists"));

    // Keyboard
    btnType.addActionListener(e -> handleTypeText());
    btnKeyCombo.addActionListener(e -> handleKeyCombo());

    // Other
    btnPause.addActionListener(e -> handlePause());
  }

  // ── Capture helpers ──

  /**
   * Hide recorder during capture (IDE is already hidden).
   */
  private void hideForCapture() {
    setVisible(false);
    focusAppIfScoped();
  }

  private void showAfterCapture() {
    setVisible(true);
  }

  // ── Image capture workflows ──

  private void handleImageCapture(String actionType) {
    if (!workflow.startCapture(actionType)) return;

    String imagePath = pickImage(actionType);
    if (imagePath == null) {
      workflow.reset();
      return;
    }

    workflow.onCaptureComplete(); // -> WAITING_PATTERN_VALIDATION

    try {
      Pattern pattern = new Pattern(imagePath);

      // Best-effort validation against current screen
      PatternValidator.ValidationResult result = null;
      try {
        java.awt.image.BufferedImage candidate =
            javax.imageio.ImageIO.read(new File(imagePath));
        if (candidate != null) {
          result = PatternValidator.validate(
              new Screen().capture().getImage(), candidate);
        }
      } catch (Exception | UnsatisfiedLinkError ignored) {
        // OpenCV missing or IO error — skip validation
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

      String code = generateImageCode(actionType, pattern);
      addActionCode(code);
      workflow.onActionComplete(); // -> IDLE

    } catch (Exception ex) {
      workflow.reset();
      RecorderNotifications.error("Action failed: " + ex.getMessage());
    }
  }

  private boolean isAppScoped() {
    return currentApp != null && chkScopeToApp.isSelected();
  }

  private void addActionCode(String code) {
    if (isAppScoped() && !(codeGenerator instanceof RobotFrameworkCodeGenerator)) {
      codePreview.addLine(appVarName + "." + code);
    } else {
      codePreview.addLine(code);
    }
  }

  private String generateImageCode(String actionType, Pattern pattern) {
    String[] noModifiers = new String[0];
    String code;
    switch (actionType) {
      case "click":
        code = codeGenerator.click(pattern, noModifiers);
        break;
      case "doubleClick":
        code = codeGenerator.doubleClick(pattern, noModifiers);
        break;
      case "rightClick":
        code = codeGenerator.rightClick(pattern, noModifiers);
        break;
      case "wait":
        code = codeGenerator.wait(pattern, 10, null);
        break;
      default:
        code = "# " + actionType + "(\"" + pattern.getFilename() + "\")";
        break;
    }
    return code;
  }

  private void handleDragDrop() {
    if (!workflow.startDragDrop()) return;

    // Step 1: pick source
    String sourcePath = pickImage("Drag SOURCE");
    if (sourcePath == null) {
      workflow.reset();
      return;
    }
    workflow.advanceDragDrop();

    // Step 2: pick destination
    String destPath = pickImage("Drop DESTINATION");
    if (destPath == null) {
      workflow.reset();
      return;
    }

    try {
      Pattern sourcePattern = new Pattern(sourcePath);
      Pattern destPattern = new Pattern(destPath);
      String code = codeGenerator.dragDrop(sourcePattern, destPattern);
      addActionCode(code);
      workflow.onActionComplete();
      RecorderNotifications.success("Drag & Drop recorded");
    } catch (Exception ex) {
      workflow.reset();
      RecorderNotifications.error("Drag & Drop failed: " + ex.getMessage());
    }
  }

  private void handleSwipe() {
    if (!workflow.startCapture("swipe")) return;

    hideForCapture();

    new Thread(() -> {
      ScreenImage capture = new Screen().userCapture("Select region for swipe");

      SwingUtilities.invokeLater(() -> {
        showAfterCapture();

        if (capture == null) {
          workflow.reset();
          return;
        }

        try {
          String defaultName = "swipe_zone_" + System.currentTimeMillis();
          String imageName = JOptionPane.showInputDialog(RecorderAssistant.this,
              "Name this zone:", defaultName);
          if (imageName == null || imageName.trim().isEmpty()) imageName = defaultName;
          imageName = imageName.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_");
          if (!imageName.endsWith(".png")) imageName += ".png";

          String imagePath = capture.save(screenshotDir.getAbsolutePath(), imageName);
          capturedImages.add(imagePath);

          RecorderSwipeDialog dialog = new RecorderSwipeDialog(
              RecorderAssistant.this, capture.getImage(), imagePath);
          dialog.setVisible(true);
          String[] lines = dialog.getResultLines();

          if (lines != null) {
            for (String line : lines) addActionCode(line);
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

  private void handleWheelCapture() {
    if (!workflow.startCapture("wheel")) return;

    hideForCapture();

    new Thread(() -> {
      ScreenImage capture = new Screen().userCapture("Select region for wheel action");

      SwingUtilities.invokeLater(() -> {
        showAfterCapture();

        if (capture == null) {
          workflow.reset();
          return;
        }

        try {
          String imagePath = capture.save(screenshotDir.getAbsolutePath());
          capturedImages.add(imagePath);

          // Open interactive Wheel dialog with captured image + crosshair offset
          RecorderWheelDialog dialog = new RecorderWheelDialog(
              RecorderAssistant.this, capture.getImage(), imagePath);
          dialog.setVisible(true);
          String code = dialog.getResult();

          if (code != null) {
            addActionCode(code);
          }
          workflow.onActionComplete();
        } catch (Exception ex) {
          workflow.reset();
          RecorderNotifications.error("Wheel failed: " + ex.getMessage());
        }
      });
    }, "RecorderWheel").start();
  }

  // ── Text OCR workflows ──

  private void handleTextAction(String actionType) {
    if (!workflow.startTextInput()) return;

    String label;
    switch (actionType) {
      case "textClick":  label = "Text to click on:"; break;
      case "textWait":   label = "Text to wait for:"; break;
      case "textExists": label = "Text to check:"; break;
      default:           label = "Text:"; break;
    }

    String text = JOptionPane.showInputDialog(this, label, "Text Action",
        JOptionPane.PLAIN_MESSAGE);
    if (text != null && !text.trim().isEmpty()) {
      addActionCode(generateTextCode(actionType, text.trim()));
    }
    workflow.onActionComplete();
  }

  private String generateTextCode(String actionType, String text) {
    switch (actionType) {
      case "textClick":
        return "click(\"" + text + "\")";
      case "textWait":
        return "wait(\"" + text + "\", 10)";
      case "textExists":
        return "exists(\"" + text + "\")";
      default:
        return "# " + actionType + "(\"" + text + "\")";
    }
  }

  // ── Keyboard workflows ──

  private void handleTypeText() {
    if (!workflow.startTextInput()) return;

    String text = JOptionPane.showInputDialog(this, "Text to type:", "Type Text",
        JOptionPane.PLAIN_MESSAGE);
    if (text != null && !text.isEmpty()) {
      String code = codeGenerator.typeText(text, new String[0]);
      addActionCode(code);
    }
    workflow.onActionComplete();
  }

  private void handleKeyCombo() {
    if (!workflow.startKeyComboCApture()) return;

    RecorderKeyComboDialog dialog = new RecorderKeyComboDialog(this);
    dialog.setVisible(true);
    String combo = dialog.getResult();
    if (combo != null && !combo.isEmpty()) {
      addActionCode(combo);
    }
    workflow.onActionComplete();
  }

  // ── Pause ──

  private void handlePause() {
    if (!workflow.startPauseInput()) return;

    String seconds = JOptionPane.showInputDialog(this, "Pause duration (seconds):",
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

  // ── Insert generated code into editor ──

  private void insertAndClose() {
    DefaultListModel<String> model = (DefaultListModel<String>) codePreview.getModel();
    if (model.isEmpty()) {
      workflow.dispose();
      getOwner().setVisible(true);
      dispose();
      return;
    }

    // Build code string
    StringBuilder code = new StringBuilder("\n");
    for (int i = 0; i < model.size(); i++) {
      code.append(model.get(i)).append("\n");
    }
    String codeStr = code.toString();

    // Ask: insert in current script or new script?
    String[] options = {"Current Script", "New Script", "Cancel"};
    int choice = JOptionPane.showOptionDialog(this,
        "Insert " + model.size() + " line(s) of generated code:",
        "Insert Code",
        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
        null, options, options[0]);

    if (choice == 2 || choice < 0) {
      return; // Cancel — stay in recorder
    }

    workflow.dispose();
    SikulixIDE ide = (SikulixIDE) getOwner();
    ide.setVisible(true); // Restore IDE
    cleanupTempDir();

    if (choice == 1) {
      ide.createEmptyScriptContext();
    }

    // Insert directly into the active editor pane
    java.awt.EventQueue.invokeLater(() -> {
      SikulixIDE.PaneContext ctx = ide.getActiveContext();
      if (ctx != null && ctx.getPane() != null) {
        // Copy captured images to the script's image folder
        File imageFolder = ctx.getImageFolder();
        if (imageFolder != null && screenshotDir != null) {
          File[] captures = screenshotDir.listFiles((dir, name) -> name.endsWith(".png"));
          if (captures != null) {
            for (File img : captures) {
              try {
                java.nio.file.Files.copy(img.toPath(),
                    new File(imageFolder, img.getName()).toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
              } catch (IOException ex) {
                System.err.println("[Recorder] Failed to copy image: " + ex.getMessage());
              }
            }
          }
        }

        ctx.getPane().insertString(codeStr);
        ctx.reparse();
        RecorderNotifications.success(model.size() + " line(s) inserted.");
      }
    });

    dispose();
  }

  // ── OCR status check (reserved for future OCR-based actions) ──

  private void checkOcrStatus() {
    // Text actions are now simple text input, no OCR required
  }

  // ── Status updates ──

  private void updateStatus(RecorderWorkflow.RecorderState newState) {
    switch (newState) {
      case IDLE:
        statusLabel.setText("\u2B24 Ready");
        statusLabel.setForeground(new Color(0x3D, 0xDB, 0xA4));
        setActionButtonsEnabled(true);
        break;
      case CAPTURING_REGION:
        statusLabel.setText("\u2B24 Capturing region...");
        statusLabel.setForeground(new Color(0xFF, 0xA5, 0x00));
        setActionButtonsEnabled(false);
        break;
      case WAITING_OCR:
        statusLabel.setText("\u2B24 Recognizing text...");
        statusLabel.setForeground(new Color(0xFF, 0xA5, 0x00));
        setActionButtonsEnabled(false);
        break;
      case WAITING_KEY_COMBO:
        statusLabel.setText("\u2B24 Press key combo...");
        statusLabel.setForeground(new Color(0xFF, 0xA5, 0x00));
        setActionButtonsEnabled(false);
        break;
      case WAITING_PATTERN_VALIDATION:
        statusLabel.setText("\u2B24 Validating pattern...");
        statusLabel.setForeground(new Color(0xFF, 0xA5, 0x00));
        setActionButtonsEnabled(false);
        break;
      case WAITING_USER_INPUT:
        statusLabel.setText("\u2B24 Waiting for input...");
        statusLabel.setForeground(new Color(0x64, 0xB5, 0xF6));
        setActionButtonsEnabled(false);
        break;
    }
  }

  private void setActionButtonsEnabled(boolean enabled) {
    btnClick.setEnabled(enabled);
    btnDblClick.setEnabled(enabled);
    btnRClick.setEnabled(enabled);
    btnDragDrop.setEnabled(enabled);
    btnWheel.setEnabled(enabled);
    btnWait.setEnabled(enabled);
    btnType.setEnabled(enabled);
    btnKeyCombo.setEnabled(enabled);
    btnPause.setEnabled(enabled);
    btnTextClick.setEnabled(enabled);
    btnTextWait.setEnabled(enabled);
    btnTextExists.setEnabled(enabled);
  }

  /**
   * Unified image picker — gives the user three options:
   * capture a new region, reuse an existing session image, or browse a file.
   * Returns the absolute path of the chosen image, or null if cancelled.
   */
  private String pickImage(String purpose) {
    java.util.List<String> options = new java.util.ArrayList<>();
    options.add("Capture screen");
    options.add("Browse file...");
    if (!capturedImages.isEmpty()) {
      options.add("Use existing image");
    }

    int choice = JOptionPane.showOptionDialog(this,
        "Choose image source for: " + purpose,
        purpose,
        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
        null, options.toArray(), options.get(0));

    if (choice < 0) return null;
    String selected = (String) options.get(choice);

    if ("Capture screen".equals(selected)) {
      return captureImage(purpose);
    }
    if ("Browse file...".equals(selected)) {
      return browseImage();
    }
    if ("Use existing image".equals(selected)) {
      return pickFromLibrary();
    }
    return null;
  }

  private String captureImage(String purpose) {
    setVisible(false);
    getOwner().setVisible(false);
    final ScreenImage[] captured = new ScreenImage[1];
    try {
      captured[0] = new Screen().userCapture("Select region for " + purpose);
    } finally {
      getOwner().setVisible(true);
      setVisible(true);
    }
    if (captured[0] == null) return null;

    try {
      String defaultName = purpose.replaceAll("\\s+", "_").toLowerCase() + "_" + System.currentTimeMillis();
      String imageName = JOptionPane.showInputDialog(this, "Name this image:", defaultName);
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

  private String browseImage() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Select image file");
    chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
        "Image files (*.png, *.jpg, *.jpeg, *.gif)", "png", "jpg", "jpeg", "gif"));
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      File f = chooser.getSelectedFile();
      // Copy into the session dir so the path is local to the bundle later
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

  private String pickFromLibrary() {
    if (capturedImages.isEmpty()) return null;
    String[] names = capturedImages.stream()
        .map(p -> new File(p).getName())
        .toArray(String[]::new);
    String chosen = (String) JOptionPane.showInputDialog(this,
        "Choose image:", "Image Library",
        JOptionPane.PLAIN_MESSAGE, null, names, names[names.length - 1]);
    if (chosen == null) return null;
    return capturedImages.stream()
        .filter(p -> new File(p).getName().equals(chosen))
        .findFirst().orElse(null);
  }

  // ── Launch / Close App ──

  private void handleLaunchApp() {
    String appPath = JOptionPane.showInputDialog(this,
        "Application path or command:", "Launch App", JOptionPane.PLAIN_MESSAGE);
    if (appPath == null || appPath.trim().isEmpty()) return;
    appPath = appPath.trim();

    try {
      currentApp = App.open(appPath);
      if (currentApp == null) {
        RecorderNotifications.error("Failed to launch: " + appPath);
        return;
      }

      btnLaunchApp.setEnabled(false);
      btnCloseApp.setEnabled(true);
      chkScopeToApp.setEnabled(true);

      appVarName = appPath.replaceAll(".*[/\\\\]", "")
          .replaceAll("\\.[^.]+$", "")
          .replaceAll("[^a-zA-Z0-9]", "")
          .toLowerCase();
      if (appVarName.isEmpty()) appVarName = "app";

      if (codeGenerator instanceof JavaCodeGenerator) {
        codePreview.addLine("App " + appVarName + " = App.open(\"" + appPath + "\");");
        if (chkScopeToApp.isSelected()) {
          codePreview.addLine(appVarName + ".focus();");
          codePreview.addLine("Region " + appVarName + "Region = " + appVarName + ".window();");
        }
      } else if (codeGenerator instanceof RobotFrameworkCodeGenerator) {
        codePreview.addLine("    Open Application    " + appPath);
      } else {
        codePreview.addLine(appVarName + " = App.open(\"" + appPath + "\")");
        if (chkScopeToApp.isSelected()) {
          codePreview.addLine(appVarName + ".focus()");
          codePreview.addLine(appVarName + " = " + appVarName + ".window()");
        }
      }
      RecorderNotifications.success("Launched: " + appPath);
    } catch (Exception ex) {
      RecorderNotifications.error("Launch failed: " + ex.getMessage());
    }
  }

  private void handleCloseApp() {
    if (currentApp != null) {
      try {
        currentApp.close();
        if (codeGenerator instanceof JavaCodeGenerator) {
          codePreview.addLine(appVarName + ".close();");
        } else if (codeGenerator instanceof RobotFrameworkCodeGenerator) {
          codePreview.addLine("    Close Application    " + appVarName);
        } else {
          codePreview.addLine(appVarName + ".close()");
        }
        RecorderNotifications.success("App closed");
      } catch (Exception ex) {
        RecorderNotifications.error("Close failed: " + ex.getMessage());
      }
    }
    currentApp = null;
    appVarName = null;
    btnLaunchApp.setEnabled(true);
    btnCloseApp.setEnabled(false);
    chkScopeToApp.setEnabled(false);
  }

  private void focusAppIfScoped() {
    if (currentApp != null && chkScopeToApp.isSelected()) {
      try {
        currentApp.focus();
      } catch (Exception ignored) {
      }
    }
  }

  private void cleanupTempDir() {
    // Clean all oculix_recorder_* dirs in temp (including orphans from crashes)
    File tempDir = new File(System.getProperty("java.io.tmpdir"));
    File[] recorderDirs = tempDir.listFiles((dir, name) -> name.startsWith("oculix_recorder_"));
    if (recorderDirs != null) {
      for (File dir : recorderDirs) {
        File[] files = dir.listFiles();
        if (files != null) {
          for (File f : files) {
            f.delete();
          }
        }
        dir.delete();
      }
    }
  }

  private void setOpacitySafe(float opacity) {
    try {
      setOpacity(opacity);
    } catch (Exception ignored) {
      // Opacity not supported (Wayland, etc.)
    }
  }
}
