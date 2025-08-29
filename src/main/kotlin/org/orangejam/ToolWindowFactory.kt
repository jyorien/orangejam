package com.example.orangejam

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefBrowser
import org.orangejam.ClasspathGraphRunner
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.nio.file.Files
import javax.swing.*

class ToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val projectSvc = project.getService(ProjectService::class.java)
        val uiSvc = project.getService(GraphUiService::class.java)

        val modules: List<Module> = projectSvc?.knitModules().orEmpty()
        val browser = JBCefBrowser()

        val top = JPanel(FlowLayout(FlowLayout.LEFT))
        val status = JBLabel("Knit: detecting…")
        val moduleBox = JComboBox(modules.toTypedArray()).apply {
            renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): java.awt.Component {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    text = (value as? Module)?.name ?: "—"
                    return this
                }
            }
            selectedItem = modules.firstOrNull()
        }
        val generateBtn = JButton("Generate")
        val refreshBtn = JButton("Refresh")
        val openDotBtn = JButton("Open DOT")
        val openSvgBtn = JButton("Open SVG")
        val openPngBtn = JButton("Open PNG")

        top.add(JBLabel("Module:"))
        top.add(moduleBox)
        top.add(generateBtn)
        top.add(refreshBtn)
        top.add(openDotBtn)
        top.add(openSvgBtn)
        top.add(openPngBtn)
        top.add(Box.createHorizontalStrut(8))
        top.add(status)

        val panel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(top, BorderLayout.NORTH)
            add(browser.component, BorderLayout.CENTER)
        }

        fun selectedModule(): Module? = moduleBox.selectedItem as? Module

        fun loadSvg(m: Module?) {
            if (m == null) {
                browser.loadHTML("<html><body style='font:13px sans-serif;padding:12px'>Select a module.</body></html>")
                return
            }
            val svg = KnitPaths.svgPath(m)
            if (Files.isRegularFile(svg)) {
                LocalFileSystem.getInstance().refreshAndFindFileByPath(svg.toString())?.refresh(false, false)
                browser.loadURL(VfsUtil.pathToUrl(svg.toString()))
                status.text = "Loaded ${svg.fileName}"
            } else {
                browser.loadHTML("<html><body style='font:13px sans-serif;padding:12px'>No graph yet for <b>${m.name}</b>. Click <i>Generate</i>.</body></html>")
                status.text = "No SVG found"
            }
        }

        fun recomputeDetection() {
            val m = selectedModule()
            if (m != null) {
                val moduleUsesKnit = DetectKnit.scanModule(m)
                generateBtn.isEnabled = moduleUsesKnit
                status.text = if (moduleUsesKnit) "Module uses Knit" else "Module does not use Knit"
            } else {
                val uses = projectSvc?.projectUsesKnit() ?: false
                status.text = "Knit detected: $uses"
                generateBtn.isEnabled = false
            }
        }

        fun doGenerate() {
            val m = selectedModule() ?: return
            generateBtn.isEnabled = false
            status.text = "Running ASM GraphGen in ${m.name}…"

            val outDir = KnitPaths.moduleBuildDir(m).resolve("knit-graph")

            // This part already runs in background in your ClasspathGraphRunner
            ClasspathGraphRunner.generate(project, m, outDir) { dotPath ->
                if (dotPath == null) {
                    // Switch to EDT to update UI
                    ApplicationManager.getApplication().invokeLater(
                        Runnable {
                            status.text = "No DOT generated; see idea.log"
                            loadSvg(m)
                            generateBtn.isEnabled = true
                        }
                    )
                    return@generate
                }

                // Run rendering in a background task (NOT on EDT)
                com.intellij.openapi.progress.ProgressManager.getInstance().run(
                    object : com.intellij.openapi.progress.Task.Backgroundable(
                        project, "Rendering Knit Graph for ${m.name}", true
                    ) {
                        // results to report back on EDT in onSuccess()
                        private var svgOk = false
                        private var pngOk = false

                        override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                            indicator.text = "Rendering DOT → SVG/PNG…"
                            val svg = KnitPaths.svgPath(m)
                            val png = KnitPaths.pngPath(m)

                            // These calls may spawn external processes; keep them OFF the EDT
                            svgOk = GraphRenderer.renderDotToSvg(dotPath, svg)
                            pngOk = GraphRenderer.renderDotToPng(dotPath, png)
                        }

                        override fun onSuccess() {
                            // Back on EDT: update UI safely
                            status.text = when {
                                svgOk && pngOk -> "Rendered SVG & PNG"
                                svgOk -> "Rendered SVG (PNG failed)"
                                pngOk -> "Rendered PNG (SVG failed)"
                                else -> "Render failed; see idea.log"
                            }
                            loadSvg(m)
                            generateBtn.isEnabled = true
                        }

                        override fun onThrowable(error: Throwable) {
                            // Also EDT
                            status.text = "Render failed: ${error.message ?: "see idea.log"}"
                            generateBtn.isEnabled = true
                        }

                        override fun onCancel() {
                            status.text = "Render cancelled"
                            generateBtn.isEnabled = true
                        }
                    }
                )
            }
        }


        // Wire buttons
        refreshBtn.addActionListener { loadSvg(selectedModule()) }
        generateBtn.addActionListener { doGenerate() }
        openDotBtn.addActionListener {
            selectedModule()?.let { m ->
                val dot = KnitPaths.dotPath(m)
                if (Files.isRegularFile(dot)) {
                    com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                        .openFile(LocalFileSystem.getInstance().refreshAndFindFileByPath(dot.toString())!!, true)
                }
            }
        }
        openSvgBtn.addActionListener {
            selectedModule()?.let { m ->
                val svg = KnitPaths.svgPath(m)
                if (Files.isRegularFile(svg)) {
                    com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                        .openFile(LocalFileSystem.getInstance().refreshAndFindFileByPath(svg.toString())!!, true)
                }
            }
        }
        openPngBtn.addActionListener {
            selectedModule()?.let { m ->
                val png = KnitPaths.pngPath(m)
                if (Files.isRegularFile(png)) {
                    com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                        .openFile(LocalFileSystem.getInstance().refreshAndFindFileByPath(png.toString())!!, true)
                }
            }
        }
        (moduleBox as JComboBox<Module>).addActionListener { 
            loadSvg(selectedModule())
            recomputeDetection()
        }

        // Tool window reacts to watcher refreshes
        project.getService(GraphUiService::class.java)?.registerRefreshCallback {
            loadSvg(selectedModule())
        }

        // Initial state
        recomputeDetection()
        loadSvg(selectedModule())

        val content = ContentFactory.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true
}
