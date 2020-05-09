package com.graknmythos.server.model

/**
 * Represents a saved legend.
 *
 * @version 0.2.1
 * @since 0.1.0
 * @author [Brandon Fergerson](mailto:bfergerson@apache.org)
 */
data class Legend(
        /** Unique identifier */
        val id: String,
        /** Useful description */
        val description: String,
        /** Optional image thumbnail */
        val image: String?,
        /** Query used to re-create graph */
        val query: String? = null,
        /** Query options to re-create graph */
        val queryOptions: QueryOptions
)
