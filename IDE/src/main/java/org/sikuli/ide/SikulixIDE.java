/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.ide;

import org.sikuli.basics.*;
import org.sikuli.support.FileManager;
import org.sikuli.support.ide.*;
import org.sikuli.script.Sikulix;
import org.sikuli.script.*;
import org.sikuli.support.ide.Runner;
import org.sikuli.support.runner.TextRunner;
import org.sikuli.support.Commons;
import org.sikuli.support.devices.IScreen;
import org.sikuli.support.RunTime;
import org.sikuli.support.gui.SXDialog;
import org.sikuli.util.*;

import javax.swing.*;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Element;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLDecoder;
import java.security.CodeSource;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SikulixIDE extends JFrame {

  static final String me = "IDE: ";

  private static void log(String message, Object... args) {
    Debug.logx(3, me + message, args);
  }

  private static void trace(String message, Object... args) {
    Debug.logx(4, me + message, args);
  }

  private static void error(String message, Object... args) {
    Debug.logx(-1, me + message, args);
  }

  private static void fatal(String message, Object... args) {
    Debug.logx(-1, me + "FATAL: " + message, args);
  }

  private static void todo(String message, Object... args) {
    Debug.logx(-1, me + "TODO: " + message, args);
  }

  //<editor-fold desc="00 IDE instance">
  static final SikulixIDE sikulixIDE = new SikulixIDE();

  static PreferencesUser prefs;

  final IDEFileManager fileManager = new IDEFileManager(this);
  final IDERunManager runManager = new IDERunManager(this);
  final IDEWindowManager windowManager = new IDEWindowManager(this);

  private SikulixIDE() {
    prefs = PreferencesUser.get();
    if (prefs.getUserType() < 0) {
      prefs.setIdeSession("");
      prefs.setDefaults();
    }
  }

  public static SikulixIDE get() {
    if (sikulixIDE == null) {
      throw new SikuliXception("SikulixIDE:get(): instance should not be null");
    }
    return sikulixIDE;
  }

  static Rectangle ideWindowRect = null;

  public static Rectangle getWindowRect() {
    Dimension windowSize = prefs.getIdeSize();
    Point windowLocation = prefs.getIdeLocation();
    if (windowSize.width < 700) {
      windowSize.width = 800;
    }
    if (windowSize.height < 500) {
      windowSize.height = 600;
    }
    return new Rectangle(windowLocation, windowSize);
  }

  public static Point getWindowCenter() {
    return new Point((int) getWindowRect().getCenterX(), (int) getWindowRect().getCenterY());
  }

  public static Point getWindowTop() {
    Rectangle rect = getWindowRect();
    int x = rect.x + rect.width / 2;
    int y = rect.y + 30;
    return new Point(x, y);
  }

  static JFrame ideWindow = null;

  public static void setWindow() {
    if (ideWindow == null) {
      ideWindow = sikulixIDE;
    }
  }

  public void setIDETitle(String title) {
    ideWindow.setTitle(title);
  }

  public static void doShow() {
    showAgain();
  }

  public static boolean notHidden() {
    return ideWindow.isVisible();
  }

  public static void  doHide() {
    doHide(0.5f);
  }

  public static void doHide(float waitTime) {
    ideWindow.setVisible(false);
    RunTime.pause(waitTime);
  }

  static void showAgain() {
    ideWindow.setVisible(true);
    sikulixIDE.getActiveContext().focus();
  }

  //TODO showAfterStart to be revised
  static String _I(String key, Object... args) {
    try {
      return SikuliIDEI18N._I(key, args);
    } catch (Exception e) {
      log("[I18N] " + key);
      return key;
    }
  }

  static ImageIcon getIconResource(String name) {
    URL url = SikulixIDE.class.getResource(name);
    if (url == null) {
      Debug.error("IDE: Could not load \"" + name + "\" icon");
      return null;
    }
    return new ImageIcon(url);
  }
  //</editor-fold>

  //<editor-fold desc="01 startup / quit">
  static AtomicBoolean ideIsReady = new AtomicBoolean(false);

  protected static void start() {

    ideWindowRect = getWindowRect();

    IDEDesktopSupport.init();
    IDESupport.initIDESupport();
    if (!Commons.hasOption(CommandArgsEnum.CONSOLE)) {
      get().windowManager.setMessages(new EditorConsolePane());
    }
    IDESupport.initRunners();
    Commons.startLog(1, "IDESupport ready --- GUI start (%4.1f)", Commons.getSinceStart());

    sikulixIDE.startGUI();
  }

  boolean ideIsQuitting = false;

  private boolean closeIDE() {
    ideIsQuitting = true;
    if (!doBeforeQuit()) {
      return false;
    }
    ideIsQuitting = false;
    return true;
  }

  private boolean doBeforeQuit() {
    if (checkDirtyPanes()) {
      int answer = askForSaveAll("Quit");
      if (answer == SXDialog.DECISION_CANCEL) {
        return false;
      }
      if (answer == SXDialog.DECISION_ACCEPT) {
        return saveSession(DO_SAVE_ALL);
      }
      log("Quit: without saving anything");
    }
    return saveSession(DO_SAVE_NOTHING);
  }

  int DO_SAVE_ALL = 1;
  int DO_SAVE_NOTHING = -1;

  private int askForSaveAll(String typ) {
    String message = "Some scripts are not saved yet!";
    String title = SikuliIDEI18N._I("dlgAskCloseTab");
    String ignore = typ + " immediately";
    String accept = "Save all and " + typ;
    return SXDialog.askForDecision(sikulixIDE, title, message, ignore, accept);
  }

  public boolean terminate() {
    log("Quit requested");
    if (closeIDE()) {
      RunTime.terminate(0, "");
    }
    log("Quit: cancelled or did not work");
    return false;
  }
  //</editor-fold>

  //<editor-fold desc="02 init IDE">
  private void startGUI() {
    setWindow();

    windowManager.installCaptureHotkey();
    runManager.installStopHotkey();

    ideWindow.setSize(ideWindowRect.getSize());
    ideWindow.setLocation(ideWindowRect.getLocation());

    Debug.log("IDE: Adding components to window");
    initMenuBars(ideWindow);
    final Container ideContainer = ideWindow.getContentPane();
    ideContainer.setLayout(new BorderLayout());

    windowManager.buildLayout(ideContainer);

    _status = new SikuliIDEStatusBar();
    ideContainer.add(_status, BorderLayout.SOUTH);

    Debug.log("IDE: Putting all together - before layout");
    ideContainer.doLayout();

    Debug.log("IDE: Putting all together - after layout");
    ideWindow.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

    ideWindow.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        terminate();
      }
    });

    ideWindow.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        PreferencesUser.get().setIdeSize(ideWindow.getSize());
      }

      @Override
      public void componentMoved(ComponentEvent e) {
        PreferencesUser.get().setIdeLocation(ideWindow.getLocation());
      }
    });
    ToolTipManager.sharedInstance().setDismissDelay(30000);

    createEmptyScriptContext();
    EditorConsolePane msgs = windowManager.getMessages();
    if (msgs != null) {
      msgs.initRedirect();
    }
    Debug.log("IDE: Putting all together - Restore last Session");
    restoreSession();

    windowManager.initShortcutKeys();
    ideIsReady.set(true);
    Commons.startLog(3, "IDE ready: on Java %d (%4.1f sec)", Commons.getJavaVersion(), Commons.getSinceStart());
  }

  public static void showAfterStart() {
    IDEWindowManager.showAfterStart(sikulixIDE, sikulixIDE.windowManager);
  }

  static SikuliIDEStatusBar getStatusbar() {
    return sikulixIDE._status;
  }

  private SikuliIDEStatusBar _status;

  CloseableTabbedPane getTabs() {
    return windowManager.getTabs();
  }
  //</editor-fold>

  //<editor-fold desc="03 PaneContext">
  List<PaneContext> contexts = new ArrayList<>();
  List<PaneContext> contextsClosed = new ArrayList<>();
  String tempName = "sxtemp";
  int tempIndex = 1;

  PaneContext lastContext = null;

  PaneContext getActiveContext() {
    final int ix = tabs.getSelectedIndex();
    if (ix < 0) {
      fatal("PaneContext: no context available"); //TODO possible?
    }
    return contexts.get(ix);
  }

  PaneContext setActiveContext(int pos) {
    tabs.setSelectedIndex(pos);
    PaneContext context = contexts.get(pos);
    showContext(context);
    return context;
  }

  PaneContext getContextAt(int ix) {
    return contexts.get(ix);
  }

  void switchContext(int ix) {
    if (ix < 0) {
      return;
    }
    if (ix >= contexts.size()) {
      RunTime.terminate(999, "IDE: switchPane: invalid tab index: %d (valid: 0 .. %d)",
              ix, contexts.size() - 1);
    }
    PaneContext context = contexts.get(ix);
    PaneContext previous = lastContext;
    lastContext = context;

    if (null != previous && previous.isDirty()) {
      previous.save();
    }
    showContext(context);
  }

  private void showContext(PaneContext context) {
    setIDETitle(context.getFile().getAbsolutePath());
    ImagePath.setBundleFolder(context.getFolder());

    getStatusbar().setType(context.getType());

    chkShowThumbs.setState(getActiveContext().getShowThumbs());

    final EditorPane editorPane = context.getPane();
    int dot = editorPane.getCaret().getDot();
    editorPane.setCaretPosition(dot);
    updateUndoRedoStates();

    if (context.isText()) {
      collapseMessageArea();
    } else {
      uncollapseMessageArea();
    }
  }

  void createEmptyScriptContext() {
    final PaneContext context = new PaneContext();
    context.setRunner(IDESupport.getDefaultRunner());
    context.setFile();
    context.create();
  }

  void createEmptyTextContext() {
    final PaneContext context = new PaneContext();
    context.setRunner(Runner.getRunner(TextRunner.class));
    context.setFile();
    context.create();
  }

  void createFileContext(File file) {
    final int pos = alreadyOpen(file);
    if (pos >= 0) {
      setActiveContext(pos);
      log("PaneContext: alreadyopen: %s", file);
      return;
    }
    final PaneContext context = new PaneContext();
    context.setFile(file);
    context.setRunner();
    if (!context.isValid()) {
      log("PaneContext: open not posssible: %s", file);
      return;
    }
    context.pos = contexts.size();
    context.create();
    context.doShowThumbs();
    context.notDirty();
  }

  int alreadyOpen(File file) {
    for (PaneContext context : contexts) {
      File folderOrFile = context.file;
      if (context.isBundle(file)) {
        folderOrFile = context.folder;
      }
      if (file.equals(folderOrFile)) {
        return context.pos;
      }
    }
    return -1;
  }

  public File selectFileToOpen() {
    return fileManager.selectFileToOpen();
  }

  public File selectFileForSave(PaneContext context) {
    return fileManager.selectFileForSave(context);
  }

  boolean checkDirtyPanes() {
    return fileManager.checkDirtyPanes();
  }

  // PaneContext extracted to PaneContext.java

  EditorPane getCurrentCodePane() {
    return getActiveContext().getPane();
  }

  EditorPane getPaneAtIndex(int index) {
    return getContextAt(index).getPane();
  }

  public void exportAsZip() {
    fileManager.exportAsZip();
  }

  public void reparseOnRenameImage(String oldName, String newName, boolean fileOverWritten) {
    fileManager.reparseOnRenameImage(oldName, newName, fileOverWritten);
  }

  public String getImageNameFromLine() {
    return fileManager.getImageNameFromLine();
  }

  String getLineTextAtCaret() {
    return fileManager.getLineTextAtCaret();
  }
