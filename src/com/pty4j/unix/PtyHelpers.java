/*
 * JPty - A small PTY interface for Java.
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.pty4j.unix;

import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import com.pty4j.util.LazyValue;
import com.pty4j.util.PtyUtil;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import jtermios.JTermios;
import jtermios.Termios;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Provides access to the pseudoterminal functionality on POSIX(-like) systems,
 * emulating such system calls on non POSIX systems.
 */
public class PtyHelpers {
  private static final Logger LOG = LoggerFactory.getLogger(PtyHelpers.class);

  /**
   * Provides a OS-specific interface to the PtyHelpers methods.
   */
  public interface OSFacade {
    /**
     * Terminates or signals the process with the given PID.
     *
     * @param pid the process ID to terminate or signal;
     * @param sig the signal number to send, for example, 9 to terminate the
     *            process.
     * @return a value of <code>0</code> upon success, or a non-zero value in case
     *         of an error (see {@link PtyHelpers#errno()} for details).
     */
    int kill(int pid, int sig);

    /**
     * Waits until the process with the given PID is stopped.
     *
     * @param pid     the PID of the process to wait for;
     * @param stat    the array in which the result code of the process will be
     *                stored;
     * @param options the options for waitpid (not used at the moment).
     * @return 0 upon success, -1 upon failure (see {@link PtyHelpers#errno()} for
     *         details).
     */
    int waitpid(int pid, int[] stat, int options);

    int sigprocmask(int how, com.sun.jna.ptr.IntByReference set, com.sun.jna.ptr.IntByReference oldset);

    String strerror(int errno);

    int getpt(); //getpt

    int grantpt(int fdm);

    int unlockpt(int fdm);

    int close(int fdm);

    String ptsname(int fdm);

    int killpg(int pid, int sig);

    int fork();

    int pipe(int[] pipe2);

    int setsid();

    int getpid();

    int setpgid(int pid, int pgid);

    void dup2(int fds, int fileno);

    int getppid();

    void unsetenv(String s);

    int login_tty(int fd);

    void chdir(String dirpath);

    default int tcdrain(int fd) {
      return JTermios.tcdrain(fd);
    }

    default int open(String path, int mode) {
      return JTermios.open(path, mode);
    }

    default int read(int fd, byte[] buffer, int len) {
      return JTermios.read(fd, buffer, len);
    }

    default int errno() {
      return JTermios.errno();
    }

    default int tcgetattr(int fd, TerminalSettings settings) {
      Termios termios = new Termios();
      int result = JTermios.tcgetattr(fd, termios);
      fillTerminalSettings(settings, termios);
      return result;
    }

    default int tcsetattr(int fd, int opt, TerminalSettings settings) {
      Termios termios = convertToTermios(settings);
      return JTermios.tcsetattr(fd, opt, termios);
    }
  }

  public static class TerminalSettings {
    public int c_iflag;
    public int c_oflag;
    public int c_cflag;
    public int c_lflag;
    public byte[] c_cc = new byte[20];
    public int c_ispeed;
    public int c_ospeed;
  }

  private static Termios convertToTermios(TerminalSettings settings) {
    Termios result = new Termios();
    result.c_iflag = settings.c_iflag;
    result.c_oflag = settings.c_oflag;
    result.c_cflag = settings.c_cflag;
    result.c_lflag = settings.c_lflag;
    System.arraycopy(settings.c_cc, 0, result.c_cc, 0, settings.c_cc.length);
    result.c_ispeed = settings.c_ispeed;
    result.c_ospeed = settings.c_ospeed;
    return result;
  }

  private static void fillTerminalSettings(TerminalSettings settings, Termios termios) {
    settings.c_iflag = termios.c_iflag;
    settings.c_oflag = termios.c_oflag;
    settings.c_cflag = termios.c_cflag;
    settings.c_lflag = termios.c_lflag;
    System.arraycopy(termios.c_cc, 0, settings.c_cc, 0, termios.c_cc.length);
    settings.c_ispeed = termios.c_ispeed;
    settings.c_ospeed = termios.c_ospeed;
  }

