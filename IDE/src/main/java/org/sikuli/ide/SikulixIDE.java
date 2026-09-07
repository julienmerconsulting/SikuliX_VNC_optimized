/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.ide;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.sikuli.basics.*;
import org.sikuli.idesupport.IDEDesktopSupport;
import org.sikuli.idesupport.IDETaskbarSupport;
import org.sikuli.support.FileManager;
import org.sikuli.support.ide.ExtensionManager;
import org.sikuli.support.ide.IButton;
import org.sikuli.support.ide.IDESupport;
import org.sikuli.support.ide.IIDESupport;
import org.sikuli.support.runnerSupport.JythonSupport;
import org.sikuli.support.runner.Runner;
import org.sikuli.support.ide.SikuliIDEI18N;
import org.sikuli.support.ide.syntaxhighlight.ResolutionException;
import org.sikuli.support.ide.syntaxhighlight.grammar.Lexer;
import org.sikuli.support.ide.syntaxhighlight.grammar.Token;
import org.sikuli.support.ide.syntaxhighlight.grammar.TokenType;
import org.sikuli.script.Image;
import org.sikuli.script.*;
import org.sikuli.support.runner.IRunner;
import org.sikuli.support.runner.InvalidRunner;
import org.sikuli.support.runner.JythonRunner;
import org.sikuli.support.runner.TextRunner;
import org.sikuli.support.Commons;
import org.sikuli.support.devices.IScreen;
import org.sikuli.support.recorder.Recorder;
import org.sikuli.support.devices.ScreenDevice;
import org.sikuli.support.recorder.generators.ICodeGenerator;
import org.sikuli.support.gui.SXDialog;
import org.sikuli.support.recorder.actions.IRecordedAction;
import org.sikuli.ide.ui.OculixSidebar;
import org.sikuli.ide.ui.ScriptExplorer;
import org.sikuli.ide.ui.SidebarSubmenu;
import org.sikuli.ide.ui.WelcomeTab;
import org.sikuli.ide.ui.WorkspaceDialog;
import org.sikuli.util.EventObserver;
import org.sikuli.util.EventSubject;
import org.sikuli.util.OverlayCapturePrompt;
import org.sikuli.util.SikulixFileChooser;

