package org.orangejam

import org.orangejam.KnitPaths
import com.intellij.openapi.module.Module
import java.nio.file.Path

object ComponentPaths {
    fun dir(module: Module): Path =
        KnitPaths.moduleBuildDir(module).resolve("knit-graph").resolve("components")

    private fun sanitize(fqn: String) =
        fqn.replace('.', '_').replace('$', '_').replace('/', '_')

    fun dot(module: Module, fqn: String): Path = dir(module).resolve("${sanitize(fqn)}.dot")
    fun svg(module: Module, fqn: String): Path = dir(module).resolve("${sanitize(fqn)}.svg")
    fun png(module: Module, fqn: String): Path = dir(module).resolve("${sanitize(fqn)}.png")
}