  // CONSTANTS

  public static int ONLCR = 0x04;

  public static int VINTR = 0;
  public static int VQUIT = 1;
  public static int VERASE = 2;
  public static int VKILL = 3;
  public static int VSUSP = 10;
  public static int VREPRINT = 12;
  public static int VWERASE = 14;

  public static int ECHOCTL = 0x1000;
  public static int ECHOKE = 0x4000;
  public static int ECHOK = 0x00000004;

  public static int IMAXBEL = 0x00002000;
  public static int HUPCL = 0x00004000;

  public static int IUTF8 = 0x00004000;

  public static int O_NOCTTY = JTermios.O_NOCTTY;
  public static int O_RDWR = JTermios.O_RDWR;
  public static int TCSANOW = JTermios.TCSANOW;

  private static final int STDIN_FILENO = 0;
  private static final int STDOUT_FILENO = 1;
  private static final int STDERR_FILENO = 2;


  /*
 * Flags for sigprocmask:
 */
  private static final int SIG_UNBLOCK = 2;

  public static int SIGHUP = 1;
  public static int SIGINT = 2;
  public static int SIGQUIT = 3;
  public static int SIGILL = 4;
  public static int SIGABORT = 6;
  public static int SIGFPE = 8;
  public static int SIGKILL = 9;
  public static int SIGSEGV = 11;
  public static int SIGPIPE = 13;
  public static int SIGALRM = 14;
  public static int SIGTERM = 15;
  public static int SIGCHLD = 20;

  public static int WNOHANG = 1;
  public static int WUNTRACED = 2;

  private static final LazyValue<OSFacade> OS_FACADE_VALUE = new LazyValue<>(() -> {
    if (Platform.isMac()) {
      return new com.pty4j.unix.macosx.OSFacadeImpl();
    }
    if (Platform.isFreeBSD()) {
      return new com.pty4j.unix.freebsd.OSFacadeImpl();
    }
    if (Platform.isOpenBSD()) {
      return new com.pty4j.unix.openbsd.OSFacadeImpl();
    }
    if (Platform.isLinux() || Platform.isAndroid()) {
      return new com.pty4j.unix.linux.OSFacadeImpl();
    }
    if (Platform.isWindows()) {
      throw new IllegalArgumentException("WinPtyProcess should be used on Windows");
    }
    throw new RuntimeException("Pty4J has no support for OS " + System.getProperty("os.name"));
  });

  private static final LazyValue<PtyExecutor> PTY_EXECUTOR_VALUE = new LazyValue<>(() -> {
    File lib = PtyUtil.resolveNativeLibrary();
    return new NativePtyExecutor(lib.getAbsolutePath());
  });

  static {
    try {
      getOsFacade();
    }
    catch (Throwable t) {
      LOG.error(t.getMessage(), t.getCause());
    }
    try {
      getPtyExecutor();
    }
    catch (Throwable t) {
      LOG.error(t.getMessage(), t.getCause());
    }
  }

  @NotNull
  private static OSFacade getOsFacade() {
    try {
      return OS_FACADE_VALUE.getValue();
    }
    catch (Throwable t) {
      throw new RuntimeException("Cannot load implementation of " + OSFacade.class, t);
    }
  }

  @NotNull static PtyExecutor getPtyExecutor() {
    try {
      return PTY_EXECUTOR_VALUE.getValue();
    }
    catch (Throwable t) {
      throw new RuntimeException("Cannot load native pty executor library", t);
    }
  }

  public static OSFacade getInstance() {
    return getOsFacade();
  }

