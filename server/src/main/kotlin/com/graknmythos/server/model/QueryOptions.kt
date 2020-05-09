package com.graknmythos.server.model

import com.google.gson.Gson
import com.graknmythos.server.model.export.Graph
import com.graknmythos.server.model.export.Node

/**
 * Used to modify the query used on Grakn and the resulting [Graph].
 *
 * @version 0.2.0
 * @since 0.1.0
 * @author [Brandon Fergerson](mailto:bfergerson@apache.org)
 */
data class QueryOptions(
        val includeAnonymousVariables: Boolean = false,
        val displayOptions: DisplayOptions = DisplayOptions()
) {
    override fun toString(): String {
        return Gson().toJson(this)
    }
}

/**
 * Allows modification of the resulting [Graph] by adjusting how Grakn ConceptMaps are parsed.
 */
data class DisplayOptions(
        val entityNamingScheme: EntityNamingScheme = EntityNamingScheme.BY_VARIABLE,
        val relationNamingScheme: RelationNamingScheme = RelationNamingScheme.BY_VARIABLE,
        val attributeNamingScheme: AttributeNamingScheme = AttributeNamingScheme.BY_VALUE,
        val linkNodesById: Boolean = false
)

/**
 * Allows modification of the resulting [Graph] by adjusting how [Node]s ([Node.type] = 'entity') are named.
 */
enum class EntityNamingScheme {
    BY_VARIABLE,
    BY_TYPE,
    BY_ID
}

/**
 * Allows modification of the resulting [Graph] by adjusting how [Node]s ([Node.type] = 'relation') are named.
 */
enum class RelationNamingScheme {
    BY_VARIABLE,
    BY_TYPE,
    BY_ID
}

/**
 * Allows modification of the resulting [Graph] by adjusting how [Node]s ([Node.type] = 'attribute') are named.
 */
enum class AttributeNamingScheme {
    BY_VALUE,
    BY_ID
}