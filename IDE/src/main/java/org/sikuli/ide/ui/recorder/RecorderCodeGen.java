/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui.recorder;

import org.sikuli.script.*;
import org.sikuli.support.recorder.generators.ICodeGenerator;
import org.sikuli.support.recorder.generators.JavaCodeGenerator;
import org.sikuli.support.recorder.generators.RobotFrameworkCodeGenerator;

import javax.swing.*;
import java.io.File;

class RecorderCodeGen {

  private final ICodeGenerator codeGenerator;
  private final RecorderCodePreview codePreview;

  RecorderCodeGen(ICodeGenerator codeGenerator, RecorderCodePreview codePreview) {
    this.codeGenerator = codeGenerator;
    this.codePreview = codePreview;
  }

  ICodeGenerator getGenerator() {
    return codeGenerator;
  }

  boolean isJava() {
    return codeGenerator instanceof JavaCodeGenerator;
  }

  boolean isRF() {
    return codeGenerator instanceof RobotFrameworkCodeGenerator;
  }

  void addLine(String code) {
    codePreview.addLine(code);
  }

  void addActionCode(String code, boolean appScoped, String appVarName) {
    if (appScoped && !isRF()) {
      codePreview.addLine(appVarName + "." + code);
    } else {
      codePreview.addLine(code);
    }
  }

  void addMultilineActionCode(String code, boolean appScoped, String appVarName) {
    if (code.contains("\n")) {
      for (String line : code.split("\n")) addActionCode(line, appScoped, appVarName);
    } else {
      addActionCode(code, appScoped, appVarName);
    }
  }

  String generateImageCode(String actionType, Pattern pattern) {
    String patStr = codeGenerator.pattern(pattern);
    String semi = isJava() ? ";" : "";

    switch (actionType) {
      case "click":
        if (isRF()) return "    Wait Until Screen Contain    " + patStr + "    10\n    Click    " + patStr;
        return "wait(" + patStr + ", 10).click()" + semi;
      case "doubleClick":
        if (isRF()) return "    Wait Until Screen Contain    " + patStr + "    10\n    Double Click    " + patStr;
        return "wait(" + patStr + ", 10).doubleClick()" + semi;
      case "rightClick":
        if (isRF()) return "    Wait Until Screen Contain    " + patStr + "    10\n    Right Click    " + patStr;
        return "wait(" + patStr + ", 10).rightClick()" + semi;
      case "wait":
        return codeGenerator.wait(pattern, 10, null);
      default:
        return "# " + actionType + "(\"" + pattern.getFilename() + "\")";
    }
  }

  String generateTextCode(String actionType, String text) {
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

  void generateVanish(JDialog parent, Pattern pattern, boolean appScoped, String appVarName) {
    JCheckBox chkVanish = new JCheckBox("Assert UI change after this action (waitVanish)");
    JOptionPane.showMessageDialog(parent, chkVanish, "Post-action assertion",
        JOptionPane.PLAIN_MESSAGE);
    if (chkVanish.isSelected()) {
      String patStr = codeGenerator.pattern(pattern);
      if (isRF()) {
        addActionCode("    Wait Until Screen Not Contain    " + patStr + "    5", appScoped, appVarName);
      } else {
        addActionCode("waitVanish(" + patStr + ", 5)" + (isJava() ? ";" : ""), appScoped, appVarName);
      }
    }
  }

  void generateLaunchApp(String appPath, String appVarName, boolean scoped) {
    if (isJava()) {
      addLine("App " + appVarName + " = App.open(\"" + appPath + "\");");
      if (scoped) {
        addLine(appVarName + ".focus();");
        addLine("Region " + appVarName + "Region = " + appVarName + ".window();");
      }
    } else if (isRF()) {
      addLine("    Open Application    " + appPath);
    } else {
      addLine(appVarName + " = App.open(\"" + appPath + "\")");
      if (scoped) {
        addLine(appVarName + ".focus()");
        addLine(appVarName + " = " + appVarName + ".window()");
      }
    }
  }

  void generateCloseApp(String appVarName) {
    if (isJava()) {
      addLine(appVarName + ".close();");
    } else if (isRF()) {
      addLine("    Close Application    " + appVarName);
    } else {
      addLine(appVarName + ".close()");
    }
  }

  void initRFHeaders() {
    if (isRF()) {
      addLine("*** Settings ***");
      addLine("Library    SikuliLibrary");
      addLine("Documentation    Recorded by OculiX Modern Recorder");
      addLine("");
      addLine("*** Test Cases ***");
      addLine("Recorded Test");
    }
  }
}