  public static Termios createTermios() {
    Termios term = new Termios();

    boolean isUTF8 = true;
    term.c_iflag = JTermios.ICRNL | JTermios.IXON | JTermios.IXANY | IMAXBEL | JTermios.BRKINT | (isUTF8 ? IUTF8 : 0);
    term.c_oflag = JTermios.OPOST | ONLCR;
    term.c_cflag = JTermios.CREAD | JTermios.CS8 | HUPCL;
    term.c_lflag = JTermios.ICANON | JTermios.ISIG | JTermios.IEXTEN | JTermios.ECHO | JTermios.ECHOE | ECHOK | ECHOKE | ECHOCTL;

    term.c_cc[JTermios.VEOF] = CTRLKEY('D');
//    term.c_cc[VEOL] = -1;
//    term.c_cc[VEOL2] = -1;
    term.c_cc[VERASE] = 0x7f;           // DEL
    term.c_cc[VWERASE] = CTRLKEY('W');
    term.c_cc[VKILL] = CTRLKEY('U');
    term.c_cc[VREPRINT] = CTRLKEY('R');
    term.c_cc[VINTR] = CTRLKEY('C');
    term.c_cc[VQUIT] = 0x1c;           // Control+backslash
    term.c_cc[VSUSP] = CTRLKEY('Z');
//    term.c_cc[VDSUSP] = CTRLKEY('Y');
    term.c_cc[JTermios.VSTART] = CTRLKEY('Q');
    term.c_cc[JTermios.VSTOP] = CTRLKEY('S');
//    term.c_cc[VLNEXT] = CTRLKEY('V');
//    term.c_cc[VDISCARD] = CTRLKEY('O');
//    term.c_cc[VMIN] = 1;
//    term.c_cc[VTIME] = 0;
//    term.c_cc[VSTATUS] = CTRLKEY('T');

    term.c_ispeed = JTermios.B38400;
    term.c_ospeed = JTermios.B38400;

    return term;
  }

  private static byte CTRLKEY(char c) {
    return (byte)((byte)c - (byte)'A' + 1);
  }

  private static int __sigbits(int __signo) {
    return __signo > 32 ? 0 : (1 << (__signo - 1));
  }

  public static @NotNull WinSize getWinSize(int fd, @Nullable PtyProcess process) throws UnixPtyException {
    return getPtyExecutor().getWindowSize(fd, process);
  }

  /**
   * Tests whether the process with the given process ID is alive or terminated.
   *
   * @param pid the process-ID to test.
   * @return <code>true</code> if the process with the given process ID is
   *         alive, <code>false</code> if it is terminated.
   */
  public static boolean isProcessAlive(int pid) {
    int[] stat = {-1};
    int result = PtyHelpers.waitpid(pid, stat, WNOHANG);
    return (result == 0) && (stat[0] < 0);
  }

  /**
   * Terminates or signals the process with the given PID.
   *
   * @param pid    the process ID to terminate or signal;
   * @param signal the signal number to send, for example, 9 to terminate the
   *               process.
   * @return a value of <code>0</code> upon success, or a non-zero value in case of
   *         an error.
   */
  public static int signal(int pid, int signal) {
    return getOsFacade().kill(pid, signal);
  }

  /**
   * Blocks and waits until the given PID either terminates, or receives a
   * signal.
   *
   * @param pid     the process ID to wait for;
   * @param stat    an array of 1 integer in which the status of the process is
   *                stored;
   * @param options the bit mask with options.
   */
  public static int waitpid(int pid, int[] stat, int options) {
    return getOsFacade().waitpid(pid, stat, options);
  }

  /**
   * Returns the last known error.
   *
   * @return the last error number from the native system.
   */
  public static int errno() {
    return Native.getLastError();
  }

  public static String strerror() {
    return getOsFacade().strerror(errno());
  }

  public static void chdir(String dirpath) {
    getOsFacade().chdir(dirpath);
  }

  public static int execPty(String full_path,
                            String[] argv,
                            String[] envp,
                            String dirpath,
                            String pts_name,
                            int fdm,
                            String err_pts_name,
                            int err_fdm,
                            boolean console) {
    PtyExecutor executor = getPtyExecutor();
    return executor.execPty(full_path, argv, envp, dirpath, pts_name, fdm, err_pts_name, err_fdm, console);
  }
}
