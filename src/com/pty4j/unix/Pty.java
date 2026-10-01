/*
 * Copyright (c) 2002, 2010 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.pty4j.unix;

import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.Cleaner;


/**
 * Pty - pseudo terminal support.
 */
public final class Pty {

  private static final Logger LOG = LoggerFactory.getLogger(Pty.class);
  private static final Object PTSNAME_LOCK = new Object();
  private static final Cleaner CLEANER = Cleaner.create();

  private final String mySlaveName;
  private final UnixPtyInputStream myIn;
  private final UnixPtyOutputStream myOut;
  private final Descriptors myFds = new Descriptors();

  public Pty() throws IOException {
    this(false);
  }

  Pty(boolean openOpenTtyToPreserveOutputAfterTermination) throws IOException {
    CLEANER.register(this, myFds); // first, so throwing after allocation leaks nothing
    Pair<Integer, String> masterSlave = openMaster();
    int master = masterSlave.getFirst();
    if (master < 0) {
      throw new IOException("Cannot create pty: " + failedStep(master) + " failed: " + lastError());
    }
    myFds.master = master;
    mySlaveName = masterSlave.getSecond();

    // Without this line, on macOS the slave side of the pty will be automatically closed on process termination, and it
    // will be impossible to read process output after exit. It has a side effect: the child process won't be terminated
    // until we've read all the output from it.
    //
    // See this report for details: https://developer.apple.com/forums/thread/663632
    myFds.slave = openOpenTtyToPreserveOutputAfterTermination ? CLibrary.open(mySlaveName, CLibrary.O_WRONLY) : -1;

    myIn = new UnixPtyInputStream(this);
    myOut = new UnixPtyOutputStream(this);
    if (CLibrary.pipe(myFds.pipe) != 0) {
      String reason = lastError();
      try {
        close();
      }
      catch (IOException ignored) {
      }
      throw new IOException("Cannot create pty: pipe() failed: " + reason);
    }
  }

  public String getSlaveName() {
    return mySlaveName;
  }

  public int getMasterFD() {
    return myFds.master;
  }

  public @NotNull OutputStream getOutputStream() {
    return myOut;
  }

  public @NotNull InputStream getInputStream() {
    return myIn;
  }

  /**
   * Change terminal window size to given width and height.
   * <p>
   * This should only be used when the pseudo terminal is configured for use with a terminal emulation, i.e. when
   * {@link UnixPtyProcess#isConsoleMode()} returns {@code false}.
   *
   * @param winSize new window size
   */
  public void setWindowSize(@NotNull WinSize winSize, @Nullable PtyProcess process) throws UnixPtyException {
    PtyHelpers.getPtyExecutor().setWindowSize(myFds.master, winSize, process);
  }


  /**
   * Returns the current window size of this Pty.
   *
   * @return a {@link com.pty4j.WinSize} instance with information about the master sid of the Pty.
   * @throws UnixPtyException in case obtaining the window size failed.
   */
  public @NotNull WinSize getWinSize(@Nullable PtyProcess process) throws UnixPtyException {
    return PtyHelpers.getPtyExecutor().getWindowSize(myFds.master, process);
  }

  /**
   * Creates a pty pair (master file descriptor and slave path).
   * If creation fails, the master file descriptor is negative.
   *
   * @return the created pty pair
   */
  public static Pair<Integer, String> ptyMasterOpen() {

    PtyHelpers.OSFacade m_jpty = PtyHelpers.getInstance();

    String name = "/dev/ptmx";

    int fdm = getpt(m_jpty);

    if (fdm < 0) {
      return new Pair<>(-1, name);
    }
    if (m_jpty.grantpt(fdm) < 0) { /* grant access to slave */
      m_jpty.close(fdm);
      return new Pair<>(-2, name);
    }
    if (m_jpty.unlockpt(fdm) < 0) { /* clear slave's lock flag */
      m_jpty.close(fdm);
      return new Pair<>(-3, name);
    }

    String ptr = ptsname(m_jpty, fdm);

    if (ptr == null) { /* get slave's name */
      m_jpty.close(fdm);
      return new Pair<>(-4, name);
    }
    return new Pair<>(fdm, ptr);
  }

  /** Four attempts were the most needed with 64 threads opening ptys at once. */
  private static final int MAX_GETPT_ATTEMPTS = 10;

