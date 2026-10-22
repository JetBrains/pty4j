# Bundled ConPTY

The Windows x64 and ARM64 binaries come from Microsoft's stable
[Microsoft.Windows.Console.ConPTY 1.25.260930003 NuGet package](https://www.nuget.org/packages/Microsoft.Windows.Console.ConPTY/1.25.260930003).
Their Windows file version is `1.25.2609.30003`.

Package SHA-256:
`02b07b349af66d801159bdf9e440d4a1ce78bb951f37fc8609731665afdae7ee`.

Replace each architecture's `conpty.dll` and `OpenConsole.exe` together:

| NuGet entry | Repository destination |
| --- | --- |
| `runtimes/win-x64/native/conpty.dll` | `os/win/x86-64/conpty.dll` |
| `build/native/runtimes/x64/OpenConsole.exe` | `os/win/x86-64/OpenConsole.exe` |
| `runtimes/win-arm64/native/conpty.dll` | `os/win/aarch64/conpty.dll` |
| `build/native/runtimes/arm64/OpenConsole.exe` | `os/win/aarch64/OpenConsole.exe` |

This version includes Microsoft's [cursor synchronization fix](https://github.com/microsoft/terminal/pull/20009)
for unknown sequences, including Kitty graphics APCs. The regression
`WinConPtyProcessTest.testConsoleCursorSynchronizedAfterGraphics` answers the
backend's cursor query and verifies that the child's console API sees the
terminal's cursor position. It fails with the previous bundled 1.24 binaries.

Run `./gradlew check` on Windows to exercise native behavior and JAR packaging.
The runtime's existing Windows build requirement and system-ConPTY fallback are
unchanged. Microsoft's binaries are distributed under the
[MIT license](LICENSE.txt).
