@file:Suppress("SpellCheckingInspection")

import jetbrains.sign.GpgSignSignatoryProvider
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.readText

buildscript {
  repositories {
    maven { url = uri("https://packages.jetbrains.team/maven/p/jcs/maven") }
  }
  dependencies {
    classpath("com.jetbrains:jet-sign:38")
  }
}

plugins {
  `java-library`
  `maven-publish`
  id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
  signing
}

repositories {
  maven {
    url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
  }
  mavenCentral()
}

group = "org.jetbrains.pty4j"

val pathToNativeInJar = "resources/com/pty4j/native"
val projectVersion = rootProject.projectDir.toPath().resolve("VERSION").readText().trim()

version = projectVersion

sourceSets {
  main {
    java.srcDirs("src")
  }
  test {
    java.srcDirs("test")
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
  withSourcesJar()
  withJavadocJar()
}

tasks {
  test {
    testLogging {
      events("passed", "skipped", "failed")
      showStackTraces = true
      exceptionFormat = TestExceptionFormat.FULL
      showStandardStreams = true
    }
  }

  jar {
    from("os") {
      include("**/*")
      into(pathToNativeInJar)
    }
    manifest {
      attributes(
        "Build-Timestamp" to DateTimeFormatter.ISO_INSTANT.format(ZonedDateTime.now()),
        "Created-By" to "Gradle ${gradle.gradleVersion}",
        "Build-Jdk" to System.getProperty("java.runtime.version"),
        "Build-OS" to "${System.getProperty("os.name")} ${System.getProperty("os.arch")} ${System.getProperty("os.version")}"
      )
    }
  }
}

tasks.register<Test>("testJar") {
  dependsOn(tasks.jar, tasks.testClasses)
  description = "Runs tests on built jar instead of build/classes/java/main/**/*.class files"
  group = "verification"

  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = project.files(
    tasks.jar.get().archiveFile.get().asFile.absolutePath,
    sourceSets.test.get().output.classesDirs,
    configurations.testRuntimeClasspath
  )
  systemProperty("pty4j.preferred.native.folder", false)
  shouldRunAfter(tasks.test)
}

tasks.check {
  dependsOn("testJar")
}

dependencies {
  implementation("org.jetbrains.pty4j:purejavacomm:0.0.11.1")
  implementation("org.jetbrains:annotations:24.0.1")
  implementation("org.slf4j:slf4j-api:2.0.9")
  implementation("net.java.dev.jna:jna:5.13.0")
  implementation("net.java.dev.jna:jna-platform:5.13.0")
  testImplementation("com.google.guava:guava:32.1.3-jre")
  testImplementation("junit:junit:4.13.2")
  testRuntimeOnly("org.slf4j:slf4j-simple:2.0.9")
}

val publishingUser: String? = System.getenv("PUBLISHING_USER")
val publishingPassword: String? = System.getenv("PUBLISHING_PASSWORD")

nexusPublishing.repositories.sonatype {
  username = publishingUser
  password = publishingPassword
}

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      from(components["java"])
      groupId = rootProject.group.toString()
      artifactId = rootProject.name
      version = if (publishingUser != null) projectVersion else "$projectVersion-SNAPSHOT"
      pom {
        name = rootProject.name
        description = "Pseudo terminal(PTY) implementation in Java"
        url = "https://github.com/JetBrains/pty4j"
        licenses {
          license {
            name = "Eclipse Public License 1.0"
            url = "https://opensource.org/licenses/eclipse-1.0.php"
          }
        }
        developers {
          developer {
            id = "sergey.simonchik"
            name = "Sergey Simonchik"
            organization = "JetBrains"
            organizationUrl = "https://www.jetbrains.com"
            email = "sergey.simonchik@jetbrains.com"
          }
          developer {
            id = "dmitry.trofimov"
            name = "Dmitry Trofimov"
            organization = "JetBrains"
            organizationUrl = "https://www.jetbrains.com"
            email = "dmitry.trofimov@jetbrains.com"
          }
        }
        scm {
          connection = "scm:git:git@github.com:JetBrains/pty4j.git"
          developerConnection = "scm:git:ssh:github.com/JetBrains/pty4j.git"
          url = "https://github.com/JetBrains/pty4j"
        }
      }
    }
  }
}

signing {
  sign(publishing.publications["mavenJava"])
  signatories = GpgSignSignatoryProvider()
}