  /**
   * Opens the master, retrying a failed {@code open("/dev/ptmx")}.
   * <p>
   * On macOS the call fails sporadically when threads race for the same pty unit.
   */
  private static int getpt(@NotNull PtyHelpers.OSFacade m_jpty) {
    int fdm = m_jpty.getpt();
    for (int attempt = 1; fdm < 0 && attempt < MAX_GETPT_ATTEMPTS; attempt++) {
      fdm = m_jpty.getpt();
    }
    return fdm;
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

  /** The last native error as text, with the errno value appended. */
  private static String lastError() {
    int errno = PtyHelpers.errno();
    return PtyHelpers.getInstance().strerror(errno) + " (errno " + errno + ")";
  }

  /** The call that made {@link #ptyMasterOpen} return the given negative descriptor. */
  private static String failedStep(int code) {
    switch (code) {
      case -1: return "getpt()";
      case -2: return "grantpt()";
      case -3: return "unlockpt()";
      case -4: return "ptsname()";
      default: return "ptyMasterOpen() (code " + code + ")";
    }
  }

  static int raise(long pid, int sig) {
    PtyHelpers.OSFacade m_jpty = PtyHelpers.getInstance();

    int status = m_jpty.killpg((int)pid, sig);

    if (status == -1) {
      status = m_jpty.kill((int)pid, sig);
    }

    return status;
  }

  public boolean isClosed() {
    return myFds.master == -1;
  }

  public void close() throws IOException {
    myFds.close();
  }

  void breakRead() {
    myFds.breakRead();
  }

  int read(byte[] buf, int off, int len) {
    // Both descriptors are used under the lock: closeMaster() closes them only after
    // acquiring it, so their numbers cannot be reused by another pty while in use here.
    // The read after poll() normally returns at once, because poll() reported data.
    // If the child discards that data first, with tcflush(), the read blocks until the
    // child writes again, and so does close().
    synchronized (myFds.inUseLock) {
      int fd = myFds.master;
      int pipeReadFd = myFds.pipe[0];
      if (fd == -1 || pipeReadFd == -1) return -1;
      boolean haveBytes = poll(pipeReadFd, fd);
      return haveBytes ? CLibrary.read(fd, buf, off, len) : -1;
    }
  }

  private static boolean poll(int pipeFd, int fd) {
    Pollfd[] poll_fds = new Pollfd[]{
      new Pollfd(pipeFd, CLibrary.POLLIN),
      new Pollfd(fd, CLibrary.POLLIN)
    };
    while (CLibrary.poll(poll_fds, -1) <= 0) {
      int errno = CLibrary.errno();
      if (errno != CLibrary.EAGAIN && errno != CLibrary.EINTR) return false;
    }
    return (poll_fds[1].getRevents() & CLibrary.POLLIN) != 0;
  }

  int write(byte[] buf, int off, int len) {
    int masterFd = myFds.master;
    if (masterFd == -1) return -1;
    // No lock here, unlike in read(): a write lock that closeMaster() also took would
    // make close() wait for a writer blocked on a child that does not read its input,
    // and nothing can wake such a writer early, because the pipe only interrupts poll().
    //
    // The price is a small window in which a write racing close() can reach
    // a reused descriptor.
    return CLibrary.write(masterFd, buf, off, len);
  }

  /**
   * The native descriptors, their locks, and the code that closes them.
   * <p>
   * This is the {@link Cleaner}'s action for a {@link Pty} that was never closed,
   * so it must not reference a {@code Pty} object. Otherwise, the {@code Pty}
   * object would never become unreachable and the cleaner would never run.
   */
  private static final class Descriptors implements Runnable {
    private final Object closeLock = new Object();
    /**
     * Held while the descriptors are in use. It ensures that a descriptor
     * number cannot be released and reused by another party.
     */
    private final Object inUseLock = new Object();

    /** -1 means "not open"; the default 0 would be the process's stdin. */
    private volatile int master = -1;
    private volatile int slave = -1;
    private final int[] pipe = {-1, -1};

    /**
     * Wakes up a thread blocked in {@code poll()} inside {@link Pty#read}.
     * <p>
     * Runs under {@code closeLock}, which {@link #close} holds for the whole of
     * {@link #closeMaster}, so a late call finds the pipe already closed and does
     * nothing. Without the lock it could read the descriptor number before
     * {@code closeMaster()} releases it and write after a new pty has reused it =>
     * the new pty would report a false end of stream (#181).
     */
    void breakRead() {
      synchronized (closeLock) {
        int pipeWriteFd = pipe[1];
        if (pipeWriteFd != -1) {
          CLibrary.write(pipeWriteFd, new byte[1], 1);
        }
      }
    }

    /**
     * Closes whatever is still open; a second call is a no-op. The pipe and the master
     * are closed only once no reader is inside {@link Pty#read}, and a concurrent call
     * waits for all of it.
     *
     * @throws IOException if closing the master or the slave failed
     */
    void close() throws IOException {
      synchronized (closeLock) {
        closeMaster();
        if (slave != -1) {
          int fd = slave;
          slave = -1;
          if (CLibrary.close(fd) == -1) {
            throw new IOException("Close error");
          }
        }
      }
    }

    /**
     * Marks the master closed, wakes up the reader, waits for it to leave
     * {@link Pty#read}, then closes the pipe and the master.
     */
    private void closeMaster() throws IOException {
      int masterFd = master;
      if (masterFd == -1) return;
      master = -1;
      breakRead();
      synchronized (inUseLock) {
        CLibrary.close(pipe[0]);
        CLibrary.close(pipe[1]);
        pipe[0] = -1;
        pipe[1] = -1;
        if (CLibrary.close(masterFd) == -1) {
          throw new IOException("Close error");
        }
      }
    }

    /** The cleaning action for a pty that has become unreachable. */
    @Override
    public void run() {
      try {
        close();
      }
      catch (IOException e) {
        LOG.warn("Failed to close the descriptors of a pty that was never closed", e);
      }
    }
  }
}
