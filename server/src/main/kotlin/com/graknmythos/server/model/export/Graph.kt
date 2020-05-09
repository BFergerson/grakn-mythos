package com.graknmythos.server.model.export

/**
 * Graph format used for visualization.
 *
 * @version 0.2.2
 * @since 0.1.0
 * @author [Brandon Fergerson](mailto:bfergerson@apache.org)
 */
data class Graph(
        val nodes: List<Node>,
        val links: List<Any> //todo: more strict Edge or GraknEdge
)
