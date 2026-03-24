/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.ide;

import org.sikuli.basics.Debug;
import org.sikuli.basics.PreferencesUser;
import org.sikuli.script.Image;
import org.sikuli.script.SX;
import org.sikuli.support.FileManager;
import org.sikuli.support.ide.IDEDesktopSupport;
import org.sikuli.support.ide.SikuliIDEI18N;
import org.sikuli.support.ide.syntaxhighlight.ResolutionException;
import org.sikuli.support.ide.syntaxhighlight.grammar.Lexer;
import org.sikuli.support.ide.syntaxhighlight.grammar.Token;
import org.sikuli.support.ide.syntaxhighlight.grammar.TokenType;

import org.sikuli.util.SikulixFileChooser;

import javax.swing.*;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class IDEFileManager {

  private static final String me = "IDE: ";

  private static void log(String message, Object... args) {
    Debug.logx(3, me + message, args);
  }

  private static void trace(String message, Object... args) {
    Debug.logx(4, me + message, args);
  }

  private static void error(String message, Object... args) {
    Debug.logx(-1, me + message, args);
  }

  private final SikulixIDE ide;

  IDEFileManager(SikulixIDE ide) {
    this.ide = ide;
  }

  // --- File chooser dialogs ---

  File selectFileToOpen() {
    File fileSelected = new SikulixFileChooser(ide).open();
    if (fileSelected == null) {
      return null;
    }
    return fileSelected;
  }

  File selectFileForSave(PaneContext context) {
    File fileSelected = new SikulixFileChooser(ide).saveAs(
            context.getExt(), context.isBundle() || context.isTemp());
    if (fileSelected == null) {
      return null;
    }
    return fileSelected;
  }

  // --- Dirty check / collect ---

  boolean checkDirtyPanes() {
    for (PaneContext context : ide.contexts) {
      if (context.isDirty() || (context.isTemp() && context.hasContent())) {
        return true;
      }
    }
    return false;
  }

  List<File> collectPaneFiles() {
    List<File> files = new ArrayList<>();
    for (PaneContext context : ide.contexts) {
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

  // --- Export ---

  private void convertSrcToHtml(String bundle) {
//    IScriptRunner runner = ScriptingSupport.getRunner(null, "jython");
//    if (runner != null) {
//      runner.doSomethingSpecial("convertSrcToHtml", new String[]{bundle});
//    }
  }

  void exportAsZip() {
    PaneContext context = ide.getActiveContext();
    var chooser = new SikulixFileChooser(SikulixIDE.get());
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

  static void zipDir(File zipDir, File zipFile, String fScript) throws IOException {
    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
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
          try (FileInputStream fis = new FileInputStream(f)) {
            zos.putNextEntry(anEntry);
            while ((bytesIn = fis.read(readBuffer)) != -1) {
              zos.write(readBuffer, 0, bytesIn);
            }
          }
        }
      }
    } catch (Exception ex) {
      String msg = ex.getMessage() + "";
    }
  }

  // --- Image parsing ---

  private static final Map<String, Lexer> lexers = new HashMap<>();

  int lineNumber = 0;
  String uncompleteStringError = "uncomplete_string_error";

  Map<String, List<Integer>> parseforImages() {
    File imageFolder = ide.getActiveContext().getImageFolder();
    trace("parseforImages: in %s", imageFolder);
    String scriptText = ide.getActiveContext().getPane().getText();
    Lexer lexer = getLexer();
    Map<String, List<Integer>> images = new HashMap<>();
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

  // --- Rename image ---

  void reparseOnRenameImage(String oldName, String newName, boolean fileOverWritten) {
    if (fileOverWritten) {
      Image.unCache(newName);
    }
    Map<String, List<Integer>> images = parseforImages();
    oldName = new File(oldName).getName();
    List<Integer> poss = images.get(oldName);
    if (images.containsKey(oldName) && poss.size() > 0) {
      Collections.sort(poss, (o1, o2) -> {
        if (o1 > o2) return -1;
        return 1;
      });
      reparseRenameImages(poss, oldName, new File(newName).getName());
    }
    //TODO doReparse();
  }

  private boolean reparseRenameImages(List<Integer> poss, String oldName, String newName) {
    var text = new StringBuilder(ide.getActiveContext().getPane().getText());
    int lenOld = oldName.length();
    for (int pos : poss) {
      text.replace(pos - lenOld, pos, newName);
    }
    ide.getActiveContext().getPane().setText(text.toString());
    return true;
  }

  // --- Lexer ---

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

  // --- Caret helpers ---

  String getImageNameFromLine() {
    String line = getLineTextAtCaret().strip();
    Pattern aName = Pattern.compile("^([A-Za-z0-9_]+).*?=");
    Matcher matcher = aName.matcher(line);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return "";
  }

  String getLineTextAtCaret() {
    return ide.getActiveContext().getPane().getLineTextAtCaret();
  }

  // --- Session save / restore ---

  static final String[] loadScripts = new String[0];

  boolean saveSession(int saveAction) {
    StringBuilder sbuf = new StringBuilder();
    for (PaneContext context : ide.contexts.toArray(new PaneContext[]{new PaneContext()})) {
      if (saveAction == ide.DO_SAVE_ALL) {
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

  List<File> restoreSession() {
    String session_str = SikulixIDE.prefs.getIdeSession();
    List<File> filesToLoad = new ArrayList<>();
    if (IDEDesktopSupport.filesToOpen != null && IDEDesktopSupport.filesToOpen.size() > 0) {
      for (File f : IDEDesktopSupport.filesToOpen) {
        filesToLoad.add(f);
      }
    }
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
    //TODO implement load scripts (preload)
    if (loadScripts.length > 0) {
      log("Preload given scripts");
      for (String loadScript : loadScripts) {
        if (loadScript.isEmpty()) {
          continue;
        }
        File f = new File(loadScript);
        if (f.exists() && !filesToLoad.contains(f)) {
          if (f.getName().endsWith(".py")) {
            Debug.info("Python script: %s", f.getName());
          } else {
            log("Sikuli script: %s", f);
          }
        }
      }
    }
    if (filesToLoad.size() > 0) {
      for (File file : filesToLoad) {
        ide.createFileContext(file);
      }
      ide.getContextAt(0).closeSilent();
      ide.tempIndex = 1;
    }
    return filesToLoad;
  }
}
