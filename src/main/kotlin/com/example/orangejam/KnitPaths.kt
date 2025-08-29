package com.example.orangejam

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object KnitPaths {
    private val log = Logger.getInstance(KnitPaths::class.java)

    fun moduleBuildDir(module: Module): Path {
        val root = ModuleRootManager.getInstance(module).contentRoots.firstOrNull()
        val moduleDir = root?.let { Paths.get(it.path) }
            ?: module.project.basePath?.let { Paths.get(it) }
            ?: Paths.get(".").toAbsolutePath().normalize()

        return moduleDir.resolve("build")
    }

    fun dotPath(module: Module): Path = moduleBuildDir(module)
        .resolve("knit-graph")
        .resolve("graph.dot")

    fun svgPath(module: Module): Path = moduleBuildDir(module)
        .resolve("knit-graph")
        .resolve("graph.svg")

    fun pngPath(module: Module): Path = moduleBuildDir(module)
        .resolve("knit-graph")
        .resolve("graph.png")

}
