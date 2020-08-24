/*******************************************************************************
 * Copyright (c) 2002, 2010 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.pty4j.unix;

import com.pty4j.WinSize;
import com.pty4j.util.Pair;
import jtermios.FDSet;
import jtermios.JTermios;

import java.io.IOException;
import java.util.Locale;


/**
 * Pty - pseudo terminal support.
 */
public class Pty {
  private final boolean myConsole;
  private String mySlaveName;
  private PTYInputStream myIn;
  private PTYOutputStream myOut;
  private final Object myFDLock = new Object();
  private final Object mySelectLock = new Object();
  private final int[] myPipe = new int[2];

  private volatile int myMaster;

  private static boolean setTerminalSizeErrorAlreadyLogged;

  private static final boolean useSelect = isOSXLessThanOrEqualTo106();

  private static boolean isOSXLessThanOrEqualTo106() {
    if (System.getProperty("os.name").toLowerCase(Locale.US).startsWith("mac")) {
      String version = System.getProperty("os.version").toLowerCase(Locale.US);
      String[] strings = version.split("\\.");
      if (strings.length > 1 && strings[0].equals("10") && Integer.valueOf(strings[1]) <= 6) return true;
    }
    return false;
  }

  private static final Object PTSNAME_LOCK = new Object();

  public Pty() throws IOException {
    this(false);
  }

  public Pty(boolean console) throws IOException {
    myConsole = console;

    Pair<Integer, String> masterSlave = openMaster();
    myMaster = masterSlave.first;
    mySlaveName = masterSlave.second;

    if (mySlaveName == null) {
      throw new IOException("Util.exception.cannotCreatePty");
    }

    myIn = new PTYInputStream(this);
    myOut = new PTYOutputStream(this);
    JTermios.pipe(myPipe);
  }

  public String getSlaveName() {
    return mySlaveName;
  }

  public int getMasterFD() {
    return myMaster;
  }

  /**
   * @return whether this pseudo terminal is for use with the console.
   */
  public final boolean isConsole() {
    return myConsole;
  }

  public PTYOutputStream getOutputStream() {
    return myOut;
  }

  public PTYInputStream getInputStream() {
    return myIn;
  }

  @Deprecated
  public final void setTerminalSize(int width, int height) {
    setTerminalSize(new WinSize(width, height, 0, 0));
  }

  /**
   * Change terminal window size to given width and height.
   * <p>
   * This should only be used when the pseudo terminal is configured for use with a terminal emulation, i.e. when
   * {@link #isConsole()} returns {@code false}.
   * </p>
   * <p>
   * <strong>Note:</strong> This method may not be supported on all platforms. Known platforms which support this method
   * are: {@code linux-x86}, {@code linux-x86_64}, {@code solaris-sparc}, {@code macosx}.
   * </p>
   *
   * @param winSize new size structure.
   */
  public final void setTerminalSize(WinSize winSize) {
    try {
      int res = changeWindowSize(myMaster, winSize);
      if (res != 0) {
        throw new IllegalStateException("Can set new window size. ioctl returns " + res + ", errorno=" + JTermios.errno());
      }
    } catch (UnsatisfiedLinkError e) {
      if (!setTerminalSizeErrorAlreadyLogged) {
        setTerminalSizeErrorAlreadyLogged = true;
      }
    }
  }


  /**
   * Returns the current window size of this Pty.
   *
   * @return a {@link com.pty4j.WinSize} instance with information about the master side
   * of the Pty, never <code>null</code>.
   * @throws IOException in case obtaining the window size failed.
   */
  public WinSize getWinSize() throws IOException {
    WinSize result = new WinSize();
    if (PtyHelpers.getWinSize(myMaster, result) < 0) {
      throw new IOException("Failed to get window size: " + PtyHelpers.errno());
    }
    return result;
  }

  /**
   * Creates a pty pair (master file descriptor and slave path).
   * If creation fails, the master file descriptor is negative.
   * @return the created pty pair
   */
  public static Pair<Integer, String> ptyMasterOpen() {

    PtyHelpers.OSFacade m_jpty = PtyHelpers.getInstance();

    String name = "/dev/ptmx";

    int fdm = m_jpty.getpt();

    if (fdm < 0) {
      return Pair.create(-1, name);
    }
    if (m_jpty.grantpt(fdm) < 0) { /* grant access to slave */
      m_jpty.close(fdm);
      return Pair.create(-2, name);
    }
    if (m_jpty.unlockpt(fdm) < 0) { /* clear slave's lock flag */
      m_jpty.close(fdm);
      return Pair.create(-3, name);
    }

    String ptr = ptsname(m_jpty, fdm);

    if (ptr == null) { /* get slave's name */
      m_jpty.close(fdm);
      return Pair.create(-4, name);
    }
    return Pair.create(fdm, ptr);
  }

