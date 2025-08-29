package org.orangejam.graph

import io.github.rchowell.dotlin.digraph
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import tiktok.knit.plugin.MetadataContainer
import tiktok.knit.plugin.asMetadataContainer
import tiktok.knit.plugin.element.ComponentClass
import tiktok.knit.plugin.injection.Injection
import java.io.File
import java.io.FileInputStream
import java.util.LinkedList
import java.util.Queue

fun main(args: Array<String>){
    if(args.isEmpty()) { throw IllegalArgumentException("Please give the path to the classpath") }
    val pathStr = args[0]
    val path = File(pathStr)

    try {
        val context = getContext(path)
        val dependencyGraph = parseDependencyGraph(context)
        val dot = toDotWithDotlin(dependencyGraph)
        println(dot)
        File("./graph.dot").writeText(dot)
    }catch (e:Exception){
        e.printStackTrace()
        System.err.println(e.message)
    }
}

fun getContext(path: File): GraphContext {
    val context = path.walk().filter { it.isFile && it.extension == "class"  }.map { file ->
        readClassFile(file.absolutePath).asMetadataContainer()!!
    }.toList().toContext()
    return context
}

fun readClassFile(klassFile: String): ClassNode {
    FileInputStream(klassFile).use {
        val classNode = ClassNode()
        val classReader = ClassReader(it)

        classReader.accept(classNode, ClassReader.SKIP_FRAMES)
        return classNode
    }
}

fun MetadataContainer.toComponent(): ComponentClass {
    return ComponentClass.from(this)
}

private fun nodeId(node: Node): String = when (node) {
    is Node.InjectionField -> "\"${node.containerClass}\$${node.field}\""
    is Node.ProviderMethod -> "\"${node.containerClass}\$${node.functionName}\""
}

private fun nodeLabel(n: Node): String = when (n) {
    is Node.InjectionField -> n.field
    is Node.ProviderMethod -> "${n.functionName}${n.signature}"
}

/**
 *  We just parse the GraphContext into simple Dependency Graph structure here
 */
fun parseDependencyGraph(
    context: GraphContext
): Graph<Node> {
    val vertexById = LinkedHashMap<String, Vertex<Node>>()
    val edges = ArrayList<Edge<Node>>()
    var idx = 0

    fun internVertex(node: Node): Vertex<Node> { // Keep one copy of the Vertex
        val id = nodeId(node)
        return vertexById.getOrPut(id) {
            Vertex(index = idx++, data = node)
        }
    }

    context.boundComponentMap.forEach { (klassName, v) ->
        v.injections?.forEach { (field, injection) ->

            val injectionField = Node.InjectionField(klassName, field)
            val injectionFieldVertex = internVertex(injectionField)

            val q: Queue<Pair<Vertex<Node>, Injection>> = LinkedList()
            // Just simple bfs to collect all the injections
            q.add(injectionFieldVertex to injection)

            while(q.isNotEmpty()) {
                val (parentVertex, injection) = q.poll()

                // Create node
                val pm = injection.providesMethod
                val provider = Node.ProviderMethod(pm.containerClass, pm.functionName, pm.descWithReturnType())
                val providerVertex = internVertex(provider)

                //Link node
                if(providerVertex != parentVertex)edges += Edge(source = providerVertex, destination = parentVertex)

                q.addAll(injection.requirementInjections.map { providerVertex to it })
            }
        }
    }

    // recreate the vertices with edges (not very optimal, but we will make due with this for now)
    val outgoing = edges.groupBy { it.source.index }
    val vertices = vertexById.values.map { v ->
        v.copy(edges = outgoing[v.index]?.toSet() ?: emptySet())
    }

    return Graph(vertices, edges)
}

fun toDotWithDotlin(dependencyGraph: Graph<Node>): String {
    var c = 0

    val g = digraph {
        node  { style = "filled" }

        // Group by containerClass for subgraph clusters
        val byCluster = dependencyGraph.vertices.groupBy { v ->
            when (val d = v.data) {
                is Node.InjectionField -> d.containerClass
                is Node.ProviderMethod -> d.containerClass
            }
        }

        byCluster.forEach { (klass, verts) ->
            +subgraph("cluster_${c++}") {
                color = "blue"
                label = klass

                verts.forEach { v ->
                    val id = nodeId(v.data)
                    +id + { label = nodeLabel(v.data) }
                }
            }
        }

        // Edges (directed)
        dependencyGraph.edges.forEach { e ->
            nodeId(e.source.data) - nodeId(e.destination.data)
        }
    }

    return g.dot()
}