package com.pty4j.windows.winpty;

import com.pty4j.WinSize;
import com.pty4j.util.PtyUtil;
import com.sun.jna.*;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static com.sun.jna.platform.win32.WinBase.INFINITE;
import static com.sun.jna.platform.win32.WinNT.GENERIC_READ;
import static com.sun.jna.platform.win32.WinNT.GENERIC_WRITE;

/**
 * @author traff
 */
public class WinPty {

  private static final Logger LOG = LoggerFactory.getLogger(WinPty.class);

  private static final boolean DEFAULT_MIN_INITIAL_TERMINAL_WINDOW_HEIGHT =
    !Boolean.getBoolean("disable.minimal.initial.terminal.window.height");

  private Pointer myWinpty;

  private WinNT.HANDLE myProcess;
  private NamedPipe myConinPipe;
  private NamedPipe myConoutPipe;
  private NamedPipe myConerrPipe;

  private boolean myChildExited = false;
  private int myStatus = -1;
  private boolean myClosed = false;
  private WinSize myLastWinSize;

  private int openInputStreamCount = 0;

  WinPty(@NotNull String cmdline,
         @Nullable String cwd,
         @NotNull String env,
         boolean consoleMode,
         @Nullable Integer initialColumns,
         @Nullable Integer initialRows,
         boolean enableAnsiColor) throws WinPtyException, IOException {
    int cols = initialColumns != null ? initialColumns : Integer.getInteger("win.pty.cols", 80);
    int rows = getInitialRows(initialRows);
    IntByReference errCode = new IntByReference();
    PointerByReference errPtr = new PointerByReference(null);
    Pointer agentCfg = null;
    Pointer spawnCfg = null;
    Pointer winpty = null;
    WinNT.HANDLEByReference processHandle = new WinNT.HANDLEByReference();
    NamedPipe coninPipe = null;
    NamedPipe conoutPipe = null;
    NamedPipe conerrPipe = null;

    try {
      // Configure the winpty agent.
      long agentFlags = 0;
      if (consoleMode) {
        agentFlags = WinPtyLib.WINPTY_FLAG_CONERR | WinPtyLib.WINPTY_FLAG_PLAIN_OUTPUT;
        if (enableAnsiColor) {
          agentFlags |= WinPtyLib.WINPTY_FLAG_COLOR_ESCAPES;
        }
      }
      agentCfg = INSTANCE.winpty_config_new(agentFlags, null);
      if (agentCfg == null) {
        throw new WinPtyException("winpty agent cfg is null");
      }
      INSTANCE.winpty_config_set_initial_size(agentCfg, cols, rows);
      myLastWinSize = new WinSize(cols, rows);

      // Start the agent.
      winpty = INSTANCE.winpty_open(agentCfg, errPtr);
      if (winpty == null) {
        WString errMsg = INSTANCE.winpty_error_msg(errPtr.getValue());
        String errorMessage = errMsg.toString();
        if ("ConnectNamedPipe failed: Windows error 232".equals(errorMessage)) {
          errorMessage += "\n" + suggestFixForError232();
        }
        throw new WinPtyException("Error starting winpty: " + errorMessage);
      }

      // Connect the pipes.  These calls return immediately (i.e. they don't block).
      coninPipe = NamedPipe.connectToServer(INSTANCE.winpty_conin_name(winpty).toString(), GENERIC_WRITE);
      conoutPipe = NamedPipe.connectToServer(INSTANCE.winpty_conout_name(winpty).toString(), GENERIC_READ);
      if (consoleMode) {
        conerrPipe = NamedPipe.connectToServer(INSTANCE.winpty_conerr_name(winpty).toString(), GENERIC_READ);
      }

      for (int i = 0; i < 5; i++) {
        boolean result = INSTANCE.winpty_set_size(winpty, cols, rows, null);
        if (!result) {
          LOG.info("Cannot resize to workaround extra newlines issue");
          break;
        }
        try {
          Thread.sleep(10);
        }
        catch (InterruptedException e) {
          e.printStackTrace();
        }
      }

      // Spawn a child process.
      spawnCfg = INSTANCE.winpty_spawn_config_new(
          WinPtyLib.WINPTY_SPAWN_FLAG_AUTO_SHUTDOWN |
              WinPtyLib.WINPTY_SPAWN_FLAG_EXIT_AFTER_SHUTDOWN,
          null,
          toWString(cmdline),
          toWString(cwd),
          toWString(env),
          null);
      if (spawnCfg == null) {
        throw new WinPtyException("winpty spawn cfg is null");
      }
      if (!INSTANCE.winpty_spawn(winpty, spawnCfg, processHandle, null, errCode, errPtr)) {
        WString errMsg = INSTANCE.winpty_error_msg(errPtr.getValue());
        throw new WinPtyException("Error running process: " + errMsg.toString() + ". Code " + errCode.getValue());
      }

      // Success!  Save the values we want and let the `finally` block clean up the rest.

      myWinpty = winpty;
      myProcess = processHandle.getValue();
      myConinPipe = coninPipe;
      myConoutPipe = conoutPipe;
      myConerrPipe = conerrPipe;
      openInputStreamCount = consoleMode ? 2 : 1;

      // Designate a thread to wait for the process to exit.
      Thread waitForExit = new WaitForExitThread();
      waitForExit.setDaemon(true);
      waitForExit.start();

      winpty = null;
      processHandle.setValue(null);
      coninPipe = conoutPipe = conerrPipe = null;

    } finally {
      INSTANCE.winpty_error_free(errPtr.getValue());
      INSTANCE.winpty_config_free(agentCfg);
      INSTANCE.winpty_spawn_config_free(spawnCfg);
      INSTANCE.winpty_free(winpty);
      if (processHandle.getValue() != null) {
        Kernel32.INSTANCE.CloseHandle(processHandle.getValue());
      }
      closeNamedPipeQuietly(coninPipe);
      closeNamedPipeQuietly(conoutPipe);
      closeNamedPipeQuietly(conerrPipe);
    }
  }

