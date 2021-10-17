package com.pty4j.windows.conpty;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.win32.W32APIOptions;

public class ConsoleProcessListChildProcessMain {

  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.println("single argument expected: pid");
      return;
    }
    int pid;
    try {
      pid = Integer.parseInt(args[0]);
    } catch (NumberFormatException e) {
      System.err.println("Cannot parse pid from " + args[0]);
      return;
    }
    if (!Kernel32.INSTANCE.FreeConsole()) {
      System.err.println(LastErrorExceptionEx.getErrorMessage("FreeConsole"));
      return;
    }
    if (!Kernel32.INSTANCE.AttachConsole(pid)) {
      System.err.println(LastErrorExceptionEx.getErrorMessage("AttachConsole"));
      return;
    }
    int MAX_COUNT = 64;
    Pointer buffer = new Memory(WinDef.DWORD.SIZE * MAX_COUNT);
    WinDef.DWORD result = MyConsoleLibrary.INSTANCE.GetConsoleProcessList(buffer, new WinDef.DWORD(MAX_COUNT));
    int count = result.intValue();
    if (count == 0) {
      System.err.println(LastErrorExceptionEx.getErrorMessage("GetConsoleProcessList"));
      return;
    }
    System.out.println(count);
  }


  private interface MyConsoleLibrary extends Library {
    MyConsoleLibrary INSTANCE = Native.load("kernel32", MyConsoleLibrary.class, W32APIOptions.DEFAULT_OPTIONS);

    WinDef.DWORD GetConsoleProcessList(Pointer processList, WinDef.DWORD maxProcessCount);
  }
}
