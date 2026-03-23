/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */

package org.sikuli.script.runners;

import java.io.File;

import org.sikuli.support.Commons;
import org.sikuli.support.FileManager;
import org.sikuli.support.runner.IRunner;
import org.sikuli.support.RunTime;

public class AppleScriptRunner extends AbstractLocalFileScriptRunner {

  public static final String NAME = "AppleScript";
  public static final String TYPE = "text/applescript";
  public static final String[] EXTENSIONS = new String[] {"script"};

  private static final int LVL = 3;

  @Override
  protected int doEvalScript(String script, IRunner.Options options) {
    String osascriptShebang = "#!/usr/bin/osascript\n";
    script = osascriptShebang + script;
    File aFile = FileManager.createTempFile("script");
    aFile.setExecutable(true);
    FileManager.writeStringToFile(script, aFile);

    int retcode = runScript(aFile.getAbsolutePath(), null, options);

    if (retcode != 0) {
      if (options != null && options.isSilent()) {
        log(LVL, "AppleScript:\n%s\nreturned:\n%s", script, RunTime.getLastCommandResult());
      } else {
        log(-1, "AppleScript:\n%s\nreturned:\n%s", script, RunTime.getLastCommandResult());
      }
    }
    return retcode;
  }

  @Override
  protected int doRunScript(String scriptFile, String[] scriptArgs, IRunner.Options options) {
    String prefix = options != null && options.isSilent() ? "!" : "";

    String retVal = RunTime.runcmd(new String[]{prefix + new File(scriptFile).getAbsolutePath()});
    String[] parts = retVal.split("\n");
    int retcode = -1;
    try {
      retcode = Integer.parseInt(parts[0]);
    } catch (Exception ex) {
    }
    return retcode;
  }

  @Override
  public boolean isSupported() {
    return Commons.runningMac();
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String[] getExtensions() {
    return EXTENSIONS.clone();
  }

  @Override
  public String getType() {
    return TYPE;
  }
}