  private static String ptsname(PtyHelpers.OSFacade m_jpty, int fdm) {
    synchronized (PTSNAME_LOCK) {
      // ptsname() function is not thread-safe: http://man7.org/linux/man-pages/man3/ptsname.3.html
      return m_jpty.ptsname(fdm);
    }
  }


  private Pair<Integer, String> openMaster() {
    return ptyMasterOpen();
  }

  @Deprecated
  public static int changeWindowsSize(int fd, int width, int height) {
    return changeWindowSize(fd, new WinSize(width, height));
  }

  private static int changeWindowSize(int fd, WinSize ws) {
    return PtyHelpers.getInstance().setWinSize(fd, ws);
  }

  public static int raise(int pid, int sig) {
    PtyHelpers.OSFacade m_jpty = PtyHelpers.getInstance();

    int status = m_jpty.killpg(pid, sig);

    if (status == -1) {
      status = m_jpty.kill(pid, sig);
    }

    return status;
  }

  public boolean isClosed() {
    return myMaster == -1;
  }

  public void close() throws IOException {
    if (myMaster != -1) {
      synchronized (myFDLock) {
        if (myMaster != -1) {
          try {
            int status = close0(myMaster);
            if (status == -1) {
              throw new IOException("Close error");
            }
          } finally {
            myMaster = -1;
          }
        }
      }
    }
  }

  @Override
  protected void finalize() throws Throwable {
    close();
    super.finalize();
  }

  private int close0(int fd) throws IOException {
    int ret = JTermios.close(fd);

    breakRead();

    synchronized (mySelectLock) {
      JTermios.close(myPipe[0]);
      JTermios.close(myPipe[1]);
      myPipe[0] = -1;
      myPipe[1] = -1;
    }

    return ret;
  }

  void breakRead() {
    JTermios.write(myPipe[1], new byte[1], 1);
  }

  public static int wait0(int pid) {
    PtyHelpers.OSFacade m_jpty = PtyHelpers.getInstance();

    int[] status = new int[1];

    if (pid < 0) {
      return -1;
    }

    for (; ; ) {
      if (m_jpty.waitpid(pid, status, 0) < 0) {
        if (JTermios.errno() == JTermios.EINTR) {
          // interrupted system call - retry
          continue;
        }
      }
      break;
    }
    if (WIFEXITED(status[0])) {
      return WEXITSTATUS(status[0]);
    }

    return status[0];
  }

  static int WEXITSTATUS(int status) {
    return (status >> 8) & 0x000000ff;
  }

  static boolean WIFEXITED(int status) {
    return _WSTATUS(status) == 0;
  }

  private static int _WSTATUS(int status) {
    return status & 0177;
  }

  int read(byte[] buf, int len) throws IOException {
    int fd = myMaster;
    if (fd == -1) return -1;

    boolean haveBytes;
    synchronized (mySelectLock) {
      if (myPipe[0] == -1) return -1;

      haveBytes = useSelect ? select(myPipe[0], fd) : poll(myPipe[0], fd);
    }

    return haveBytes ? JTermios.read(fd, buf, len) : -1;
  }

  private static boolean poll(int pipeFd, int fd) {
    // each {int, short, short} structure is represented by two ints
    int[] poll_fds = new int[]{pipeFd, JTermios.POLLIN, fd, JTermios.POLLIN};
    while (true) {
      if (JTermios.poll(poll_fds, 2, -1) > 0) break;

      int errno = JTermios.errno();
      if (errno != JTermios.EAGAIN && errno != JTermios.EINTR) return false;
    }
    return ((poll_fds[3] >> 16) & JTermios.POLLIN) != 0;
  }

  private static boolean select(int pipeFd, int fd) {
    FDSet set = JTermios.newFDSet();

    JTermios.FD_SET(pipeFd, set);
    JTermios.FD_SET(fd, set);
    JTermios.select(Math.max(fd, pipeFd) + 1, set, null, null, null);

    return JTermios.FD_ISSET(fd, set);
  }

  int write(byte[] buf, int len) throws IOException {
    return JTermios.write(myMaster, buf, len);
  }

}
