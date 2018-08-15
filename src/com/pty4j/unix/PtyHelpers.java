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


import com.google.common.collect.Lists;
import com.pty4j.WinSize;
import com.pty4j.util.PtyUtil;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Structure;
import jtermios.JTermios;
import jtermios.Termios;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.Arrays;
import java.util.List;


/**
 * Provides access to the pseudoterminal functionality on POSIX(-like) systems,
 * emulating such system calls on non POSIX systems.
 */
public class PtyHelpers {
  private static final Logger LOG = Logger.getLogger(PtyHelpers.class);

  /**
   * Provides a OS-specific interface to the PtyHelpers methods.
   */
  public static interface OSFacade {
    /**
     * Transforms the calling process into a new process.
     *
     * @param command the command to execute;
     * @param argv    the arguments, by convention begins with the command to execute;
     * @param env     the (optional) environment options.
     * @return 0 upon success, -1 upon failure (see {@link PtyHelpers#errno()} for
     *         details).
     */
    int execve(String command, String[] argv, String[] env);

    /**
     * Returns the window size information for the process with the given FD and
     * stores the results in the given {@link com.pty4j.WinSize} structure.
     *
     * @param fd the FD of the process to query;
     * @param ws the WinSize structure to store the results into.
     * @return 0 upon success, -1 upon failure (see {@link PtyHelpers#errno()} for
     *         details).
     */
    int getWinSize(int fd, WinSize ws);

    /**
     * Terminates or signals the process with the given PID.
     *
     * @param pid the process ID to terminate or signal;
     * @param sig the signal number to send, for example, 9 to terminate the
     *            process.
     * @return a value of <tt>0</tt> upon success, or a non-zero value in case
     *         of an error (see {@link PtyHelpers#errno()} for details).
     */
    int kill(int pid, int sig);

    /**
     * Sets the window size information for the process with the given FD using
     * the given {@link WinSize} structure.
     *
     * @param fd the FD of the process to set the window size for;
     * @param ws the WinSize structure with information about the window size.
     * @return 0 upon success, -1 upon failure (see {@link PtyHelpers#errno()} for
     *         details).
     */
    int setWinSize(int fd, WinSize ws);

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

    void execv(String path, String[] argv);

    int getpid();

    int setpgid(int pid, int pgid);

    void dup2(int fds, int fileno);

    int getppid();

    void unsetenv(String s);

    int login_tty(int fd);

    void chdir(String dirpath);
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

  // VARIABLES

  private static OSFacade myOsFacade;

  // METHODS

  static {
    if (Platform.isMac()) {
      myOsFacade = new com.pty4j.unix.macosx.OSFacadeImpl();
    }
    else if (Platform.isFreeBSD()) {
      myOsFacade = new com.pty4j.unix.freebsd.OSFacadeImpl();
    }
    else if (Platform.isOpenBSD()) {
      myOsFacade = new com.pty4j.unix.openbsd.OSFacadeImpl();
    }
    else if (Platform.isLinux()) {
      myOsFacade = new com.pty4j.unix.linux.OSFacadeImpl();
    }
    else if (Platform.isWindows()) {
      throw new IllegalArgumentException("WinPtyProcess should be used on Windows");
    }
    else {
      throw new RuntimeException("Pty4J has no support for OS " + System.getProperty("os.name"));
    }
  }

  private static PtyExecutor myPtyExecutor;

  static {
    try {
      myPtyExecutor = loadPtyExecutor();
    }
    catch (Exception e) {
      LOG.error("Can't load native pty executor library", e);
    }
  }

  @NotNull
  private static PtyExecutor loadPtyExecutor() throws Exception {
    File lib = PtyUtil.resolveNativeLibrary();
    return new NativePtyExecutor(lib.getAbsolutePath());
  }

  @NotNull
  private static PtyExecutor getPtyExecutor() throws Exception {
    if (myPtyExecutor != null) {
      return myPtyExecutor;
    }
    return loadPtyExecutor();
  }

  @TestOnly
  public static void dropPtyExecutor() {
    myPtyExecutor = null;
  }

  public static OSFacade getInstance() {
    return myOsFacade;
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

  /**
   * Reports the window size for the given file descriptor.
   *
   * @param fd the file descriptor to report the window size for;
   * @param ws the window size to place the results in.
   * @return 0 upon success, or -1 upon failure.
   */
  public static int getWinSize(int fd, WinSize ws) {
    return myOsFacade.getWinSize(fd, ws);
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
   * Sets the window size for the given file descriptor.
   *
   * @param fd the file descriptor to set the window size for;
   * @param ws the new window size to set.
   * @return 0 upon success, or -1 upon failure.
   */
  public static int setWinSize(int fd, WinSize ws) {
    return myOsFacade.setWinSize(fd, ws);
  }

  /**
   * Terminates or signals the process with the given PID.
   *
   * @param pid    the process ID to terminate or signal;
   * @param signal the signal number to send, for example, 9 to terminate the
   *               process.
   * @return a value of <tt>0</tt> upon success, or a non-zero value in case of
   *         an error.
   */
  public static int signal(int pid, int signal) {
    return myOsFacade.kill(pid, signal);
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
    return myOsFacade.waitpid(pid, stat, options);
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
    return myOsFacade.strerror(errno());
  }

  /**
   * Not public as this method <em>replaces</em> the current process and
   * therefore should be used with caution.
   *
   * @param command the command to execute.
   */
  private static int execve(String command, String[] argv, String[] env) {
    return myOsFacade.execve(command, argv, env);
  }

  public static void chdir(String dirpath) {
    myOsFacade.chdir(dirpath);
  }

  /**
   * Processes the given command + arguments and crafts a valid array of
   * arguments as expected by {@link #execve(String, String[], String[])}.
   *
   * @param command   the command to run, cannot be <code>null</code>;
   * @param arguments the command line arguments, can be <code>null</code>.
   * @return a new arguments array, never <code>null</code>.
   */
  private static String[] processArgv(String command, String[] arguments) {
    final String[] argv;
    if (arguments == null) {
      argv = new String[]{command};
    }
    else {
      if (!command.equals(arguments[0])) {
        argv = new String[arguments.length + 1];
        argv[0] = command;
        System.arraycopy(arguments, 0, argv, 1, arguments.length);
      }
      else {
        argv = Arrays.copyOf(arguments, arguments.length);
      }
    }
    return argv;
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
    PtyExecutor executor;
    try {
      executor = getPtyExecutor();
    }
    catch (Exception e) {
      throw new RuntimeException("Can't load native pty executor library", e);
    }
    return executor.execPty(full_path, argv, envp, dirpath, pts_name, fdm, err_pts_name, err_fdm, console);
  }

  public static class winsize extends Structure {
    public short ws_row;
    public short ws_col;
    public short ws_xpixel;
    public short ws_ypixel;

    @Override
    protected List getFieldOrder() {
      return Lists.newArrayList("ws_row", "ws_col", "ws_xpixel", "ws_ypixel");
    }

    public winsize() {
    }

    public winsize(WinSize ws) {
      ws_row = ws.ws_row;
      ws_col = ws.ws_col;
      ws_xpixel = ws.ws_xpixel;
      ws_ypixel = ws.ws_ypixel;
    }

    public void update(WinSize winSize) {
      winSize.ws_col = ws_col;
      winSize.ws_row = ws_row;
      winSize.ws_xpixel = ws_xpixel;
      winSize.ws_ypixel = ws_ypixel;
    }
  }
}