//</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="04 save / restore session">
  private boolean saveSession(int saveAction) {
    return fileManager.saveSession(saveAction);
  }

  private List<File> restoreSession() {
    return fileManager.restoreSession();
  }
//</editor-fold>

  //<editor-fold desc="07 menu helpers">
  public void showAbout() {
    new SXDialog("sxideabout", SikulixIDE.getWindowTop(), SXDialog.POSITION.TOP).run();
  }

  public boolean quit() {
    return terminate();
  }

  public void showPreferencesWindow() {
    var pwin = new PreferencesWin();
    pwin.setAlwaysOnTop(true);
    pwin.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    pwin.setVisible(true);
  }

  void openSpecial() {
    log("Open Special requested");
//    Map<String, String> specialFiles = new Hashtable<>();
//    specialFiles.put("1 SikuliX Global Options", Commons.getOptions().getOptionsFile());
//    File extensionsFile = ExtensionManager.getExtensionsFile();
//    specialFiles.put("2 SikuliX Extensions Options", extensionsFile.getAbsolutePath());
//    File sitesTxt = ExtensionManager.getSitesTxt();
//    specialFiles.put("3 SikuliX Additional Sites", sitesTxt.getAbsolutePath());
//    String[] defaults = new String[specialFiles.size()];
//    defaults[0] = Options.getOptionsFileDefault();
//    defaults[1] = ExtensionManager.getExtensionsFileDefault();
//    defaults[2] = ExtensionManager.getSitesTxtDefault();
//    String msg = "";
//    int num = 1;
//    String[] files = new String[specialFiles.size()];
//    for (String specialFile : specialFiles.keySet()) {
//      files[num - 1] = specialFiles.get(specialFile).trim();
//      msg += specialFile + "\n";
//      num++;
//    }
//    msg += "\n" + "Enter a number to select a file";
//    String answer = SX.input(msg, "Edit a special SikuliX file", false, 10);
//    if (null != answer && !answer.isEmpty()) {
//      try {
//        num = Integer.parseInt(answer.substring(0, 1));
//        if (num > 0 && num <= specialFiles.size()) {
//          String file = files[num - 1];
//          if (!new File(file).exists()) {
//            FileManager.writeStringToFile(defaults[num - 1], file);
//          }
//          log( "Open Special: should load: %s", file);
//          newTabWithContent(file);
//        }
//      } catch (NumberFormatException e) {
//      }
//    }
  }
