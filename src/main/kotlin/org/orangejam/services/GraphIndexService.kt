package org.orangejam.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import org.orangejam.graph.Graph
import org.orangejam.graph.Node
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class GraphIndexService {
    private val log = Logger.getInstance(GraphIndexService::class.java)

    // Module -> set of normalized FQNs
    private val eligibleByModule = ConcurrentHashMap<Module, Set<String>>()

    fun isEligible(module: Module, classFqn: String, graphProvider: () -> Graph<Node>?): Boolean {
        val norm = normalize(classFqn)
        val cached = eligibleByModule[module]
        if (cached != null) return norm in cached

        val g = graphProvider() ?: return false
        val built = buildEligibleIndex(g)
        eligibleByModule[module] = built
        return norm in built
    }

    private fun buildEligibleIndex(g: Graph<Node>): Set<String> {
        if (g.vertices.isEmpty()) return emptySet()
        val set = HashSet<String>(g.vertices.size)
        g.vertices.forEach { v ->
            when (val d = v.data) {
                is Node.InjectionField -> set += normalize(d.containerClass)
                is Node.ProviderMethod -> set += normalize(d.containerClass)
            }
        }
        return set
    }

    private fun normalize(name: String): String = name.replace('/', '.')
}
