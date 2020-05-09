package com.graknmythos.server.model.export

/**
 * Represents a id-based graph edge.
 *
 * @version 0.2.1
 * @since 0.1.0
 * @author [Brandon Fergerson](mailto:bfergerson@apache.org)
 */
data class GraknEdge(
        val source: String,
        val target: String,
        val name: String,
        val type: String
)
