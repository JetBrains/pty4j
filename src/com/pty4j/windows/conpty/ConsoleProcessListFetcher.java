package com.pty4j.windows.conpty;

import com.sun.jna.Library;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.WinDef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ConsoleProcessListFetcher {
  private static final Logger LOG = LoggerFactory.getLogger(ConsoleProcessListFetcher.class);
  private static final int TIMEOUT_MILLIS = 5000;
  private static final List<String> JAVA_OPTIONS_ENV_VARS = List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS"); 

  static int getConsoleProcessCount(long pid) throws IOException {
    ProcessBuilder builder = new ProcessBuilder(getPathToJavaExecutable(),
        //  tune JVM to behave more like a client VM for faster startup
        "-XX:TieredStopAtLevel=1", "-XX:CICompilerCount=1", "-XX:+UseSerialGC",
        "-XX:-UsePerfData", // disable the performance monitoring feature
        "-Xms8m", "-Xmx16m", // -Xmx2m still works, but -Xmx1m fails with "Too small maximum heap"
        "-cp",
        buildClasspath(ConsoleProcessListChildProcessMain.class, Library.class, WinDef.DWORD.class),
        ConsoleProcessListChildProcessMain.class.getName(),
        String.valueOf(pid));
    // ignore common Java cli options as it may slow down the VM startup
    JAVA_OPTIONS_ENV_VARS.forEach(builder.environment().keySet()::remove);
    builder.redirectErrorStream(true);
    Process process = builder.start();
    StreamGobbler stdout = new StreamGobbler(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    try {
      process.waitFor(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    } catch (InterruptedException ignored) {
    }
    if (process.isAlive()) {
      LOG.info("Terminating still running child process");
      process.destroy();
    }
    stdout.awaitReadingEnds();
    int exitCode;
    try {
      exitCode = process.exitValue();
    } catch (IllegalThreadStateException e) {
      throw new IOException("Still running child process");
    }
    if (exitCode != 0) {
      throw new IOException("Failed to get console process list: exit code " + exitCode + ", output: " + stdout.getText());
    }
    String processCountStr = getProcessCountStr(stdout.getText());
    try {
      int result = Integer.parseInt(processCountStr);
      if (result <= 1) {
        throw new IOException("Unexpected amount of console processes: " + result);
      }
      return result - 1; // minus "java ConsoleProcessListChildProcessMain" process
    } catch (NumberFormatException e) {
      throw new IOException("Failed to get console process list: cannot parse int from '" + processCountStr +
          "', all output: " + stdout.getText().trim());
    }
  }

  private static @NotNull String getProcessCountStr(@NotNull String stdout) throws IOException {
    int prefixInd = stdout.lastIndexOf(ConsoleProcessListChildProcessMain.PREFIX);
    if (prefixInd != -1) {
      int suffixInd = stdout.indexOf(ConsoleProcessListChildProcessMain.SUFFIX, prefixInd);
      if (suffixInd != -1) {
        return stdout.substring(prefixInd + ConsoleProcessListChildProcessMain.PREFIX.length(), suffixInd);
      }
    }
    throw new IOException("Cannot find process count in " + stdout);
  }

  private static @NotNull String getPathToJavaExecutable() throws IOException {
    Path javaHome = Path.of(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java.exe");
    if (!Files.isRegularFile(javaHome)) {
      throw new IOException("No such executable " + javaHome);
    }
    return javaHome.toAbsolutePath().toString();
  }

  private static class StreamGobbler implements Runnable {

    private final Reader myReader;
    private final StringBuilder myBuffer = new StringBuilder();
    private final Thread myThread;
    private boolean myIsStopped = false;

    private StreamGobbler(Reader reader) {
      myReader = reader;
      myThread = new Thread(this, "ConsoleProcessListFetcher output reader");
      myThread.start();
    }

    public void run() {
      char[] buf = new char[8192];
      try {
        int readCount;
        while (!myIsStopped && (readCount = myReader.read(buf)) >= 0) {
          myBuffer.append(buf, 0, readCount);
        }
        if (myIsStopped) {
          myBuffer.append("Failed to read output: force stopped");
        }
      }
      catch (Exception e) {
        myBuffer.append("Failed to read output: ").append(e.getClass().getName()).append(" raised");
      }
    }

    private void awaitReadingEnds() {
      try {
        myThread.join(TIMEOUT_MILLIS); // await to read whole buffered output
      } catch (InterruptedException ignored) {
      }
      myIsStopped = true;
    }

    private String getText() {
      return myBuffer.toString();
    }
  }

  private static @NotNull String buildClasspath(@NotNull Class<?>... classes) {
    List<String> paths = Arrays.stream(classes).map(ConsoleProcessListFetcher::getJarPathForClass).collect(Collectors.toList());
    return String.join(Platform.isWindows() ? ";" : ":", paths);
  }

  private static String getJarPathForClass(@NotNull Class<?> aClass) {
    String resourceRoot = getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
    return new File(Objects.requireNonNull(resourceRoot)).getAbsolutePath();
  }

  @Nullable
  private static String getResourceRoot(@NotNull Class<?> context, @NotNull String path) {
    URL url = context.getResource(path);
    if (url == null) {
      url = ClassLoader.getSystemResource(path.substring(1));
    }
    return url != null ? extractRoot(url, path) : null;
  }

  @NotNull
  private static String extractRoot(@NotNull URL resourceURL, @NotNull String resourcePath) {
    if (!resourcePath.startsWith("/")) {
      throw new IllegalStateException("precondition failed: " + resourcePath);
    }

    String resultPath = null;
    String protocol = resourceURL.getProtocol();
    if ("file".equals(protocol)) {
      String path = urlToFile(resourceURL).getPath();
      String testPath = path.replace('\\', '/');
      String testResourcePath = resourcePath.replace('\\', '/');
      if (testPath.endsWith(testResourcePath)) {
        resultPath = path.substring(0, path.length() - resourcePath.length());
      }
    }
    else if ("jar".equals(protocol)) {
      resultPath = getJarPath(resourceURL.getFile());
    }

    if (resultPath == null) {
      throw new IllegalStateException("Cannot extract '" + resourcePath + "' from '" + resourceURL + "', " + protocol);
    }
    return resultPath;
  }

  @NotNull
  private static File urlToFile(@NotNull URL url) {
    try {
      return new File(url.toURI().getSchemeSpecificPart());
    }
    catch (URISyntaxException e) {
      throw new IllegalArgumentException("URL='" + url + "'", e);
    }
  }

  private static @Nullable String getJarPath(@NotNull String urlFilePart) {
    int pivot = urlFilePart.indexOf("!/");
    if (pivot < 0) {
      return null;
    }
    String fileUrlStr = urlFilePart.substring(0, pivot);

    String filePrefix = "file:";
    if (!fileUrlStr.startsWith(filePrefix)) {
      return fileUrlStr;
    }

    URL fileUrl;
    try {
      fileUrl = new URL(fileUrlStr);
    }
    catch (MalformedURLException e) {
      return null;
    }

    File result = urlToFile(fileUrl);
    return result.getPath().replace('\\', '/');
  }
}
