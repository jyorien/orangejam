package com.example.orangejam

import com.example.orangejam.GraphGen.generateGraphDot
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path

object ClasspathGraphRunner {
    private val log = Logger.getInstance(ClasspathGraphRunner::class.java)

    fun generate(project: Project, module: Module, outDir: Path, onFinished: (Path?) -> Unit) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Generating Knit Graph", true
        ) {
            override fun run(indicator: ProgressIndicator) {
                // build with gradle
                indicator.text = "Compiling module via Gradle 'build'…"
                // fallback: a simple 'build' usually compiles the right variant for JVM/KMP
                val latch = Object()
                var ok = false
                GradleRunner.runTask(project, module, "build") { success ->
                    ok = success
                    synchronized(latch) { latch.notifyAll() }
                }
                // wait for some time
                synchronized(latch) { latch.wait(10 * 60 * 1000) }
                if (!ok) {
                    onFinished(null); return
                }

                // get all the class files
                val classesRoot = ClassesDirFinder.findCompiledClassesRoot(module)

                if (classesRoot == null) {
                    log.warn("No compiled classes found for module ${module.name}")
                    onFinished(null)
                    return
                }

                // put the class files in zihengs graph gen
                indicator.text = "Running GraphGen on $classesRoot…"
                try {
                    Files.createDirectories(outDir)
                    generateGraphDot(arrayOf(classesRoot.toString(), outDir.toString()))
                    val dot = outDir.resolve("graph.dot")
                    onFinished(if (Files.isRegularFile(dot)) dot else null)
                } catch (t: Throwable) {
                    log.warn("GraphGen failed", t)
                    onFinished(null)
                }
            }
        })
    }
}
