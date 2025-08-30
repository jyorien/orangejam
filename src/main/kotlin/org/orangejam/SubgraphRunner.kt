package org.orangejam

import com.example.orangejam.GraphRenderer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import org.orangejam.graph.GraphSlice
import org.orangejam.graph.toDotWithDotlin
import org.orangejam.services.GraphCacheService
import java.nio.file.Files
import java.nio.file.Path

object SubgraphRunner {
    private val log = Logger.getInstance(SubgraphRunner::class.java)

    fun generateForClass(project: Project, module: Module, classFqn: String, onFinished: (Path?) -> Unit = {}) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generating graph for $classFqn", true) {
            private var pngOut: Path? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Preparing module graph…"
                val full = project.getService(GraphCacheService::class.java).getOrBuild(module) ?: run {
                    log.warn("SubgraphRunner: full graph unavailable for module ${module.name}")
                    return
                }

                indicator.text = "Slicing graph for $classFqn…"
                val sub = GraphSlice.subgraphForComponent(full, classFqn)
                if (sub.vertices.isEmpty()) {
                    log.warn("SubgraphRunner: no dependency slice found for $classFqn")
                    return
                }

                val dotPath = ComponentPaths.dot(module, classFqn)
                val pngPath = ComponentPaths.png(module, classFqn)
                Files.createDirectories(dotPath.parent)

                indicator.text = "Writing DOT…"
                val dotText = toDotWithDotlin(sub)
                Files.writeString(dotPath, dotText)

                indicator.text = "Rendering DOT → PNG…"
                val pngOk = GraphRenderer.renderDotToPng(dotPath, pngPath)
                if (pngOk) {
                    pngOut = pngPath
                } else {
                    log.warn("SubgraphRunner: PNG render failed for $classFqn at $pngPath")
                }
            }

            override fun onSuccess() {
                val path = pngOut ?: return
                val vfile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.toString()) ?: return
                FileEditorManager.getInstance(project).openFile(vfile, true, true)
                onFinished(path)
            }
        })
    }
}