  @NotNull
  private static String suggestFixForError232() {
    try {
      File dllFile = new File(getLibraryPath());
      File exeFile = new File(dllFile.getParentFile(), "winpty-agent.exe");
      return "This error can occur due to antivirus blocking winpty from creating a pty. Please exclude the following files in your antivirus:\n" +
             " - " + exeFile.getAbsolutePath() + "\n" +
             " - " + dllFile.getAbsolutePath();
    }
    catch (Exception e) {
      return e.getMessage();
    }
  }

  private int getInitialRows(@Nullable Integer initialRows) {
    if (initialRows != null) {
      return initialRows;
    }
    Integer rows = Integer.getInteger("win.pty.rows");
    if (rows != null) {
      return rows;
    }
    try {
      WindowsVersion.getVersion();
      // workaround for https://github.com/Microsoft/console/issues/270
      return DEFAULT_MIN_INITIAL_TERMINAL_WINDOW_HEIGHT ? 1 : 25;
    }
    catch (Exception e) {
      e.printStackTrace();
      return 25;
    }
  }

  private static void closeNamedPipeQuietly(NamedPipe pipe) {
    try {
      if (pipe != null) {
        pipe.close();
      }
    } catch (IOException e) {
    }
  }

  private static WString toWString(String string) {
    return string == null ? null : new WString(string);
  }

  synchronized void setWinSize(@NotNull WinSize winSize) throws IOException {
    if (myClosed) {
      throw new IOException("Unable to set window size: closed=" + myClosed + ", winSize=" + winSize);
    }
    boolean result = INSTANCE.winpty_set_size(myWinpty, winSize.getColumns(), winSize.getRows(), null);
    if (result) {
      myLastWinSize = new WinSize(winSize.getColumns(), winSize.getRows());
    }
  }

  synchronized @NotNull WinSize getWinSize() throws IOException {
    // The implementation might be improved after https://github.com/rprichard/winpty/issues/153
    WinSize lastWinSize = myLastWinSize;
    if (myClosed || lastWinSize == null) {
      throw new IOException("Unable to get window size: closed=" + myClosed + ", lastWinSize=" + lastWinSize);
    }
    return new WinSize(lastWinSize.getColumns(), lastWinSize.getRows());
  }

  synchronized void decrementOpenInputStreamCount() {
    openInputStreamCount--;
    if (openInputStreamCount == 0) {
      close();
    }
  }

