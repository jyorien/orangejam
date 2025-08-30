package org.orangejam.navigation

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.CompilerProjectExtension
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.orangejam.graph.Graph
import org.orangejam.graph.Node
import org.orangejam.graph.getContext
import org.orangejam.graph.parseDependencyGraph
import java.io.File

@Service(Service.Level.PROJECT)
class GraphProvider(private val project: Project) {
    fun graph(): Graph<Node> = obtainGraph(project)
}

// ---- cached builder ----
private var cachedGraph: CachedValue<Graph<Node>>? = null

private fun obtainGraph(project: Project): Graph<Node> {
    val cvm = CachedValuesManager.getManager(project)
    return cachedGraph?.value ?: cvm.createCachedValue {
        val classesDir = ReadAction.compute<File?, RuntimeException> {
            findFirstProductionOutputDir(project)
                ?: CompilerProjectExtension.getInstance(project)?.compilerOutput?.let { File(it.path) }
        } ?: error("No compiled classes directory found. Build the project, or set a fixed path.")

        val context = getContext(classesDir)
        val graph = parseDependencyGraph(context)

        CachedValueProvider.Result.create(
            graph,
            PsiModificationTracker.MODIFICATION_COUNT,            // PSI changes
            ProjectRootManager.getInstance(project),              // roots/classpath changes
            VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS        // jars/out dirs
        )
    }.also { cachedGraph = it }.value
}

private fun findFirstProductionOutputDir(project: Project): File? {
    for (m in ModuleManager.getInstance(project).modules) {
        CompilerPaths.getModuleOutputPath(m, false)?.let { p ->
            val f = File(p); if (f.exists()) return f
        }
    }
    for (m in ModuleManager.getInstance(project).modules) {
        CompilerPaths.getModuleOutputPath(m, true)?.let { p ->
            val f = File(p); if (f.exists()) return f
        }
    }
    return null
}
