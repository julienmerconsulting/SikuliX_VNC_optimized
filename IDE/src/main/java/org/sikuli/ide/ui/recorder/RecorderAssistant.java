/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui.recorder;

import net.miginfocom.swing.MigLayout;
import org.sikuli.script.*;
import org.sikuli.support.recorder.PatternValidator;
import org.sikuli.support.recorder.generators.JythonCodeGenerator;

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
  private final JythonCodeGenerator codeGenerator;
  private File screenshotDir;

  // UI components
  private JLabel statusLabel;
  private JButton btnClick, btnDblClick, btnRClick;
  private JButton btnTextClick, btnTextWait, btnTextExists;
  private JButton btnType, btnKeyCombo;
  private JButton btnDragDrop, btnWheel, btnWait, btnPause;
  private JButton btnInsert, btnClear;

  public RecorderAssistant(Frame parent) {
    super(parent, "OculiX Modern Recorder (beta)", false);
    setSize(400, 580);
    setLocationRelativeTo(parent);
    setAlwaysOnTop(true);
    setType(Window.Type.UTILITY);
    setResizable(false);

    this.workflow = new RecorderWorkflow();
    this.codePreview = new RecorderCodePreview();
    this.codeGenerator = new JythonCodeGenerator();

    // Create temp dir for screenshots
    try {
      screenshotDir = Files.createTempDirectory("oculix_recorder_").toFile();
      screenshotDir.deleteOnExit();
    } catch (IOException e) {
      screenshotDir = new File(System.getProperty("java.io.tmpdir"));
    }

    buildUI();
    wireWorkflow();
    checkOcrStatus();

    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        workflow.dispose();
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

    JPanel imageRow2 = new JPanel(new MigLayout("insets 0, gap 4", "[grow][grow][grow]"));
    imageRow2.setOpaque(false);
    btnDragDrop = createActionButton("Drag&Drop");
    btnWheel = createActionButton("Wheel");
    btnWait = createActionButton("Wait");
    imageRow2.add(btnDragDrop, "grow");
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

    // Image actions
    btnClick.addActionListener(e -> handleImageCapture("click"));
    btnDblClick.addActionListener(e -> handleImageCapture("doubleClick"));
    btnRClick.addActionListener(e -> handleImageCapture("rightClick"));
    btnWait.addActionListener(e -> handleImageCapture("wait"));
    btnWheel.addActionListener(e -> handleWheelCapture());
    btnDragDrop.addActionListener(e -> handleDragDrop());

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
  }

  private void showAfterCapture() {
    setVisible(true);
  }

  // ── Image capture workflows ──

  private void handleImageCapture(String actionType) {
    if (!workflow.startCapture(actionType)) return;

    hideForCapture();

    new Thread(() -> {
      ScreenImage capture = new Screen().userCapture("Select region to " + actionType);

      SwingUtilities.invokeLater(() -> {
        showAfterCapture();

        if (capture == null) {
          workflow.reset();
          return;
        }

        try {
          String imagePath = capture.save(screenshotDir.getAbsolutePath());
          if (imagePath == null) {
            workflow.reset();
            RecorderNotifications.error("Failed to save screenshot");
            return;
          }

          workflow.onCaptureComplete(); // -> WAITING_PATTERN_VALIDATION

          // Validate pattern
          PatternValidator.ValidationResult result = PatternValidator.validate(
              new Screen().capture().getImage(), capture.getImage());

          Pattern pattern = new Pattern(imagePath);
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

          String code = generateImageCode(actionType, pattern);
          codePreview.addLine(code);
          workflow.onActionComplete(); // -> IDLE

        } catch (Exception ex) {
          workflow.reset();
          RecorderNotifications.error("Capture failed: " + ex.getMessage());
        }
      });
    }, "RecorderCapture").start();
  }

  private String generateImageCode(String actionType, Pattern pattern) {
    String[] noModifiers = new String[0];
    switch (actionType) {
      case "click":
        return codeGenerator.click(pattern, noModifiers);
      case "doubleClick":
        return codeGenerator.doubleClick(pattern, noModifiers);
      case "rightClick":
        return codeGenerator.rightClick(pattern, noModifiers);
      case "wait":
        return codeGenerator.wait(pattern, 10, null);
      default:
        return "# " + actionType + "(\"" + pattern.getFilename() + "\")";
    }
  }

  private void handleDragDrop() {
    if (!workflow.startDragDrop()) return;

    hideForCapture();

    new Thread(() -> {
      // Step 1: capture source
      ScreenImage sourceCapture = new Screen().userCapture("Select DRAG SOURCE");
      if (sourceCapture == null) {
        SwingUtilities.invokeLater(() -> { showAfterCapture(); workflow.reset(); });
        return;
      }
      workflow.advanceDragDrop(); // SOURCE -> DESTINATION

      // Step 2: capture destination
      ScreenImage destCapture = new Screen().userCapture("Select DROP DESTINATION");

      SwingUtilities.invokeLater(() -> {
        showAfterCapture();

        if (destCapture == null) {
          workflow.reset();
          return;
        }

        try {
          String sourcePath = sourceCapture.save(screenshotDir.getAbsolutePath());
          String destPath = destCapture.save(screenshotDir.getAbsolutePath());

          Pattern sourcePattern = new Pattern(sourcePath);
          Pattern destPattern = new Pattern(destPath);
          String code = codeGenerator.dragDrop(sourcePattern, destPattern);
          codePreview.addLine(code);
          workflow.onActionComplete();
          RecorderNotifications.success("Drag & Drop recorded");
        } catch (Exception ex) {
          workflow.reset();
          RecorderNotifications.error("Drag & Drop failed: " + ex.getMessage());
        }
      });
    }, "RecorderDragDrop").start();
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
          Pattern pattern = new Pattern(imagePath);

          // Ask direction and steps
          String[] options = {"Down", "Up"};
          int dir = JOptionPane.showOptionDialog(this, "Wheel direction?",
              "Wheel", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
              null, options, options[0]);
          if (dir < 0) { workflow.reset(); return; }

          String stepsStr = JOptionPane.showInputDialog(this, "Number of steps:", "3");
          if (stepsStr == null) { workflow.reset(); return; }
          int steps = Integer.parseInt(stepsStr.trim());

          String code = codeGenerator.wheel(pattern, dir == 0 ? 1 : -1, steps, new String[0], 0);
          codePreview.addLine(code);
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
      codePreview.addLine(generateTextCode(actionType, text.trim()));
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
      codePreview.addLine(code);
    }
    workflow.onActionComplete();
  }

  private void handleKeyCombo() {
    if (!workflow.startKeyComboCApture()) return;

    String combo = JOptionPane.showInputDialog(this,
        "Key combination (e.g. Key.ENTER, Key.CTRL + 'c'):",
        "Key Combo", JOptionPane.PLAIN_MESSAGE);
    if (combo != null && !combo.isEmpty()) {
      codePreview.addLine("type(" + combo + ")");
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
      getOwner().setVisible(true); // Restore IDE
      dispose();
      return;
    }

    // Build code string
    StringBuilder code = new StringBuilder("\n");
    for (int i = 0; i < model.size(); i++) {
      code.append(model.get(i)).append("\n");
    }

    // Copy to clipboard
    java.awt.datatransfer.StringSelection selection =
        new java.awt.datatransfer.StringSelection(code.toString());
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);

    workflow.dispose();
    getOwner().setVisible(true); // Restore IDE
    RecorderNotifications.success("Code copied to clipboard. Paste with Ctrl+V.");
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

  private void setOpacitySafe(float opacity) {
    try {
      setOpacity(opacity);
    } catch (Exception ignored) {
      // Opacity not supported (Wayland, etc.)
    }
  }
}