  // Close the winpty_t object, which disconnects libwinpty from the winpty
  // agent process.  The agent will then close the hidden console, killing
  // everything attached to it.
  synchronized void close() {
    // This function can be called from WinPty.finalize, so its member fields
    // may have already been finalized.  The JNA Pointer class has no finalizer,
    // so it's safe to use, and the various JNA Library objects are static, so
    // they won't ever be collected.
    if (myClosed) {
      return;
    }
    INSTANCE.winpty_free(myWinpty);
    myWinpty = null;
    myClosed = true;
    closeUnusedProcessHandle();
  }

  private synchronized void closeUnusedProcessHandle() {
    // Keep the process handle open until both conditions are met:
    //  1. The process has exited.
    //  2. We have disconnected from the agent, by closing the winpty_t
    //     object.
    // As long as the process handle is open, Windows will not reuse the child
    // process' PID.
    // https://blogs.msdn.microsoft.com/oldnewthing/20110107-00/?p=11803
    if (myClosed && myChildExited && myProcess != null) {
      Kernel32.INSTANCE.CloseHandle(myProcess);
      myProcess = null;
    }
  }

  // Returns true if the child process is still running.  The winpty_t and
  // WinPty objects may be closed/freed either before or after the child
  // process exits.
  synchronized boolean isRunning() {
    return !myChildExited;
  }

  // Waits for the child process to exit.
  synchronized int waitFor() throws InterruptedException {
    while (!myChildExited) {
      wait();
    }
    return myStatus;
  }

  synchronized int getChildProcessId() {
    if (myClosed) {
      return -1;
    }
    return Kernel32.INSTANCE.GetProcessId(myProcess);
  }

  synchronized int exitValue() {
    if (!myChildExited) {
      throw new IllegalThreadStateException("Process not Terminated");
    }
    return myStatus;
  }

  @Override
  protected void finalize() throws Throwable {
    close();
    super.finalize();
  }

  NamedPipe getInputPipe() {
    return myConoutPipe;
  }

  NamedPipe getOutputPipe() {
    return myConinPipe;
  }

  NamedPipe getErrorPipe() {
    return myConerrPipe;
  }

  @Nullable
  String getWorkingDirectory() throws IOException {
    if (myClosed) {
      return null;
    }
    int bufferLength = 1024;
    Pointer buffer = new Memory(Native.WCHAR_SIZE * bufferLength);
    PointerByReference errPtr = new PointerByReference();
    try {
      int result = INSTANCE.winpty_get_current_directory(myWinpty, bufferLength, buffer, errPtr);
      if (result > 0) {
        return buffer.getWideString(0);
      }
      WString message = INSTANCE.winpty_error_msg(errPtr.getValue());
      int code = INSTANCE.winpty_error_code(errPtr.getValue());
      throw new IOException("winpty_get_current_directory failed, code: " + code + ", message: " + message);
    }
    finally {
      INSTANCE.winpty_error_free(errPtr.getValue());
    }
  }

  int getConsoleProcessList() throws IOException {
    if (myClosed) {
      return 0;
    }
    int MAX_COUNT = 64;
    Pointer buffer = new Memory(Native.LONG_SIZE * MAX_COUNT);
    PointerByReference errPtr = new PointerByReference();
    try {
      int actualProcessCount = INSTANCE.winpty_get_console_process_list(myWinpty, buffer, MAX_COUNT, errPtr);
      if (actualProcessCount == 0) {
        WString message = INSTANCE.winpty_error_msg(errPtr.getValue());
        int code = INSTANCE.winpty_error_code(errPtr.getValue());
        throw new IOException("winpty_get_console_process_list failed, code: " + code + ", message: " + message);
      }
      // use buffer.getIntArray(0, actualProcessCount); to get actual PIDs
      return actualProcessCount;
    }
    finally {
      INSTANCE.winpty_error_free(errPtr.getValue());
    }
  }

  // It is mostly possible to avoid using this thread; instead, the above
  // methods could call WaitForSingleObject themselves, using either a 0 or
  // INFINITE timeout as appropriate.  It is tricky, though, because we need
  // to avoid closing the process handle as long as any threads are waiting on
  // it, but we can't do an INFINITE wait inside a synchronized method.  It
  // could be done using an extra reference count, or by using DuplicateHandle
  // for INFINITE waits.
  private class WaitForExitThread extends Thread {
    private IntByReference myStatusByRef = new IntByReference(-1);

