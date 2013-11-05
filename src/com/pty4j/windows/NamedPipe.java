package com.pty4j.windows;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;

/**
 * @author traff
 */
public class NamedPipe {
  private WinNT.HANDLE myHandle;

  private boolean writeNotify = false;
  private boolean wrote = false;
  private boolean readNotify = false;

  public NamedPipe(WinNT.HANDLE handle) {
    myHandle = handle;
  }

  public boolean write(byte[] buf, int len) throws IOException {
    boolean wSuccess = false;
    if (len < 0) {
      len = 0;
    }
    try {
      while (readNotify) {
      }//wait for read to finished
      writeNotify = true;
      write0(myHandle, buf, len);
      wrote = true;
    } catch (IOException e) {
      throw new IOException("IO Exception while writing to the pipe.", e);
    } finally {
      writeNotify = false;
    }
    return wSuccess;
  }

  public int read(byte[] buf, int len) throws IOException {
    int byteTransfer = -1;
    if (len < 0) {
      len = 0;
    }
    long curLength = 0;
    while (curLength == 0) {
      try {
        curLength = available(myHandle);
      } catch (IOException e) {
        curLength = -1;
      }
      try {
        Thread.sleep(20);
      } catch (InterruptedException e) {
        curLength = -1;
      }
    }
    //handle exceptions
    if (curLength == -1) {
      return byteTransfer;
    }
    if (!wrote && curLength > 0) {
      //incoming stream. read now
      try {
        while (writeNotify) {
        }//wait for write to finish
        readNotify = true;
        byteTransfer = read0(myHandle, buf, len);
      } catch (IOException e) {
        throw new IOException("IO Exception while reading from the pipe.", e);
      } finally {
        readNotify = false;
      }
    } else if (wrote && curLength > 0) {
      //input stream available. read now
      try {
        while (writeNotify) {
        }//wait for write to finish
        readNotify = true;
        byteTransfer = read0(myHandle, buf, len);
      } catch (IOException e) {
        throw new IOException("IO Exception while reading from the pipe.", e);
      } finally {
        wrote = false;
        readNotify = false;
      }
    } else {
      //unknown condition
    }
    return byteTransfer;
  }

  private long available(WinNT.HANDLE handle) throws IOException {
    if (handle == null) {
      return -1;
    }
    
    IntByReference read = new IntByReference(0);
    Buffer b = ByteBuffer.wrap(new byte[10]);

    

    if (!WinPty.KERNEL32.PeekNamedPipe(handle, b, b.capacity(), new IntByReference(), read, new IntByReference())) {
      throw new IOException("Cant peek named pipe");
    }

    return read.getValue();
  }

  private int read0(WinNT.HANDLE handle, byte[] b, int len) throws IOException {
    if (handle == null) {
      return -1;
    }
    IntByReference dwRead = new IntByReference();
    ByteBuffer buf = ByteBuffer.wrap(b);
    WinPty.KERNEL32.ReadFile(handle, buf, len, dwRead, null);

    return dwRead.getValue();
  }

  private int write0(WinNT.HANDLE handle, byte[] b, int len) throws IOException {
    if (handle == null) {
      return -1;
    }
    IntByReference dwWritten = new IntByReference();
    Kernel32.INSTANCE.WriteFile(handle, b, len, dwWritten, null);
    return dwWritten.getValue();
  }

  void markClosed() {
    myHandle = null;
  }

  public void close() throws IOException {
    if (myHandle == null) {
      return;
    }
    boolean status = close0(myHandle);
    if (!status) {
      throw new IOException("Close error:" + Kernel32.INSTANCE.GetLastError());
    }
    myHandle = null;
  }

  public static boolean close0(WinNT.HANDLE handle) throws IOException {
    return com.sun.jna.platform.win32.Kernel32.INSTANCE.CloseHandle(handle);
  }
}