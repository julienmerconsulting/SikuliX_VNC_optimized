/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui.recorder;

import net.miginfocom.swing.MigLayout;
import org.sikuli.ide.SikulixIDE;
import org.sikuli.support.recorder.generators.ICodeGenerator;

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
 * Delegates to RecorderActions, RecorderCodeGen, RecorderAppScope, RecorderImagePicker.
 */
public class RecorderAssistant extends JDialog {

  private final RecorderWorkflow workflow;
  private final RecorderCodePreview codePreview;
  private final RecorderCodeGen codeGen;
  private final RecorderAppScope appScope;
  private final RecorderActions actions;
  private File screenshotDir;

  private final java.util.List<String> capturedImages = new java.util.ArrayList<>();

  // UI components
  private JLabel statusLabel;
  private JButton btnLaunchApp, btnCloseApp;
  private JCheckBox chkScopeToApp;
  private JButton btnClick, btnDblClick, btnRClick;
  private JButton btnTextClick, btnTextWait, btnTextExists;
  private JButton btnType, btnKeyCombo;
  private JButton btnDragDrop, btnSwipe, btnWheel, btnWait, btnPause;
  private JButton btnInsert, btnClear;

  public RecorderAssistant(Frame parent, ICodeGenerator generator) {
    super(parent, "OculiX Modern Recorder (beta)", false);
    setSize(400, 680);
    setLocationRelativeTo(parent);
    setAlwaysOnTop(true);
    setType(Window.Type.UTILITY);
    setResizable(false);

    this.workflow = new RecorderWorkflow();
    this.codePreview = new RecorderCodePreview();
    this.codeGen = new RecorderCodeGen(generator, codePreview);

    try {
      screenshotDir = Files.createTempDirectory("oculix_recorder_").toFile();
      screenshotDir.deleteOnExit();
    } catch (IOException e) {
      screenshotDir = new File(System.getProperty("java.io.tmpdir"));
    }

    org.sikuli.support.Commons.loadOpenCV();

    buildUI();

    this.appScope = new RecorderAppScope(btnLaunchApp, btnCloseApp, chkScopeToApp, codeGen);
    RecorderImagePicker imagePicker = new RecorderImagePicker(this, screenshotDir, capturedImages);
    this.actions = new RecorderActions(this, workflow, codeGen, appScope, imagePicker,
        codePreview, screenshotDir, capturedImages);

    codeGen.initRFHeaders();

    wireWorkflow();
    checkOcrStatus();

    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        workflow.dispose();
        cleanupTempDir();
        getOwner().setVisible(true);
      }
    });

    RecorderNotifications.init(parent);
    parent.setVisible(false);
  }

  // ── UI ──

  private void buildUI() {
    JPanel content = new JPanel(new MigLayout(
        "wrap 1, insets 10, gap 6", "[grow, fill]", ""));
    content.setBackground(UIManager.getColor("Panel.background"));

    statusLabel = new JLabel("\u2B24 Ready");
    statusLabel.setFont(UIManager.getFont("defaultFont").deriveFont(11f));
    statusLabel.setForeground(new Color(0x3D, 0xDB, 0xA4));
    content.add(statusLabel);

    content.add(new JSeparator(), "growx");

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

    content.add(createSectionLabel("TEXT ACTIONS"));
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

    content.add(createSectionLabel("KEYBOARD"));
    JPanel kbRow = new JPanel(new MigLayout("insets 0, gap 4", "[grow][grow]"));
    kbRow.setOpaque(false);
    btnType = createActionButton("Type");
    btnKeyCombo = createActionButton("Key Combo");
    kbRow.add(btnType, "grow");
    kbRow.add(btnKeyCombo, "grow");
    content.add(kbRow);

    JPanel otherRow = new JPanel(new MigLayout("insets 0, gap 4", "[grow]"));
    otherRow.setOpaque(false);
    btnPause = createActionButton("Pause");
    otherRow.add(btnPause, "grow");
    content.add(otherRow);

    content.add(new JSeparator(), "growx, gaptop 4");

    content.add(createSectionLabel("GENERATED CODE"));
    JScrollPane scrollPane = new JScrollPane(codePreview);
    scrollPane.setPreferredSize(new Dimension(0, 120));
    content.add(scrollPane, "grow, push");

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

  // ── Wiring ──

  private void wireWorkflow() {
    workflow.addStateListener((oldState, newState) -> updateStatus(newState));

    btnLaunchApp.addActionListener(e -> appScope.handleLaunchApp(this));
    btnCloseApp.addActionListener(e -> appScope.handleCloseApp());
    chkScopeToApp.addActionListener(e -> {});

    btnClick.addActionListener(e -> actions.handleImageCapture("click"));
    btnDblClick.addActionListener(e -> actions.handleImageCapture("doubleClick"));
    btnRClick.addActionListener(e -> actions.handleImageCapture("rightClick"));
    btnWait.addActionListener(e -> actions.handleImageCapture("wait"));
    btnWheel.addActionListener(e -> actions.handleWheelCapture());
    btnDragDrop.addActionListener(e -> actions.handleDragDrop());
    btnSwipe.addActionListener(e -> actions.handleSwipe());

    btnTextClick.addActionListener(e -> actions.handleTextAction("textClick"));
    btnTextWait.addActionListener(e -> actions.handleTextAction("textWait"));
    btnTextExists.addActionListener(e -> actions.handleTextAction("textExists"));

    btnType.addActionListener(e -> actions.handleTypeText());
    btnKeyCombo.addActionListener(e -> actions.handleKeyCombo());

    btnPause.addActionListener(e -> actions.handlePause());
  }

  // ── Capture visibility helpers (called by RecorderActions) ──

  void hideForCapture() {
    setVisible(false);
    appScope.focusAppIfScoped();
  }

  void showAfterCapture() {
    setVisible(true);
  }

  // ── Insert & Close ──

  private void insertAndClose() {
    DefaultListModel<String> model = (DefaultListModel<String>) codePreview.getModel();
    if (model.isEmpty()) {
      workflow.dispose();
      getOwner().setVisible(true);
      dispose();
      return;
    }

    StringBuilder code = new StringBuilder("\n");
    for (int i = 0; i < model.size(); i++) {
      code.append(model.get(i)).append("\n");
    }
    String codeStr = code.toString();

    String[] options = {"Current Script", "New Script", "Cancel"};
    int choice = JOptionPane.showOptionDialog(this,
        "Insert " + model.size() + " line(s) of generated code:",
        "Insert Code",
        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
        null, options, options[0]);

    if (choice == 2 || choice < 0) return;

    workflow.dispose();
    SikulixIDE ide = (SikulixIDE) getOwner();
    ide.setVisible(true);
    cleanupTempDir();

    if (choice == 1) {
      ide.createEmptyScriptContext();
    }

    java.awt.EventQueue.invokeLater(() -> {
      SikulixIDE.PaneContext ctx = ide.getActiveContext();
      if (ctx != null && ctx.getPane() != null) {
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

  // ── Status ──

  private void checkOcrStatus() {}

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

  // ── Cleanup ──

  private void cleanupTempDir() {
    File tempDir = new File(System.getProperty("java.io.tmpdir"));
    File[] recorderDirs = tempDir.listFiles((dir, name) -> name.startsWith("oculix_recorder_"));
    if (recorderDirs != null) {
      for (File dir : recorderDirs) {
        File[] files = dir.listFiles();
        if (files != null) {
          for (File f : files) f.delete();
        }
        dir.delete();
      }
    }
  }
}
