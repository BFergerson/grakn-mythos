package com.graknmythos.server.model.export

/**
 * Represents a graph node.
 *
 * @version 0.2.0
 * @since 0.1.0
 * @author [Brandon Fergerson](mailto:bfergerson@apache.org)
 */
data class Node(
        val name: String,
        val id: String,
        val type: String,
        val category: String,
        val data: String? = null
) {
    override fun equals(other: Any?): Boolean {
        return id == (other as Node).id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
