package org.orangejam.graph

/**
 * Slice the full graph down to nodes relevant to [componentFqn].
 * We try two modes:
 *  1) Consumer mode (default): start from this class' InjectionFields and walk *backwards* (incoming edges) to providers.
 *  2) Provider mode (fallback): if there are no InjectionFields, start from this class' ProviderMethods and walk *forwards*
 *     (outgoing edges) to the things they provide / influence.
 */
object GraphSlice {

    private fun norm(s: String): String = s.replace('/', '.')

    fun subgraphForComponent(full: Graph<Node>, componentFqn: String): Graph<Node> {
        if (full.vertices.isEmpty()) return Graph(emptyList(), emptyList())
        val want = norm(componentFqn)

        // ---- Mode 1: CONSUMER (backward): seeds = injection fields owned by this class
        val incoming = full.edges.groupBy { it.destination.index }
        val consumerSeeds = full.vertices.filter { v ->
            val d = v.data
            d is Node.InjectionField && norm(d.containerClass) == want
        }

        if (consumerSeeds.isNotEmpty()) {
            val (keepV, keepE) = bfsBackward(consumerSeeds, incoming)
            return reindexSubgraph(keepV, keepE)
        }

        // ---- Mode 2: PROVIDER (forward): seeds = provider methods owned by this class
        val outgoing = full.edges.groupBy { it.source.index }
        val providerSeeds = full.vertices.filter { v ->
            val d = v.data
            d is Node.ProviderMethod && norm(d.containerClass) == want
        }

        if (providerSeeds.isNotEmpty()) {
            val (keepV, keepE) = bfsForward(providerSeeds, outgoing)
            return reindexSubgraph(keepV, keepE)
        }

        // Nothing owned by this class name
        return Graph(emptyList(), emptyList())
    }

    private fun bfsBackward(
        seeds: List<Vertex<Node>>,
        incoming: Map<Int, List<Edge<Node>>>
    ): Pair<LinkedHashSet<Vertex<Node>>, LinkedHashSet<Edge<Node>>> {
        val keepV = LinkedHashSet<Vertex<Node>>()
        val keepE = LinkedHashSet<Edge<Node>>()
        val q = ArrayDeque<Vertex<Node>>()
        seeds.forEach { if (keepV.add(it)) q += it }
        while (q.isNotEmpty()) {
            val dst = q.removeFirst()
            val inEdges = incoming[dst.index].orEmpty()
            for (e in inEdges) {
                keepE += e
                if (keepV.add(e.source)) q += e.source
            }
        }
        return keepV to keepE
    }

    private fun bfsForward(
        seeds: List<Vertex<Node>>,
        outgoing: Map<Int, List<Edge<Node>>>
    ): Pair<LinkedHashSet<Vertex<Node>>, LinkedHashSet<Edge<Node>>> {
        val keepV = LinkedHashSet<Vertex<Node>>()
        val keepE = LinkedHashSet<Edge<Node>>()
        val q = ArrayDeque<Vertex<Node>>()
        seeds.forEach { if (keepV.add(it)) q += it }
        while (q.isNotEmpty()) {
            val src = q.removeFirst()
            val outEdges = outgoing[src.index].orEmpty()
            for (e in outEdges) {
                keepE += e
                if (keepV.add(e.destination)) q += e.destination
            }
        }
        return keepV to keepE
    }

    private fun reindexSubgraph(
        keepV: LinkedHashSet<Vertex<Node>>,
        keepE: LinkedHashSet<Edge<Node>>
    ): Graph<Node> {
        if (keepV.isEmpty()) return Graph(emptyList(), emptyList())
        val indexMap = keepV.withIndex().associate { (newIdx, v) -> v.index to newIdx }
        val newVertices = keepV.map { v -> Vertex(index = indexMap.getValue(v.index), data = v.data, edges = emptySet()) }
        val byNewIndex = newVertices.associateBy { it.index }
        val newEdges = keepE.map { e ->
            val src = byNewIndex.getValue(indexMap.getValue(e.source.index))
            val dst = byNewIndex.getValue(indexMap.getValue(e.destination.index))
            Edge(source = src, destination = dst)
        }
        val outBySrc = newEdges.groupBy { it.source.index }
        val finalVertices = newVertices.map { v -> v.copy(edges = outBySrc[v.index]?.toSet() ?: emptySet()) }
        return Graph(finalVertices, newEdges)
    }
}
