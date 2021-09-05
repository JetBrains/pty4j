package com.pty4j.windows.conpty;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;

import java.util.Arrays;
import java.util.List;

public class ProcessUtils {
    private static Kernel32 kernel32 = Kernel32.INSTANCE;

    public static WinNT.STARTUPINFOEX prepareStartupInformation(PseudoConsole pseudoConsole) {
        WinNT.STARTUPINFOEX startupInfo = new WinNT.STARTUPINFOEX();
        startupInfo.StartupInfo.cb = new WinDef.DWORD(startupInfo.size());

        WinNT.SIZE_TByReference bytesRequired = new WinNT.SIZE_TByReference();
        if (kernel32.InitializeProcThreadAttributeList(
                null,
                new WinDef.DWORD(1),
                new WinDef.DWORD(0),
                bytesRequired)) {
            throw new IllegalStateException("InitializeProcThreadAttributeList was unexpected to succeed");
        }

        Memory threadAttributeList = new Memory(bytesRequired.getValue().intValue());
        threadAttributeList.clear();

        startupInfo.lpAttributeList = threadAttributeList;

        if (!kernel32.InitializeProcThreadAttributeList(
                threadAttributeList,
                new WinDef.DWORD(1),
                new WinDef.DWORD(0),
                bytesRequired)) {
            Kernel32.throwLastError();
        }

        if (!kernel32.UpdateProcThreadAttribute(
                threadAttributeList,
                new WinDef.DWORD(0),
                Kernel32.PROC_THREAD_ATTRIBUTE_PSEUDOCONSOLE,
                new WinDef.PVOID(pseudoConsole.getHandle().getPointer()),
                new BaseTSD.SIZE_T(Native.POINTER_SIZE),
                null,
                null
        )) {
            Kernel32.throwLastError();
        }

        return startupInfo;
    }

    public static WinBase.PROCESS_INFORMATION start(WinNT.STARTUPINFOEX startupInfo, String commandLine) {
        WinBase.PROCESS_INFORMATION processInfo = new WinBase.PROCESS_INFORMATION();
        char[] commandLineArray = new char[commandLine.length() + 1];
        System.arraycopy(commandLine.toCharArray(), 0, commandLineArray, 0, commandLine.length());
        if (!kernel32.CreateProcessW(
                null,
                commandLineArray,
                null,
                null,
                false,
                new WinDef.DWORD(Kernel32.EXTENDED_STARTUPINFO_PRESENT),
                null,
                null,
                startupInfo,
                processInfo)) {
            Kernel32.throwLastError();
        }

        return processInfo;
    }

    public static int getProcessExitCodeStatus(WinBase.PROCESS_INFORMATION processInformation) {
        IntByReference exitCode = new IntByReference();
        if (!kernel32.GetExitCodeProcess(processInformation.hProcess, exitCode)) {
            Kernel32.throwLastError();
        }

        return exitCode.getValue();
    }

    public static void closeHandles(WinBase.PROCESS_INFORMATION processInformation) {
        if (!kernel32.CloseHandle(processInformation.hThread)) {
            Kernel32.throwLastError();
        }
        if (!kernel32.CloseHandle(processInformation.hProcess)) {
            Kernel32.throwLastError();
        }
    }
}
