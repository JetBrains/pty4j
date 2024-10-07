package com.pty4j.unix;

import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
interface PtyExecutor {
  int execPty(String full_path, String[] argv, String[] envp,
              String dirpath, String pts_name, int fdm, String err_pts_name, int err_fdm, boolean console);

  int waitForProcessExitAndGetExitCode(int pid);

  @NotNull WinSize getWindowSize(int fd, @Nullable PtyProcess process) throws UnixPtyException;

  void setWindowSize(int fd, @NotNull WinSize winSize, @Nullable PtyProcess process) throws UnixPtyException;
}
