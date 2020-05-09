package com.graknmythos.server.model.api

import com.graknmythos.server.model.QueryOptions

/**
 * Data class for requests to save legends.
 *
 * @version 0.2.2
 * @since 0.1.0
 * @author [Brandon Fergerson](mailto:bfergerson@apache.org)
 */
data class SaveLegendRequest(
        val query: String,
        val queryOptions: QueryOptions,
        val description : String,
        val image: String?
)