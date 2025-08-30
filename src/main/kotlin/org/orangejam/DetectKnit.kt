package org.orangejam

import com.example.orangejam.ProjectService
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor

// look through Gradle modules to look for knit
object DetectKnit {
    private val log = Logger.getInstance(DetectKnit::class.java)

    private val buildFileNames = setOf(
        "build.gradle", "build.gradle.kts",
        "settings.gradle", "settings.gradle.kts"
    )

    // if these deps are found, then knit is detected
    private val needles = listOf(
        "io.github.tiktok.knit.plugin",
        "io.github.tiktok.knit:knit-plugin",
    )

    fun detect(project: Project): Boolean {
        val modules = project.getService(ProjectService::class.java)?.gradleModules().orEmpty()
        if (modules.isEmpty()) {
            val base = project.projectFile?.parent ?: project.guessProjectDir()
            return base != null && scanRoot(base)
        }

        for (m in modules) {
            if (scanModule(m)) return true
        }
        return false
    }

    fun scanModule(module: Module): Boolean {
        val roots = ModuleRootManager.getInstance(module).contentRoots
        for (root in roots) {
            if (scanRoot(root)) return true
        }
        return false
    }

    private fun scanRoot(root: VirtualFile): Boolean {
        var found = false
        VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Any>() {
            override fun visitFile(file: VirtualFile): Boolean {
                if (found) return false
                if (!file.isDirectory && file.name in buildFileNames) {
                    val text = runCatching { VfsUtilCore.loadText(file) }.getOrNull()
                    if (text != null && needles.any { text.contains(it) }) {
                        found = true
                        return false
                    }
                }
                return true
            }
        })
        return found
    }
}
