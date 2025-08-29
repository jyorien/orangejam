package com.example.orangejam.GraphGen

import io.github.rchowell.dotlin.digraph
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import tiktok.knit.plugin.MetadataContainer
import tiktok.knit.plugin.asMetadataContainer
import tiktok.knit.plugin.element.ComponentClass
import java.io.File
import java.io.FileInputStream

data class GraphNode(
    val id: String,
    val label: String,
    val clusterId: String? = null
)

data class GraphEdge(
    val from: String,
    val to: String
)

data class GraphCluster(
    val id: String,
    val label: String,
    val nodes: List<GraphNode>
)

data class Graph(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val clusters: List<GraphCluster>
)

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

fun generateGraphDot(args: Array<String>){
    if(args.isEmpty()) { throw IllegalArgumentException("Please give the path to the classpath") }
    val pathStr = args[0]
    val path = File(pathStr)
    val outputPath = if (args.size >= 2) File(args[1]) else File(".")

    try {
        val context = path.walk().filter { it.isFile && it.extension == "class"  }.map { file ->
            readClassFile(file.absolutePath).asMetadataContainer()!!
        }.toList().toContext()

        var c = 0
        val g = digraph {
            context.boundComponentMap.forEach { (k, v) ->
                +subgraph("cluster_${c++}") {
                    node {
                        style = "filled"
                    }
                    color = "blue"
                    label = k
                    v.injections?.forEach { (field, injected) ->
                        +"\"$k$$field\"" + { label= "$field${injected.providesMethod.desc}" }
                        +"\"$k$${injected.providesMethod.functionName}\"" + { label= injected.providesMethod.functionName }
                    }
                    v.provides.forEach {
                        +"\"$k$${it.functionName}\"" + { label= "${it.functionName}${it.desc}" }
                    }
                }
                println("View Class: $k")
                v.injections?.forEach { (field, injected) ->
//                field - injected.providesMethod.functionName
                    "\"${injected.providesMethod.containerClass}$${injected.providesMethod.functionName}\"" - "\"$k$$field\""
                    println("   Field '$field' injected by '${injected.providesMethod.functionName}' from class '${injected.providesMethod.containerClass}'")
                }
                println()
            }
        }

        val graph = g.dot()
        println(graph)
        File(outputPath, "./graph.dot").writeText(graph)
    }catch (e:Exception){
        e.printStackTrace()
        System.err.println(e.message)
    }
}

fun generateGraph(classPath: File): Graph {
    val context = classPath.walk().filter { it.isFile && it.extension == "class" }.map { file ->
        readClassFile(file.absolutePath).asMetadataContainer()!!
    }.toList().toContext()

    val nodes = mutableListOf<GraphNode>()
    val edges = mutableListOf<GraphEdge>()
    val clusters = mutableListOf<GraphCluster>()

    var clusterCount = 0
    context.boundComponentMap.forEach { (componentName, component) ->
        val clusterId = "cluster_${clusterCount++}"
        val clusterNodes = mutableListOf<GraphNode>()

        component.injections?.forEach { (field, injected) ->
            val fieldNodeId = "${componentName}\$${field}"
            val fieldNode = GraphNode(
                id = fieldNodeId,
                label = "$field${injected.providesMethod.desc}",
                clusterId = clusterId
            )
            nodes.add(fieldNode)
            clusterNodes.add(fieldNode)

            val providerNodeId = "${componentName}\$${injected.providesMethod.functionName}"
            val providerNode = GraphNode(
                id = providerNodeId,
                label = injected.providesMethod.functionName,
                clusterId = clusterId
            )
            nodes.add(providerNode)
            clusterNodes.add(providerNode)
        }

        component.provides.forEach { providesMethod ->
            val providerNodeId = "${componentName}\$${providesMethod.functionName}"
            val providerNode = GraphNode(
                id = providerNodeId,
                label = "${providesMethod.functionName}${providesMethod.desc}",
                clusterId = clusterId
            )
            nodes.add(providerNode)
            clusterNodes.add(providerNode)
        }

        clusters.add(GraphCluster(
            id = clusterId,
            label = componentName,
            nodes = clusterNodes
        ))

        component.injections?.forEach { (field, injected) ->
            val fromNodeId = "${injected.providesMethod.containerClass}\$${injected.providesMethod.functionName}"
            val toNodeId = "${componentName}\$${field}"
            edges.add(GraphEdge(from = fromNodeId, to = toNodeId))
        }
    }

    return Graph(
        nodes = nodes.distinctBy { it.id },
        edges = edges,
        clusters = clusters
    )
}