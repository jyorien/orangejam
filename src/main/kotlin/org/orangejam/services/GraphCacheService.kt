package org.orangejam.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.orangejam.ClassesDirFinder
import org.orangejam.graph.Graph
import org.orangejam.graph.Node
import org.orangejam.graph.getContext
import org.orangejam.graph.parseDependencyGraph
import java.nio.file.Files

@Service(Service.Level.PROJECT)
class GraphCacheService(private val project: Project) {
    private val log = Logger.getInstance(GraphCacheService::class.java)
    private val cache = LinkedHashMap<Module, Graph<Node>>()

    fun getOrBuild(module: Module): Graph<Node>? {
        cache[module]?.let { return it }

        val classesRoot = ClassesDirFinder.findCompiledClassesRoot(module) ?: run {
            log.warn("GraphCache: no compiled classes found for module ${module.name}")
            return null
        }
        if (!Files.isDirectory(classesRoot)) {
            log.warn("GraphCache: classes root not a directory: $classesRoot")
            return null
        }

        return try {
            val ctx = getContext(classesRoot.toFile())
            val g = parseDependencyGraph(ctx)
            cache[module] = g
            g
        } catch (t: Throwable) {
            log.warn("GraphCache: parse failed for module ${module.name}", t)
            null
        }
    }

    fun invalidate(module: Module) { cache.remove(module) }
    fun clear() = cache.clear()
}