//</editor-fold>

  //<editor-fold desc="10 Init Menus">
  private JMenuBar _menuBar = new JMenuBar();
  private JMenu _fileMenu = null; //new JMenu(_I("menuFile"));
  private JMenu _editMenu = null; //new JMenu(_I("menuEdit"));
  private UndoAction _undoAction = null; //new UndoAction();
  private RedoAction _redoAction = null; //new RedoAction();
  private JMenu _runMenu = null; //new JMenu(_I("menuRun"));
  private JMenu _viewMenu = null; //new JMenu(_I("menuView"));
  private JMenu _toolMenu = null; //new JMenu(_I("menuTool"));
  private JMenu _helpMenu = null; //new JMenu(_I("menuHelp"));

  void initMenuBars(JFrame frame) {
    try {
      initFileMenu();
      initEditMenu();
      initRunMenu();
      initViewMenu();
      initToolMenu();
      initHelpMenu();
    } catch (NoSuchMethodException e) {
      log("Problem when initializing menues\nError: %s", e.getMessage());
    }

    _menuBar.add(_fileMenu);
    _menuBar.add(_editMenu);
    _menuBar.add(_runMenu);
    _menuBar.add(_viewMenu);
    _menuBar.add(_toolMenu);
    _menuBar.add(_helpMenu);
    frame.setJMenuBar(_menuBar);
  }

  JMenuItem createMenuItem(JMenuItem item, KeyStroke shortcut, ActionListener listener) {
    if (shortcut != null) {
      item.setAccelerator(shortcut);
    }
    item.addActionListener(listener);
    return item;
  }

  JMenuItem createMenuItem(String name, KeyStroke shortcut, ActionListener listener) {
    var item = new JMenuItem(name);
    return createMenuItem(item, shortcut, listener);
  }

  class MenuAction implements ActionListener {

    Method actMethod = null;
    String action;

    MenuAction() {
    }

    MenuAction(String item) throws NoSuchMethodException {
      Class[] paramsWithEvent = new Class[1];
      try {
        paramsWithEvent[0] = Class.forName("java.awt.event.ActionEvent");
        actMethod = this.getClass().getMethod(item, paramsWithEvent);
        action = item;
      } catch (ClassNotFoundException cnfe) {
        log("Can't find menu action: " + cnfe);
      }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if (actMethod != null) {
        try {
          log("MenuAction." + action);
          Object[] params = new Object[1];
          params[0] = e;
          actMethod.invoke(this, params);
        } catch (Exception ex) {
          log("Problem when trying to invoke menu action %s\nError: %s",
                  action, ex.getMessage());
        }
      }
    }
  }

  private static JMenu recentMenu = null;
  private static Map<String, String> recentProjects = new HashMap<>();
  private static java.util.List<String> recentProjectsMenu = new ArrayList<>();
  private static int recentMax = 10;
  private static int recentMaxMax = recentMax + 10;
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="11 Init FileMenu">
  JMenu getFileMenu() {
    return _fileMenu;
  }

  //<editor-fold desc="menu">
  private void initFileMenu() throws NoSuchMethodException {
    _fileMenu = new JMenu(_I("menuFile"));
    JMenuItem jmi;
    int scMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    _fileMenu.setMnemonic(java.awt.event.KeyEvent.VK_F);

    if (IDEDesktopSupport.showAbout) {
      _fileMenu.add(createMenuItem("About SikuliX",
              KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A, scMask),
              new FileAction(FileAction.ABOUT)));
      _fileMenu.addSeparator();
    }

    _fileMenu.add(createMenuItem(_I("menuFileNew"),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, scMask),
            new FileAction(FileAction.NEW)));

    jmi = _fileMenu.add(createMenuItem(_I("menuFileOpen"),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, scMask),
            new FileAction(FileAction.OPEN)));
    jmi.setName("OPEN");

    recentMenu = new JMenu(_I("menuRecent"));

    if (Settings.experimental) {
      _fileMenu.add(recentMenu);
    }

    jmi = _fileMenu.add(createMenuItem(_I("menuFileSave"),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, scMask),
            new FileAction(FileAction.SAVE)));
    jmi.setName("SAVE");

    jmi = _fileMenu.add(createMenuItem(_I("menuFileSaveAs"),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S,
                    InputEvent.SHIFT_DOWN_MASK | scMask),
            new FileAction(FileAction.SAVE_AS)));
    jmi.setName("SAVE_AS");

    _fileMenu.add(createMenuItem(_I("menuFileExport"),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_E,
                    InputEvent.SHIFT_DOWN_MASK | scMask),
            new FileAction(FileAction.EXPORT)));

    _fileMenu.add(createMenuItem("Export as jar",
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_J, scMask),
            new FileAction(FileAction.ASJAR)));

    _fileMenu.add(createMenuItem("Export as runnable jar",
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_J,
                    InputEvent.SHIFT_DOWN_MASK | scMask),
            new FileAction(FileAction.ASRUNJAR)));

    jmi = _fileMenu.add(createMenuItem(_I("menuFileCloseTab"),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W, scMask),
            new FileAction(FileAction.CLOSE_TAB)));
    jmi.setName("CLOSE_TAB");

    if (IDEDesktopSupport.showPrefs) {
      _fileMenu.addSeparator();
      _fileMenu.add(createMenuItem(_I("menuFilePreferences"),
              KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, scMask),
              new FileAction(FileAction.PREFERENCES)));
    }

    _fileMenu.addSeparator();
    _fileMenu.add(createMenuItem("Open Special Files",
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, InputEvent.ALT_DOWN_MASK | scMask),
            new FileAction(FileAction.OPEN_SPECIAL)));

