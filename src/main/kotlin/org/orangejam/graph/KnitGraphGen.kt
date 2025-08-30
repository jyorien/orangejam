package org.orangejam.graph

import io.github.rchowell.dotlin.digraph
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import tiktok.knit.plugin.MetadataContainer
import tiktok.knit.plugin.asMetadataContainer
import tiktok.knit.plugin.element.ComponentClass
import tiktok.knit.plugin.element.ProvidesMethod
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

private fun attachInjectionTree(
    fieldVertex: Vertex<Node>,
    root: Injection,
    internVertex: (Node) -> Vertex<Node>,
    addEdge: (Edge<Node>) -> Unit
) {
    val q: ArrayDeque<Pair<Vertex<Node>, Injection>> = ArrayDeque()
    val seen = HashSet<String>() // avoid infinite loops on cycles

    fun key(pm: ProvidesMethod) = "${pm.containerClass}#${pm.functionName}:${pm.descWithReturnType()}"

    q.add(fieldVertex to root)

    while (q.isNotEmpty()) {
        val (parentVertex, inj) = q.removeFirst()

        val pm = inj.providesMethod
        if (!seen.add(key(pm))) continue

        val providerVertex = internVertex(
            Node.ProviderMethod(
                containerClass = pm.containerClass,
                functionName   = pm.functionName,
                signature      = pm.descWithReturnType()
            )
        )

        if (providerVertex != parentVertex) {
            // Provider -> parent (parent is either the field, or an upstream provider)
            addEdge(Edge(source = providerVertex, destination = parentVertex))
        }

        // Recurse into requirements: they will point to this provider
        inj.requirementInjections.forEach { child ->
            q.add(providerVertex to child)
        }
    }
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
        v.injections?.filter { (_, injection) -> injection.from == Injection.From.COMPOSITE }?.forEach { (field, injection) ->
            val fieldVertex = internVertex(Node.InjectionField(klassName, field))
            val pm = injection.providesMethod

            val records = v.getCompositeRecords()
            val pathsToProvider = records.get(pm.containerClass)

            val providerVertex = internVertex(Node.ProviderMethod(pm.containerClass, pm.functionName, pm.desc))

            for (rec in pathsToProvider) {
                var prev = providerVertex
                val hops = rec.steps

                for (hop in hops.asReversed()) {
                    val viaOwnerVertex = internVertex(Node.ProviderMethod(hop.ownerClass, hop.propName, pm.desc))
                    edges += Edge(source = prev, destination = viaOwnerVertex)
                    prev = viaOwnerVertex
                }

                edges += Edge(source = prev, destination = fieldVertex)
            }

        }
        v.injections?.filter { (_, injection) -> injection.from == Injection.From.SELF }?.forEach { (field, injection) ->

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