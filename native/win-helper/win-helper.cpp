#include <windows.h>
#include <winternl.h>
#include <string>
#include <sstream>

typedef NTSTATUS(NTAPI* _NtQueryInformationProcess)(
        HANDLE ProcessHandle,
        PROCESSINFOCLASS ProcessInformationClass,
        PVOID ProcessInformation,
        ULONG ProcessInformationLength,
        PULONG ReturnLength
);

void SetErrorMessage(const char* operationName, WCHAR*& errorMessagePtr) {
    LPWSTR msg;
    DWORD lastError = GetLastError();
    FormatMessageW(
            FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
            NULL,
            lastError,
            MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
            (LPWSTR)&msg,
            0,
            NULL);
    std::wstringstream s;
    s << operationName << " failed with error " << lastError << ": ";
    if (msg) {
        s << msg;
        LocalFree(msg);
    }
    else {
        s << "(no message available)";
    }
    std::wstring copy(s.str());
    errorMessagePtr = _wcsdup(copy.c_str());
}

extern "C" __declspec(dllexport) WCHAR* getCurrentDirectory(DWORD pid, WCHAR*& errorMessagePtr) {
    HANDLE hProcess = OpenProcess(PROCESS_ALL_ACCESS, FALSE, pid);
    if (!hProcess) {
        SetErrorMessage("OpenProcess", errorMessagePtr);
        return NULL;
    }
    HMODULE ntdllHandle = GetModuleHandle("ntdll.dll");
    if (!ntdllHandle) {
        SetErrorMessage("GetModuleHandle", errorMessagePtr);
        return NULL;
    }
    _NtQueryInformationProcess NtQueryInformationProcess = (_NtQueryInformationProcess)GetProcAddress(ntdllHandle, "NtQueryInformationProcess");

    BOOL wow;
    IsWow64Process(GetCurrentProcess(), &wow);
    if (wow) {
        SetErrorMessage("Cannot fetch current directory for WoW64 process", errorMessagePtr);
        return NULL;
    }

    PROCESS_BASIC_INFORMATION pbi;

    NTSTATUS status = NtQueryInformationProcess(hProcess, ProcessBasicInformation, &pbi, sizeof(pbi), NULL);
    if (!NT_SUCCESS(status)) {
        errorMessagePtr = _wcsdup((L"NtQueryInformationProcess failed to fetch ProcessBasicInformation:" + std::to_wstring(status)).c_str());
        return NULL;
    }

    PEB peb;
    if (!ReadProcessMemory(hProcess, pbi.PebBaseAddress, &peb, sizeof(peb), NULL))
    {
        SetErrorMessage("ReadProcessMemory(PROCESS_BASIC_INFORMATION.PebBaseAddress)", errorMessagePtr);
        return NULL;
    }

    RTL_USER_PROCESS_PARAMETERS procParams;
    if (!ReadProcessMemory(hProcess, peb.ProcessParameters, &procParams, sizeof(procParams), NULL))
    {
        SetErrorMessage("ReadProcessMemory(PEB.ProcessParameters)", errorMessagePtr);
        return NULL;
    }

    // Unfortunately, CurrentDirectory is not declared in _RTL_USER_PROCESS_PARAMETERS, it's somewhere in the Reserved2 area.
    // Use WinDbg, run "dt ntdll!_PEB - r2" command, find ProcessParameters, then find CurrentDirectory: offset 0x38.
    UNICODE_STRING currentDirUnicodeStr = *(UNICODE_STRING*)((PCHAR)&procParams + 0x38);

    if (currentDirUnicodeStr.Length <= 0 || currentDirUnicodeStr.MaximumLength <= 0
        || currentDirUnicodeStr.Length >= currentDirUnicodeStr.MaximumLength || currentDirUnicodeStr.MaximumLength > 8192) {
        std::string err = "Bad current directory: Length=" + std::to_string(currentDirUnicodeStr.Length)
                          + ", MaximumLength=" + std::to_string(currentDirUnicodeStr.MaximumLength);
        SetErrorMessage(err.c_str(), errorMessagePtr);
        return NULL;
    }

    LPWSTR lpCurrentDir = new WCHAR[currentDirUnicodeStr.MaximumLength / sizeof(WCHAR) + 1];
    if (!ReadProcessMemory(hProcess, currentDirUnicodeStr.Buffer, lpCurrentDir, currentDirUnicodeStr.MaximumLength, NULL))
    {
        delete[] lpCurrentDir;
        SetErrorMessage("ReadProcessMemory(ProcessParameters.CurrentDirectory)", errorMessagePtr);
        return NULL;
    }
    std::wstring currentDirectory = lpCurrentDir;
    delete[] lpCurrentDir;
    return _wcsdup(currentDirectory.c_str());
}
