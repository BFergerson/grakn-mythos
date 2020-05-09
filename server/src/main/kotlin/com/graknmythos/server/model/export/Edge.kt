package com.graknmythos.server.model.export

/**
 * Represents a index-based graph edge.
 *
 * @version 0.2.1
 * @since 0.1.0
 * @author [Brandon Fergerson](mailto:bfergerson@apache.org)
 */
data class Edge(
        val source: Int,
        val target: Int,
        val name: String,
        val type: String
)
