package com.pty4j.unix;

/**
 * @author traff
 */
public interface PtyExecutor {
  int execPty(String full_path, String[] argv, String[] envp,
              String dirpath, int[] channels, String pts_name, int fdm, boolean console);
}
