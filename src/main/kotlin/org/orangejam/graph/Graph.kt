package org.orangejam.graph

//This is a directed graph
data class Vertex<T>(val index: Int, val data: T, val edges: Set<Edge<T>> = emptySet())

data class Edge<T>(val source: Vertex<T>, val destination: Vertex<T>)

sealed interface Node {
    data class InjectionField(
        val containerClass: String,
        val field: String,
    ) : Node

    data class ProviderMethod(
        val containerClass: String,
        val functionName: String,
        val signature: String
    ) : Node
}

data class Graph<T>(
    val vertices: List<Vertex<T>>,
    val edges: List<Edge<T>>
)