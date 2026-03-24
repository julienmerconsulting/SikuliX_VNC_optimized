/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.ide;

import org.apache.commons.io.FilenameUtils;
import org.sikuli.basics.Debug;
import org.sikuli.basics.HotkeyEvent;
import org.sikuli.basics.HotkeyListener;
import org.sikuli.basics.HotkeyManager;
import org.sikuli.basics.PreferencesUser;
import org.sikuli.basics.Settings;
import org.sikuli.script.Key;
import org.sikuli.script.Location;
import org.sikuli.script.Region;
import org.sikuli.script.Sikulix;
import org.sikuli.support.RunTime;
import org.sikuli.support.devices.ScreenDevice;
import org.sikuli.support.recorder.Recorder;
import org.sikuli.support.recorder.actions.IRecordedAction;
import org.sikuli.support.recorder.generators.ICodeGenerator;
import org.sikuli.util.EventObserver;
import org.sikuli.util.EventSubject;
import org.sikuli.util.OverlayCapturePrompt;
import org.sikuli.util.SikulixFileChooser;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

class IDEWindowManager {

  private static final String me = "IDE: ";

  private static void log(String message, Object... args) {
    Debug.logx(3, me + message, args);
  }

  private final SikulixIDE ide;

  IDEWindowManager(SikulixIDE ide) {
    this.ide = ide;
  }

  // --- Tabs ---

  private CloseableTabbedPane tabs;

  CloseableTabbedPane getTabs() {
    return tabs;
  }

  void initTabs() {
    tabs = new CloseableTabbedPane();
    tabs.setUI(new AquaCloseableTabbedPaneUI());
    tabs.addCloseableTabbedPaneListener(tabIndexToClose -> {
      ide.getContextAt(tabIndexToClose).close();
      return false;
    });
    tabs.addChangeListener(e -> {
      JTabbedPane tab = (JTabbedPane) e.getSource();
      int ix = tab.getSelectedIndex();
      ide.switchContext(ix);
    });
  }

  // --- Shortcut keys ---

