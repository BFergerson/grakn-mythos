package com.graknmythos.server.model.api

import com.graknmythos.server.model.QueryOptions

/**
 * Data class for requests to execute legends.
 *
 * @version 0.2.0
 * @since 0.1.0
 * @author [Brandon Fergerson](mailto:bfergerson@apache.org)
 */
data class ExecuteLegendRequest(
        val query: String,
        val queryOptions: QueryOptions
)