/*******************************************************************************
 * Copyright (c) 2000, 2011 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.pty4j.unix;

import com.pty4j.PtyProcess;
import com.pty4j.WinSize;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class UnixPtyProcess extends PtyProcess {
  public int NOOP = 0;
  public int SIGHUP = 1;
  public int SIGINT = 2;
  public int SIGKILL = 9;
  public int SIGTERM = 15;

  /**
   * On Windows, what this does is far from easy to explain. Some of the logic is in the JNI code, some in the
   * spawner.exe code.
   * <p/>
   * <ul>
   * <li>If the process this is being raised against was launched by us (the Spawner)
   * <ul>
   * <li>If the process is a cygwin program (has the cygwin1.dll loaded), then issue a 'kill -SIGINT'. If the 'kill'
   * utility isn't available, send the process a CTRL-C
   * <li>If the process is <i>not</i> a cygwin program, send the process a CTRL-C
   * </ul>
   * <li>If the process this is being raised against was <i>not</i> launched by us, use DebugBreakProcess to interrupt
   * it (sending a CTRL-C is easy only if we share a console with the target process)
   * </ul>
   * <p/>
   * On non-Windows, raising this just raises a POSIX SIGINT
   */
  public int INT = 2;

  /**
   * A fabricated signal number for use on Windows only. Tells the starter program to send a CTRL-C regardless of
   * whether the process is a Cygwin one or not.
   *
   * @since 5.2
   */
  public int CTRLC = 1000; // arbitrary high number to avoid collision

  private int pid = 0;
  private int myStatus;
  private boolean isDone;
  private OutputStream out;
  private InputStream in;
  private InputStream err;
  private Pty myPty;
  private Pty myErrPty;

  public UnixPtyProcess(String[] cmdarray, String[] envp, String dir, Pty pty, Pty errPty) throws IOException {
    if (dir == null) {
      dir = ".";
    }
    if (pty == null) {
      throw new IOException("pty cannot be null");
    }
    myPty = pty;
    myErrPty = errPty;
    execInPty(cmdarray, envp, dir, pty, errPty);
  }

  public Pty getPty() {
    return myPty;
  }

  @Override
  protected void finalize() throws Throwable {
    closeUnusedStreams();
    super.finalize();
  }

  /**
   * See java.lang.Process#getInputStream (); The client is responsible for closing the stream explicitly.
   */
  @Override
  public synchronized InputStream getInputStream() {
    if (null == in) {
      in = myPty.getInputStream();
    }
    return in;
  }

  /**
   * See java.lang.Process#getOutputStream (); The client is responsible for closing the stream explicitly.
   */
  @Override
  public synchronized OutputStream getOutputStream() {
    if (null == out) {
      out = myPty.getOutputStream();
    }
    return out;
  }

  /**
   * See java.lang.Process#getErrorStream (); The client is responsible for closing the stream explicitly.
   */
  @Override
  public synchronized InputStream getErrorStream() {
    if (null == err) {
      if (!myPty.isConsole()) {
        // If Pty is used and it's not in "Console" mode, then stderr is redirected to the Pty's output stream.
        // Therefore, return a dummy stream for error stream.
        err = new InputStream() {
          @Override
          public int read() {
            return -1;
          }
        };
      }
      else {
        err = myErrPty.getInputStream();
      }
    }
    return err;
  }

  /**
   * See java.lang.Process#waitFor ();
   */
  @Override
  public synchronized int waitFor() throws InterruptedException {
    while (!isDone) {
      wait();
    }

    return myStatus;
  }

  /**
   * See java.lang.Process#exitValue ();
   */
  @Override
  public synchronized int exitValue() {
    if (!isDone) {
      throw new IllegalThreadStateException("Process not Terminated");
    }
    return myStatus;
  }

  /**
   * See java.lang.Process#destroy ();
   * <p/>
   * Clients are responsible for explicitly closing any streams that they have requested through getErrorStream(),
   * getInputStream() or getOutputStream()
   */
  @Override
  public synchronized void destroy() {
    // Sends the TERM
    terminate();

    closeUnusedStreams();

    // Grace before using the heavy gone.
    if (!isDone) {
      try {
        wait(1000);
      }
      catch (InterruptedException e) {
      }
    }
    if (!isDone) {
      kill();
    }
  }

  public int interrupt() {
    return Pty.raise(pid, INT);
  }

  public int interruptCTRLC() {
    //    if (Platform.getOS().equals(Platform.OS_WIN32)) {
    //      return raise(pid, CTRLC);
    //    }
    return interrupt();
  }

  public int hangup() {
    return Pty.raise(pid, SIGHUP);
  }

  public int kill() {
    return Pty.raise(pid, SIGKILL);
  }

  public int terminate() {
    return Pty.raise(pid, SIGTERM);
  }

  @Override
  public boolean isRunning() {
    return (Pty.raise(pid, NOOP) == 0);
  }

  private void execInPty(String[] command, String[] environment, String workingDirectory, Pty pty, Pty errPty) throws IOException {
    String cmd = command[0];
    SecurityManager s = System.getSecurityManager();
    if (s != null) {
      s.checkExec(cmd);
    }
    if (environment == null) {
      environment = new String[0];
    }
    final String slaveName = pty.getSlaveName();
    final int masterFD = pty.getMasterFD();
    final String errSlaveName = errPty == null ? null : errPty.getSlaveName();
    final int errMasterFD = errPty == null ? -1 : errPty.getMasterFD();
    final boolean console = pty.isConsole();
    // int fdm = pty.get
    Reaper reaper = new Reaper(command, environment, workingDirectory, slaveName, masterFD, errSlaveName, errMasterFD, console);

    reaper.setDaemon(true);
    reaper.start();
    // Wait until the subprocess is started or error.
    synchronized (this) {
      while (pid == 0) {
        try {
          wait();
        }
        catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }
    if (pid == -1) {
      throw new IOException("Exec_tty error:" + reaper.getErrorMessage());
    }
  }

  /**
   * Close the streams on this side.
   * <p/>
   * We only close the streams that were
   * never used by any client.
   * So, if the stream was not created yet,
   * we create it ourselves and close it
   * right away, so as to release the pipe.
   * Note that even if the stream was never
   * created, the pipe has been allocated in
   * native code, so we need to create the
   * stream and explicitly close it.
   * <p/>
   * We don't close streams the clients have
   * created because we don't know when the
   * client will be finished using them.
   * It is up to the client to close those
   * streams.
   * <p/>
   * But 345164
   */
  private synchronized void closeUnusedStreams() {
    try {
      if (null == err) {
        getErrorStream().close();
      }
    }
    catch (IOException e) {
    }
    try {
      if (null == in) {
        getInputStream().close();
      }
    }
    catch (IOException e) {
    }
    try {
      if (null == out) {
        getOutputStream().close();
      }
    }
    catch (IOException e) {
    }
  }

  int exec(String[] cmd, String[] envp, String dirname, String slaveName, int masterFD,
           String errSlaveName, int errMasterFD, boolean console) throws IOException {
    int pid = -1;

    if (cmd == null) {
      return pid;
    }

    if (envp == null) {
      return pid;
    }

    return PtyHelpers.execPty(cmd[0], cmd, envp, dirname, slaveName, masterFD, errSlaveName, errMasterFD, console);
  }

  int waitFor(int processID) {
    return Pty.wait0(processID);
  }


  @Override
  public void setWinSize(WinSize winSize) {
    myPty.setTerminalSize(winSize);
  }

  @Override
  public WinSize getWinSize() throws IOException {
    return myPty.getWinSize();
  }

  // Spawn a thread to handle the forking and waiting.
  // We do it this way because on linux the SIGCHLD is send to the one thread. So do the forking and then wait in the
  // same thread.
  class Reaper extends Thread {
    private String[] myCommand;
    private String[] myEnv;
    private String myDir;
    private String mySlaveName;
    private int myMasterFD;
    private String myErrSlaveName;
    private int myErrMasterFD;
    private boolean myConsole;
    volatile Throwable myException;

    public Reaper(String[] command, String[] environment, String workingDirectory, String slaveName, int masterFD, String errSlaveName,
                  int errMasterFD, boolean console) {
      super("PtyProcess Reaper");
      myCommand = command;
      myEnv = environment;
      myDir = workingDirectory;
      mySlaveName = slaveName;
      myMasterFD = masterFD;
      myErrSlaveName = errSlaveName;
      myErrMasterFD = errMasterFD;
      myConsole = console;
      myException = null;
    }

    int execute(String[] cmd, String[] env, String dir) throws IOException {
      return exec(cmd, env, dir, mySlaveName, myMasterFD, myErrSlaveName, myErrMasterFD, myConsole);
    }

    @Override
    public void run() {
      try {
        pid = execute(myCommand, myEnv, myDir);
      }
      catch (Exception e) {
        pid = -1;
        myException = e;
      }
      // Tell spawner that the process started.
      synchronized (UnixPtyProcess.this) {
        UnixPtyProcess.this.notifyAll();
      }
      if (pid != -1) {
        // Sync with spawner and notify when done.
        myStatus = waitFor(pid);
        synchronized (UnixPtyProcess.this) {
          isDone = true;
          UnixPtyProcess.this.notifyAll();
        }
        myPty.breakRead();
        if (myErrPty != null) myErrPty.breakRead();
      }
    }

    public String getErrorMessage() {
      return myException != null ? myException.getMessage() : "Unknown reason";
    }
  }
}