    @Override
    public void run() {
      Kernel32.INSTANCE.WaitForSingleObject(myProcess, INFINITE);
      Kernel32.INSTANCE.GetExitCodeProcess(myProcess, myStatusByRef);
      synchronized (WinPty.this) {
        WinPty.this.myChildExited = true;
        WinPty.this.myStatus = myStatusByRef.getValue();
        closeUnusedProcessHandle();
        WinPty.this.notifyAll();
      }
    }
  }

  static final Kern32 KERNEL32 = Native.loadLibrary("kernel32", Kern32.class);

  interface Kern32 extends Library {
    boolean PeekNamedPipe(WinNT.HANDLE hFile,
                          Pointer lpBuffer,
                          int nBufferSize,
                          IntByReference lpBytesRead,
                          IntByReference lpTotalBytesAvail,
                          IntByReference lpBytesLeftThisMessage);

    boolean ReadFile(WinNT.HANDLE file, Pointer buf, int len, IntByReference actual, Pointer over);

    boolean WriteFile(WinNT.HANDLE file, Pointer buf, int len, IntByReference actual, Pointer over);

    boolean GetOverlappedResult(WinNT.HANDLE file, Pointer over, IntByReference actual, boolean wait);

    WinNT.HANDLE CreateNamedPipeA(String lpName,
                                  int dwOpenMode,
                                  int dwPipeMode,
                                  int nMaxInstances,
                                  int nOutBufferSize,
                                  int nInBufferSize,
                                  int nDefaultTimeout,
                                  WinBase.SECURITY_ATTRIBUTES securityAttributes);

    boolean ConnectNamedPipe(WinNT.HANDLE hNamedPipe, WinBase.OVERLAPPED overlapped);

    boolean CloseHandle(WinNT.HANDLE hObject);

    WinNT.HANDLE CreateEventA(WinBase.SECURITY_ATTRIBUTES lpEventAttributes, boolean bManualReset, boolean bInitialState, String lpName);

    int GetLastError();

    int WaitForSingleObject(WinNT.HANDLE hHandle, int dwMilliseconds);

    boolean CancelIo(WinNT.HANDLE hFile);

    int GetCurrentProcessId();
  }

  private static WinPtyLib INSTANCE = Native.loadLibrary(getLibraryPath(), WinPtyLib.class);

  private static String getLibraryPath() {
    try {
      return PtyUtil.resolveNativeLibrary().getAbsolutePath();
    }
    catch (Exception e) {
      throw new IllegalStateException("Couldn't detect jar containing folder", e);
    }
  }

  private interface WinPtyLib extends Library {
    /*
     * winpty API.
     */

    long WINPTY_FLAG_CONERR = 1;
    long WINPTY_FLAG_PLAIN_OUTPUT = 2;
    long WINPTY_FLAG_COLOR_ESCAPES = 4;

    long WINPTY_SPAWN_FLAG_AUTO_SHUTDOWN = 1;
    long WINPTY_SPAWN_FLAG_EXIT_AFTER_SHUTDOWN = 2;

    int winpty_error_code(Pointer err);
    WString winpty_error_msg(Pointer err);
    void winpty_error_free(Pointer err);

    Pointer winpty_config_new(long flags, PointerByReference err);
    void winpty_config_free(Pointer cfg);
    void winpty_config_set_initial_size(Pointer cfg, int cols, int rows);
    Pointer winpty_open(Pointer cfg, PointerByReference err);

    WString winpty_conin_name(Pointer wp);
    WString winpty_conout_name(Pointer wp);
    WString winpty_conerr_name(Pointer wp);

    Pointer winpty_spawn_config_new(long flags,
                                    WString appname,
                                    WString cmdline,
                                    WString cwd,
                                    WString env,
                                    PointerByReference err);

    void winpty_spawn_config_free(Pointer cfg);

    boolean winpty_spawn(Pointer winpty,
                         Pointer cfg,
                         WinNT.HANDLEByReference process_handle,
                         WinNT.HANDLEByReference thread_handle,
                         IntByReference create_process_error,
                         PointerByReference err);

    boolean winpty_set_size(Pointer winpty, int cols, int rows, PointerByReference err);

    int winpty_get_console_process_list(Pointer winpty,
                                        Pointer processList,
                                        int processCount,
                                        PointerByReference err);

    int winpty_get_current_directory(Pointer winpty,
                                     int nBufferLength,
                                     Pointer lpBuffer,
                                     PointerByReference err);
    void winpty_free(Pointer winpty);
  }
}
