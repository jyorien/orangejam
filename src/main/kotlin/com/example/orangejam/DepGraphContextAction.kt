package com.example.orangejam

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Files

class DepGraphContextAction : AnAction() {
    private val log = Logger.getInstance(DepGraphContextAction::class.java)

    override fun update(event: AnActionEvent) {
        val project = event.project
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE)
        
        if (project == null || file == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        val module = ModuleUtil.findModuleForFile(file, project)
        if (module == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }

        val usesKnit = DetectKnit.scanModule(module)
        event.presentation.isEnabledAndVisible = usesKnit
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val module = ModuleUtil.findModuleForFile(file, project) ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generating Dependency Graph for ${module.name}", true) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                val outDir = KnitPaths.moduleBuildDir(module).resolve("knit-graph")
                // build -> graphgen -> render -> open in new tab
                ClasspathGraphRunner.generate(project, module, outDir) { dotPath ->
                    if (dotPath == null) {
                        return@generate
                    }
                    val pngPath = KnitPaths.pngPath(module)
                    val renderSuccess = GraphRenderer.renderDotToPng(dotPath, pngPath)

                    ApplicationManager.getApplication().invokeLater {
                        if (renderSuccess && Files.isRegularFile(pngPath)) {
                            val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(pngPath.toString())
                            if (virtualFile != null) {
                                virtualFile.refresh(false, false)
                                FileEditorManager.getInstance(project).openFile(virtualFile, true)
                                log.info("Successfully opened dependency graph: $pngPath")
                            } else {
                                Messages.showErrorDialog(project, "Generated PNG file but couldn't open it: $pngPath", "Graph Generation")
                            }
                        } else {
                            Messages.showErrorDialog(project, "Failed to render PNG file. Check logs for details.", "Graph Rendering Failed")
                        }
                    }
                }
            }
        })
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}