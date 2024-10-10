@file:OptIn(ExperimentalPathApi::class)

package com.pty4j

import com.jetbrains.signatureverifier.ILogger
import com.jetbrains.signatureverifier.PeFile
import com.jetbrains.signatureverifier.Resources
import com.jetbrains.signatureverifier.SignatureData
import com.jetbrains.signatureverifier.crypt.*
import com.jetbrains.signatureverifier.macho.MachoArch
import com.jetbrains.util.filetype.FileProperties
import com.jetbrains.util.filetype.FileType
import com.jetbrains.util.filetype.FileTypeDetector.DetectFileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.JUnitSoftAssertions
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.walk


class SignaturesTest {
  @JvmField
  @Rule
  var asserts: JUnitSoftAssertions = JUnitSoftAssertions()

  @Test
  @Throws(IOException::class)
  fun testMacOSHelpersSigned() = runBlocking {
    val root = Path.of("os/darwin")
    val natives = root.walk().toList()
    Resources.GetDefaultRoots().use { defaultRootsStream ->
      val verificationParams = SignatureVerificationParams(
        signRootCertStore = defaultRootsStream,
        timestampRootCertStore = null,
        buildChain = true,
        withRevocationCheck = false
      )

      for (path in natives) {
        val fileType: Pair<FileType, EnumSet<FileProperties>> = Files.newByteChannel(path).use { fs ->
          fs.DetectFileType()
        }
        println("${root.relativize(path)}: $fileType") // (MachO, [SharedLibraryType, MultiArch])
        asserts.assertThat(fileType.first).isEqualTo(FileType.MachO)
        verifyFatMacho(path, verificationParams, SimpleConsoleLogger(path))
      }
    }
  }

  @Test
  @Throws(IOException::class)
  fun testWindowsHelpersSigned() = runBlocking {
    val root = Path.of("os/win")
    val natives = root.walk().toList()
    Resources.GetDefaultRoots().use { defaultRootsStream ->
      val verificationParams = SignatureVerificationParams(
        signRootCertStore = defaultRootsStream,
        timestampRootCertStore = null,
        buildChain = true,
        withRevocationCheck = false
      )

      for (path in natives) {
        val fileType: Pair<FileType, EnumSet<FileProperties>> = Files.newByteChannel(path).use { fs ->
          fs.DetectFileType()
        }
        println("${root.relativize(path)}: $fileType") // (Pe, [SharedLibraryType, Signed])
        asserts.assertThat(fileType.first).isEqualTo(FileType.Pe)
        verifyPortableExecutable(path, verificationParams, SimpleConsoleLogger(path))
      }
    }
  }

  private suspend fun verifyFatMacho(
    pathToExecutable: Path,
    verificationParams: SignatureVerificationParams,
    logger: ILogger
  ) {

    withContext(Dispatchers.IO) {
      Files.newByteChannel(pathToExecutable)
    }.use { fs ->
      val machoArch = MachoArch(fs, logger)

      for (executable in machoArch.Extract()) {
        val result = verifySignature(executable.GetSignatureData(), verificationParams, logger, pathToExecutable.toString())
        checkResult(result, pathToExecutable)
      }
    }
  }

  private suspend fun verifyPortableExecutable(
    pathToExecutable: Path,
    verificationParams: SignatureVerificationParams,
    logger: ILogger
  ) {
    withContext(Dispatchers.IO) {
      Files.newByteChannel(pathToExecutable)
    }.use { fs ->
      val executable = PeFile(fs)
      val result = verifySignature(executable.GetSignatureData(), verificationParams, logger, pathToExecutable.toString())
      checkResult(result, pathToExecutable)
    }
  }

  private suspend fun verifySignature(
    signatureData: SignatureData,
    verificationParams: SignatureVerificationParams,
    logger: ILogger,
    path: String
  ): VerifySignatureResult {
    if (signatureData.IsEmpty) {
      return VerifySignatureResult(VerifySignatureStatus.InvalidSignature, "No signature data found in '$path'")
    }
    val signedMessage = SignedMessage.CreateInstance(signatureData)
    val signedMessageVerifier = SignedMessageVerifier(logger)
    return signedMessageVerifier.VerifySignatureAsync(signedMessage, verificationParams)
  }

  private fun checkResult(result: VerifySignatureResult, pathToExecutable: Path) {
    if (result.Status == VerifySignatureStatus.Valid)
      println("$pathToExecutable: Signature is OK!")
    else
      asserts.fail<Unit>("$pathToExecutable: Signature is invalid! ${result.Message}")
  }

  @Suppress("TestFunctionName")
  inner class SimpleConsoleLogger(private val path: Path) : ILogger {
    override fun Info(str: String) = println("INFO: $path: $str")
    override fun Warning(str: String) = println("WARNING: $path: $str")
    override fun Error(str: String) = asserts.fail<Unit>("ERROR: $path: $str")
    override fun Trace(str: String) = println("TRACE: $path: $str")
  }
}


