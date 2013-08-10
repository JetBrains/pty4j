package com.pty4j.unix;

import com.sun.jna.Native;

/**
 * @author traff
 */
public class NativePtyExecutor implements PtyExecutor {

  private final Pty4J myPty4j;

  public NativePtyExecutor(String libname) {
    myPty4j = (Pty4J) Native.loadLibrary(libname, Pty4J.class);
  }

  @Override
  public int execPty(String full_path, String[] argv, String[] envp, String dirpath, int[] channels, String pts_name, int fdm, boolean console) {
    return myPty4j.exec_pty(full_path, argv, envp, dirpath, channels, pts_name, fdm, console);
  }

  public interface Pty4J extends com.sun.jna.Library {
    int exec_pty(String full_path, String[] argv, String[] envp, String dirpath, int[] channels, String pts_name, int fdm, boolean console);
  }
}
