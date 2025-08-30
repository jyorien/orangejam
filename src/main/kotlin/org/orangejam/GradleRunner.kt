package org.orangejam

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.SystemInfo
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object GradleRunner {
    private val log = Logger.getInstance(GradleRunner::class.java)

    private fun findWorkDir(project: Project, module: Module): Path {
        val moduleRoot = ModuleRootManager.getInstance(module).contentRoots.firstOrNull()
        return moduleRoot?.let { Paths.get(it.path) }
            ?: Paths.get(project.basePath ?: ".").toAbsolutePath().normalize()
    }

    private fun findGradleExecutable(workDir: Path, projectBase: String?): String {
        val gradlewName = if (SystemInfo.isWindows) "gradlew.bat" else "gradlew"
        val candidates = sequenceOf(
            workDir.resolve(gradlewName),
            projectBase?.let { Paths.get(it).resolve(gradlewName) }
        ).filterNotNull()
        val wrapper = candidates.firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }
        return wrapper?.toString() ?: if (SystemInfo.isWindows) "gradle.bat" else "gradle"
    }

    // used by anything to run `./gradlew ...
    // usually just to build the program so we can get the graph out
    fun runTask(project: Project, module: Module, taskName: String, onFinished: (Boolean) -> Unit) {
        val workDir = findWorkDir(project, module)
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Running $taskName", true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Gradle: $taskName"
                val exe = findGradleExecutable(workDir, project.basePath)
                val cmd = GeneralCommandLine(exe)
                    .withWorkDirectory(workDir.toFile())
                    .withParameters(taskName)

                log.info("Running: ${cmd.commandLineString}  (cwd=$workDir)")
                val handler = CapturingProcessHandler(cmd.createProcess(), StandardCharsets.UTF_8, cmd.commandLineString)
                val result = handler.runProcess(10 * 60 * 1000)

                val ok = result.exitCode == 0
                if (!ok) {
                    log.warn("Gradle task '$taskName' failed (exit=${result.exitCode}).\nOUT:\n${result.stdout}\nERR:\n${result.stderr}")
                }
                onFinished(ok)
            }
        })
    }
}
