package com.pty4j.windows.conpty;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessOptions;
import com.pty4j.WinSize;
import com.pty4j.util.PtyUtil;
import com.pty4j.windows.WinHelper;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.ptr.IntByReference;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static com.sun.jna.platform.win32.WinBase.INFINITE;

public final class WinConPtyProcess extends PtyProcess {

  private static final Logger LOG = LoggerFactory.getLogger('#' + WinConPtyProcess.class.getName());

  private final PseudoConsole pseudoConsole;
  private final WinBase.PROCESS_INFORMATION processInformation;
  private final WinHandleInputStream myInputStream;
  private final WinHandleOutputStream myOutputStream;
  private final ExitCodeInfo myExitCodeInfo = new ExitCodeInfo();
  private final List<String> myCommand;

  public WinConPtyProcess(@NotNull PtyProcessOptions options) throws IOException {
    myCommand = Arrays.asList(options.getCommand());
    checkExec(myCommand);
    Pipe inPipe = new Pipe();
    Pipe outPipe = new Pipe();
    pseudoConsole = new PseudoConsole(getInitialSize(options), inPipe.getReadPipe(), outPipe.getWritePipe());
    processInformation = ProcessUtils.startProcess(pseudoConsole, options.getCommand(), options.getDirectory(),
            options.getEnvironment());
    if (!Kernel32.INSTANCE.CloseHandle(inPipe.getReadPipe())) {
      throw new LastErrorExceptionEx("CloseHandle stdin after process creation");
    }
    if (!Kernel32.INSTANCE.CloseHandle(outPipe.getWritePipe())) {
      throw new LastErrorExceptionEx("CloseHandle stdout after process creation");
    }
    myInputStream = new WinHandleInputStream(outPipe.getReadPipe());
    myOutputStream = new WinHandleOutputStream(inPipe.getWritePipe());
    startAwaitingThread(Arrays.asList(options.getCommand()));
  }

  private static void checkExec(@NotNull List<String> command) {
    String exec = command.size() > 0 ? command.get(0) : null;
    SecurityManager s = System.getSecurityManager();
    if (s != null && exec != null) {
      s.checkExec(exec);
    }
  }

  public @NotNull List<String> getCommand() {
    return myCommand;
  }

  private static @NotNull WinSize getInitialSize(@NotNull PtyProcessOptions options) {
    return new WinSize(PtyUtil.requireNonNullElse(options.getInitialColumns(), 80),
        PtyUtil.requireNonNullElse(options.getInitialRows(), 25));
  }

  private void startAwaitingThread(@NotNull List<String> command) {
    String commandLine = String.join(" ", command);
    Thread t = new Thread(() -> {
      int result = Kernel32.INSTANCE.WaitForSingleObject(processInformation.hProcess, INFINITE);
      int exitCode = -100;
      if (result == WinBase.WAIT_OBJECT_0) {
        IntByReference exitCodeRef = new IntByReference();
        if (!Kernel32.INSTANCE.GetExitCodeProcess(processInformation.hProcess, exitCodeRef)) {
          LOG.info(LastErrorExceptionEx.getErrorMessage("GetExitCodeProcess(" + commandLine + ")"));
        } else {
          exitCode = exitCodeRef.getValue();
        }
      } else {
        if (result == WinBase.WAIT_FAILED) {
          LOG.info(LastErrorExceptionEx.getErrorMessage("WaitForSingleObject(" + commandLine + ")"));
        } else {
          LOG.info("WaitForSingleObject(" + commandLine + ") returned " + result);
        }
      }
      myExitCodeInfo.setExitCode(exitCode);
      myInputStream.awaitAvailableOutputIsRead();
      cleanup();
    }, "WinConPtyProcess WaitFor " + commandLine);
    t.setDaemon(true);
    t.start();
  }

  @Override
  public void setWinSize(@NotNull WinSize winSize) {
    try {
      pseudoConsole.resize(winSize);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public @NotNull WinSize getWinSize() throws IOException {
    return pseudoConsole.getWinSize();
  }

  @Override
  public long pid() {
    return processInformation.dwProcessId.longValue();
  }

  @Override
  public OutputStream getOutputStream() {
    return myOutputStream;
  }

  @Override
  public InputStream getInputStream() {
    return myInputStream;
  }

  @Override
  public InputStream getErrorStream() {
    return NullInputStream.INSTANCE;
  }

  @Override
  public int waitFor() throws InterruptedException {
    return myExitCodeInfo.waitFor();
  }

  @Override
  public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
    return myExitCodeInfo.waitFor(timeout, unit);
  }

  @Override
  public int exitValue() {
    Integer exitCode = myExitCodeInfo.getExitCodeNow();
    if (exitCode != null) {
      return exitCode;
    }
    throw new IllegalThreadStateException("Process is still alive");
  }

  @Override
  public boolean isAlive() {
    return myExitCodeInfo.getExitCodeNow() == null;
  }

/*
  @Override
  public boolean supportsNormalTermination() {
    return false;
  }
*/

  @Override
  public void destroy() {
    if (!isAlive()) {
      return;
    }
    if (!Kernel32.INSTANCE.TerminateProcess(processInformation.hProcess, 1)) {
      LOG.info("Failed to terminate process with pid " + processInformation.dwProcessId + ". "
          + LastErrorExceptionEx.getErrorMessage("TerminateProcess"));
    }
  }

  public @NotNull String getWorkingDirectory() throws IOException {
    return WinHelper.getCurrentDirectory(pid());
  }

  public int getConsoleProcessCount() throws IOException {
    return ConsoleProcessListFetcher.getConsoleProcessCount(pid());
  }

  private void cleanup() {
    try {
      ProcessUtils.closeHandles(processInformation);
    } catch (IOException e) {
      e.printStackTrace();
    }
    pseudoConsole.close();
    try {
      myInputStream.close();
    } catch (IOException e) {
      LOG.info("Cannot close input stream", e);
    }
    try {
      myOutputStream.close();
    } catch (IOException e) {
      LOG.info("Cannot close output stream", e);
    }
  }

  private static class ExitCodeInfo {
    private Integer myExitCode = null;
    private final ReentrantLock myLock = new ReentrantLock();
    private final Condition myCondition = myLock.newCondition();

    public void setExitCode(int exitCode) {
      myLock.lock();
      try {
        myExitCode = exitCode;
        myCondition.signalAll();
      } finally {
        myLock.unlock();
      }
    }

    public int waitFor() throws InterruptedException {
      myLock.lock();
      try {
        while (myExitCode == null) {
          myCondition.await();
        }
        return myExitCode;
      } finally {
        myLock.unlock();
      }
    }

    Integer getExitCodeNow() {
      myLock.lock();
      try {
        return myExitCode;
      } finally {
        myLock.unlock();
      }
    }

    public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
      long startTime = System.nanoTime();
      long remaining = unit.toNanos(timeout);
      myLock.lock();
      try {
        while (myExitCode == null && remaining > 0) {
          //noinspection ResultOfMethodCallIgnored
          myCondition.awaitNanos(remaining);
          remaining = unit.toNanos(timeout) - (System.nanoTime() - startTime);
        }
        return myExitCode != null;
      } finally {
        myLock.unlock();
      }
    }
  }
}
