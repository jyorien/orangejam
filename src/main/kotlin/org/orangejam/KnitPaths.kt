package org.orangejam

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import java.nio.file.Path
import java.nio.file.Paths

object KnitPaths {
    private val log = Logger.getInstance(KnitPaths::class.java)

    fun moduleBuildDir(module: Module): Path {
        val linked: String? = ExternalSystemModulePropertyManager.getInstance(module).getLinkedProjectPath()
        if (linked != null) {
            return Paths.get(linked).resolve("build")
        }

        val content = ModuleRootManager.getInstance(module).contentRoots.firstOrNull()?.path
        if (content != null) {
            return Paths.get(content).resolve("build")
        }

        val base = module.project.basePath
        if (base != null) {
            return Paths.get(base).resolve("build")
        }

        return Paths.get(".").toAbsolutePath().normalize().resolve("build")
    }

    fun dotPath(module: Module): Path = moduleBuildDir(module).resolve("knit-graph/graph.dot")
    fun svgPath(module: Module): Path = moduleBuildDir(module).resolve("knit-graph/graph.svg")
    fun pngPath(module: Module): Path = moduleBuildDir(module).resolve("knit-graph/graph.png")
}
