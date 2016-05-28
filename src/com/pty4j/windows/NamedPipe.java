package com.pty4j.windows;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import static com.pty4j.windows.WinPty.KERNEL32;
import static com.sun.jna.platform.win32.WinBase.INVALID_HANDLE_VALUE;

public class NamedPipe {
  private WinNT.HANDLE myHandle;
  boolean myCloseHandleOnFinalize;

  private WinNT.HANDLE shutdownEvent;
  private AtomicBoolean shutdownFlag = new AtomicBoolean();

  private ReentrantLock readLock = new ReentrantLock();
  private ReentrantLock writeLock = new ReentrantLock();

  private Memory readBuffer = new Memory(16 * 1024);
  private Memory writeBuffer = new Memory(16 * 1024);

  private WinNT.HANDLE readEvent;
  private WinNT.HANDLE writeEvent;

  private WinNT.HANDLE[] readWaitHandles;
  private WinNT.HANDLE[] writeWaitHandles;

  private IntByReference readActual = new IntByReference();
  private IntByReference writeActual = new IntByReference();
  private IntByReference peekActual = new IntByReference();

  private WinNT.OVERLAPPED readOver = new WinNT.OVERLAPPED();
  private WinNT.OVERLAPPED writeOver = new WinNT.OVERLAPPED();

  /**
   * The NamedPipe object closes the given handle when it is closed.  If you
   * do not own the handle, call markClosed instead of close, or call the Win32
   * DuplicateHandle API to get a new handle.
   */
  public NamedPipe(WinNT.HANDLE handle, boolean closeHandleOnFinalize) {
    myHandle = handle;
    myCloseHandleOnFinalize = closeHandleOnFinalize;
    shutdownEvent = Kernel32.INSTANCE.CreateEvent(null, true, false, null);
    readEvent = Kernel32.INSTANCE.CreateEvent(null, true, false, null);
    writeEvent = Kernel32.INSTANCE.CreateEvent(null, true, false, null);
    readWaitHandles = new WinNT.HANDLE[] { readEvent, shutdownEvent };
    writeWaitHandles = new WinNT.HANDLE[] { writeEvent, shutdownEvent };
  }

  public static NamedPipe connectToServer(String name, int desiredAccess) throws IOException {
    WinNT.HANDLE handle = Kernel32.INSTANCE.CreateFile(
        name, desiredAccess, 0, null, WinNT.OPEN_EXISTING, 0, null);
    if (handle == INVALID_HANDLE_VALUE) {
      throw new IOException("Error connecting to pipe '" + name + "': " + Native.getLastError());
    }
    return new NamedPipe(handle, true);
  }

  /**
   * Returns -1 on any kind of error, including a pipe that isn't connected or
   * a NamedPipe instance that has been closed.
   */
  public int read(byte[] buf, int off, int len) {
    if (buf == null) {
      throw new NullPointerException();
    }
    if (off < 0 || len < 0 || len > buf.length - off) {
      throw new IndexOutOfBoundsException();
    }
    readLock.lock();
    try {
      if (shutdownFlag.get()) {
        return -1;
      }
      if (len == 0) {
        return 0;
      }
      if (readBuffer.size() < len) {
        readBuffer = new Memory(len);
      }
      readOver.hEvent = readEvent;
      readOver.write();
      readActual.setValue(0);
      boolean success = KERNEL32.ReadFile(myHandle, readBuffer, len, readActual, readOver.getPointer());
      if (!success && Native.getLastError() == WinNT.ERROR_IO_PENDING) {
        int waitRet = Kernel32.INSTANCE.WaitForMultipleObjects(
                readWaitHandles.length, readWaitHandles, false, WinNT.INFINITE);
        if (waitRet != WinNT.WAIT_OBJECT_0) {
          KERNEL32.CancelIo(myHandle);
        }
        success = KERNEL32.GetOverlappedResult(myHandle, readOver.getPointer(), readActual, true);
      }
      int actual = readActual.getValue();
      if (!success || actual <= 0) {
        return -1;
      }
      readBuffer.read(0, buf, off, actual);
      return actual;
    } finally {
      readLock.unlock();
    }
  }

  /**
   * This function ignores I/O errors.
   */
  public void write(byte[] buf, int off, int len) {
    if (buf == null) {
      throw new NullPointerException();
    }
    if (off < 0 || len < 0 || len > buf.length - off) {
      throw new IndexOutOfBoundsException();
    }
    writeLock.lock();
    try {
      if (shutdownFlag.get()) {
        return;
      }
      if (len == 0) {
        return;
      }
      if (writeBuffer.size() < len) {
        writeBuffer = new Memory(len);
      }
      writeBuffer.write(0, buf, off, len);
      writeOver.hEvent = writeEvent;
      writeOver.write();
      writeActual.setValue(0);
      boolean success = KERNEL32.WriteFile(myHandle, writeBuffer, len, writeActual, writeOver.getPointer());
      if (!success && Native.getLastError() == WinNT.ERROR_IO_PENDING) {
        int waitRet = Kernel32.INSTANCE.WaitForMultipleObjects(
                writeWaitHandles.length, writeWaitHandles, false, WinNT.INFINITE);
        if (waitRet != WinNT.WAIT_OBJECT_0) {
          KERNEL32.CancelIo(myHandle);
        }
        KERNEL32.GetOverlappedResult(myHandle, writeOver.getPointer(), writeActual, true);
      }
    } finally {
      writeLock.unlock();
    }
  }

  public int available() throws IOException {
    readLock.lock();
    try {
      if (shutdownFlag.get()) {
        return -1;
      }
      peekActual.setValue(0);
      if (!KERNEL32.PeekNamedPipe(myHandle, null, 0, null, peekActual, null)) {
        throw new IOException("PeekNamedPipe failed");
      }
      return peekActual.getValue();
    } finally {
      readLock.unlock();
    }
  }

  /**
   * Like close(), but leave the pipe handle itself alone.
   */
  public synchronized void markClosed() {
    closeImpl();
  }

  /**
   * Shut down the NamedPipe cleanly and quickly.  Use an event to abort any
   * pending I/O, then acquire the locks to ensure that the I/O has ended.
   * Once everything has stopped, close all the native handles.
   *
   * Mark the function synchronized to ensure that a later call cannot return
   * earlier.
   */
  public synchronized void close() throws IOException {
    if (!closeImpl()) {
      return;
    }
    if (!Kernel32.INSTANCE.CloseHandle(myHandle)) {
      throw new IOException("Close error:" + Native.getLastError());
    }
  }

  private boolean closeImpl() {
    if (shutdownFlag.getAndSet(true)) {
      // If shutdownFlag is already set, then the handles are already closed.
      return false;
    }
    Kernel32.INSTANCE.SetEvent(shutdownEvent);
    readLock.lock();
    writeLock.lock();
    writeLock.unlock();
    readLock.unlock();
    Kernel32.INSTANCE.CloseHandle(shutdownEvent);
    Kernel32.INSTANCE.CloseHandle(readEvent);
    Kernel32.INSTANCE.CloseHandle(writeEvent);
    return true;
  }

  @Override
  protected void finalize() throws Throwable {
    if (myCloseHandleOnFinalize) {
      close();
    }
    super.finalize();
  }
}