  void initShortcutKeys() {
    Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
      private boolean isKeyNextTab(java.awt.event.KeyEvent ke) {
        if (ke.getKeyCode() == java.awt.event.KeyEvent.VK_TAB
                && ke.getModifiersEx() == InputEvent.CTRL_DOWN_MASK) {
          return true;
        }
        if (ke.getKeyCode() == java.awt.event.KeyEvent.VK_CLOSE_BRACKET
                && ke.getModifiersEx() == (InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)) {
          return true;
        }
        return false;
      }

      private boolean isKeyPrevTab(java.awt.event.KeyEvent ke) {
        if (ke.getKeyCode() == java.awt.event.KeyEvent.VK_TAB
                && ke.getModifiersEx() == (InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)) {
          return true;
        }
        if (ke.getKeyCode() == java.awt.event.KeyEvent.VK_OPEN_BRACKET
                && ke.getModifiersEx() == (InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)) {
          return true;
        }
        return false;
      }

      public void eventDispatched(AWTEvent e) {
        java.awt.event.KeyEvent ke = (java.awt.event.KeyEvent) e;
        if (ke.getID() == java.awt.event.KeyEvent.KEY_PRESSED) {
          if (isKeyNextTab(ke)) {
            int i = tabs.getSelectedIndex();
            int next = (i + 1) % tabs.getTabCount();
            tabs.setSelectedIndex(next);
          } else if (isKeyPrevTab(ke)) {
            int i = tabs.getSelectedIndex();
            int prev = (i - 1 + tabs.getTabCount()) % tabs.getTabCount();
            tabs.setSelectedIndex(prev);
          }
        }
      }
    }, AWTEvent.KEY_EVENT_MASK);
  }

  // --- Toolbar ---

  private ButtonCapture btnCapture;
  private ButtonRecord btnRecord;

  ButtonCapture getBtnCapture() {
    return btnCapture;
  }

  ButtonRecord getBtnRecord() {
    return btnRecord;
  }

  JToolBar initToolbar() {
    var toolbar = new JToolBar();
    var btnInsertImage = new ButtonInsertImage();
    var btnSubregion = new ButtonSubregion();
    var btnLocation = new ButtonLocation();
    var btnOffset = new ButtonOffset();
//TODO ButtonShow/ButtonShowIn
    var btnShow = new ButtonShow();
    var btnShowIn = new ButtonShowIn();

    btnCapture = new ButtonCapture();
    toolbar.add(btnCapture);
    toolbar.add(btnInsertImage);
    toolbar.add(btnSubregion);
    toolbar.add(btnLocation);
    toolbar.add(btnOffset);
/*
    toolbar.add(btnShow);
    toolbar.add(btnShowIn);
*/
    toolbar.add(Box.createHorizontalGlue());
    toolbar.add(ide.runManager.createBtnRun());
    toolbar.add(ide.runManager.createBtnRunSlow());
    toolbar.add(Box.createHorizontalGlue());

    toolbar.add(Box.createRigidArea(new Dimension(7, 0)));
    toolbar.setFloatable(false);

    btnRecord = new ButtonRecord();
    toolbar.add(btnRecord);

//    JComponent jcSearchField = createSearchField();
//    toolbar.add(jcSearchField);

    return toolbar;
  }

  // --- Toolbar button inner classes ---

  class ButtonInsertImage extends ButtonOnToolbar {

    ButtonInsertImage() {
      super();
      buttonText = SikulixIDE._I("btnInsertImageLabel");
      buttonHint = SikulixIDE._I("btnInsertImageHint");
      iconFile = "/icons/insert-image-icon.png";
      init();
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
      final String name = SikulixIDE.get().getImageNameFromLine();
      final PaneContext context = ide.getActiveContext();
      EditorPane codePane = context.getPane();
      File file = new SikulixFileChooser(SikulixIDE.ideWindow).loadImage();
      if (file == null) {
        return;
      }
      File imgFile = codePane.copyFileToBundle(file); //TODO context
      if (!name.isEmpty()) {
        final File newFile = new File(imgFile.getParentFile(), name + "." + FilenameUtils.getExtension(imgFile.getName()));
        if (!newFile.exists()) {
          if (imgFile.renameTo(newFile)) {
            imgFile = newFile;
          }
        } else {
          final String msg = String.format("%s already exists - stored as %s", newFile.getName(), imgFile.getName());
          Sikulix.popError(msg, "IDE: Insert Image");
        }
      }
      if (context.getShowThumbs()) {
        codePane.insertComponent(new EditorImageButton(imgFile));
      } else {
        codePane.insertString("\"" + imgFile.getName() + "\"");
      }
    }
  }

  class ButtonSubregion extends ButtonOnToolbar implements EventObserver {

    String promptText;
    Point start = new Point(0, 0);
    Point end = new Point(0, 0);

    ButtonSubregion() {
      super();
      buttonText = "Region"; // SikuliIDE._I("btnRegionLabel");
      buttonHint = SikulixIDE._I("btnRegionHint");
      iconFile = "/icons/region-icon.png";
      promptText = SikulixIDE._I("msgCapturePrompt");
      init();
    }

    @Override
    public void runAction(ActionEvent ae) {
      if (shouldRun()) {
        OverlayCapturePrompt.capturePrompt(this, promptText);
      }
    }

    @Override
    public void update(EventSubject es) {
      OverlayCapturePrompt ocp = (OverlayCapturePrompt) es;
      Rectangle selectedRectangle = ocp.getSelectionRectangle();
      start = ocp.getStart();
      end = ocp.getEnd();
      ocp.close();
      ScreenDevice.closeCapturePrompts();
      captureComplete(selectedRectangle);
      SikulixIDE.showAgain();
    }

    void captureComplete(Rectangle selectedRectangle) {
      int x, y, w, h;
      EditorPane codePane = ide.getCurrentCodePane();
      if (selectedRectangle != null) {
        Rectangle roi = selectedRectangle;
        x = (int) roi.getX();
        y = (int) roi.getY();
        w = (int) roi.getWidth();
        h = (int) roi.getHeight();
        if (codePane.context.getShowThumbs()) {
          if (SikulixIDE.prefs.getPrefMoreImageThumbs()) { //TODO
            codePane.insertComponent(new EditorRegionButton(codePane, x, y, w, h));
          } else {
            codePane.insertComponent(new EditorRegionLabel(codePane,
                    new EditorRegionButton(codePane, x, y, w, h).toString()));
          }
        } else {
          codePane.insertString(codePane.getRegionString(x, y, w, h));
        }
      }
    }
  }

  class ButtonLocation extends ButtonSubregion {

    ButtonLocation() {
      super();
      buttonText = "Location";
      buttonHint = "Location as center of selection";
      iconFile = "/icons/region-icon.png";
      promptText = "Select a Location";
      init();
    }

    @Override
    public void captureComplete(Rectangle selectedRectangle) {
      int x, y, w, h;
      if (selectedRectangle != null) {
        Rectangle roi = selectedRectangle;
        x = (int) (roi.getX() + roi.getWidth() / 2);
        y = (int) (roi.getY() + roi.getHeight() / 2);
        ide.getCurrentCodePane().insertString(String.format("Location(%d, %d)", x, y));
      }
    }
  }

  class ButtonOffset extends ButtonSubregion {

    ButtonOffset() {
      super();
      buttonText = "Offset";
      buttonHint = "Offset as width/height of selection";
      iconFile = "/icons/region-icon.png";
      promptText = "Select an Offset";
      init();
    }

    @Override
    public void captureComplete(Rectangle selectedRectangle) {
      int x, y, ox, oy;
      if (selectedRectangle != null) {
        Rectangle roi = selectedRectangle;
        ox = (int) roi.getWidth();
        oy = (int) roi.getHeight();
        Location start = new Location(super.start);
        Location end = new Location(super.end);
        if (end.x < start.x) {
          ox *= -1;
        }
        if (end.y < start.y) {
          oy *= -1;
        }
        ide.getCurrentCodePane().insertString(String.format("Offset(%d, %d)", ox, oy));
      }
    }
  }

  class ButtonShow extends ButtonOnToolbar {

    ButtonShow() {
      super();
      buttonText = "Show";
      buttonHint = "Find and highlight the image in current line";
      iconFile = "/icons/region-icon.png";
      init();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      EditorPane codePane = ide.getActiveContext().getPane();
      String line = codePane.getLineTextAtCaret();
      final String item = codePane.parseLineText(line);
      if (!item.isEmpty()) {
        SikulixIDE.doHide();
        new Thread(new Runnable() {
          @Override
          public void run() {
            String eval = "";
            eval = item.replaceAll("\"", "\\\"");
            if (item.startsWith("Region")) {
              if (item.contains(".asOffset()")) {
                eval = item.replace(".asOffset()", "");
              }
              eval = "Region.create" + eval.substring(6) + ".highlight(2);";
            } else if (item.startsWith("Location")) {
              eval = "new " + item + ".grow(10).highlight(2);";
            } else if (item.startsWith("Pattern")) {
              eval = "m = Screen.all().exists(new " + item + ", 0);";
              eval += "if (m != null) m.highlight(2);";
            } else if (item.startsWith("\"")) {
              eval = "m = Screen.all().exists(" + item + ", 0); ";
              eval += "if (m != null) m.highlight(2);";
            }
            log("ButtonShow:\n%s", eval); //TODO eval ButtonShow
            SikulixIDE.doShow();
          }
        }).start();
        return;
      }
      Sikulix.popup("ButtonShow: Nothing to show!" +
              "\nThe line with the cursor should contain:" +
              "\n- an absolute Region or Location" +
              "\n- an image file name or" +
              "\n- a Pattern with an image file name");
    }
  }

  class ButtonShowIn extends ButtonSubregion {

    String item = "";

    ButtonShowIn() {
      super();
      buttonText = "Show in";
      buttonHint = "Like Show, but in selected region";
      iconFile = "/icons/region-icon.png";
      init();
    }

    public boolean shouldRun() {
      EditorPane codePane = ide.getCurrentCodePane();
      String line = codePane.getLineTextAtCaret();
      item = codePane.parseLineText(line);
      item = item.replaceAll("\"", "\\\"");
      if (item.startsWith("Pattern")) {
        item = "m = null; r = #region#; "
                + "if (r != null) m = r.exists(new " + item + ", 0); "
                + "if (m != null) m.highlight(2); else print(m);";
      } else if (item.startsWith("\"")) {
        item = "m = null; r = #region#; "
                + "if (r != null) m = r.exists(" + item + ", 0); "
                + "if (m != null) m.highlight(2); else print(m);";
      } else {
        item = "";
      }
      return !item.isEmpty();
    }

    public void captureComplete(Rectangle selectedRectangle) {
      if (selectedRectangle != null) {
        Region reg = new Region(selectedRectangle);
        String itemReg = String.format("new Region(%d, %d, %d, %d)", reg.x, reg.y, reg.w, reg.h);
        item = item.replace("#region#", itemReg);
        final String evalText = item;
        new Thread(new Runnable() {
          @Override
          public void run() {
            log("ButtonShowIn:\n%s", evalText);
            //TODO ButtonShowIn perform show
          }
        }).start();
        RunTime.pause(2.0f);
      } else {
        SikulixIDE.showAgain();
        Sikulix.popup("ButtonShowIn: Nothing to show!" +
                "\nThe line with the cursor should contain:" +
                "\n- an image file name or" +
                "\n- a Pattern with an image file name");
      }
    }
  }

  class ButtonRecord extends ButtonOnToolbar {

    private Recorder recorder = new Recorder();

    ButtonRecord() {
      super();

      URL imageURL = SikulixIDE.class.getResource("/icons/record.png");
      setIcon(new ImageIcon(imageURL));
      initTooltip();
      addActionListener(this);
      setText(SikulixIDE._I("btnRecordLabel"));
      // setMaximumSize(new Dimension(45,45));
    }

    private void initTooltip() {
      PreferencesUser pref = PreferencesUser.get();
      String strHotkey = Key.convertKeyToText(pref.getStopHotkey(), pref.getStopHotkeyModifiers());
      String stopHint = SikulixIDE._I("btnRecordStopHint", strHotkey);
      setToolTipText(SikulixIDE._I("btnRecord", stopHint));
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
      SikulixIDE.ideWindow.setVisible(false);
      recorder.start();
    }

    public void stopRecord() {
      SikulixIDE.showAgain();

      if (isRunning()) {
        PaneContext context = ide.getActiveContext();
        EditorPane pane = context.getPane();
        ICodeGenerator generator = pane.getCodeGenerator();

        ProgressMonitor progress = new ProgressMonitor(pane, SikulixIDE._I("processingWorkflow"), "", 0, 0);
        progress.setMillisToDecideToPopup(0);
        progress.setMillisToPopup(0);

        new Thread(() -> {
          try {
            List<IRecordedAction> actions = recorder.stop(progress);

            if (!actions.isEmpty()) {
              List<String> actionStrings = actions.stream().map((a) -> a.generate(generator)).collect(Collectors.toList());

              EventQueue.invokeLater(() -> {
                pane.insertString("\n" + String.join("\n", actionStrings) + "\n");
                context.reparse();
              });
            }
          } finally {
            progress.close();
          }
        }).start();
      }
    }

    public boolean isRunning() {
      return recorder.isRunning();
    }
  }

  // --- Message Area ---

  private JTabbedPane messageArea = null;
  private EditorConsolePane messages = null;
  private boolean SHOULD_WRAP_LINE = false;
  private boolean messageAreaCollapsed = false;
  private JSplitPane mainPane;

  JTabbedPane getMessageArea() {
    return messageArea;
  }

  void setMessageArea(JTabbedPane messageArea) {
    this.messageArea = messageArea;
  }

  EditorConsolePane getMessages() {
    return messages;
  }

  void setMessages(EditorConsolePane messages) {
    this.messages = messages;
  }

  JSplitPane getMainPane() {
    return mainPane;
  }

  void setMainPane(JSplitPane mainPane) {
    this.mainPane = mainPane;
  }

  void initMessageArea() {
    messages.init(SHOULD_WRAP_LINE);
    messageArea = new JTabbedPane();
    messageArea.addTab(SikulixIDE._I("paneMessage"), null, messages, "DoubleClick to hide/unhide");
    if (Settings.isWindows() || Settings.isLinux()) {
      messageArea.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
    }
    messageArea.addMouseListener(new MouseListener() {
      @Override
      public void mouseClicked(MouseEvent me) {
        if (me.getClickCount() < 2) {
          return;
        }
        toggleCollapsed();
      }
      //<editor-fold defaultstate="collapsed" desc="mouse events not used">

      @Override
      public void mousePressed(MouseEvent me) {
      }

      @Override
      public void mouseReleased(MouseEvent me) {
      }

      @Override
      public void mouseEntered(MouseEvent me) {
      }

      @Override
      public void mouseExited(MouseEvent me) {
      }
      //</editor-fold>
    });
  }

  void clearMessageArea() {
    if (messages == null) {
      return;
    }
    messages.clear();
  }

  void collapseMessageArea() {
    if (messages == null) {
      return;
    }
    if (messageAreaCollapsed) {
      return;
    }
    toggleCollapsed();
  }

  void uncollapseMessageArea() {
    if (messages == null) {
      return;
    }
    if (messageAreaCollapsed) {
      toggleCollapsed();
    }
  }

  private void toggleCollapsed() {
    if (messages == null) {
      return;
    }
    if (messageAreaCollapsed) {
      mainPane.setDividerLocation(mainPane.getLastDividerLocation());
      messageAreaCollapsed = false;
    } else {
      int pos = mainPane.getWidth() - 35;
      mainPane.setDividerLocation(pos);
      messageAreaCollapsed = true;
    }
  }

  public EditorConsolePane getConsole() {
    return messages;
  }

  // --- Capture Hotkey ---

  void removeCaptureHotkey() {
    HotkeyManager.getInstance().removeHotkey("Capture");
  }

  void installCaptureHotkey() {
    HotkeyManager.getInstance().addHotkey("Capture", new HotkeyListener() {
      @Override
      public void hotkeyPressed(HotkeyEvent e) {
        if (SikulixIDE.get().isVisible()) {
          btnCapture.capture(0);
        }
      }
    });
  }

  // --- showAfterStart ---

  static void showAfterStart(SikulixIDE ide, IDEWindowManager wm) {
    while (!SikulixIDE.ideIsReady.get()) {
      RunTime.pause(100);
    }
    org.sikuli.ide.Sikulix.stopSplash();
    SikulixIDE.ideWindow.setVisible(true);
    if (wm.mainPane != null) {
      wm.mainPane.setDividerLocation(0.6); //TODO saved value
    }
    ide.getActiveContext().focus();
  }

  // --- Layout builder (from startGUI) ---

  void buildLayout(Container ideContainer) {
    Debug.log("IDE: creating tabbed editor");
    initTabs();
    Debug.log("IDE: creating message area");
    if (messages != null) {
      initMessageArea();
    }
    Debug.log("IDE: creating combined work window");
    var codePane = new JPanel(new BorderLayout(10, 10));
    codePane.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
    codePane.add(tabs, BorderLayout.CENTER);

    Debug.log("IDE: Putting all together");
    var editPane = new JPanel(new BorderLayout(0, 0));
    mainPane = null;
    if (messageArea != null) {
      if (SikulixIDE.prefs.getPrefMoreMessage() == PreferencesUser.VERTICAL) {
        mainPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, codePane, messageArea);
      } else {
        mainPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, codePane, messageArea);
      }
      mainPane.setResizeWeight(0.6);
      mainPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
      editPane.add(mainPane, BorderLayout.CENTER);
    } else {
      editPane.add(codePane, BorderLayout.CENTER);
    }

    ideContainer.add(editPane, BorderLayout.CENTER);
    Debug.log("IDE: Putting all together - after main pane");

    JToolBar tb = initToolbar();
    ideContainer.add(tb, BorderLayout.NORTH);
    Debug.log("IDE: Putting all together - after toolbar");
  }
}