//TODO restart IDE
/*
    _fileMenu.add(createMenuItem("Restart IDE",
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R,
                    InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK),
            new FileAction(FileAction.RESTART)));
*/
    if (IDEDesktopSupport.showQuit) {
      _fileMenu.addSeparator();
      _fileMenu.add(createMenuItem(_I("menuFileQuit"),
              null, new FileAction(FileAction.QUIT)));
    }
  }

  FileAction getFileAction(int tabIndex) {
    return new FileAction(tabIndex);
  }
//</editor-fold>

  class FileAction extends MenuAction {

    static final String ABOUT = "doAbout";
    static final String NEW = "doNew";
    static final String OPEN = "doOpen";
    static final String RECENT = "doRecent";
    static final String SAVE = "doSave";
    static final String SAVE_AS = "doSaveAs";
    static final String SAVE_ALL = "doSaveAll";
    static final String EXPORT = "doExport";
    static final String ASJAR = "doAsJar";
    static final String ASRUNJAR = "doAsRunJar";
    static final String CLOSE_TAB = "doCloseTab";
    static final String PREFERENCES = "doPreferences";
    static final String QUIT = "doQuit";
    static final String ENTRY = "doRecent";
    static final String OPEN_SPECIAL = "doOpenSpecial";

    FileAction(String item) throws NoSuchMethodException {
      super(item);
    }

    FileAction(int tabIndex) {
      super();
      targetTab = tabIndex;
    }

    private int targetTab = -1;

    public void doNew(ActionEvent ae) {
      createEmptyScriptContext();
    }

    public void doOpen(ActionEvent ae) {
      final File file = selectFileToOpen();
      createFileContext(file);
    }

    public void doRecent(ActionEvent ae) { //TODO
      log(ae.getActionCommand());
    }

    class OpenRecent extends MenuAction {

      OpenRecent() {
        super();
      }

      void openRecent(ActionEvent ae) {
        log(ae.getActionCommand());
      }
    }

    public void doSave(ActionEvent ae) {
      getActiveContext().save();
    }

    public void doSaveAs(ActionEvent ae) {
      getActiveContext().saveAs();
    }

    public void doExport(ActionEvent ae) {
      exportAsZip();
    }

    public void doAsJar(ActionEvent ae) {
      EditorPane codePane = getCurrentCodePane();
      String orgName = codePane.getCurrentShortFilename();
      log("doAsJar requested: %s", orgName);
      if (codePane.isDirty()) {
        Sikulix.popError("Please save script before!", "Export as jar");
      } else {
        File fScript = codePane.saveAndGetCurrentFile();
        List<String> options = new ArrayList<>();
        options.add("plain");
        options.add(fScript.getParentFile().getAbsolutePath());
        String fpJar = FileManager.makeScriptjar(options);
        if (null != fpJar) {
          Sikulix.popup(fpJar, "Export as jar ...");
        } else {
          Sikulix.popError("did not work for: " + orgName, "Export as jar");
        }
      }
    }

    public void doAsRunJar(ActionEvent ae) {
      EditorPane codePane = getCurrentCodePane();
      String orgName = codePane.getCurrentShortFilename();
      log("doAsRunJar requested: %s", orgName);
      if (codePane.isDirty()) {
        Sikulix.popError("Please save script before!", "Export as runnable jar");
      } else {
        File fScript = codePane.saveAndGetCurrentFile();
        List<String> options = new ArrayList<>();
        options.add(fScript.getParentFile().getAbsolutePath());
        String fpJar = FileManager.makeScriptjar(options);
        if (null != fpJar) {
          Sikulix.popup(fpJar, "Export as runnable jar ...");
        } else {
          Sikulix.popError("did not work for: " + orgName, "Export as runnable jar");
        }
      }
    }

    public void doCloseTab(ActionEvent ae) {
      getActiveContext().close();
    }

    public void doAbout(ActionEvent ae) {
      new SXDialog("sxideabout", SikulixIDE.getWindowTop(), SXDialog.POSITION.TOP).run();
    }

    public void doPreferences(ActionEvent ae) {
      showPreferencesWindow();
    }

    public void doOpenSpecial(ActionEvent ae) {
      (new Thread() {
        @Override
        public void run() {
          openSpecial();
        }
      }).start();
    }

    public void doQuit(ActionEvent ae) {
      log("Quit requested");
      terminate();
      log("Quit: cancelled or did not work");
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="12 Init EditMenu">
  private String findText = "";

  private void initEditMenu() throws NoSuchMethodException {
    int scMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

    _editMenu = new JMenu(_I("menuEdit"));
    _editMenu.setMnemonic(java.awt.event.KeyEvent.VK_E);

    _undoAction = new UndoAction();
    JMenuItem undoItem = _editMenu.add(_undoAction);
    undoItem.setAccelerator(
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, scMask));

    _redoAction = new RedoAction();
    JMenuItem redoItem = _editMenu.add(_redoAction);
    redoItem.setAccelerator(
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, scMask | InputEvent.SHIFT_DOWN_MASK));

    _editMenu.addSeparator();
    _editMenu.add(createMenuItem(_I("menuEditCopy"),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, scMask),
            new EditAction(EditAction.COPY)));
    _editMenu.add(createMenuItem("Copy line",
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, scMask | InputEvent.SHIFT_DOWN_MASK),
            new EditAction(EditAction.COPY)));
    _editMenu.add(createMenuItem(_I("menuEditCut"),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, scMask),
            new EditAction(EditAction.CUT)));
    _editMenu.add(createMenuItem("Cut line",
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, scMask | InputEvent.SHIFT_DOWN_MASK),
            new EditAction(EditAction.CUT)));
    _editMenu.add(createMenuItem(_I("menuEditPaste"),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, scMask),
            new EditAction(EditAction.PASTE)));
    _editMenu.add(createMenuItem(_I("menuEditSelectAll"),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A, scMask),
            new EditAction(EditAction.SELECT_ALL)));

    _editMenu.addSeparator();
    var findMenu = new JMenu(_I("menuFind"));
    findMenu.setMnemonic(KeyEvent.VK_F);
    findMenu.add(createMenuItem(_I("menuFindFind"),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, scMask),
            new FindAction(FindAction.FIND)));
    findMenu.add(createMenuItem(_I("menuFindFindNext"),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_G, scMask),
            new FindAction(FindAction.FIND_NEXT)));
    findMenu.add(createMenuItem(_I("menuFindFindPrev"),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_G, scMask | InputEvent.SHIFT_MASK),
            new FindAction(FindAction.FIND_PREV)));
    _editMenu.add(findMenu);

    _editMenu.addSeparator();
    _editMenu.add(createMenuItem(_I("menuEditIndent"),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_TAB, 0),
            new EditAction(EditAction.INDENT)));
    _editMenu.add(createMenuItem(_I("menuEditUnIndent"),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_TAB, InputEvent.SHIFT_MASK),
            new EditAction(EditAction.UNINDENT)));
  }

  class EditAction extends MenuAction {

    static final String CUT = "doCut";
    static final String COPY = "doCopy";
    static final String PASTE = "doPaste";
    static final String SELECT_ALL = "doSelectAll";
    static final String INDENT = "doIndent";
    static final String UNINDENT = "doUnindent";

    EditAction() {
      super();
    }

    EditAction(String item) throws NoSuchMethodException {
      super(item);
    }

    //mac: defaults write -g ApplePressAndHoldEnabled -bool false

    private void performEditorAction(String action, ActionEvent ae) {
      EditorPane pane = getCurrentCodePane();
      pane.getActionMap().get(action).actionPerformed(ae);
    }

    private void selectLine() {
      EditorPane pane = getCurrentCodePane();
      Element lineAtCaret = pane.getLineAtCaret(-1);
      pane.select(lineAtCaret.getStartOffset(), lineAtCaret.getEndOffset());
    }

    public void doCut(ActionEvent ae) {
      if (getCurrentCodePane().getSelectedText() == null) {
        selectLine();
      }
      performEditorAction(DefaultEditorKit.cutAction, ae);
    }

    public void doCopy(ActionEvent ae) {
      if (getCurrentCodePane().getSelectedText() == null) {
        selectLine();
      }
      performEditorAction(DefaultEditorKit.copyAction, ae);
    }

    public void doPaste(ActionEvent ae) {
      performEditorAction(DefaultEditorKit.pasteAction, ae);
    }

    public void doSelectAll(ActionEvent ae) {
      performEditorAction(DefaultEditorKit.selectAllAction, ae);
    }

    public void doIndent(ActionEvent ae) {
      EditorPane pane = getCurrentCodePane();
      (new SikuliEditorKit.InsertTabAction()).actionPerformed(pane);
    }

    public void doUnindent(ActionEvent ae) {
      EditorPane pane = getCurrentCodePane();
      (new SikuliEditorKit.DeindentAction()).actionPerformed(pane);
    }
  }

  class FindAction extends MenuAction {

    static final String FIND = "doFind";
    static final String FIND_NEXT = "doFindNext";
    static final String FIND_PREV = "doFindPrev";

    FindAction() {
      super();
    }

    FindAction(String item) throws NoSuchMethodException {
      super(item);
    }

    public void doFind(ActionEvent ae) {
//      _searchField.selectAll();
//      _searchField.requestFocus();
      findText = Sikulix.input(
              "Enter text to be searched (case sensitive)\n" +
                      "Start with ! to search case insensitive\n",
              findText, "SikuliX IDE -- Find");
      if (null == findText) {
        return;
      }
      if (findText.isEmpty()) {
        Debug.error("Find(%s): search text is empty", findText);
        return;
      }
      if (!findStr(findText)) {
        Debug.error("Find(%s): not found", findText);
      }
    }

    public void doFindNext(ActionEvent ae) {
      if (!findText.isEmpty()) {
        findNext(findText);
      }
    }

    public void doFindPrev(ActionEvent ae) {
      if (!findText.isEmpty()) {
        findPrev(findText);
      }
    }

    private boolean _find(String str, int begin, boolean forward) {
      if (str == "!") {
        return false;
      }
      EditorPane codePane = getCurrentCodePane();
      int pos = codePane.search(str, begin, forward);
      log("find \"" + str + "\" at " + begin + ", found: " + pos);
      if (pos < 0) {
        return false;
      }
      return true;
    }

    boolean findStr(String str) {
      if (getCurrentCodePane() != null) {
        return _find(str, getCurrentCodePane().getCaretPosition(), true);
      }
      return false;
    }

    boolean findPrev(String str) {
      if (getCurrentCodePane() != null) {
        return _find(str, getCurrentCodePane().getCaretPosition(), false);
      }
      return false;
    }

    boolean findNext(String str) {
      if (getCurrentCodePane() != null) {
        return _find(str,
                getCurrentCodePane().getCaretPosition() + str.length(),
                true);
      }
      return false;
    }

//    public void setFailed(boolean failed) {
//      Debug.log( "search failed: " + failed);
//      _searchField.setBackground(Color.white);
//      if (failed) {
//        _searchField.setForeground(COLOR_SEARCH_FAILED);
//      } else {
//        _searchField.setForeground(COLOR_SEARCH_NORMAL);
//      }
//    }
  }

  class UndoAction extends AbstractAction {

    UndoAction() {
      super(_I("menuEditUndo"));
      setEnabled(false);
    }

    void updateUndoState() {
      final PaneContext activePane = getActiveContext();
      EditorPane pane = activePane.getPane();
      if (pane != null) {
        final UndoManager manager = pane.getUndoRedo().getUndoManager();
        if (manager.canUndo()) {
          setEnabled(true);
        }
      } else {
        setEnabled(false);
      }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      final PaneContext activePane = getActiveContext();
      EditorPane pane = activePane.getPane();
      if (pane != null) {
        try {
          pane.getUndoRedo().getUndoManager().undo();
        } catch (CannotUndoException ex) {
        }
        updateUndoState();
        _redoAction.updateRedoState();
      }
    }
  }

  class RedoAction extends AbstractAction {

    RedoAction() {
      super(_I("menuEditRedo"));
      setEnabled(false);
    }

    protected void updateRedoState() {
      final PaneContext activePane = getActiveContext();
      EditorPane pane = activePane.getPane();
      if (pane != null) {
        if (pane.getUndoRedo().getUndoManager().canRedo()) {
          setEnabled(true);
        }
      } else {
        setEnabled(false);
      }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      final PaneContext activePane = getActiveContext();
      EditorPane pane = activePane.getPane();
      if (pane != null) {
        try {
          pane.getUndoRedo().getUndoManager().redo();
        } catch (CannotRedoException ex) {
        }
        updateRedoState();
        _undoAction.updateUndoState();
      }
    }
  }

  void updateUndoRedoStates() {
    _undoAction.updateUndoState();
    _redoAction.updateRedoState();
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="13 Init Run Menu">
  JMenu getRunMenu() {
    return _runMenu;
  }

  private void initRunMenu() throws NoSuchMethodException {
    int scMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
    _runMenu = new JMenu(_I("menuRun"));
    _runMenu.setMnemonic(java.awt.event.KeyEvent.VK_R);
    _runMenu.add(createMenuItem(_I("menuRunRun"),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, scMask),
            new RunAction(RunAction.RUN)));
    _runMenu.add(createMenuItem(_I("menuRunRunAndShowActions"),
            KeyStroke.getKeyStroke(KeyEvent.VK_R,
                    InputEvent.ALT_DOWN_MASK | scMask),
            new RunAction(RunAction.RUN_SHOW_ACTIONS)));
    _runMenu.add(createMenuItem("Run selection",
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R,
                    InputEvent.SHIFT_DOWN_MASK | scMask),
            new RunAction(RunAction.RUN_SELECTION)));
  }

  class RunAction extends MenuAction {

    static final String RUN = "runNormal";
    static final String RUN_SHOW_ACTIONS = "runShowActions";
    static final String RUN_SELECTION = "runSelection";

    RunAction() {
      super();
    }

    RunAction(String item) throws NoSuchMethodException {
      super(item);
    }

    public void runNormal(ActionEvent ae) {
      runManager.getBtnRun().runCurrentScript();
    }

    public void runShowActions(ActionEvent ae) {
      runManager.getBtnRunSlow().runCurrentScript();
    }

    public void runSelection(ActionEvent ae) {
      getCurrentCodePane().runSelection();
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="14 Init View Menu">
  private JCheckBoxMenuItem chkShowThumbs;

  private void initViewMenu() throws NoSuchMethodException {
    int scMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
    _viewMenu = new JMenu(_I("menuView"));
    _viewMenu.setMnemonic(java.awt.event.KeyEvent.VK_V);

    boolean prefMorePlainText = PreferencesUser.get().getPrefMorePlainText();

    chkShowThumbs = new JCheckBoxMenuItem(_I("menuViewShowThumbs"), !prefMorePlainText);
    _viewMenu.add(createMenuItem(chkShowThumbs,
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T, scMask),
            new ViewAction(ViewAction.SHOW_THUMBS)));

//TODO Message Area clear
//TODO Message Area LineBreak
  }

  class ViewAction extends MenuAction {

    static final String SHOW_THUMBS = "toggleShowThumbs";

    ViewAction() {
      super();
    }

    ViewAction(String item) throws NoSuchMethodException {
      super(item);
    }

    private long lastWhen = -1;

    public void toggleShowThumbs(ActionEvent ae) {
      if (Commons.runningMac()) {
        if (lastWhen < 0) {
          lastWhen = new Date().getTime();
        } else {
          long delay = new Date().getTime() - lastWhen;
          lastWhen = -1;
          if (delay < 500) {
            JCheckBoxMenuItem source = (JCheckBoxMenuItem) ae.getSource();
            source.setState(!source.getState());
            return;
          }
        }
      }
      boolean showThumbsState = chkShowThumbs.getState();
      getActiveContext().setShowThumbs(showThumbsState);
      getActiveContext().reparse();
      return;
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="15 Init ToolMenu">
  private void initToolMenu() throws NoSuchMethodException {
    int scMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
    _toolMenu = new JMenu(_I("menuTool"));
    _toolMenu.setMnemonic(java.awt.event.KeyEvent.VK_T);

    _toolMenu.add(createMenuItem(_I("menuToolExtensions"),
            null,
            new ToolAction(ToolAction.EXTENSIONS)));

    _toolMenu.add(createMenuItem("Pack Jar with Jython",
            null,
            new ToolAction(ToolAction.JARWITHJYTHON)));

    _toolMenu.add(createMenuItem("Pack Jar with Jython",
        null,
        new ToolAction(ToolAction.JARWITHJYTHON)));

    _toolMenu.add(createMenuItem(_I("menuToolAndroid"),
            null,
            new ToolAction(ToolAction.ANDROID)));
  }

  class ToolAction extends MenuAction {

    static final String EXTENSIONS = "extensions";
    static final String JARWITHJYTHON = "jarWithJython";
    static final String ANDROID = "android";

    ToolAction() {
      super();
    }

    ToolAction(String item) throws NoSuchMethodException {
      super(item);
    }

    public void extensions(ActionEvent ae) {
      showExtensions();
    }

    public void jarWithJython(ActionEvent ae) {
      if (SX.popAsk("*** You should know what you are doing! ***\n\n" +
              "This may take a while. Wait for success popup!" +
              "\nClick Yes to start.", "Creating jar file")) {
        (new Thread() {
          @Override
          public void run() {
            makeJarWithJython();
          }
        }).start();
      }
    }

    public void android(ActionEvent ae) {
      androidSupport();
    }
  }

  private void showExtensions() {
    ExtensionManager.show();
  }

  private static IScreen defaultScreen = null;

  static IScreen getDefaultScreen() {
    return defaultScreen;
  }

  private void makeJarWithJython() {
    String ideJarName = getRunningJar(SikulixIDE.class);
    if (ideJarName.isEmpty()) {
      log("makeJarWithJython: JAR containing IDE not available");
      return;
    }
    if (ideJarName.endsWith("/classes/")) {
      String version = Commons.getSXVersionShort();
      String name = "sikulixide-" + version + "-complete.jar";
      ideJarName = new File(new File(ideJarName).getParentFile(), name).getAbsolutePath();
    }
    String jythonJarName = "";
    try {
      jythonJarName = getRunningJar(Class.forName("org.python.util.jython"));
    } catch (ClassNotFoundException e) {
      log("makeJarWithJython: Jar containing Jython not available");
      return;
    }
    String targetJar = new File(Commons.getAppDataStore(), "sikulixjython.jar").getAbsolutePath();
    String[] jars = new String[]{ideJarName, jythonJarName};
    SikulixIDE.getStatusbar().setMessage(String.format("Creating SikuliX with Jython: %s", targetJar));
    if (FileManager.buildJar(targetJar, jars, null, null, null)) {
      String msg = String.format("Created SikuliX with Jython: %s", targetJar);
      log(msg);
      SX.popup(msg.replace(": ", "\n"));
    } else {
      String msg = String.format("Create SikuliX with Jython not possible: %s", targetJar);
      log(msg);
      SX.popError(msg.replace(": ", "\n"));
    }
    SikulixIDE.getStatusbar().resetMessage();
  }

  private String getRunningJar(Class clazz) {
    String jarName = "";
    CodeSource codeSrc = clazz.getProtectionDomain().getCodeSource();
    if (codeSrc != null && codeSrc.getLocation() != null) {
      try {
        jarName = codeSrc.getLocation().getPath();
        jarName = URLDecoder.decode(jarName, "utf8");
      } catch (UnsupportedEncodingException e) {
        log("URLDecoder: not possible: " + jarName);
        jarName = "";
      }
    }
    return jarName;
  }

  private void androidSupport() {
//    final ADBScreen aScr = new ADBScreen();
//    String title = "Android Support - !!EXPERIMENTAL!!";
//    if (aScr.isValid()) {
//      String warn = "Device found: " + aScr.getDeviceDescription() + "\n\n" +
//          "click Check: a short test is run with the device\n" +
//          "click Default...: set device as default screen for capture\n" +
//          "click Cancel: capture is reset to local screen\n" +
//          "\nBE PREPARED: Feature is experimental - no guarantee ;-)";
//      String[] options = new String[3];
//      options[WARNING_DO_NOTHING] = "Check";
//      options[WARNING_ACCEPTED] = "Default Android";
//      options[WARNING_CANCEL] = "Cancel";
//      int ret = JOptionPane.showOptionDialog(this, warn, title, 0, JOptionPane.WARNING_MESSAGE, null, options, options[2]);
//      if (ret == WARNING_CANCEL || ret == JOptionPane.CLOSED_OPTION) {
//        defaultScreen = null;
//        return;
//      }
//      if (ret == WARNING_DO_NOTHING) {
//        SikulixIDE.hideIDE();
//        Thread test = new Thread() {
//          @Override
//          public void run() {
//            androidSupportTest(aScr);
//          }
//        };
//        test.start();
//      } else if (ret == WARNING_ACCEPTED) {
//        defaultScreen = aScr;
//        return;
//      }
//    } else if (!ADBClient.isAdbAvailable) {
//      Sikulix.popError("Package adb seems not to be available.\nIt must be installed for Android support.", title);
//    } else {
//      Sikulix.popError("No android device attached", title);
//    }
  }

  private void androidSupportTest(IScreen aScr) {
//    ADBTest.ideTest(aScr);
//    ADBScreen.stop();
    SikulixIDE.doShow();
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="16 Init Help Menu">
  private static Boolean newBuildAvailable = null;
  private static String newBuildStamp = "";

  private void initHelpMenu() throws NoSuchMethodException {
    int scMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
    _helpMenu = new JMenu(_I("menuHelp"));
    _helpMenu.setMnemonic(java.awt.event.KeyEvent.VK_H);

    _helpMenu.add(createMenuItem(_I("menuHelpQuickStart"),
            null, new HelpAction(HelpAction.QUICK_START)));
    _helpMenu.addSeparator();

    _helpMenu.add(createMenuItem(_I("menuHelpGuide"),
            null, new HelpAction(HelpAction.OPEN_DOC)));
//    _helpMenu.add(createMenuItem(_I("menuHelpDocumentations"),
//            null, new HelpAction(HelpAction.OPEN_GUIDE)));
    _helpMenu.add(createMenuItem(_I("menuHelpFAQ"),
            null, new HelpAction(HelpAction.OPEN_FAQ)));
    _helpMenu.add(createMenuItem(_I("menuHelpAsk"),
            null, new HelpAction(HelpAction.OPEN_ASK)));
    _helpMenu.add(createMenuItem(_I("menuHelpBugReport"),
            null, new HelpAction(HelpAction.OPEN_BUG_REPORT)));

//    _helpMenu.add(createMenuItem(_I("menuHelpTranslation"),
//            null, new HelpAction(HelpAction.OPEN_TRANSLATION)));
    _helpMenu.addSeparator();
    _helpMenu.add(createMenuItem(_I("menuHelpHomepage"),
            null, new HelpAction(HelpAction.OPEN_HOMEPAGE)));

//    _helpMenu.addSeparator();
//    _helpMenu.add(createMenuItem("SikuliX1 Downloads",
//        null, new HelpAction(HelpAction.OPEN_DOWNLOADS)));
//    _helpMenu.add(createMenuItem(_I("menuHelpCheckUpdate"),
//        null, new HelpAction(HelpAction.CHECK_UPDATE)));
  }

  private void lookUpdate() {
    newBuildAvailable = null;
    String token = "This version was built at ";
    String httpDownload = "#https://raiman.github.io/SikuliX1/downloads.html";
    String pageDownload = FileManager.downloadURLtoString(httpDownload);
    if (!pageDownload.isEmpty()) {
      newBuildAvailable = false;
    }
    int locStamp = pageDownload.indexOf(token);
    if (locStamp > 0) {
      locStamp += token.length();
      String latestBuildFull = pageDownload.substring(locStamp, locStamp + 16);
      String latestBuild = latestBuildFull.replaceAll("-", "").replace("_", "").replace(":", "");
      String actualBuild = Commons.getSxBuildStamp();
      try {
        long lb = Long.parseLong(latestBuild);
        long ab = Long.parseLong(actualBuild);
        if (lb > ab) {
          newBuildAvailable = true;
          newBuildStamp = latestBuildFull;
        }
        log("latest build: %s this build: %s (newer: %s)", latestBuild, actualBuild, newBuildAvailable);
      } catch (NumberFormatException e) {
        log("check for new build: stamps not readable");
      }
    }
  }

  class HelpAction extends MenuAction {

    static final String CHECK_UPDATE = "doCheckUpdate";
    static final String QUICK_START = "openQuickStart";
    static final String OPEN_DOC = "openDoc";
    static final String OPEN_GUIDE = "openTutor";
    static final String OPEN_FAQ = "openFAQ";
    static final String OPEN_ASK = "openAsk";
    static final String OPEN_BUG_REPORT = "openBugReport";
    static final String OPEN_TRANSLATION = "openTranslation";
    static final String OPEN_HOMEPAGE = "openHomepage";
    static final String OPEN_DOWNLOADS = "openDownloads";

    HelpAction() {
      super();
    }

    HelpAction(String item) throws NoSuchMethodException {
      super(item);
    }

    public void openQuickStart(ActionEvent ae) {
      FileManager.openURL("http://sikulix.com/quickstart/");
    }

    public void openDoc(ActionEvent ae) {
      FileManager.openURL("http://sikulix-2014.readthedocs.org/en/latest/index.html");
    }

    public void openTutor(ActionEvent ae) {
      FileManager.openURL("http://www.sikuli.org/videos.html");
    }

    public void openFAQ(ActionEvent ae) {
      FileManager.openURL("https://answers.launchpad.net/sikuli/+faqs");
    }

    public void openAsk(ActionEvent ae) {
      String title = "SikuliX - Ask a question";
      String msg = """
              If you want to ask a question about SikuliX
              %s

              please do the following:
              - after having clicked yes
                 the page on Launchpad should open in your browser.
              - You should first check using Launchpad's search funktion,
                 wether similar questions have already been asked.
              - If you decide to ask a new question,
                 try to enter a short but speaking title
              - In a new questions's text field first paste using ctrl/cmd-v
                 which should enter the SikuliX version/system/java info
                 that was internally stored in the clipboard before

              If you do not want to ask a question now: click No""";
      askBugOrAnswer(msg, title, "https://answers.launchpad.net/sikuli");
    }

    public void openBugReport(ActionEvent ae) {
      String title = "SikuliX - Report a bug";
      String msg = """
              If you want to report a bug for SikuliX
              %s

              please do the following:
              - after having clicked yes
                 the page on Launchpad should open in your browser
              - fill in a short but speaking bug title and create the bug
              - in the bug's text field first paste using ctrl/cmd-v
                 which should enter the SikuliX version/system/java info
                 that was internally stored in the clipboard before

              If you do not want to report a bug now: click No""";
      askBugOrAnswer(msg, title, "https://bugs.launchpad.net/sikuli/+filebug");
    }

    private void askBugOrAnswer(String msg, String title, String url) {
      String si = Commons.getSystemInfo();
      System.out.println(si);
      msg = String.format(msg, si);
      if (Sikulix.popAsk(msg, title)) {
        Clipboard clb = Toolkit.getDefaultToolkit().getSystemClipboard();
        StringSelection sic = new StringSelection(si.toString());
        clb.setContents(sic, sic);
        FileManager.openURL(url);
      }
    }

    public void openTranslation(ActionEvent ae) {
      FileManager.openURL("https://translations.launchpad.net/sikuli/sikuli-x/+translations");
    }

    public void openHomepage(ActionEvent ae) {
      FileManager.openURL("http://sikulix.com");
    }

    public void openDownloads(ActionEvent ae) {
      FileManager.openURL("https://raiman.github.io/SikuliX1/downloads.html");
    }

    public void doCheckUpdate(ActionEvent ae) {
      if (!checkUpdate(false)) {
        lookUpdate();
        int msgType = newBuildAvailable != null ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE;
        String updMsg = newBuildAvailable != null ? (newBuildAvailable ?
                _I("msgUpdate") + ": " + newBuildStamp :
                _I("msgNoUpdate")) : _I("msgUpdateError");
        JOptionPane.showMessageDialog(null, updMsg,
                Commons.getSXVersionIDE(), msgType);
      }
    }

    boolean checkUpdate(boolean isAutoCheck) {
      String ver = "";
      String details;
      var au = new AutoUpdater();
      PreferencesUser pref = PreferencesUser.get();
      log("being asked to check update");
      int whatUpdate = au.checkUpdate();
      if (whatUpdate >= AutoUpdater.SOMEBETA) {
//TODO add Prefs wantBeta check
        whatUpdate -= AutoUpdater.SOMEBETA;
      }
      if (whatUpdate > 0) {
        if (whatUpdate == AutoUpdater.BETA) {
          ver = au.getBeta();
          details = au.getBetaDetails();
        } else {
          ver = au.getVersion();
          details = au.getDetails();
        }
        if (isAutoCheck && pref.getLastSeenUpdate().equals(ver)) {
          return false;
        }
        au.showUpdateFrame(_I("dlgUpdateAvailable", ver), details, whatUpdate);
        PreferencesUser.get().setLastSeenUpdate(ver);
        return true;
      }
      return false;
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="20 Init ToolBar Buttons — delegated to IDEWindowManager">
  ButtonRecord getBtnRecord() {
    return windowManager.getBtnRecord();
  }
//</editor-fold>

  //<editor-fold desc="21 Init Run Buttons — delegated to IDERunManager">
  void addErrorMark(int line) {
    runManager.addErrorMark(line);
  }

  void resetErrorMark() {
    runManager.resetErrorMark();
  }
  //</editor-fold>

  //<editor-fold desc="30 MsgArea — delegated to IDEWindowManager">
  void clearMessageArea() {
    windowManager.clearMessageArea();
  }

  void collapseMessageArea() {
    windowManager.collapseMessageArea();
  }

  void uncollapseMessageArea() {
    windowManager.uncollapseMessageArea();
  }

  public EditorConsolePane getConsole() {
    return windowManager.getConsole();
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="25 Init ShortCuts HotKeys — delegated">
  void removeCaptureHotkey() {
    windowManager.removeCaptureHotkey();
  }

  void installCaptureHotkey() {
    windowManager.installCaptureHotkey();
  }

  void onStopRunning() {
    runManager.onStopRunning();
  }
  //</editor-fold>
}
