package org.jetbrains.kotlin.doctor.diagnostics

import org.jetbrains.kotlin.doctor.entity.*

class JavaDiagnostic(private val system: System) : Diagnostic() {
    override fun diagnose(): Diagnosis {
        val result = Diagnosis.Builder("Java")

        var javaLocation = system.execute("which", "java").output
        val javaVersion = system.execute("java", "-version").output?.lineSequence()?.firstOrNull()
        val systemJavaHome = system.execute("/usr/libexec/java_home").output
        val javaHome = system.getEnvVar("JAVA_HOME")
        if (javaLocation == "/usr/bin/java") {
            javaLocation = when {
                javaHome?.isNotBlank() == true -> javaHome.plus("/bin/java")
                systemJavaHome?.isNotBlank() == true -> systemJavaHome.plus("/bin/java")
                else -> javaLocation
            }
        }
        if (javaLocation.isNullOrBlank() || javaVersion.isNullOrBlank()) {
            result.addFailure(
                "Java not found",
                "Get JDK from https://www.oracle.com/java/technologies/javase-downloads.html"
            )
            return result.build()
        }

        val java = Application("Java", Version(javaVersion), javaLocation)
        result.addSuccess("${java.name} (${java.version})\nLocation: ${java.location}")
        result.addEnvironment(setOf(EnvironmentPiece.Jdk(java.version)))

        if (javaHome.isNullOrBlank()) {
            result.addInfo("JAVA_HOME is not set", javaHomeHint(javaLocation))
        } else {
            val javaHomeCanonical = javaHome.removeSuffix("/")
            val javaCmdLocations =
                listOf(javaHomeCanonical, "$javaHomeCanonical/bin/java", "$javaHomeCanonical/bin/jre/sh/java")
            if (javaCmdLocations.none { system.fileExists(it) }) {
                result.addFailure(
                    "JAVA_HOME is set to an invalid directory",
                    "JAVA_HOME: $javaHome",
                    javaHomeHint(javaLocation)
                )
            } else {
                result.addSuccess("JAVA_HOME: $javaHome")
                if (javaCmdLocations.none { it == javaLocation }) {
                    result.addInfo(
                        "JAVA_HOME does not match Java binary location",
                        "Java binary location found in PATH: $javaLocation",
                        "Note that, by default, Gradle will use Java environment provided by JAVA_HOME"
                    )
                }
            }
        }

        val xcodeJavaHome =
            system.execute("defaults", "read", "com.apple.dt.Xcode", "IDEApplicationwideBuildSettings").output
                ?.lines()
                ?.lastOrNull { it.contains("\"JAVA_HOME\"") }
                ?.split("=")
                ?.lastOrNull()
                ?.trim(' ', '"', ';')
                ?: systemJavaHome

        if (xcodeJavaHome != javaHome) {
            if (javaHome != null) {
                result.addInfo(
                    "Xcode JAVA_HOME does not match the environment variable",
                    "Xcode JAVA_HOME: $xcodeJavaHome",
                    "System JAVA_HOME: $javaHome",
                    "Set JAVA_HOME in Xcode -> Preferences -> Locations -> Custom Paths"
                )
            } else {
                result.addInfo(
                    "Xcode JAVA_HOME does not match the environment variable",
                    "Xcode JAVA_HOME: $xcodeJavaHome",
                    "Set JAVA_HOME in Xcode -> Preferences -> Locations -> Custom Paths"
                )
            }
        }

        result.addInfo(
            "Note that, by default, Android Studio uses bundled JDK for Gradle tasks execution.",
            "Gradle JDK can be configured in Android Studio Preferences under Build, Execution, Deployment -> Build Tools -> Gradle section"
        )

        return result.build()
    }

    private fun javaHomeHint(javaLocation: String?) = """
        Consider adding the following to ${system.shell?.profile ?: "your shell profile"} for setting JAVA_HOME
        export JAVA_HOME=${javaLocation?.removeSuffix("/bin/java") ?: "<path to java>"}
    """.trimIndent()
}