package org.orangejam

import com.example.orangejam.KnitPaths
import com.intellij.openapi.module.Module
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream

object ClassesDirFinder {
    // the class root is typically {any_module}/build/classes/kotlin/main/{any_module} right
    fun findCompiledClassesRoot(module: Module): Path? {
        val buildDir = KnitPaths.moduleBuildDir(module)
        val candidates = listOf(
            buildDir.resolve("classes/kotlin/main"),
            buildDir.resolve("classes/java/main"),
        )
        for (p in candidates) {
            if (Files.isDirectory(p) && hasClassFiles(p)) return p
        }
        return null
    }

    private fun hasClassFiles(dir: Path): Boolean =
        Files.walk(dir, 4).use {
            s: Stream<Path> -> s.anyMatch { it.toString().endsWith(".class") }
        }
}
