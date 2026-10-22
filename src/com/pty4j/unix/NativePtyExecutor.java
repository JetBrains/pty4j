package com.pty4j.unix;

import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author traff
 */
class NativePtyExecutor implements PtyExecutor {

  private final Pty4J myPty4j;

  NativePtyExecutor(@NotNull String libraryName) {
    myPty4j = Native.load(libraryName, Pty4J.class);
  }

  @Override
  public int execPty(String full_path, String[] argv, String[] envp, String dirpath, String pts_name, int fdm,
                     String err_pts_name, int err_fdm, boolean console) {
    return myPty4j.exec_pty(full_path, argv, envp, dirpath, pts_name, fdm, err_pts_name, err_fdm, console);
  }

  @Override
  public int waitForProcessExitAndGetExitCode(int pid) {
    return myPty4j.wait_for_child_process_exit(pid);
  }

  @Override
  public @NotNull WinSize getWindowSize(int fd, @Nullable PtyProcess process) throws UnixPtyException {
    WinSizeStructure ws = new WinSizeStructure();
    int errno = myPty4j.get_window_size(fd, ws);
    if (errno != 0) {
      throw new UnixPtyException("Failed to get window size:" +
        " fd=" + fd + (myPty4j.is_valid_fd(fd) ? "(valid)" : "(invalid)") +
        ", " + getErrorInfo(errno, process), errno);
    }
    return ws.toWinSize();
  }

  @Override
  public void setWindowSize(int fd, @NotNull WinSize winSize, @Nullable PtyProcess process) throws UnixPtyException {
    int errno = myPty4j.set_window_size(fd, new WinSizeStructure(winSize));
    if (errno != 0) {
      boolean validFd = myPty4j.is_valid_fd(fd);
      String message = "Failed to set window size: [" + winSize + "]" +
        ", fd=" + fd + (validFd ? "(valid)" : "(invalid)") +
        ", " + getErrorInfo(errno, process);
      throw new UnixPtyException(message, errno);
    }
  }

  private static @NotNull String getErrorInfo(int errno, @Nullable PtyProcess process) {
    String message = "errno=" + errno + "(" + (errno != -1 ? PtyHelpers.getInstance().strerror(errno) : "unknown") + ")";
    if (process != null) {
      Integer exitCode = getExitCode(process);
      message += ", pid:" + process.pid() + ", running:" + process.isAlive() +
        ", exit code:" + (exitCode != null ? exitCode : "N/A");
    }
    return message;
  }

  private static @Nullable Integer getExitCode(@NotNull PtyProcess process) {
    Integer exitCode = null;
    try {
      exitCode = process.exitValue();
    }
    catch (IllegalThreadStateException ignored) {
    }
    return exitCode;
  }

  /** Whether {@code fd} is an open file descriptor of this process. */
  boolean isValidFd(int fd) {
    return myPty4j.is_valid_fd(fd);
  }

  private interface Pty4J extends com.sun.jna.Library {
    int exec_pty(String full_path, String[] argv, String[] envp, String dirpath, String pts_name, int fdm,
                 String err_pts_name, int err_fdm, boolean console);

    int wait_for_child_process_exit(int child_pid);

    int get_window_size(int fd, WinSizeStructure win_size);

    int set_window_size(int fd, WinSizeStructure win_size);

    boolean is_valid_fd(int fd);
  }

  /**
   * Denotes the winsize struct from "sys/ioctl.h":
   * <pre><code>
   * struct winsize {
   *   unsigned short ws_row;
   *   unsigned short ws_col;
   *   unsigned short ws_xpixel;
   *   unsigned short ws_ypixel;
   * };
   * </code></pre>
   * @see <a href="https://man7.org/linux/man-pages/man2/TIOCSWINSZ.2const.html">TIOCSWINSZ</a>
   */
  protected static class WinSizeStructure extends Structure {
    private static final List<String> FIELD_ORDER = List.of("ws_row", "ws_col", "ws_xpixel", "ws_ypixel");

    public short ws_row;
    public short ws_col;
    public short ws_xpixel;
    public short ws_ypixel;

    @Override
    protected List<String> getFieldOrder() {
      return FIELD_ORDER;
    }

    private WinSizeStructure() {
    }

    WinSizeStructure(@NotNull WinSize ws) {
      ws_row = (short)ws.getRows();
      ws_col = (short)ws.getColumns();
      ws_xpixel = (short)ws.getWidth();
      ws_ypixel = (short)ws.getHeight();
    }

    @NotNull WinSize toWinSize() {
      // Native winsize fields are unsigned; Java shorts must not sign-extend dimensions above 32767.
      return new WinSize(Short.toUnsignedInt(ws_col), Short.toUnsignedInt(ws_row),
                         Short.toUnsignedInt(ws_xpixel), Short.toUnsignedInt(ws_ypixel));
    }
  }
}