import javax.swing.*;
import javax.swing.text.BadLocationException;
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
import java.nio.charset.Charset;
import java.security.CodeSource;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
    Commons.pause(waitTime);
  }

  static void showAgain() {
    ideWindow.setVisible(true);
    PaneContext ctx = sikulixIDE.getActiveContext();
    if (ctx != null) ctx.focus();
    sikulixIDE.refreshWorkspace(); // refresh card image counts after capture
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
  private static AtomicBoolean ideIsReady = new AtomicBoolean(false);

  protected static void start() {

    ideWindowRect = getWindowRect();

    IDEDesktopSupport.init(sikulixIDE);
    IDETaskbarSupport.setTaskbarIcon(getIconResource("/icons/gecko_cyclope_icon.png").getImage());
    sikulixIDE.setIconImage(getIconResource("/icons/gecko_cyclope_icon.png").getImage());
    IDESupport.initIDESupport();
    if (Sikulix.ideWithConsole) {
      get().messages = new EditorConsolePane();
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
      try {
        Commons.terminate(0, "");
      } catch (Exception ex) {
        log("Quit: RunTime.terminate failed — forcing exit");
        System.exit(1);
      }
    }
    log("Quit: cancelled or did not work");
    return false;
  }
  //</editor-fold>

  //<editor-fold desc="02 init IDE">
  private void startGUI() {
    setWindow();

    installCaptureHotkey();
    installStopHotkey();

    ideWindow.setSize(ideWindowRect.getSize());
    ideWindow.setLocation(ideWindowRect.getLocation());

    Debug.log("IDE: Adding components to window");
    initMenuBars(ideWindow);
    final Container ideContainer = ideWindow.getContentPane();
    ideContainer.setLayout(new BorderLayout());
    Debug.log("IDE: creating tabbed editor");
    initTabs();
    Debug.log("IDE: creating message area");
    if (messages != null) {
      initMessageArea();
    }
    Debug.log("IDE: creating combined work window");
    JPanel codePane = new JPanel(new BorderLayout(0, 0));
    codePane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    codePane.add(tabs, BorderLayout.CENTER);

    // Script file explorer (left of editor)
    Debug.log("IDE: creating file explorer");
    explorer = new ScriptExplorer();
    explorer.setOnCardSelected(e -> {
      int cardIndex = e.getID();
      int tabIndex = welcomeShowing ? cardIndex + 1 : cardIndex;
      if (tabIndex >= 0 && tabIndex < tabs.getTabCount()) {
        tabs.setSelectedIndex(tabIndex);
      }
    });
    explorer.setOnCardRenamed(e -> {
      int cardIndex = e.getID();
      String newName = e.getActionCommand();
      if (cardIndex >= 0 && cardIndex < contexts.size() && newName != null) {
        // Rename is a save-as with new name — for now just update the tab title
        PaneContext ctx = contexts.get(cardIndex);
        int tabIndex = welcomeShowing ? cardIndex + 1 : cardIndex;
        tabs.setTitleAt(tabIndex, newName);
        refreshWorkspace();
      }
    });

    // Explorer + Editor in a horizontal split.
    // The explorer (workspace pane) is hidden by default: when the IDE boots
    // there's no script open, so a half-empty Workspace column on the left
    // just steals 180px of editor real-estate and visually duplicates the
    // sidebar. The split divider sits at 0 → explorer collapsed. As soon as
    // a script is created or a workspace is loaded, refreshExplorerVisibility()
    // (called from refreshWorkspace) re-opens the divider to its working width.
    editorSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, explorer, codePane);
    editorSplit.setDividerLocation(0);
    editorSplit.setResizeWeight(0.0); // editor gets all extra space
    editorSplit.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, UIManager.getColor("Separator.foreground")));
    editorSplit.setOneTouchExpandable(true);

    Debug.log("IDE: Putting all together");
    JPanel editPane = new JPanel(new BorderLayout(0, 0));
    mainPane = null;
    if (messageArea != null) {
      // Console always at the bottom (Eclipse-style). The split divider gets
      // a visible 1px ink-500 line so the editor / log boundary reads clearly
      // in both themes — without that border, the surfaces blur into each
      // other (sidebar paper-100 ≈ workspace paper-100 ≈ editor white in light).
      mainPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorSplit, messageArea);
      mainPane.setResizeWeight(0.75);
      mainPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
      mainPane.setOneTouchExpandable(true);
      mainPane.setDividerSize(4);
      mainPane.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
        @Override
        public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
          return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
            @Override
            public void paint(Graphics g) {
              boolean dark = UIManager.getLookAndFeel().getName().toLowerCase(java.util.Locale.ROOT).contains("dark");
              g.setColor(dark ? new Color(0x2B, 0x3A, 0x72) : new Color(0xBC, 0xC7, 0xE2));
              g.fillRect(0, 0, getSize().width, getSize().height);
            }
          };
        }
      });
      editPane.add(mainPane, BorderLayout.CENTER);
    } else {
      editPane.add(editorSplit, BorderLayout.CENTER);
    }

    ideContainer.add(editPane, BorderLayout.CENTER);
    Debug.log("IDE: Putting all together - after main pane");

    // Phase 1a: init toolbar (actions are still needed) but don't add to layout
    JToolBar tb = initToolbar();
    // Phase 1b: JToolBar hidden, actions migrated to sidebar
    tb.setVisible(false);

    // Phase 1b: Create and configure sidebar
    Debug.log("IDE: Putting all together - init sidebar");
    sidebar = new OculixSidebar();
    initSidebarActions();
    initSidebarNavigation();
    sidebar.initFooter(Commons.getSXVersionShort(), e -> refreshAfterThemeChange());
    ideContainer.add(sidebar, BorderLayout.WEST);
    updateScriptDependentItems(); // grey out Run/Capture/Record until a script is open
    Debug.log("IDE: Putting all together - after sidebar");

    // Phase 1a: migrate accelerators from JMenuBar to rootPane
    migrateAcceleratorsToRootPane();

    // Phase 1b: hide JMenuBar (kept as safety net, removed in Phase 3)
    _menuBar.setVisible(false);

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

    // Restore-from-maximize override : when the user clicks the Restore button
    // (or double-clicks the title bar to leave maximised state), Windows
    // restores the window to whatever pre-maximize size it had — often glued
    // to a screen edge and awkward to resize. We instead snap to a comfy
    // 70% × 70% of the screen, centred, leaving 15% margin on every side so
    // the user can grab any border to resize. Only fires on the
    // MAXIMIZED_BOTH → NORMAL transition; minimize (NORMAL → ICONIFIED) and
    // restore-from-icon are left alone.
    ideWindow.addWindowStateListener(new java.awt.event.WindowStateListener() {
      @Override
      public void windowStateChanged(java.awt.event.WindowEvent e) {
        int oldState = e.getOldState();
        int newState = e.getNewState();
        boolean wasMaximised = (oldState & java.awt.Frame.MAXIMIZED_BOTH) != 0;
        boolean isNormal     = (newState & (java.awt.Frame.MAXIMIZED_BOTH | java.awt.Frame.ICONIFIED)) == 0;
        if (wasMaximised && isNormal) {
          java.awt.Rectangle screen = ideWindow.getGraphicsConfiguration().getBounds();
          int w = (int) (screen.width  * 0.70);
          int h = (int) (screen.height * 0.70);
          int x = screen.x + (screen.width  - w) / 2;
          int y = screen.y + (screen.height - h) / 2;
          SwingUtilities.invokeLater(() -> {
            ideWindow.setSize(w, h);
            ideWindow.setLocation(x, y);
          });
        }
      }
    });
    ToolTipManager.sharedInstance().setDismissDelay(30000);

    if (messages != null) {
      messages.initRedirect();
    }
    Debug.log("IDE: Putting all together - Restore last Session");
    try {
      List<File> restored = restoreSession();
      if (restored.isEmpty() || tabs.getTabCount() == 0) {
        showWelcomeTab();
      }
    } catch (Exception e) {
      log("Session restore failed: %s", e.getMessage());
      showWelcomeTab();
    }

    initShortcutKeys();
    ideIsReady.set(true);
    Commons.startLog(3, "IDE ready: on Java %d (%4.1f sec)", Commons.getJavaVersion(), Commons.getSinceStart());
  }

  public static void showAfterStart() {
    while (!ideIsReady.get()) {
      Commons.pause(100);
    }
    org.sikuli.ide.Sikulix.stopSplash();
    ideWindow.setVisible(true);
    if (sikulixIDE.mainPane != null) {
      sikulixIDE.mainPane.setDividerLocation(0.7); //TODO saved value — 0.7 keeps the Welcome footer visible at default IDE proportions
    }
    if (!sikulixIDE.contexts.isEmpty()) {
      sikulixIDE.getActiveContext().focus();
    }
    if (Sikulix.shouldExecuteOnStart) {
      // Mirrors the -r flow at Sikulix.start verbatim — same loadOpenCV +
      // resolveRelativeFiles + runScripts sequence — minus the
      // RunTime.terminate(...) at the end so the IDE stays open after the
      // auto-run completes. Wrapped in a thread so the EDT (running
      // showAfterStart) is not blocked by the synchronous Runner call.
      // Output goes to the message panel because messages.initRedirect()
      // ran in startGUI() before ideIsReady, so System.out is already
      // piped to the panel reader at this point.
      //
      // Diagnostic logs at every junction (Commons.startLog level 3, always
      // visible without -v) so a Windows tester pasting the message panel
      // tells us exactly where the path stops if -e silently fails. The
      // last visible "-e: ..." line in the panel is the breakpoint.
      new Thread(() -> {
        try {
          doHide();
          Commons.loadOpenCV();
          String[] scripts = new String[] {Sikulix.scriptToStart.getAbsolutePath()};
          Commons.startLog(3, "-e: resolved script: %s",
              scripts == null ? "null" : java.util.Arrays.toString(scripts));
          int exitCode = Runner.runScripts(scripts, Sikulix.getUserArgs(),
              new IRunner.Options());
          if (exitCode > 255) {
            exitCode = 254;
          }
          Commons.startLog(3, "-e: runScripts returned exitCode=%d", exitCode);
        } catch (Throwable t) {
          // Surface any exception that would otherwise die silently in the
          // background thread — the panel keeps the truth even when stdout
          // is hijacked by a Jython interpreter that captured a stale
          // reference. Concatenate the stack trace as a single string so
          // formatting holds across the pipe.
          java.io.StringWriter sw = new java.io.StringWriter();
          t.printStackTrace(new java.io.PrintWriter(sw));
          Commons.startLog(3, "-e: thread crashed: %s\n%s",
              t.getClass().getName() + ": " + t.getMessage(),
              sw.toString());
        }
        // No RunTime.terminate(): -e leaves the IDE running for the user.
        showAgain();
      }, "auto-run-e").start();
    }
    Debug.quietOff();
  }

  //TODO initShortcutKey
  private void initShortcutKeys() {
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

  // Phase 1a: Migrate all JMenuItem accelerators to rootPane InputMap/ActionMap
  // This ensures keyboard shortcuts survive the JMenuBar being hidden.
  private void migrateAcceleratorsToRootPane() {
    JRootPane root = ideWindow.getRootPane();
    InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
    ActionMap am = root.getActionMap();
    migrateMenuAccelerators(_fileMenu, im, am);
    migrateMenuAccelerators(_editMenu, im, am);
    migrateMenuAccelerators(_runMenu, im, am);
    migrateMenuAccelerators(_viewMenu, im, am);
    migrateMenuAccelerators(_toolMenu, im, am);
    migrateMenuAccelerators(_helpMenu, im, am);
    Debug.log("IDE: Accelerators migrated to rootPane");
  }

  private void migrateMenuAccelerators(JMenu menu, InputMap im, ActionMap am) {
    if (menu == null) return;
    for (int i = 0; i < menu.getItemCount(); i++) {
      JMenuItem item = menu.getItem(i);
      if (item == null) continue; // separators
      if (item instanceof JMenu) {
        migrateMenuAccelerators((JMenu) item, im, am);
      } else if (item.getAccelerator() != null) {
        KeyStroke ks = item.getAccelerator();
        String actionKey = "sidebar_" + menu.getText() + "_" + item.getText();
        im.put(ks, actionKey);
        final JMenuItem menuItem = item;
        am.put(actionKey, new AbstractAction() {
          @Override
          public void actionPerformed(ActionEvent e) {
            for (ActionListener al : menuItem.getActionListeners()) {
              al.actionPerformed(new ActionEvent(menuItem, ActionEvent.ACTION_PERFORMED, menuItem.getActionCommand()));
            }
          }
        });
      }
    }
  }

  // Phase 1b: init sidebar navigation items
  private void initSidebarActions() {
    sidebar.initNavItems();
  }

  // Phase 1b: build sidebar submenus from existing menu actions
  private void initSidebarNavigation() {
    SidebarSubmenu fileSub = buildFileSubmenu();
    SidebarSubmenu editSub = buildSubmenuFrom(_editMenu);
    SidebarSubmenu runSub = buildRunSubmenu();
    SidebarSubmenu toolsSub = buildToolsSubmenu();
    SidebarSubmenu helpSub = buildSubmenuFrom(_helpMenu);
    sidebar.initNavigation(fileSub, editSub, runSub, toolsSub, helpSub);
  }

  // Items that require an open script to be functional
  private java.util.List<JMenuItem> scriptDependentItems = new ArrayList<>();

  private SidebarSubmenu buildFileSubmenu() {
    SidebarSubmenu sub = new SidebarSubmenu();
    int scMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

    // ── Nouveau ──
    JMenuItem newHeader = new JMenuItem(_I("menuFileNew"));
    newHeader.setEnabled(false);
    newHeader.setFont(UIManager.getFont("defaultFont").deriveFont(Font.BOLD, 11f));
    sub.add(newHeader);
    sub.addItem(_I("cmdScript"),
        new FlatSVGIcon("icons/menu/page.svg", 16, 16),
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, scMask),
        e -> { createEmptyScriptContext(); });
    sub.addItem(_I("cmdWorkspace"),
        new FlatSVGIcon("icons/sidebar/folder.svg", 16, 16),
        null, e -> openNewWorkspaceDialog());

    // ── Ouvrir ──
    JMenuItem openHeader = new JMenuItem(_I("menuFileOpen"));
    openHeader.setEnabled(false);
    openHeader.setFont(UIManager.getFont("defaultFont").deriveFont(Font.BOLD, 11f));
    sub.add(openHeader);
    sub.addItem(_I("cmdScript"),
        new FlatSVGIcon("icons/menu/page.svg", 16, 16),
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, scMask),
        e -> { File f = selectFileToOpen(); if (f != null) createFileContext(f); });
    sub.addItem(_I("cmdWorkspace"),
        new FlatSVGIcon("icons/sidebar/folder.svg", 16, 16),
        null, e -> openExistingWorkspace());

    sub.addSeparator();

    // Enregistrer
    scriptDependentItems.add(sub.addItem(_I("menuFileSave"),
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, scMask),
        e -> { PaneContext ctx = getActiveContext(); if (ctx != null) ctx.save(); }));
    scriptDependentItems.add(sub.addItem(_I("menuFileSaveAs"),
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK | scMask),
        e -> { PaneContext ctx = getActiveContext(); if (ctx != null) ctx.saveAs(); }));
    scriptDependentItems.add(sub.addItem("Export packed source...",
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_E, InputEvent.SHIFT_DOWN_MASK | scMask),
        e -> { exportAsZip(); }));
    scriptDependentItems.add(sub.addItem("Export as .jar (script library)", null,
        e -> { exportAsJar(); }));

    sub.addSeparator();

    scriptDependentItems.add(sub.addItem(_I("menuFileCloseTab"),
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W, scMask),
        e -> { PaneContext ctx = getActiveContext(); if (ctx != null) ctx.close(); }));

    sub.addSeparator();

    sub.addItem(_I("menuFilePreferences"),
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, scMask),
        e -> showPreferencesWindow());

    sub.addSeparator();

    sub.addItem(_I("menuFileQuit"), null,
        e -> terminate());

    return sub;
  }

  private SidebarSubmenu buildRunSubmenu() {
    SidebarSubmenu sub = new SidebarSubmenu();
    int scMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    scriptDependentItems.add(sub.addItem(_I("menuRunRun"),
        new FlatSVGIcon("icons/sidebar/play.svg", 16, 16),
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, scMask),
        e -> btnRun.runCurrentScript()));
    scriptDependentItems.add(sub.addItem(_I("menuRunRunAndShowActions"),
        new FlatSVGIcon("icons/sidebar/play.svg", 16, 16),
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, InputEvent.ALT_DOWN_MASK | scMask),
        e -> btnRunSlow.runCurrentScript()));
    sub.addSeparator();
    scriptDependentItems.add(sub.addItem(_I("menuRunRunSelection"),
        new FlatSVGIcon("icons/sidebar/play.svg", 16, 16),
        KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, InputEvent.SHIFT_DOWN_MASK | scMask),
        e -> getCurrentCodePane().runSelection()));
    return sub;
  }

  private SidebarSubmenu buildToolsSubmenu() {
    SidebarSubmenu sub = new SidebarSubmenu();
    scriptDependentItems.add(sub.addItem(_I("menuToolCapture"),
        new FlatSVGIcon("icons/menu/camera.svg", 16, 16), null,
        e -> btnCapture.captureWithAutoDelay()));
    // Ins\u00E9rer image \u2014 picks a PNG/JPG from disk, copies it to the script
    // bundle and inserts the matching code. Same action class as the legacy
    // toolbar button. Auto-disabled when no script is open via
    // scriptDependentItems \u2192 no NPE on getActiveContext().getPane().
    scriptDependentItems.add(sub.addItem(_I("btnInsertImageLabel"),
        new FlatSVGIcon("icons/menu/picture.svg", 16, 16), null,
        e -> btnInsertImage.actionPerformed(e)));
    scriptDependentItems.add(sub.addItem(_I("menuToolRecord"),
        new FlatSVGIcon("icons/menu/red-dot.svg", 16, 16), null,
        e -> btnRecord.actionPerformed(e)));
    scriptDependentItems.add(sub.addItem(_I("menuToolModernRecorder"),
        new FlatSVGIcon("icons/menu/green-dot.svg", 16, 16), null,
        e -> {
          org.sikuli.ide.ui.recorder.RecorderConfigDialog config =
              new org.sikuli.ide.ui.recorder.RecorderConfigDialog(SikulixIDE.this);
          config.setVisible(true);
          ICodeGenerator selectedGenerator = config.isConfirmed() ? config.getSelectedGenerator() : null;
          config.dispose();
          config = null;
          if (selectedGenerator != null) {
            new org.sikuli.ide.ui.recorder.RecorderAssistant(
                SikulixIDE.this, selectedGenerator
            ).setVisible(true);
          }
        }));
    sub.addSeparator();
    // Add items from the existing _toolMenu (Extensions, etc.)
    if (_toolMenu != null) {
      for (int i = 0; i < _toolMenu.getItemCount(); i++) {
        JMenuItem item = _toolMenu.getItem(i);
        if (item != null) {
          ActionListener[] listeners = item.getActionListeners();
          sub.addItem(item.getText(), item.getAccelerator(),
              listeners.length > 0 ? listeners[0] : null);
        }
      }
    }
    return sub;
  }

  private void updateScriptDependentItems() {
    boolean hasScript = !contexts.isEmpty();
    for (JMenuItem item : scriptDependentItems) {
      item.setEnabled(hasScript);
    }
  }

  // Invoked by OculixSidebar.initFooter around the Dark/Light toggle. The
  // sidebar triggers FlatLaf.updateUI() itself; we dispatch a symmetric
  // before/after pair so every ThemeAware component can tear down and
  // rebuild its LaF-sensitive state around the swap. This is the central
  // entry point for theme lifecycle (issue #165).
  private void refreshAfterThemeChange() {
    java.util.List<ThemeAware> themeAware = collectThemeAware();
    for (ThemeAware t : themeAware) {
      try { t.beforeThemeChange(); }
      catch (Exception ex) { error("beforeThemeChange on %s: %s", t, ex.getMessage()); }
    }
    // FlatLaf.updateUI() has already been run by the sidebar toggle before
    // this callback; the actual swap happens between the two phases.
    for (ThemeAware t : themeAware) {
      try { t.afterThemeChange(); }
      catch (Exception ex) { error("afterThemeChange on %s: %s", t, ex.getMessage()); }
    }
    if (tabs != null) {
      for (int i = 0; i < tabs.getTabCount(); i++) {
        Component tab = tabs.getComponentAt(i);
        if (tab != null) {
          tab.revalidate();
          tab.repaint();
        }
      }
    }
    if (ideWindow != null) {
      ideWindow.revalidate();
      ideWindow.repaint();
    }
  }

  private java.util.List<ThemeAware> collectThemeAware() {
    java.util.List<ThemeAware> list = new java.util.ArrayList<>();
    if (contexts != null) {
      for (PaneContext ctx : contexts) {
        if (ctx != null && ctx.pane instanceof ThemeAware) {
          list.add((ThemeAware) ctx.pane);
        }
      }
    }
    if (messages instanceof ThemeAware) {
      list.add((ThemeAware) messages);
    }
    if (explorer instanceof ThemeAware) {
      list.add((ThemeAware) explorer);
    }
    if (sidebar != null) {
      // Sidebar is not a ThemeAware by itself, but its cached submenus need
      // updateUI() replayed since JPopupMenu lives outside the window tree.
      list.add(new ThemeAware() {
        @Override public void beforeThemeChange() {}
        @Override public void afterThemeChange() { sidebar.refreshSubmenuLaF(); }
      });
    }
    return list;
  }

  private SidebarSubmenu buildSubmenuFrom(JMenu sourceMenu) {
    SidebarSubmenu sub = new SidebarSubmenu();
    if (sourceMenu == null) return sub;
    boolean lastWasSeparator = true; // avoid leading separator
    for (int i = 0; i < sourceMenu.getItemCount(); i++) {
      JMenuItem item = sourceMenu.getItem(i);
      if (item == null) {
        // separator — skip duplicates
        if (!lastWasSeparator) {
          sub.addSeparator();
          lastWasSeparator = true;
        }
      } else if (item instanceof JMenu) {
        // Nested menu (e.g., Find submenu, Recent) — flatten into submenu
        JMenu nested = (JMenu) item;
        if (nested.getItemCount() == 0) continue; // skip empty menus like Recent
        if (!lastWasSeparator) {
          sub.addSeparator();
          lastWasSeparator = true;
        }
        for (int j = 0; j < nested.getItemCount(); j++) {
          JMenuItem nestedItem = nested.getItem(j);
          if (nestedItem != null) {
            ActionListener[] listeners = nestedItem.getActionListeners();
            sub.addItem(nestedItem.getText(), nestedItem.getAccelerator(),
                listeners.length > 0 ? listeners[0] : null);
            lastWasSeparator = false;
          }
        }
      } else if (!item.isEnabled()) {
        // Section header label (disabled bold item) — reproduce in submenu
        JMenuItem header = new JMenuItem(item.getText());
        header.setEnabled(false);
        header.setFont(UIManager.getFont("defaultFont").deriveFont(Font.BOLD, 11f));
        sub.add(header);
        lastWasSeparator = true; // treat as separator to avoid doubles
      } else {
        ActionListener[] listeners = item.getActionListeners();
        sub.addItem(item.getText(), item.getAccelerator(),
            listeners.length > 0 ? listeners[0] : null);
        lastWasSeparator = false;
      }
    }
    return sub;
  }

  static SikuliIDEStatusBar getStatusbar() {
    return sikulixIDE._status;
  }

  private SikuliIDEStatusBar _status;
  private OculixSidebar sidebar;
  private ScriptExplorer explorer;

  private JSplitPane mainPane;
  private JSplitPane editorSplit;
  private static final int EXPLORER_OPEN_WIDTH = 200;

  /**
   * Re-opens or collapses the workspace explorer pane based on whether any
   * scripts are open OR a workspace is loaded. Called after every
   * refreshWorkspace() — the explorer is purely contextual: empty IDE = no
   * workspace and no scripts = collapsed; otherwise visible.
   *
   * <p>The setDividerLocation call is wrapped in {@link SwingUtilities#invokeLater}
   * because {@link JSplitPane} silently ignores divider position changes if
   * the pane hasn't been fully laid out yet — typical when this is called
   * synchronously from a workspace-load action triggered by a button click
   * before the pane has had its first paint pass. invokeLater pushes it
   * onto the EDT after the current event has finished and the next layout
   * round has run.
   */
  private void refreshExplorerVisibility() {
    if (editorSplit == null) return;
    boolean hasScripts = !contexts.isEmpty();
    boolean hasWorkspace = currentWorkspaceDir != null;
    boolean shouldShow = hasScripts || hasWorkspace;
    final int target = shouldShow ? EXPLORER_OPEN_WIDTH : 0;
    SwingUtilities.invokeLater(() -> {
      if (explorer != null) {
        explorer.setVisible(shouldShow);
      }
      editorSplit.setDividerLocation(target);
      editorSplit.setDividerSize(shouldShow ? 4 : 0);
      editorSplit.revalidate();
      editorSplit.repaint();
    });
  }

  private void initTabs() {
    tabs = new CloseableTabbedPane();
    // Phase 1c: Use FlatLaf native closeable tabs
    tabs.putClientProperty("JTabbedPane.tabClosable", true);
    tabs.putClientProperty("JTabbedPane.tabCloseToolTipText", "Close");
    tabs.putClientProperty("JTabbedPane.tabCloseCallback",
        (java.util.function.BiConsumer<JTabbedPane, Integer>) (tabbedPane, tabIndex) -> {
          // Check if this is the welcome tab (not a script context)
          Component comp = tabbedPane.getComponentAt(tabIndex);
          if (comp instanceof WelcomeTab) return;
          // Account for welcome tab offset
          int contextIndex = welcomeShowing ? tabIndex - 1 : tabIndex;
          if (contextIndex >= 0 && contextIndex < contexts.size()) {
            getContextAt(contextIndex).close();
          }
        });
    tabs.addChangeListener(e -> {
      JTabbedPane tab = (JTabbedPane) e.getSource();
      int ix = tab.getSelectedIndex();
      switchContext(ix);
    });
  }

  CloseableTabbedPane getTabs() {
    return tabs;
  }

  private CloseableTabbedPane tabs;
  private WelcomeTab welcomeTab;
  private boolean welcomeShowing = false;

  void showWelcomeTab() {
    if (!welcomeShowing && tabs.getTabCount() == 0) {
      if (welcomeTab == null) {
        welcomeTab = new WelcomeTab(
            e -> { hideWelcomeTab(); createEmptyScriptContext(); },
            e -> { File f = selectFileToOpen(); if (f != null) createFileContext(f); },
            e -> openNewWorkspaceDialog(),
            e -> openExistingWorkspace()
        );
        welcomeTab.putClientProperty("isClosable", false);
      }
      tabs.addTab("Welcome", welcomeTab);
      welcomeShowing = true;
      updateScriptDependentItems();
      if (sidebar != null) {
        sidebar.updateProjectInfo(null, null);
      }
      refreshWorkspace();
    }
  }

  void hideWelcomeTab() {
    if (welcomeShowing && welcomeTab != null) {
      int idx = tabs.indexOfComponent(welcomeTab);
      if (idx >= 0) {
        tabs.removeTabAt(idx);
      }
      welcomeShowing = false;
      updateScriptDependentItems();
    }
  }
  //</editor-fold>

  //<editor-fold desc="03 PaneContext">
  List<PaneContext> contexts = new ArrayList<>();
  List<PaneContext> contextsClosed = new ArrayList<>();
  String tempName = "sxtemp";
  int tempIndex = 1;

  PaneContext lastContext = null;

  public PaneContext getActiveContext() {
    final int ix = tabs.getSelectedIndex();
    if (ix < 0 || ix >= contexts.size()) {
      return null;
    }
    return contexts.get(ix);
  }

  PaneContext setActiveContext(int pos) {
    if (pos < 0 || pos >= contexts.size()) {
      return null;
    }
    tabs.setSelectedIndex(pos);
    PaneContext context = contexts.get(pos);
    showContext(context);
    return context;
  }

  PaneContext getContextAt(int ix) {
    if (ix < 0 || ix >= contexts.size()) {
      return null;
    }
    return contexts.get(ix);
  }

  void switchContext(int ix) {
    if (ix < 0 || contexts.isEmpty()) {
      return;
    }
    // Account for welcome tab offset
    int contextIx = welcomeShowing ? ix - 1 : ix;
    if (contextIx < 0 || contextIx >= contexts.size()) {
      return;
    }
    PaneContext context = contexts.get(contextIx);
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

    // Update sidebar project info and workspace explorer
    if (sidebar != null) {
      sidebar.updateProjectInfo(context.getFileName(), context.getFolder());
    }
    refreshWorkspace();
    updateScriptDependentItems();
  }

  /**
   * Rebuilds the workspace explorer cards from the current contexts list.
   */
  public void refreshWorkspace() {
    if (explorer == null) return;
    List<ScriptExplorer.ScriptInfo> scripts = new ArrayList<>();
    for (PaneContext ctx : contexts) {
      scripts.add(ScriptExplorer.ScriptInfo.fromFolder(
          ctx.getFileName(), ctx.getFolder(), ctx.isTemp()));
    }
    int selected = tabs.getSelectedIndex();
    // If welcome tab is showing, no card is selected
    if (welcomeShowing && selected == 0) {
      selected = -1;
    }
    explorer.updateScripts(scripts, selected);
    refreshExplorerVisibility();
  }

  public void createEmptyScriptContext() {
    hideWelcomeTab();
    final PaneContext context = new PaneContext();
    context.setRunner(IDESupport.getDefaultRunner());
    context.setFile();
    context.create();
    refreshWorkspace();
    if (sidebar != null) {
      sidebar.updateProjectInfo(context.getFileName(), context.getFolder());
    }
    updateScriptDependentItems();
  }

  void createEmptyTextContext() {
    hideWelcomeTab();
    final PaneContext context = new PaneContext();
    context.setRunner(Runner.getRunner(TextRunner.class));
    context.setFile();
    context.create();
  }

  void createFileContext(File file) {
    hideWelcomeTab();
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
    // Initial directory: leave it to SikulixFileChooser.getLastDir which has
    // a proper fallback chain (LAST_OPEN_DIR pref → user.dir → user.home).
    // Pre-setting LAST_OPEN_DIR here was overriding the value the previous
    // chooser had correctly stored (fileChosen.getParent()) with the
    // grand-parent of the active context's folder — landing the user one
    // level up from where they last picked a file.
    if (currentWorkspaceDir != null) {
      PreferencesUser.get().put("LAST_OPEN_DIR", currentWorkspaceDir.getAbsolutePath());
    }
    File fileSelected = new SikulixFileChooser(sikulixIDE).open();
    if (fileSelected == null) {
      return null;
    }
    return fileSelected;
  }

  public File selectFileForSave(PaneContext context) {
    // Same rationale as selectFileToOpen: respect the chooser's stored value
    // unless an explicit workspace is set.
    if (currentWorkspaceDir != null) {
      PreferencesUser.get().put("LAST_OPEN_DIR", currentWorkspaceDir.getAbsolutePath());
    }
    File fileSelected = new SikulixFileChooser(sikulixIDE).saveAs(
            context.getExt(), context.isBundle() || context.isTemp());
    if (fileSelected == null) {
      return null;
    }
    return fileSelected;
  }

  boolean checkDirtyPanes() {
    for (PaneContext context : contexts) {
      if (context.isDirty() || (context.isTemp() && context.hasContent())) {
        return true;
      }
    }
    return false;
  }

  List<File> collectPaneFiles() {
    List<File> files = new ArrayList<>();
    for (PaneContext context : contexts) {
      if (context.isTemp()) {
        log("TODO: collectPaneFiles: save temp pane");
        context.notDirty();
      }
      if (context.isDirty()) {
        log("TODO: collectPaneFiles: save dirty pane");
      }
      files.add(context.getFile());
    }
    return files;
  }

  public class PaneContext {
    File folder;
    File imageFolder;
    File file;
    String name;
    String ext;

    IRunner runner = null;
    IIDESupport support;
    String type;

    EditorPane pane;
    boolean showThumbs; //TODO
    int pos = 0;

    boolean dirty = false;
    boolean temp = false;

    private PaneContext() {
    }

    public void focus() {
      if (isText()) {
        collapseMessageArea();
      }
      pane.requestFocusInWindow();
    }

    public EditorPane getPane() {
      return pane;
    }

    public boolean isValid() {
      return file.exists() && runner != null;
    }

    public boolean isText() {
      return type.equals(TextRunner.TYPE);
    }

    public boolean isTemp() {
      return temp;
    }

    public boolean hasContent() {
      return !pane.getText().isEmpty();
    }

    public IRunner getRunner() {
      return runner;
    }

    private void setRunner(IRunner _runner) {
      runner = _runner;
      type = runner.getType();
      ext = runner.getDefaultExtension();
      support = IDESupport.get(type);
    }

    private void setRunner() {
      if (ext.isEmpty()) {
        runner = IDESupport.getDefaultRunner();
        ext = runner.getDefaultExtension();
        file = new File(file.getAbsolutePath() + "." + ext);
      } else {
        final IRunner _runner = Runner.getRunner(file.getAbsolutePath());
        if (!(_runner instanceof InvalidRunner)) {
          runner = _runner;
        } else {
          runner = new TextRunner();
        }
      }
      type = runner.getType();
      support = IDESupport.get(type);
    }

    public IIDESupport getSupport() {
      return support;
    }

    public String getType() {
      return type;
    }

    public File getFolder() {
      return folder;
    }

    public File getFile() {
      return file;
    }

    public String getFileName() {
      return name + "." + ext;
    }

    public String getName() {
      return name;
    }

    private void setFile() {
      name = tempName + tempIndex++;
      folder = new File(Commons.getIDETemp(), name);
      folder.mkdirs();
      file = new File(folder, name + "." + ext);
      if (file.exists()) {
        file.delete();
      }
      try {
        file.createNewFile();
      } catch (IOException e) {
        fatal("PaneContext: setFile: create not possible: %s", file); //TODO
      }
      imageFolder = folder;
      temp = true;
      log("PaneContext: setFile: %s", file);
    }

    private boolean setFile(File _file) {
      if (_file == null) {
        return false;
      }
      File _folder;
      String path = _file.getAbsolutePath();
      String _ext = FilenameUtils.getExtension(path);
      String _name = FilenameUtils.getBaseName(path);
      if (_file.exists()) {
        if (_file.isDirectory()) {
          _folder = _file;
          _file = Runner.getScriptFile(_folder);
          if (_file != null) {
            _ext = FilenameUtils.getExtension(_file.getPath());
          } else {
            // No script file found in bundle — create one with default extension
            _ext = IDESupport.getDefaultRunner().getDefaultExtension();
            _file = new File(_folder, _name + "." + _ext);
            try {
              _file.createNewFile();
            } catch (IOException e) {
              log("PaneContext: setFile: create not possible: %s", _file);
              return false;
            }
          }
        } else {
          _folder = _file.getParentFile();
        }
      } else {
        if (_ext.isEmpty() || _ext.equals("sikuli")) {
          _file.mkdirs();
          _folder = _file;
          String scriptExt = IDESupport.getDefaultRunner().getDefaultExtension();
          _file = new File(_folder, _name + "." + scriptExt);
          _ext = scriptExt;
        } else {
          _folder = _file.getParentFile();
          _folder.mkdirs();
        }
        try {
          _file.createNewFile();
        } catch (IOException e) {
          log("PaneContext: setFile: create not possible: %s", _file);
          return false;
        }
      }
      if (_file == null) {
        return false;
      }
      file = _file;
      ext = _ext;
      name = _name;
      folder = _folder;
      imageFolder = folder;
      log("PaneContext: setFile: %s", file);
      return true;
    }

    public String getExt() {
      return ext;
    }

    public boolean isBundle() {
      return folder.getAbsolutePath().endsWith(".sikuli");
    }

    public boolean isBundle(File file) {
      return file.getAbsolutePath().endsWith(".sikuli") || FilenameUtils.getExtension(file.getName()).isEmpty();
    }

    public File getImageFolder() {
      return imageFolder;
    }

    public void setImageFolder(File folder) {
      if (folder == null) {
        fatal("PaneContext: setImageFolder: is null (ignored)"); //TODO
        return;
      }
      if (!folder.exists()) {
        folder.mkdirs();
      }
      if (folder.exists()) {
        imageFolder = folder;
        ImagePath.setBundleFolder(imageFolder);
      } else {
        fatal("PaneContext: setImageFolder: create not possible: %s", folder); //TODO
      }
    }

    public File getScreenshotFolder() {
      return new File(imageFolder, ImagePath.SCREENSHOT_DIRECTORY);
    }

    public File getScreenshotFile(String name) {
      if (!FilenameUtils.getExtension(name).equals("png")) {
        name += ".png";
      }
      File shot = new File(name);
      if (!shot.isAbsolute()) {
        shot = new File(getScreenshotFolder(), name);
      }
      if (shot.exists()) {
        return shot;
      }
      return null;
    }

    public File getScreenshotFile(File shot) {
      if (shot.exists()) {
        return shot;
      }
      return null;
    }

    public boolean getShowThumbs() {
      return showThumbs;
    }

    public void setShowThumbs(boolean state) {
      showThumbs = state;
    }

    int getLineStart(int lineNumber) {
      try {
        return pane.getLineStartOffset(lineNumber);
      } catch (BadLocationException e) {
        return -1;
      }
    }

    private void create() {
      int lastPos = -1;
      if (contexts.size() > 0) {
        lastPos = getActiveContext().pos;
      }
      showThumbs = !PreferencesUser.get().getPrefMorePlainText();
      pane = new EditorPane(this);
      contexts.add(pos, this);
      tabs.addNewTab(name, pane.getScrollPane(), pos);
      tabs.setSelectedIndex(pos);
      pane.makeReady();
      if (load()) {
        pane.requestFocus();
        // Convert image filenames present in the loaded text into inline
        // EditorImageButton thumbnails. reparse() already gates internally on
        // getShowThumbs(), so this is a no-op when the user has plain-text
        // mode enabled. Without this call, opening a .py file that references
        // images in the same folder loads the text but leaves the filenames
        // un-embedded — visible regression on every IDE start before the
        // user manually toggled the "Show Thumbs" menu off and on.
        reparse();
      } else {
        if (lastPos >= 0) {
          tabs.remove(pos);
          tabs.setSelectedIndex(lastPos);
          contexts.remove(pos);
        } else {
          tabs.remove(0);
          fatal("PaneContext: create: start tab failed"); //TODO possible?
        }
      }
      resetPos();
    }

    public boolean close() {
      return doClose(false);
    }

    public boolean closeSilent() {
      return doClose(true);
    }

    boolean doClose(boolean discard) {
      boolean shouldSave = true;
      if (isDirty() || (isTemp() && !discard)) {
        if (isTemp()) {
          String msg = String.format("%s: content not yet saved!", getFileName());
          final int answer = SXDialog.askForDecision(sikulixIDE, "Closing Tab", msg,
                  "Discard", "Save");
          if (answer == SXDialog.DECISION_CANCEL) {
            return false;
          }
          if (answer == SXDialog.DECISION_ACCEPT) {
            File fileSaved = selectFileForSave(this);
            if (fileSaved != null) {
              setFile(fileSaved);
            } else {
              return false;
            }
          } else {
            shouldSave = false;
          }
        }
        if (shouldSave && !discard) {
          cleanBundle(); //TODO
          save();
        }
        notDirty();
      }
      int closedPos = pos;
      tabs.remove(pos);
      contexts.remove(pos);
      pos = -1;
      contextsClosed.add(0, this);
      if (ideIsQuitting) {
        resetPos();
        if (contexts.size() > 0) {
          setActiveContext(contexts.size() - 1);
        }
      } else {
        if (resetPos() == 0) {
          showWelcomeTab();
        } else {
          setActiveContext(Math.min(closedPos, contexts.size() - 1));
        }
      }
      return true;
    }

    private int resetPos() {
      int n = 0;
      for (PaneContext context : contexts) {
        context.pos = n++;
      }
      return n;
    }

    private void cleanBundle() { //TODO consolidate with FileManager and Settings
      log("cleanBundle: %s", getName());
      String[] text = pane.getText().split("\n");
      List<Map<String, Object>> images = collectImages(text);
      Set<String> foundImages = new HashSet<>();
      for (Map<String, Object> image : images) {
        File imgFile = (File) image.get(IButton.FILE);
        String name = imgFile.getName();
        foundImages.add(name);
      }
      deleteNotUsedImages(getImageFolder(), foundImages);
      deleteNotUsedScreenshots(getScreenshotFolder(), foundImages);
      log("cleanBundle finished: %s", getName());
    }

    public void deleteNotUsedImages(File scriptFolder, Set<String> usedImages) {
      if (!scriptFolder.isDirectory()) {
        return;
      }
      File[] files = scriptFolder.listFiles((dir, name) -> {
        if ((name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"))) {
          if (!name.startsWith("_")) {
            return true;
          }
        }
        return false;
      });
      if (files == null || files.length == 0) {
        return;
      }
      File recycle = new File(scriptFolder, ".recycle");
      for (File image : files) {
        if (!usedImages.contains(image.getName())) {
          softDeleteToRecycle(image, recycle);
        }
      }
    }

    public void deleteNotUsedScreenshots(File screenshotsDir, Set<String> usedImages) {
      if (screenshotsDir.exists()) {
        File[] files = screenshotsDir.listFiles();
        if (files == null || files.length == 0) {
          return;
        }
        File recycle = new File(screenshotsDir, ".recycle");
        for (File screenshot : files) {
          if (!usedImages.contains(screenshot.getName())) {
            softDeleteToRecycle(screenshot, recycle);
          }
        }
      }
    }

    /**
     * Soft-delete: move the file to a {@code .recycle/} sibling instead of
     * hard-deleting. cleanBundle() invokes this on every save for any
     * {@code .png/.jpg/.jpeg} not matched by {@code collectImages}, but the
     * matcher uses string-literal regex only — dynamically composed filenames
     * (concat, f-strings, variable interpolation) are missed and would be
     * lost on save without recovery. Soft-delete keeps a 1-cycle backup so
     * the user can restore by hand if the matcher rates a false negative.
     */
    private void softDeleteToRecycle(File file, File recycleDir) {
      if (!recycleDir.exists() && !recycleDir.mkdirs()) {
        // cannot create recycle bin → fall back to hard delete to keep
        // pre-fix behaviour rather than leaving stale files behind
        file.delete();
        return;
      }
      File target = new File(recycleDir, file.getName());
      if (target.exists()) {
        // Suffix with timestamp on collision so we never overwrite a previous
        // recycled copy.
        String stamp = String.valueOf(System.currentTimeMillis());
        target = new File(recycleDir, file.getName() + "." + stamp);
      }
      if (!file.renameTo(target)) {
        // rename across volumes can fail on some FS — last resort hard delete
        file.delete();
      }
    }

    public boolean isDirty() {
      return dirty;
    }

    public void setDirty() {
      if (!dirty) {
        dirty = true;
        setFileTabTitleDirty(pos, dirty);
      }
    }

    public void notDirty() {
      if (dirty) {
        dirty = false;
        setFileTabTitleDirty(pos, dirty);
      }
    }

    void setFileTabTitleDirty(int pos, boolean isDirty) {
      String title = tabs.getTitleAt(pos);
      if (!isDirty && title.startsWith("*")) {
        title = title.substring(1);
        tabs.setTitleAt(pos, title);
      } else if (isDirty && !title.startsWith("*")) {
        title = "*" + title;
        tabs.setTitleAt(pos, title);
      }
    }

    public boolean saveAs() {
      boolean shouldSave = true;
      if (!isTemp() && isDirty()) {
        todo("PaneContext: saveAs: ask: discard or save changes"); //TODO
        String msg = String.format("%s: save changes?", file.getName());
        final int answer = SXDialog.askForDecision(sikulixIDE, "Saving Tab", msg,
                "Do not save", "Save");
        if (answer == SXDialog.DECISION_CANCEL) {
          return false;
        }
        if (answer == SXDialog.DECISION_IGNORE) {
          shouldSave = false;
          notDirty();
        }
      }
      if (shouldSave) {
        if (!save()) {
          return false;
        }
      }
      boolean success = true;
      File file;
      while (true) {
        file = selectFileForSave(this);
        if (file == null) {
          return false;
        }
        final int pos = alreadyOpen(file);
        if (pos >= 0) {
          log("PaneContext: alreadyopen: %s", file);
          String msg = String.format("%s: is currently open!", file.getName());
          final int answer = SXDialog.askForDecision(sikulixIDE, "Saving Tab", msg,
                  "Overwrite", "Try again");
          if (answer == SXDialog.DECISION_CANCEL) {
            return false;
          }
          if (answer == SXDialog.DECISION_ACCEPT) {
            continue;
          }
          contexts.get(pos).close();
          setActiveContext(this.pos);
        }
        break;
      }
      final PaneContext newContext = new PaneContext();
      if (newContext.setFile(file)) {
        newContext.file.delete();
        try {
          copyContent(this, newContext, isBundle(file));
        } catch (IOException e) {
          success = false;
        }
      }
      if (success) {
        newContext.setRunner();
        newContext.pos = pos;
        newContext.pane = pane;
        newContext.showThumbs = showThumbs;
        contexts.set(pos, newContext);
        contextsClosed.add(0, this);
        tabs.setTitleAt(pos, newContext.name);
        sikulixIDE.setIDETitle(newContext.file.getAbsolutePath());
        sikulixIDE.refreshWorkspace();
        if (sikulixIDE.sidebar != null) {
          sikulixIDE.sidebar.updateProjectInfo(newContext.getFileName(), newContext.getFolder());
        }
      }
      return success;
    }

    private void copyContent(PaneContext currentContext, PaneContext newContext, boolean asBundle) throws
            IOException {
      // Save-As bug fix: previously this method assumed FileUtils.copyDirectory
      // would silently DTRT, but in practice it could leave the destination
      // empty without throwing (e.g. when the destination folder pre-exists
      // from setFile() but the source folder is itself empty due to ordering
      // races). Now we copy the .py explicitly first, then enumerate every
      // sibling file (.png images, fixtures, anything else in the bundle)
      // and copy each one individually with REPLACE_EXISTING. Logs each step
      // so a missing image is instantly diagnosable in the message panel.
      if (asBundle) {
        File srcFolder = currentContext.folder;
        File dstFolder = newContext.folder;
        if (!dstFolder.exists()) {
          dstFolder.mkdirs();
        }
        // 1. Copy the script file, renamed in-place to the new base name.
        final String oldName = currentContext.file.getName();
        final String newName = FilenameUtils.getBaseName(newContext.file.getName());
        final String ext = "." + FilenameUtils.getExtension(oldName);
        File srcScript = new File(srcFolder, oldName);
        File dstScript = new File(dstFolder, newName + ext);
        if (srcScript.isFile()) {
          FileUtils.copyFile(srcScript, dstScript);
        } else {
          // Fallback: source script is open in memory only; flush the active
          // pane's text directly into the destination.
          currentContext.getPane().write(new BufferedWriter(
              new OutputStreamWriter(new FileOutputStream(dstScript), "UTF8")));
        }
        // 2. Copy every other file in the source bundle (images, .json,
        // workspace metadata, anything the user dropped there).
        File[] siblings = srcFolder.listFiles();
        int copied = 0;
        if (siblings != null) {
          for (File sibling : siblings) {
            if (sibling.equals(srcScript)) continue;       // already handled above
            if (!sibling.isFile()) continue;               // skip subdirs for now
            File dst = new File(dstFolder, sibling.getName());
            try {
              java.nio.file.Files.copy(sibling.toPath(), dst.toPath(),
                  java.nio.file.StandardCopyOption.REPLACE_EXISTING);
              copied++;
            } catch (IOException ioex) {
              log("PaneContext: copyContent: skipped %s (%s)",
                  sibling.getName(), ioex.getMessage());
            }
          }
        }
        log("PaneContext: copyContent: %s -> %s (%d sibling file(s) copied)",
            srcFolder, dstFolder, copied);
      } else {
        FileUtils.copyFile(currentContext.file, newContext.file);
        // Same intent as the bundle branch above, but for flat-script saves:
        // walk the source's text, find every "xxx.png" / "xxx.jpg" reference
        // it can resolve against the source's image folder, and copy each one
        // alongside the destination script. Without this, a Save As of a
        // .py with image-based wait/click leaves the destination orphaned —
        // the script references files that only exist next to the *original*.
        File dstDir = newContext.file.getParentFile();
        if (dstDir == null || !dstDir.isDirectory()) {
          // Nothing else we can do safely — destination has no resolvable folder.
          log("PaneContext: copyContent: flat save: no destination folder, image copy skipped");
          return;
        }
        try {
          String[] text = currentContext.getPane().getText().split("\n");
          List<Map<String, Object>> images = currentContext.collectImages(text);
          int copied = 0, skipped = 0;
          Set<String> seen = new HashSet<>();
          for (Map<String, Object> image : images) {
            File src = (File) image.get(IButton.FILE);
            if (src == null || !src.isFile()) { skipped++; continue; }
            if (!seen.add(src.getName())) continue; // de-dup multiple references
            File dst = new File(dstDir, src.getName());
            try {
              java.nio.file.Files.copy(src.toPath(), dst.toPath(),
                  java.nio.file.StandardCopyOption.REPLACE_EXISTING);
              copied++;
            } catch (IOException ioex) {
              log("PaneContext: copyContent: flat save: skipped %s (%s)",
                  src.getName(), ioex.getMessage());
              skipped++;
            }
          }
          log("PaneContext: copyContent: flat save: %s -> %s (%d image(s) copied, %d skipped)",
              currentContext.file, newContext.file, copied, skipped);
        } catch (Exception ex) {
          log("PaneContext: copyContent: flat save: image scan failed (%s)", ex.getMessage());
        }
      }
    }

    public boolean save() {
      String msg = "";
      boolean success = true;
      try {
        pane.write(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF8")));
        notDirty();
      } catch (IOException e) {
        msg = String.format(" did not work: %s", e.getMessage());
        success = false;
      }
      log("PaneContext: save: %s%s", name, msg);
      return success;
    }

    public boolean load() {
      return load(file);
    }

    public boolean load(File file) {
      InputStreamReader isr;
      try {
        isr = new InputStreamReader(new FileInputStream(file), Charset.forName("utf-8"));
        pane.loadContent(isr);
      } catch (Exception ex) {
        log("PaneContext: loadFile: %s ERROR(%s)", file, ex.getMessage());
        return false;
      }
      return true;
    }

    public boolean load(String content) {
      InputStreamReader isr;
      try {
        isr = new InputStreamReader(new ByteArrayInputStream(content.getBytes(Charset.forName("utf-8"))),
                Charset.forName("utf-8"));
        pane.loadContent(isr);
      } catch (Exception ex) {
        log("PaneContext: loadString: ERROR(%s)", ex.getMessage());
        return false;
      }
      return true;
    }

    public void reparse() {
      pane.saveCaretPosition();
      if (getShowThumbs()) {
        doShowThumbs();
      } else {
        resetContentToText();
      }
      pane.restoreCaretPosition();
    }

    private void doShowThumbs() {
      if (!getShowThumbs()) return;
      String[] text = pane.getText().split("\n");
      List<Map<String, Object>> images = collectImages(text);
      List<Map<String, Object>> patterns = patternMatcher(images, text);
      if (images.size() == 0 && patterns.size() == 0) {
        log("ImageButtons: images(0) patterns(0)");
        return;
      }
      // Process matches in REVERSE document order so each remove+insert on
      // a later offset cannot shift earlier ones out of sync. This is the
      // fix to the offset-drift corruption that the old
      // select + insertComponent sequence produced on multi-match inserts.
      List<Map<String, Object>> ordered = new ArrayList<>(images);
      ordered.sort((a, b) -> {
        int lineCmp = Integer.compare((Integer) b.get(IButton.LINE), (Integer) a.get(IButton.LINE));
        if (lineCmp != 0) return lineCmp;
        return Integer.compare((Integer) b.get(IButton.LOFF), (Integer) a.get(IButton.LOFF));
      });
      javax.swing.text.Document doc = pane.getDocument();
      int kept = pane.getCaretPosition();
      for (Map<String, Object> item : ordered) {
        final File imgFile = (File) item.get(IButton.FILE);
        if (imgFile.getName().startsWith("_")) continue;
        try {
          int itemStart = getLineStart((Integer) item.get(IButton.LINE)) + (Integer) item.get(IButton.LOFF);
          int itemLen = ((String) item.get(IButton.TEXT)).length();
          // Defensive against the historical "Invalid remove" — present in
          // SikuliX1 too, where it manifested silently as missing thumbnails
          // on script load. The collectImages() pass works on a snapshot of
          // pane.getText() taken at method entry; between that snapshot and
          // the doc.remove() call, the document may have been mutated by
          // another listener (caret move, theme refresh, async lexer hook,
          // a parallel reparse triggered from PaneContext.create()), so the
          // (line, loff, text) tuple computed from the snapshot can point
          // PAST the live doc end or at content that no longer matches.
          //
          // Two guards prevent the BadLocationException explosion:
          //   1) bounds: itemStart in [0, docLen], itemEnd <= docLen.
          //   2) content: doc.getText(itemStart, itemLen) must equal the
          //      TEXT we captured. If it doesn't, a previous swap on the
          //      same offset already replaced the literal with the "￼"
          //      placeholder, or the text shifted — either way, skip.
          if (itemStart < 0 || itemStart + itemLen > doc.getLength()) {
            continue;
          }
          String live = doc.getText(itemStart, itemLen);
          String expected = (String) item.get(IButton.TEXT);
          if (!live.equals(expected)) {
            continue;
          }
          EditorImageButton button = new EditorImageButton(item);
          // If the captured TEXT was a Pattern(...).chain expression, replay
          // the parsed modifiers onto the freshly built button so its
          // internal state matches what the script encoded. setParameters
          // also flips _isPattern, which both makes the badge visible and
          // keeps File ▸ Save round-tripping the chain.
          String capturedText = (String) item.get(IButton.TEXT);
          if (capturedText != null && capturedText.startsWith("Pattern(")) {
            Object simObj = item.get("PATTERN_SIMILAR");
            double sim = simObj instanceof Double ? (Double) simObj : 0.7;
            boolean exact = Boolean.TRUE.equals(item.get("PATTERN_EXACT"));
            button.setParameters(exact, sim, 0);
            Object ox = item.get("PATTERN_OFFSET_X");
            Object oy = item.get("PATTERN_OFFSET_Y");
            if (ox instanceof Integer && oy instanceof Integer) {
              button.setTargetOffset(
                  new org.sikuli.script.Location((Integer) ox, (Integer) oy));
            }
          }
          javax.swing.text.SimpleAttributeSet attr = new javax.swing.text.SimpleAttributeSet();
          javax.swing.text.StyleConstants.setComponent(attr, button);
          doc.remove(itemStart, itemLen);
          doc.insertString(itemStart, "￼", attr);
        } catch (javax.swing.text.BadLocationException e) {
          error("doShowThumbs: cannot swap match: %s", e.getMessage());
        }
      }
      pane.setCaretPosition(Math.min(kept, doc.getLength()));
      log("ImageButtons: images(%d) patterns(%d)", images.size(), patterns.size());
    }

    private List<Map<String, Object>> collectImages(String[] text) {
      List<Map<String, Object>> images = new ArrayList<>();
      if (text.length > 0) {
        images = imageMatcher(images, text, Pattern.compile(".*?(\".*?\")"));
        images = imageMatcher(images, text, Pattern.compile(".*?('.*?')"));
      }
      return images;
    }

    private List<Map<String, Object>> patternMatcher(List<Map<String, Object>> images, String[] text) {
      List<Map<String, Object>> patterns = new ArrayList<>();
      for (Map<String, Object> match : images) {
        //TODO patternMatcher
      }
      return patterns;
    }

    // Optional Pattern(...) wrap with arbitrary chained modifiers like
    //   Pattern("name.png").similar(0.85).exact().targetOffset(10,5).resize(2)
    // We extend the basic "name.png" match to cover the whole chain so that
    // when doShowThumbs swaps the literal for the inline button, it
    // SUBSTITUTES THE FULL EXPRESSION — visible code becomes
    //   wait(<image>, 10)
    // instead of the cluttered
    //   wait(Pattern("name.png").similar(0.85), 10)
    // Modifier values are parsed from the chain and applied to the button
    // via setParameters / setTargetOffset so that Optimize finds the right
    // initial state and File ▸ Save round-trips the chain unchanged.
    private static final Pattern CHAIN_MODIFIER = Pattern.compile("\\.\\w+\\([^)]*\\)");
    private static final Pattern SIMILAR_PARSE  = Pattern.compile("\\.similar\\(([-0-9.eE]+)\\)");
    private static final Pattern OFFSET_PARSE   = Pattern.compile("\\.targetOffset\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\)");

    private List<Map<String, Object>> imageMatcher(List<Map<String, Object>> images, String[] text, Pattern pat) {
      for (int lnNbr = 0; lnNbr < text.length; lnNbr++) {
        String line = text[lnNbr];
        Matcher matcher = pat.matcher(line);
        if (line.contains("\"\"\"") || line.contains("'''")) {
          continue;
        }
        while (matcher.find()) {
          String match = matcher.group(1);
          if (match != null) {
            int start = matcher.start(1);
            int end = matcher.end(1);
            String imgName = match.substring(1, match.length() - 1);
            final File imgFile = imageExists(imgName);
            if (imgFile != null) {
              int extStart = start;
              int extEnd = end;
              // Look for "Pattern(" prefix immediately before the quoted
              // filename. If found AND the next char after the closing
              // quote is ')', extend the span to cover the full
              // Pattern(...).modifier(...)... chain.
              if (start >= 8 && "Pattern(".equals(line.substring(start - 8, start))
                  && end < line.length() && line.charAt(end) == ')') {
                extStart = start - 8;
                extEnd = end + 1; // include the ')' that closes Pattern(...)
                // Greedy: consume each ".name(...)" modifier one by one.
                while (extEnd < line.length()) {
                  Matcher mm = CHAIN_MODIFIER.matcher(line);
                  if (mm.region(extEnd, line.length()).useAnchoringBounds(true).lookingAt()) {
                    extEnd = mm.end();
                  } else {
                    break;
                  }
                }
              }
              String fullText = line.substring(extStart, extEnd);
              Map<String, Object> options = new HashMap<>();
              options.put(IButton.TEXT, fullText);
              options.put(IButton.LINE, lnNbr);
              options.put(IButton.LOFF, extStart);
              options.put(IButton.FILE, imgFile);
              // Pre-parse modifiers from the chain so doShowThumbs can
              // re-apply them on the freshly-built EditorImageButton.
              if (extEnd > end) {
                Matcher sm = SIMILAR_PARSE.matcher(fullText);
                if (sm.find()) {
                  try { options.put("PATTERN_SIMILAR", Double.parseDouble(sm.group(1))); }
                  catch (NumberFormatException ignore) { }
                }
                if (fullText.contains(".exact()")) {
                  options.put("PATTERN_EXACT", Boolean.TRUE);
                }
                Matcher om = OFFSET_PARSE.matcher(fullText);
                if (om.find()) {
                  try {
                    options.put("PATTERN_OFFSET_X", Integer.parseInt(om.group(1)));
                    options.put("PATTERN_OFFSET_Y", Integer.parseInt(om.group(2)));
                  } catch (NumberFormatException ignore) { }
                }
              }
              images.add(options);
            }
          }
        }
      }
      return images;
    }

    private File imageExists(String imgName) {
      String orgName = imgName;
      imgName = FilenameUtils.normalizeNoEndSeparator(imgName, true);
      if (imgName == null) {
        return null;
      }
      imgName = imgName.replaceAll("//", "/");
      String ext;
      try {
        ext = FilenameUtils.getExtension(imgName);
      } catch (Exception e) {
        return null;
      }
      File folder = getImageFolder();
      if (ext.isEmpty()) {
        ext = "png";
        imgName += ".png";
      }
      if ("png;jpg;jpeg;".contains(ext + ";")) {
        File imgFile = new File(imgName);
        if (!imgFile.isAbsolute()) {
          imgFile = new File(folder, imgName);
        }
        if (imgFile.exists()) {
          log("%s (%s)", orgName, imgFile);
          return imgFile;
        }
      }
      return null;
    }

    public void insertImageButton(File imgFile) {
      final EditorImageButton button = new EditorImageButton(imgFile);
      pane.insertComponent(button);
    }

    public boolean resetContentToText() {
      InputStreamReader isr;
      try {
        isr = new InputStreamReader(new ByteArrayInputStream(
                pane.getText().getBytes(Charset.forName("utf-8"))), Charset.forName("utf-8"));
        pane.read(new BufferedReader(isr), null);
      } catch (Exception ex) {
        error("readContent: from String (%s)", ex.getMessage());
        return false;
      }
      return true;
    }
  }

  EditorPane getCurrentCodePane() {
    PaneContext ctx = getActiveContext();
    return ctx != null ? ctx.getPane() : null;
  }

  EditorPane getPaneAtIndex(int index) {
    PaneContext ctx = getContextAt(index);
    return ctx != null ? ctx.getPane() : null;
  }

  private void convertSrcToHtml(String bundle) {
//    IScriptRunner runner = ScriptingSupport.getRunner(null, "jython");
//    if (runner != null) {
//      runner.doSomethingSpecial("convertSrcToHtml", new String[]{bundle});
//    }
  }

  public void exportAsZip() {
    PaneContext context = getActiveContext();
    SikulixFileChooser chooser = new SikulixFileChooser(SikulixIDE.get());
    File file = chooser.export();
    if (file == null) {
      return;
    }
    if (!context.save()) {
      return;
    }
    String zipPath = file.getAbsolutePath();
    if (context.isBundle()) {
      if (!file.getAbsolutePath().endsWith(".skl")) {
        zipPath += ".skl";
      }
    } else {
      if (!file.getAbsolutePath().endsWith(".zip")) {
        zipPath += ".zip";
      }
    }
    File zipFile = new File(zipPath);
    if (zipFile.exists()) {
      int answer = JOptionPane.showConfirmDialog(
              null, SikuliIDEI18N._I("msgFileExists", zipFile),
              SikuliIDEI18N._I("dlgFileExists"), JOptionPane.YES_NO_OPTION);
      if (answer != JOptionPane.YES_OPTION) {
        return;
      }
      FileManager.deleteFileOrFolder(zipFile);
    }
    try {
      zipDir(context.getFolder(), zipFile, context.getFileName());
      log("Exported packed SikuliX Script to: %s", zipFile);
    } catch (Exception ex) {
      log("ERROR: Export did not work: %s", zipFile); //TODO
    }
  }

  // #393: single implementation for the .jar export, serving both the sidebar
  // and the JMenuBar FileAction. The JMenuBar has been invisible since the
  // sidebar migration (Phase 1b) — so this feature spent its revival fully
  // implemented, correctly wired, and reachable by absolutely no one.
  // It works better with users.
  public void exportAsJar() {
    PaneContext context = getActiveContext();
    // makeScriptjar packs a .sikuli bundle — a never-saved temp script has no
    // bundle to pack. (The legacy path trusted EditorPane.editorPaneFile, a
    // field declared, initialized to null, and then left alone since the
    // PaneContext refactor. It never hurt anyone. It also never worked.)
    if (context == null || context.isTemp() || !context.isBundle()) {
      SX.popError("Please save your script as a .sikuli bundle first!", "Export as jar");
      return;
    }
    if (!context.save()) {
      SX.popError("Saving the script failed.", "Export as jar");
      return;
    }
    String orgName = context.getFileName();
    log("exportAsJar requested: %s", orgName);
    List<String> options = new ArrayList<>();
    options.add(context.getFolder().getAbsolutePath());
    String fpJar = FileManager.makeScriptjar(options);
    if (null != fpJar) {
      SX.popup(fpJar, "Export as jar ...");
    } else {
      SX.popError("did not work for: " + orgName, "Export as jar");
    }
  }

  private static void zipDir(File zipDir, File zipFile, String fScript) throws IOException {
    ZipOutputStream zos = null;
    try {
      zos = new ZipOutputStream(new FileOutputStream(zipFile));
      String[] dirList = zipDir.list();
      byte[] readBuffer = new byte[1024];
      int bytesIn;
      ZipEntry anEntry;
      String ending = fScript.substring(fScript.length() - 3);
      String sName = zipFile.getName();
      sName = sName.substring(0, sName.length() - 4) + ending;
      for (int i = 0; i < dirList.length; i++) {
        File f = new File(zipDir, dirList[i]);
        if (f.isFile()) {
          if (fScript.equals(f.getName())) {
            anEntry = new ZipEntry(sName);
          } else {
            anEntry = new ZipEntry(f.getName());
          }
          FileInputStream fis = new FileInputStream(f);
          zos.putNextEntry(anEntry);
          while ((bytesIn = fis.read(readBuffer)) != -1) {
            zos.write(readBuffer, 0, bytesIn);
          }
          fis.close();
        }
      }
    } catch (Exception ex) {
      String msg = "";
      msg = ex.getMessage() + "";
    } finally {
      zos.close();
    }
  }

  Map<String, List<Integer>> parseforImages() {
    File imageFolder = getActiveContext().getImageFolder();
    trace("parseforImages: in %s", imageFolder);
    String scriptText = getActiveContext().getPane().getText();
    Lexer lexer = getLexer();
    Map<String, List<Integer>> images = new HashMap<>();
    // getLexer() returns null when the syntaxhighlight grammar resource for
    // "python" can't be resolved on the classpath (happens at least on the
    // Modern Recorder rename flow before the lexers map is populated).
    // Without this guard, the next call walks the AST with a null Lexer and
    // throws NPE inside parseforImagesWalk -> the rename action looks
    // crashed even though the file was already moved on disk and the button
    // has been re-serialized with the new name. Bail out early — the empty
    // map is a no-op for the caller (reparseOnRenameImage) and the rename
    // succeeds visually anyway via the IButton.TEXT update done upstream.
    if (lexer == null) {
      log("parseforImages: lexer unavailable for python — skipping text reparse");
      return images;
    }
    lineNumber = 0;
    parseforImagesWalk(imageFolder, lexer, scriptText, 0, images);
    trace("parseforImages finished");
    return images;
  }

  private void parseforImagesWalk(File imageFolder, Lexer lexer,
                                  String text, int pos, Map<String, List<Integer>> images) {
    trace("parseforImagesWalk");
    Iterable<Token> tokens = lexer.getTokens(text);
    boolean inString = false;
    String current;
    String innerText;
    String[] possibleImage = new String[]{""};
    String[] stringType = new String[]{""};
    for (Token t : tokens) {
      current = t.getValue();
      if (current.endsWith("\n")) {
        if (inString) {
          SX.popError(
                  String.format("Orphan string delimiter (\" or ')\n" +
                          "in line %d\n" +
                          "No images will be deleted!\n" +
                          "Correct the problem before next save!", lineNumber),
                  "Delete images on save");
          error("DeleteImagesOnSave: No images deleted, caused by orphan string delimiter (\" or ') in line %d", lineNumber);
          images.clear();
          images.put(uncompleteStringError, null);
          break;
        }
        lineNumber++;
      }
      if (t.getType() == TokenType.Comment) {
        trace("parseforImagesWalk::Comment");
        innerText = t.getValue().substring(1);
        parseforImagesWalk(imageFolder, lexer, innerText, t.getPos() + 1, images);
        continue;
      }
      if (t.getType() == TokenType.String_Doc) {
        trace("parseforImagesWalk::String_Doc");
        innerText = t.getValue().substring(3, t.getValue().length() - 3);
        parseforImagesWalk(imageFolder, lexer, innerText, t.getPos() + 3, images);
        continue;
      }
      if (!inString) {
        inString = parseforImagesGetName(current, inString, possibleImage, stringType);
        continue;
      }
      if (!parseforImagesGetName(current, inString, possibleImage, stringType)) {
        inString = false;
        parseforImagesCollect(imageFolder, possibleImage[0], pos + t.getPos(), images);
        continue;
      }
    }
  }

  private boolean parseforImagesGetName(String current, boolean inString,
                                        String[] possibleImage, String[] stringType) {
    trace("parseforImagesGetName (inString: %s) %s", inString, current);
    if (!inString) {
      if (!current.isEmpty() && (current.contains("\"") || current.contains("'"))) {
        possibleImage[0] = "";
        stringType[0] = current.substring(current.length() - 1, current.length());
        return true;
      }
    }
    if (!current.isEmpty() && "'\"".contains(current) && stringType[0].equals(current)) {
      return false;
    }
    if (inString) {
      possibleImage[0] += current;
    }
    return inString;
  }

  private void parseforImagesCollect(File imageFolder, String img, int pos,
                                     Map<String, List<Integer>> images) {
    trace("parseforImagesCollect");
    if (img.endsWith(".png") || img.endsWith(".jpg") || img.endsWith(".jpeg")) {
      if (img.contains(File.separator)) {
        if (!img.contains(imageFolder.getPath())) {
          return;
        }
        img = new File(img).getName();
      }
      if (images.containsKey(img)) {
        images.get(img).add(pos);
      } else {
        List<Integer> poss = new ArrayList<>();
        poss.add(pos);
        images.put(img, poss);
      }
    }
  }

  public void reparseOnRenameImage(String oldName, String newName, boolean fileOverWritten) {
    if (fileOverWritten) {
      Image.unCache(newName);
    }
    Map<String, List<Integer>> images = parseforImages();
    oldName = new File(oldName).getName();
    List<Integer> poss = images.get(oldName);
    if (images.containsKey(oldName) && poss.size() > 0) {
      Collections.sort(poss, new Comparator<Integer>() {
        @Override
        public int compare(Integer o1, Integer o2) {
          if (o1 > o2) return -1;
          return 1;
        }
      });
      reparseRenameImages(poss, oldName, new File(newName).getName());
    }
    //TODO doReparse();
  }

  private boolean reparseRenameImages(List<Integer> poss, String oldName, String newName) {
    StringBuilder text = new StringBuilder(getActiveContext().getPane().getText());
    int lenOld = oldName.length();
    for (int pos : poss) {
      text.replace(pos - lenOld, pos, newName);
    }
    getActiveContext().getPane().setText(text.toString());
    return true;
  }

  private Lexer getLexer() {
//TODO this only works for cleanbundle to find the image strings
    String scriptType = "python";
    if (null != lexers.get(scriptType)) {
      return lexers.get(scriptType);
    }
    try {
      Lexer lexer = Lexer.getByName(scriptType);
      lexers.put(scriptType, lexer);
      return lexer;
    } catch (ResolutionException ex) {
      return null;
    }
  }

  private static final Map<String, Lexer> lexers = new HashMap<>();

  int lineNumber = 0;
  public String uncompleteStringError = "uncomplete_string_error";

  public String getImageNameFromLine() {
    String line = getLineTextAtCaret().strip();
    Pattern aName = Pattern.compile("^([A-Za-z0-9_]+).*?=");
    Matcher matcher = aName.matcher(line);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return "";
  }

  String getLineTextAtCaret() {
    // getActiveContext() is null whenever no script tab is selected — the Welcome
    // tab, or all tabs closed. Callers ask this to guess a name from the current
    // line, so "no line" is a perfectly good answer; throwing here previously left
    // the capture path dead with the IDE hidden.
    PaneContext context = getActiveContext();
    if (context == null) {
      return "";
    }
    return context.getPane().getLineTextAtCaret();
  }

  void zzzzzzz() {
  }
//</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="04 save / restore session">
  private boolean saveSession(int saveAction) {
    StringBuilder sbuf = new StringBuilder();
    if (contexts.isEmpty()) {
      PreferencesUser.get().setIdeSession("");
      return true;
    }
    for (PaneContext context : contexts.toArray(new PaneContext[0])) {
      if (context == null) continue;
      if (saveAction == DO_SAVE_ALL) {
        if (!context.close()) {
          return false;
        }
      }
      if (context.isTemp()) {
        continue;
      }
      if (sbuf.length() > 0) {
        sbuf.append(";");
      }
      sbuf.append(context.getFile());
    }
    PreferencesUser.get().setIdeSession(sbuf.toString());
    return true;
  }

  private static boolean shouldExecuteOnStart = false;

  private List<File> restoreSession() {
    String session_str = prefs.getIdeSession();
    List<File> filesToLoad = new ArrayList<>();
//TODO IDEDesktopSupport.filesToOpen
//    if (IDEDesktopSupport.filesToOpen != null && IDEDesktopSupport.filesToOpen.size() > 0) {
//      for (File f : IDEDesktopSupport.filesToOpen) {
//        filesToLoad.add(f);
//      }
//    }
    if (session_str != null && !session_str.isEmpty()) {
      String[] filenames = session_str.split(";");
      if (filenames.length > 0) {
        for (String filename : filenames) {
          if (filename.isEmpty()) {
            continue;
          }
          filesToLoad.add(new File(filename));
        }
      }
    }
    String[] parsedValues = Sikulix.scriptsPreloadParsedValues;
    String[] loadScripts = Sikulix.scriptsPreloaded;
    // Diagnostic — surfaces exactly what the CLI parser saw, so a tester
    // hitting "-e doesn't auto-run" on Windows can paste the message panel
    // and we know whether -l grabbed -e by mistake (the historical
    // hasArgs-unlimited bug, fixed in CommandArgs.makeOption).
    // Routed through Commons.startLog at level 3 (always-print, gates only
    // sub-3 levels) so the line shows in the IDE message panel without the
    // tester needing -v.
    Commons.startLog(3, "CLI -l parsed values: %s",
        loadScripts == null ? "null" : java.util.Arrays.toString(parsedValues));
    Commons.startLog(3, "CLI -e present: %s", Sikulix.shouldExecuteOnStart);
    if (loadScripts != null && loadScripts.length > 0) {
      Commons.startLog(3,"Preload given scripts (-l)");
      for (String loadScript : loadScripts) {
        if (loadScript == null || loadScript.isEmpty()) {
          continue;
        }
        if (loadScript.startsWith("!")) {
          Commons.startLog(3,"Preload: folder: base for next -l: %s", loadScript.substring(1));
          continue;
        }
        File f = new File(loadScript);
        if (!f.exists()) {
          Commons.startLog(3,"Preload: file does not exist: %s", loadScript);
          continue;
        }
        if (Sikulix.shouldExecuteOnStart) {
          Sikulix.scriptToStart = f;
        }
//TODO already loaded: solution? currently: remove and add
        if (filesToLoad.remove(f)) {
          Commons.startLog(3,"Preload: file already loaded (moved to end of list): %s", loadScript);
        } else {
          Commons.startLog(3,"Preload: %s", f);
        }
        filesToLoad.add(f);
      }
    }
    if (Sikulix.shouldExecuteOnStart) {
      // Validate -e directly against the CLI args, NOT against the
      // preloadedFromCli counter that the loop above maintains. The
      // counter is incremented only AFTER the duplicate check skips
      // already-loaded files — so when the IDE's session restore had
      // the same file open from a previous run, the -l file was a
      // duplicate, the increment was skipped, preloadedFromCli stayed
      // at 0, and -e silently dropped with "no effect without -l"
      // even though the user explicitly asked for it on the command
      // line. (#224 reported by @julienmerconsulting on Windows.)
      // The user contract is "-e runs the -l file regardless of
      // whether it was already open" — so the gating predicate is
      // purely on the CLI args being well-formed and pointing at an
      // existing file.
      /*String[] eArgs = Sikulix.scriptsPreloaded;
      if (eArgs != null && eArgs.length == 1 && new File(eArgs[0]).exists()) {
        Sikulix.shouldExecuteOnStart = true;
      } else if (eArgs == null || eArgs.length == 0) {
        log("-e (--execute) has no effect without a valid -l file; ignoring");
      } else if (eArgs.length > 1) {
        log("-e (--execute) requires exactly one -l file but got %d; ignoring -e", eArgs.length);
      } else {
        log("-e (--execute): -l file does not exist on disk: %s; ignoring", eArgs[0]);
      }
*/
      if (Sikulix.scriptToStart == null) {
        Sikulix.shouldExecuteOnStart = false;
        Commons.startLog(3,"-e (--execute) has no effect without a valid -l file; ignoring");
      } else {
        Commons.startLog(3,"-e (--execute) candidate to execute: %s", Sikulix.scriptToStart);
      }
    }
    if (filesToLoad.size() > 0) {
      for (File file : filesToLoad) {
        createFileContext(file);
      }
      // Close the initial empty script if it was created before session restore
      if (contexts.size() > filesToLoad.size()) {
        PaneContext firstCtx = getContextAt(0);
        if (firstCtx != null) {
          firstCtx.closeSilent();
        }
      }
      tempIndex = 1;
    }
    return filesToLoad;
  }
//</editor-fold>

  //<editor-fold desc="07 menu helpers">
  public void showAbout() {
    new SXDialog("sxideabout", SikulixIDE.getWindowTop(), SXDialog.POSITION.TOP).run();
  }

  public boolean quit() {
    return terminate();
  }

  // ── Workspace management ──

  private File currentWorkspaceDir = null;
  private String currentWorkspaceName = "Workspace";

  private void openNewWorkspaceDialog() {
    File dir = WorkspaceDialog.showDialog(ideWindow);
    if (dir != null) {
      loadWorkspace(dir);
    }
  }

  private void openExistingWorkspace() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle(_I("workspaceOpenDialogTitle"));
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setAcceptAllFileFilterUsed(false);
    // Start in current workspace's parent (so user can pick a sibling)
    // or the shared default-dir resolver (LAST_OPEN_DIR pref → user.dir →
    // user.home). The previous code only acted on a non-empty pref and
    // otherwise let JFileChooser fall back to OS default.
    if (currentWorkspaceDir != null && currentWorkspaceDir.getParentFile() != null) {
      chooser.setCurrentDirectory(currentWorkspaceDir.getParentFile());
    } else {
      chooser.setCurrentDirectory(org.sikuli.util.SikulixFileChooser.resolveDefaultDir());
    }
    int result = chooser.showOpenDialog(ideWindow);
    if (result == JFileChooser.APPROVE_OPTION) {
      File picked = chooser.getSelectedFile();
      // Shared across every chooser in the IDE — see SikulixFileChooser.persistLastDir.
      org.sikuli.util.SikulixFileChooser.persistLastDir(picked);
      loadWorkspace(picked);
    }
  }

  private void loadWorkspace(File dir) {
    currentWorkspaceDir = dir;

    // Try to read workspace.json for the name
    File wsJson = new File(dir, "workspace.json");
    if (wsJson.exists()) {
      try {
        String content = org.sikuli.support.FileManager.readFileToString(wsJson);
        // Simple JSON name extraction
        int nameIdx = content.indexOf("\"name\"");
        if (nameIdx > 0) {
          int valueStart = content.indexOf("\"", nameIdx + 7) + 1;
          int valueEnd = content.indexOf("\"", valueStart);
          currentWorkspaceName = content.substring(valueStart, valueEnd);
        }
      } catch (Exception e) {
        currentWorkspaceName = dir.getName();
      }
    } else {
      currentWorkspaceName = dir.getName();
    }

    // Update explorer header
    if (explorer != null) {
      explorer.setWorkspaceName(currentWorkspaceName);
    }

    // Discover scripts in the workspace directory. Three layouts supported:
    //   1. dir/foo.sikuli/foo.py        — classic SikuliX bundle
    //   2. dir/foo/foo.py               — plain folder bundle (modern OculiX
    //      convention since the recorder writes there)
    //   3. dir/foo.py                   — single script at the root (the
    //      user opened a single-script bundle as a "workspace", common
    //      mistake but still useful UX-wise)
    int loaded = 0;
    File[] children = dir.listFiles();
    if (children != null) {
      for (File child : children) {
        try {
          if (child.isDirectory()) {
            // Either a .sikuli bundle or a plain folder with a .py inside.
            File[] pyFiles = child.listFiles((d, name) -> name.endsWith(".py"));
            if (pyFiles != null && pyFiles.length > 0) {
              createFileContext(child);
              loaded++;
            } else {
              log("Workspace: skipping folder %s (no .py)", child.getName());
            }
          } else if (child.getName().endsWith(".py")) {
            // Single-script case (3): open the .py directly.
            createFileContext(child);
            loaded++;
          }
        } catch (Exception e) {
          log("Workspace: error loading %s: %s", child.getName(), e.getMessage());
        }
      }
    }
    log("Workspace: %d script(s) loaded from %s", loaded, dir.getAbsolutePath());

    refreshWorkspace();

    log("Workspace loaded: %s (%s)", currentWorkspaceName, dir.getAbsolutePath());
  }

  public File getCurrentWorkspaceDir() {
    return currentWorkspaceDir;
  }

  public String getCurrentWorkspaceName() {
    return currentWorkspaceName;
  }

  public void showPreferencesWindow() {
    PreferencesWin pwin = new PreferencesWin();
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
    JMenuItem item = new JMenuItem(name);
    return createMenuItem(item, shortcut, listener);
  }

  JMenuItem createMenuItem(String name, Icon icon, KeyStroke shortcut, ActionListener listener) {
    JMenuItem item = createMenuItem(name, shortcut, listener);
    if (icon != null) item.setIcon(icon);
    return item;
  }

  /** Creates a disabled menu item used as a section header label. */
  JMenuItem createSectionLabel(String text) {
    JMenuItem label = new JMenuItem(text);
    label.setEnabled(false);
    label.setFont(UIManager.getFont("defaultFont").deriveFont(Font.BOLD, 11f));
    return label;
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

    // ── Nouveau ──
    _fileMenu.add(createSectionLabel(_I("menuFileNew")));
    _fileMenu.add(createMenuItem("Script",
            new FlatSVGIcon("icons/menu/page.svg", 16, 16),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, scMask),
            new FileAction(FileAction.NEW)));
    _fileMenu.add(createMenuItem("Workspace",
            new FlatSVGIcon("icons/sidebar/folder.svg", 16, 16),
            null,
            e -> openNewWorkspaceDialog()));

    // ── Ouvrir ──
    _fileMenu.add(createSectionLabel(_I("menuFileOpen")));
    _fileMenu.add(createMenuItem("Script",
            new FlatSVGIcon("icons/menu/page.svg", 16, 16),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, scMask),
            new FileAction(FileAction.OPEN)));
    _fileMenu.add(createMenuItem("Workspace",
            new FlatSVGIcon("icons/sidebar/folder.svg", 16, 16),
            null,
            e -> openExistingWorkspace()));

    recentMenu = new JMenu(_I("menuRecent"));
    _fileMenu.add(recentMenu);

    _fileMenu.addSeparator();

    // ── Enregistrer ──
    jmi = _fileMenu.add(createMenuItem(_I("menuFileSave"),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, scMask),
            new FileAction(FileAction.SAVE)));
    jmi.setName("SAVE");

    jmi = _fileMenu.add(createMenuItem(_I("menuFileSaveAs"),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S,
                    InputEvent.SHIFT_DOWN_MASK | scMask),
            new FileAction(FileAction.SAVE_AS)));
    jmi.setName("SAVE_AS");

    JMenu exportSubMenu = new JMenu("Export");
    exportSubMenu.add(createMenuItem("As .zip (workspace)",
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_E,
                    InputEvent.SHIFT_DOWN_MASK | scMask),
            new FileAction(FileAction.EXPORT)));
    exportSubMenu.add(createMenuItem("As .jar (script library)",
            null,
            new FileAction(FileAction.ASJAR)));
    _fileMenu.add(exportSubMenu);

    _fileMenu.addSeparator();

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
      exportAsJar();
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
    _editMenu.add(createMenuItem(_I("menuEditCopyLine"),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, scMask | InputEvent.SHIFT_DOWN_MASK),
            new EditAction(EditAction.COPY)));
    _editMenu.add(createMenuItem(_I("menuEditCut"),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, scMask),
            new EditAction(EditAction.CUT)));
    _editMenu.add(createMenuItem(_I("menuEditCutLine"),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, scMask | InputEvent.SHIFT_DOWN_MASK),
            new EditAction(EditAction.CUT)));
    _editMenu.add(createMenuItem(_I("menuEditPaste"),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, scMask),
            new EditAction(EditAction.PASTE)));
    _editMenu.add(createMenuItem(_I("menuEditSelectAll"),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A, scMask),
            new EditAction(EditAction.SELECT_ALL)));

    _editMenu.addSeparator();
    JMenu findMenu = new JMenu(_I("menuFind"));
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
      findText = SX.input(
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
      if ("!".equals(str)) {
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
      btnRun.runCurrentScript();
    }

    public void runShowActions(ActionEvent ae) {
      btnRunSlow.runCurrentScript();
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
//      SX.popError("Package adb seems not to be available.\nIt must be installed for Android support.", title);
//    } else {
//      SX.popError("No android device attached", title);
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

    _helpMenu.addSeparator();
    _helpMenu.add(createMenuItem(_I("menuHelpHomepage"),
            null, new HelpAction(HelpAction.OPEN_HOMEPAGE)));
    _helpMenu.addSeparator();
    _helpMenu.add(createMenuItem(_I("menuHelpCheckUpdate"),
            null, new HelpAction(HelpAction.CHECK_UPDATE)));
  }

  private void lookUpdate() {
    newBuildAvailable = null;
    try {
      String apiUrl = "https://api.github.com/repos/oculix-org/Oculix/releases/latest";
      String response = FileManager.downloadURLtoString(apiUrl);
      if (response != null && !response.isEmpty()) {
        // Parse tag_name from JSON response (simple extraction)
        int tagIdx = response.indexOf("\"tag_name\"");
        if (tagIdx > 0) {
          int valueStart = response.indexOf("\"", tagIdx + 11) + 1;
          int valueEnd = response.indexOf("\"", valueStart);
          String latestTag = response.substring(valueStart, valueEnd);
          // Remove leading 'v' if present
          String latestVersion = latestTag.startsWith("v") ? latestTag.substring(1) : latestTag;
          String currentVersion = Commons.getSXVersionShort();
          log("Check update: latest=%s current=%s", latestVersion, currentVersion);
          if (!latestVersion.equals(currentVersion)) {
            newBuildAvailable = true;
            newBuildStamp = latestVersion;
          } else {
            newBuildAvailable = false;
          }
        }
      }
    } catch (Exception e) {
      log("Check update failed: %s", e.getMessage());
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
      String msg = "If you want to ask a question about SikuliX\n%s\n"
              + "\nplease do the following:"
              + "\n- after having clicked yes"
              + "\n   the page on Launchpad should open in your browser."
              + "\n- You should first check using Launchpad's search funktion,"
              + "\n   wether similar questions have already been asked."
              + "\n- If you decide to ask a new question,"
              + "\n   try to enter a short but speaking title"
              + "\n- In a new questions's text field first paste using ctrl/cmd-v"
              + "\n   which should enter the SikuliX version/system/java info"
              + "\n   that was internally stored in the clipboard before"
              + "\n\nIf you do not want to ask a question now: click No";
      askBugOrAnswer(msg, title, "https://answers.launchpad.net/sikuli");
    }

    public void openBugReport(ActionEvent ae) {
      String title = "SikuliX - Report a bug";
      String msg = "If you want to report a bug for SikuliX\n%s\n"
              + "\nplease do the following:"
              + "\n- after having clicked yes"
              + "\n   the page on Launchpad should open in your browser"
              + "\n- fill in a short but speaking bug title and create the bug"
              + "\n- in the bug's text field first paste using ctrl/cmd-v"
              + "\n   which should enter the SikuliX version/system/java info"
              + "\n   that was internally stored in the clipboard before"
              + "\n\nIf you do not want to report a bug now: click No";
      askBugOrAnswer(msg, title, "https://bugs.launchpad.net/sikuli/+filebug");
    }

    private void askBugOrAnswer(String msg, String title, String url) {
      String si = Commons.getSystemInfo();
      System.out.println(si);
      msg = String.format(msg, si);
      if (SX.popAsk(msg, title)) {
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
      AutoUpdater au = new AutoUpdater();
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

  //<editor-fold defaultstate="collapsed" desc="20 Init ToolBar Buttons">
  private ButtonCapture btnCapture;
  private ButtonRun btnRun;
  private ButtonRunViz btnRunSlow;
  private ButtonRecord btnRecord;
  // Promoted from a local in initToolbar() so the sidebar Tools submenu can
  // re-trigger the same action — see buildToolsSubmenu(). The new sidebar UI
  // doesn't render the legacy top toolbar, so without this exposure the
  // Insert Image action becomes unreachable from the user's IDE chrome.
  private ButtonInsertImage btnInsertImage;

  private JToolBar initToolbar() {
    JToolBar toolbar = new JToolBar();
    btnInsertImage = new ButtonInsertImage();
    JButton btnSubregion = new ButtonSubregion();
    JButton btnLocation = new ButtonLocation();
    JButton btnOffset = new ButtonOffset();
//TODO ButtonShow/ButtonShowIn
    JButton btnShow = new ButtonShow();
    JButton btnShowIn = new ButtonShowIn();

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
    btnRun = new ButtonRun();
    toolbar.add(btnRun);
    btnRunSlow = new ButtonRunViz();
    toolbar.add(btnRunSlow);
    toolbar.add(Box.createHorizontalGlue());

    toolbar.add(Box.createRigidArea(new Dimension(7, 0)));
    toolbar.setFloatable(false);

    btnRecord = new ButtonRecord();
    toolbar.add(btnRecord);

//    JComponent jcSearchField = createSearchField();
//    toolbar.add(jcSearchField);

    return toolbar;
  }

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
      final String name = sikulixIDE.getImageNameFromLine();
      final PaneContext context = getActiveContext();
      EditorPane codePane = context.getPane();
      File file = new SikulixFileChooser(ideWindow).loadImage();
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
          SX.popError(msg, "IDE: Insert Image");
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
      EditorPane codePane = getCurrentCodePane();
      if (selectedRectangle != null) {
        Rectangle roi = selectedRectangle;
        x = (int) roi.getX();
        y = (int) roi.getY();
        w = (int) roi.getWidth();
        h = (int) roi.getHeight();
        if (codePane.context.getShowThumbs()) {
          if (prefs.getPrefMoreImageThumbs()) { //TODO
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
        getCurrentCodePane().insertString(String.format("Location(%d, %d)", x, y));
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
        getCurrentCodePane().insertString(String.format("Offset(%d, %d)", ox, oy));
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
      EditorPane codePane = getActiveContext().getPane();
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
      SX.popup("ButtonShow: Nothing to show!" +
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
      EditorPane codePane = getCurrentCodePane();
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
        Commons.pause(2.0f);
      } else {
        SikulixIDE.showAgain();
        SX.popup("ButtonShowIn: Nothing to show!" +
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
      setText(_I("btnRecordLabel"));
      // setMaximumSize(new Dimension(45,45));
    }

    private void initTooltip() {
      PreferencesUser pref = PreferencesUser.get();
      String strHotkey = Key.convertKeyToText(pref.getStopHotkey(), pref.getStopHotkeyModifiers());
      String stopHint = _I("btnRecordStopHint", strHotkey);
      setToolTipText(_I("btnRecord", stopHint));
    }

    /** Re-render the tooltip after the stop hotkey has been rebound. */
    void refreshTooltip() {
      initTooltip();
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
      ideWindow.setVisible(false);
      try {
        recorder.start();
      } catch (SikuliXception e) {
        // Recorder failed to start — typically JNativeHook's native lib
        // could not load (missing libxkbcommon-x11-0 etc. on Linux). Bring
        // the IDE back so the user is not stranded with a hidden window,
        // and surface the actionable message from the exception body so
        // they know exactly which apt install to run.
        SikulixIDE.showAgain();
        JOptionPane.showMessageDialog(
            SikulixIDE.this,
            e.getMessage(),
            _I("btnRecordLabel"),
            JOptionPane.ERROR_MESSAGE);
      }
    }

    public void stopRecord() {
      SikulixIDE.showAgain();

      if (isRunning()) {
        PaneContext context = getActiveContext();
        EditorPane pane = context.getPane();
        ICodeGenerator generator = pane.getCodeGenerator();

        ProgressMonitor progress = new ProgressMonitor(pane, _I("processingWorkflow"), "", 0, 0);
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
//</editor-fold>

  //<editor-fold desc="21 Init Run Buttons">
  class ButtonRun extends ButtonOnToolbar {

    private Thread thread = null;

    ButtonRun() {
      super();

      URL imageURL = SikulixIDE.class.getResource("/icons/run_big_green.png");
      setIcon(new ImageIcon(imageURL));
      initTooltip();
      addActionListener(this);
      setText(_I("btnRunLabel"));
      //setMaximumSize(new Dimension(45,45));
    }

    private void initTooltip() {
      PreferencesUser pref = PreferencesUser.get();
      String strHotkey = Key.convertKeyToText(
              pref.getStopHotkey(), pref.getStopHotkeyModifiers());
      String stopHint = _I("btnRunStopHint", strHotkey);
      setToolTipText(_I("btnRun", stopHint));
    }

    /** Re-render the tooltip after the stop hotkey has been rebound. */
    void refreshTooltip() {
      initTooltip();
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
      runCurrentScript();
    }

    void runCurrentScript() {
      log("************** before RunScript"); //TODO
      //doBeforeQuitOrRun();
      Debug.quietOff();

      SikulixIDE.getStatusbar().resetMessage();
      SikulixIDE.doHide();

      final PaneContext context = getActiveContext();
      EditorPane editorPane = context.getPane();
      if (editorPane.getDocument().getLength() == 0) {
        log("Run script not possible: Script is empty");
        return;
      }
      String contextName = context.getName();
      context.save();

      new Thread(new Runnable() {
        @Override
        public void run() {
          if (System.out.checkError()) {
            boolean shouldContinue = SX.popAsk("System.out is broken (console output)!"
                    + "\nYou will not see any messages anymore!"
                    + "\nSave your work and restart the IDE!"
                    + "\nYou may ignore this on your own risk!" +
                    "\nYes: continue  ---  No: back to IDE", "Fatal Error");
            if (!shouldContinue) {
              log("Run script aborted: System.out is broken (console output)");
              SikulixIDE.showAgain();
              return;
            }
            log("Run script continued, though System.out is broken (console output)");
          }

          Commons.pause(0.1f);
          resetErrorMark();
          String runStamp = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
          doBeforeRun();
          Debug.info(String.format("-------- Run started @ %s ---- %s ----", runStamp, contextName));

          long runStart = System.currentTimeMillis();

          IRunner.Options runOptions = new IRunner.Options();
          runOptions.setRunningInIDE();

          int exitValue = -1;
          try {
            IRunner runner = context.getRunner();
            //TODO make reloadImported specific for each editor tab
            if (runner.getType().equals(JythonRunner.TYPE)) {
              JythonSupport.get().reloadImported();
            }
            exitValue = runner.runScript(context.getFile().getAbsolutePath(), Sikulix.getUserArgs(), runOptions);
          } catch (Exception e) {
            log("Run Script: internal error:");
            e.printStackTrace();
          } finally {
            Runner.setLastScriptRunReturnCode(exitValue);
          }

          // Update sidebar last run info
          final int finalExit = exitValue;
          final long duration = System.currentTimeMillis() - runStart;
          EventQueue.invokeLater(() -> {
            if (sidebar != null) {
              sidebar.updateLastRun(finalExit, duration);
            }
          });

          log("************** after RunScript");
          addErrorMark(runOptions.getErrorLine());
          if (Image.getIDEshouldReload()) {
            int line = context.getPane().getLineNumberAtCaret(context.getPane().getCaretPosition());
            context.reparse();
            context.getPane().jumpTo(line);
          }

          Commons.cleanUpAfterScript();
          SikulixIDE.showAgain();
        }
      }).start();
    }

    void doBeforeRun() {
      Settings.ActionLogs = prefs.getPrefMoreLogActions();
      Settings.DebugLogs = prefs.getPrefMoreLogDebug();
      Settings.InfoLogs = prefs.getPrefMoreLogInfo();
      Settings.Highlight = prefs.getPrefMoreHighlight();
    }
  }

  class ButtonRunViz extends ButtonRun {

    ButtonRunViz() {
      super();
      URL imageURL = SikulixIDE.class.getResource("/icons/run_big_yl.png");
      setIcon(new ImageIcon(imageURL));
      setToolTipText(_I("menuRunRunAndShowActions"));
      setText(_I("btnRunSlowMotionLabel"));
    }

    @Override
    protected void doBeforeRun() {
      super.doBeforeRun();
      Settings.setShowActions(true);
    }

  }

  void addErrorMark(int line) {
    if (line < 1) {
      return;
    }
    JScrollPane scrPane = (JScrollPane) tabs.getSelectedComponent();
    EditorLineNumberView lnview = (EditorLineNumberView) (scrPane.getRowHeader().getView());
    lnview.addErrorMark(line);
    EditorPane codePane = SikulixIDE.this.getCurrentCodePane();
    codePane.jumpTo(line);
    codePane.requestFocus();
  }

  void resetErrorMark() {
    JScrollPane scrPane = (JScrollPane) tabs.getSelectedComponent();
    EditorLineNumberView lnview = (EditorLineNumberView) (scrPane.getRowHeader().getView());
    lnview.resetErrorMark();
  }
  //</editor-fold>

  //<editor-fold desc="30 MsgArea">
  private JTabbedPane messageArea = null;
  private EditorConsolePane messages = null;

  private boolean SHOULD_WRAP_LINE = false;

  private void initMessageArea() {
    messages.init(SHOULD_WRAP_LINE);
    messageArea = new JTabbedPane();
    messageArea.addTab(_I("paneMessage"), null, messages, "DoubleClick to hide/unhide");
    if (Settings.isWindows() || Settings.isLinux()) {
      messageArea.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
    }

    // Branded tab title: JetBrains Mono 10, 0.18em letter-spacing, uppercase.
    // Color comes from UIManager so it follows the active LaF.
    JLabel tabTitle = new JLabel(_I("paneMessage").toUpperCase(java.util.Locale.ROOT));
    java.util.Map<java.awt.font.TextAttribute, Object> attrs = new java.util.HashMap<>();
    attrs.put(java.awt.font.TextAttribute.TRACKING, 0.18f);
    tabTitle.setFont(new Font("JetBrains Mono", Font.BOLD, 10).deriveFont(attrs));
    tabTitle.setForeground(UIManager.getColor("Label.disabledForeground"));
    tabTitle.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
    messageArea.setTabComponentAt(0, tabTitle);
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

  private boolean messageAreaCollapsed = false;

  public EditorConsolePane getConsole() {
    return messages;
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="25 Init ShortCuts HotKeys">
  void removeCaptureHotkey() {
    HotkeyManager.getInstance().removeHotkey("Capture");
  }

  /**
   * @return true if the OS accepted the registration
   */
  boolean installCaptureHotkey() {
    PreferencesUser pref = PreferencesUser.get();
    boolean registered = true;
    if (pref.getCaptureHotkeyEnabled()) {
      registered = HotkeyManager.getInstance().addHotkey("Capture", new HotkeyListener() {
        @Override
        public void hotkeyPressed(HotkeyEvent e) {
          if (sikulixIDE.isVisible()) {
            btnCapture.capture(0);
          }
        }
      });
      if (!registered) {
        warnHotkeyNotRegistered("prefCaptureHotkey",
            pref.getCaptureHotkey(), pref.getCaptureHotkeyModifiers());
      }
    } else {
      Debug.log(3, "IDE: capture hotkey is disabled by preference — not registering");
    }
    // Null at first registration: this runs from startGUI() before the toolbar is
    // built, and ButtonCapture reads the binding in its own constructor anyway.
    // Only a later rebind from Preferences needs the refresh.
    if (btnCapture != null) {
      btnCapture.refreshTooltip();
    }
    return registered;
  }

  void removeStopHotkey() {
    HotkeyManager.getInstance().removeHotkey("Abort");
  }

  /**
   * Registers the global stop/abort hotkey, unless the user has switched it off.
   *
   * @return true if the hotkey is not wanted, or if the OS accepted it; false if
   *         registration was refused (typically another application already owns
   *         the combination)
   */
  boolean installStopHotkey() {
    PreferencesUser pref = PreferencesUser.get();
    boolean registered = true;
    if (pref.getStopHotkeyEnabled()) {
      registered = HotkeyManager.getInstance().addHotkey("Abort", new HotkeyListener() {
        @Override
        public void hotkeyPressed(HotkeyEvent e) {
          onStopRunning();
        }
      });
      if (!registered) {
        warnHotkeyNotRegistered("prefStopHotkey", pref.getStopHotkey(), pref.getStopHotkeyModifiers());
      }
    } else {
      Debug.log(3, "IDE: stop hotkey is disabled by preference — not registering");
    }
    refreshStopHotkeyTooltips();
    return registered;
  }

  /**
   * The stop hotkey is baked into the Run and Record button tooltips, so it has to
   * be re-rendered whenever the binding changes or the IDE would keep advertising
   * the old combination until restart.
   */
  void refreshStopHotkeyTooltips() {
    if (btnRun != null) {
      btnRun.refreshTooltip();
    }
    if (btnRecord != null) {
      btnRecord.refreshTooltip();
    }
  }

  /**
   * Tells the user that a hotkey could not be claimed, instead of leaving a dead
   * hotkey with nothing to explain it.
   *
   * <p>How much this can actually detect depends on the platform, because the
   * failure has to survive the stack underneath:
   * <ul>
   * <li><b>Linux</b> — {@code LinuxHotkeyManager} catches jxgrabkey's
   *     {@code HotkeyConflictException} and returns false, so a genuine
   *     grab conflict reaches here.</li>
   * <li><b>macOS / Windows</b> — keymaster's {@code Provider.register()} returns
   *     void and discards the OS result, so a combination already owned by
   *     another application registers "successfully" and simply never fires.
   *     Only an unresolvable-modifier refusal (see
   *     {@code HotkeyController.addHotkey}) reaches here.</li>
   * </ul>
   *
   * <p>Reporting real conflicts on macOS/Windows would mean patching or replacing
   * the bundled keymaster library — deliberately out of scope.
   */
  private void warnHotkeyNotRegistered(String labelKey, int key, int mod) {
    String combination = Key.convertKeyToText(key, mod);
    Debug.error("IDE: could not register hotkey %s (%s) — already in use by another application?",
        combination, labelKey);
    EventQueue.invokeLater(() -> JOptionPane.showMessageDialog(ideWindow,
        _I("msgHotkeyNotRegistered", combination),
        _I("msgHotkeyNotRegisteredTitle"),
        JOptionPane.WARNING_MESSAGE));
  }

  void onStopRunning() {
    log("AbortKey was pressed: aborting all running scripts");
    Runner.abortAll();
    EventQueue.invokeLater(() -> {
      btnRecord.stopRecord();
    });
  }
  //</editor-fold>
}
