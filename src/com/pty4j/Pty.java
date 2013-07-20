/*******************************************************************************
 * Copyright (c) 2002, 2010 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.pty4j;

import com.sun.jna.Platform;
import jtermios.JTermios;
import jtermios.Termios;

import java.io.File;
import java.io.IOException;


/**
 * Pty - pseudo terminal support.
 */
public class Pty {
  private static PtyHelpers.JPtyInterface m_jpty;

  static {
    if (Platform.isMac()) {
      m_jpty = new com.pty4j.macosx.JPtyImpl();
    } else {
      throw new RuntimeException("Pty4J has no support for OS " + System.getProperty("os.name"));
    }
  }

  private static final int STDIN_FILENO = 0;
  private static final int STDOUT_FILENO = 1;
  private static final int STDERR_FILENO = 2;


  private final boolean myConsole;
  private String mySlaveName;
  private PTYInputStream myIn;
  private PTYOutputStream myOut;

  private int myMaster;

  private static boolean setTerminalSizeErrorAlreadyLogged;

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
   * @return whether this pseudo terminal is for use with the Eclipse console.
   */
  public final boolean isMyConsole() {
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
   * {@link #isMyConsole()} returns {@code false}.
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
   * @return a {@link WinSize} instance with information about the master side
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

  static int changeWindowsSize(int fd, int width, int height) {
    return m_jpty.setWinSize(fd, new WinSize(width, height));
  }

  static int execPty(String full_path, String[] argv, String[] envp,
                     String dirpath, int[] channels, String pts_name, int fdm, boolean console) {
    int[] pipe2 = new int[2];
    int childpid;

	/*
     *  Make sure we can create our pipes before forking.
	 */
    if (channels != null && console) {
      if (m_jpty.pipe(pipe2) < 0) {
//        fprintf(stderr, "%s(%d): returning due to error: %s\n", __FUNCTION__, __LINE__, strerror(errno));
        return -1;
      }
    }

    childpid = m_jpty.fork();

    if (childpid < 0) {
//      fprintf(stderr, "%s(%d): returning due to error: %s\n", __FUNCTION__, __LINE__, strerror(errno));
      return -1;
    } else if (childpid == 0) { /* child */

//      chdir(dirpath); TODO


      if (channels != null) {
        if (console && m_jpty.setsid() < 0) {
//          perror("setsid()");
          return -1;
        }

        int fds = ptySlaveOpen(fdm, pts_name);


        if (fds < 0) {
//          fprintf(stderr, "%s(%d): returning due to error: %s\n", __FUNCTION__, __LINE__, strerror(errno));
          return -1;
        }

        m_jpty.login_tty(fds);

        //TODO: this hangs pty on mac
        //but we don't need it
//        if (JTermios.tcsetattr(fds, JTermios.TCSANOW, PtyHelpers.createTermios()) != 0) { 
//          throw new PtyException("tcsetattr(" + fds + ", TCSANOW, &terminalAttributes) with IXON cleared", fds);
//        }

			/* Close the read end of pipe2 */
        if (console && m_jpty.close(pipe2[0]) == -1) {
//          perror("close(pipe2[0]))");
        }

			/* close the master, no need in the child */
        m_jpty.close(fdm);

        if (console) {
          Pty.setNoEcho(fds);

          int pid = m_jpty.getpid();
          if (m_jpty.setpgid(pid, pid) < 0) {
//            perror("setpgid()");
            return -1;
          }
        }

			/* redirections */
        m_jpty.dup2(fds, STDIN_FILENO);   /* dup stdin */
        m_jpty.dup2(fds, STDOUT_FILENO);  /* dup stdout */

        if (console) {
          m_jpty.dup2(pipe2[1], STDERR_FILENO);  /* dup stderr */
        } else {
          m_jpty.dup2(fds, STDERR_FILENO);  /* dup stderr */
        }

        m_jpty.close(fds);  /* done with fds. */
      }

		/* Close all the fd's in the child */
      {
//        int fdlimit = 16;// m_jpty.sysconf(_SC_OPEN_MAX); TODO
//        int fd = 3;
//
//        while (fd < fdlimit) {
//          m_jpty.close(fd++);
//        }
      }

      if (envp[0] == null) {
        m_jpty.execv(full_path, argv);
      } else {
        m_jpty.execve(full_path, argv, envp);
      }

      System.exit(127);
    } else if (childpid != 0) { /* parent */
      if (console) {
        Pty.setNoEcho(fdm);
      }
      if (channels != null) {
        channels[0] = fdm; /* Input Stream. */
        channels[1] = fdm; /* Output Stream.  */
        if (console) {
                /* close the write end of pipe1 */
          if (m_jpty.close(pipe2[1]) == -1) {
//            perror("close(pipe2[1])");
          }
          channels[2] = pipe2[0]; /* stderr Stream.  */
        } else {
          channels[2] = fdm; /* Error Stream.  */
        }
      }

      return childpid;
    }

    return -1;                  /*NOT REACHED */
  }

  static int ptySlaveOpen(int fdm, String pts_name) {
    int fds;
    /* following should allocate controlling terminal */
    fds = JTermios.open(pts_name, JTermios.O_RDWR);
    if (fds < 0) {
      JTermios.close(fdm);
      return -5;
    }

	/*  TIOCSCTTY is the BSD way to acquire a controlling terminal. */
//      if (JTermios.ioctl(fds, PtyHelpers.TIOCSCTTY, new int[]{}) < 0) { TODO
    // ignore error: this is expected in console-mode
//      }
    return fds;
  }

  static int raise(int pid, int sig) {
    int status = m_jpty.killpg(pid, sig);

    if (status == -1) {
      status = m_jpty.kill(pid, sig);
    }

    return status;
  }

  static int wait0(int pid) {
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


  public static PtyProcess exec(String[] command) throws IOException {
    return exec(command, null, null);
  }

  public static PtyProcess exec(String[] command, String[] environment) throws IOException {
    return exec(command, environment, null);
  }


  public static PtyProcess exec(String[] command, String[] environment, File workingDirectory) throws IOException {
    return new PtyProcess(command, environment, workingDirectory, new Pty());
  }
}
