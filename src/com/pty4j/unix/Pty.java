/*******************************************************************************
 * Copyright (c) 2002, 2010 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.pty4j.unix;

import com.pty4j.PtyException;
import com.pty4j.WinSize;
import com.pty4j.util.Pair;
import jtermios.JTermios;
import jtermios.Termios;

import java.io.IOException;


/**
 * Pty - pseudo terminal support.
 */
public class Pty {
  private final boolean myConsole;
  private String mySlaveName;
  private PTYInputStream myIn;
  private PTYOutputStream myOut;

  private int myMaster;

  private static boolean setTerminalSizeErrorAlreadyLogged;

  private static final Object myOpenLock = new Object();

  /**
   * The master fd is used on two streams. We need to wrap the fd so that when stream.close() is called the other stream
   * is disabled.
   */
  public class MasterFD {
    public int getFD() {
      return myMaster;
    }

    public void setFD(int fd) {
      myMaster = fd;
    }
  }

  public Pty() throws IOException {
    this(false);
  }

  public Pty(boolean console) throws IOException {
    myConsole = console;

    Pair<Integer, String> masterSlave = openMaster(console);
    myMaster = masterSlave.first;
    mySlaveName = masterSlave.second;

    if (mySlaveName == null) {
      throw new IOException("Util.exception.cannotCreatePty");
    }

    myIn = new PTYInputStream(new MasterFD());
    myOut = new PTYOutputStream(new MasterFD());
  }

  public String getSlaveName() {
    return mySlaveName;
  }

  public MasterFD getMasterFD() {
    return new MasterFD();
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
   * @param width  the given width.
   * @param height the given height.
   */
  public final void setTerminalSize(int width, int height) {
    try {
      changeWindowsSize(myMaster, width, height);
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
   *         of the Pty, never <code>null</code>.
   * @throws IOException in case obtaining the window size failed.
   */
  public WinSize getWinSize() throws IOException {
    WinSize result = new WinSize();
    if (PtyHelpers.getWinSize(myMaster, result) < 0) {
      throw new IOException("Failed to get window size: " + PtyHelpers.errno());
    }
    return result;
  }

  private Pair<Integer, String> ptyMasterOpen() {
    synchronized (myOpenLock) {
      PtyHelpers.OSFacade m_jpty = PtyHelpers.getInstance();

      String name = "/dev/ptmx";

      int fdm = m_jpty.getpt();

      if (fdm < 0)
        return Pair.create(-1, name);
      if (m_jpty.grantpt(fdm) < 0) { /* grant access to slave */
        m_jpty.close(fdm);
        return Pair.create(-2, name);
      }
      if (m_jpty.unlockpt(fdm) < 0) { /* clear slave's lock flag */
        m_jpty.close(fdm);
        return Pair.create(-3, name);
      }
      String ptr = m_jpty.ptsname(fdm);
      if (ptr == null) { /* get slave's name */
        m_jpty.close(fdm);
        return Pair.create(-4, name);
      }
      return Pair.create(fdm, ptr);
    }
  }

  public static void setNoEcho(int fd) {
    Termios stermios = new Termios();
    if (JTermios.tcgetattr(fd, stermios) < 0) {
      return;
    }

	/* turn off echo */
    stermios.c_lflag &= ~(JTermios.ECHO | JTermios.ECHOE | PtyHelpers.ECHOK | JTermios.ECHONL);
    /* Turn off the NL to CR/NL mapping ou output.  */
    /*stermios.c_oflag &= ~(ONLCR);*/

    stermios.c_iflag |= (JTermios.IGNCR);

    JTermios.tcsetattr(fd, JTermios.TCSANOW, stermios);
  }

  private Pair<Integer, String> openMaster(boolean console) {
    Pair<Integer, String> master = ptyMasterOpen();
    if (master.first >= 0) {
//       turn off echo
      if (console) {
        setNoEcho(master.first);
      }
    }

    return master;
  }

  public static int changeWindowsSize(int fd, int width, int height) {
    return PtyHelpers.getInstance().setWinSize(fd, new WinSize(width, height));
  }

  public static int raise(int pid, int sig) {
    PtyHelpers.OSFacade m_jpty = PtyHelpers.getInstance();

    int status = m_jpty.killpg(pid, sig);

    if (status == -1) {
      status = m_jpty.kill(pid, sig);
    }

    return status;
  }

  public static int wait0(int pid) {
    PtyHelpers.OSFacade m_jpty = PtyHelpers.getInstance();

    int[] status = new int[1];

    if (pid < 0)
      return -1;

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
}
