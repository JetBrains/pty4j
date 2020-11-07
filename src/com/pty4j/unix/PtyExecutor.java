package com.pty4j.unix;

import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public interface PtyExecutor {
  int execPty(String full_path, String[] argv, String[] envp,
              String dirpath, String pts_name, int fdm, String err_pts_name, int err_fdm, boolean console);

  int waitForProcessExitAndGetExitCode(int pid);

  @NotNull WinSize getWindowSize(int fd, @Nullable PtyProcess process) throws UnixPtyException;

  void setWindowSize(int fd, @NotNull WinSize winSize, @Nullable PtyProcess process) throws UnixPtyException;

  /*
   * When the window size changes, a SIGWINCH signal is sent to the foreground process group.
   * https://www.man7.org/linux/man-pages/man4/tty_ioctl.4.html
   * Please note that SIGWINCH has no portable number: https://en.wikipedia.org/wiki/Signal_(IPC)#POSIX_signals
   */
  void sendSigwinch(@NotNull PtyProcess process) throws UnixPtyException;
}
