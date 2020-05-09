package com.graknmythos.server.manage

import com.google.gson.Gson
import com.graknmythos.server.model.Legend
import com.graknmythos.server.model.QueryOptions
import grakn.client.GraknClient
import graql.lang.Graql.*
import graql.lang.query.GraqlDefine
import org.apache.commons.lang3.RandomStringUtils
import org.apache.commons.text.StringEscapeUtils
import org.slf4j.LoggerFactory.getLogger
import java.io.File

/**
 * Executes internal functionality necessary to run the website and API.
 *
 * @version 0.2.0
 * @since 0.1.0
 * @author [Brandon Fergerson](mailto:bfergerson@apache.org)
 */
class MythosInternal(private var client: GraknClient, private val imageStorageLocation: File, staticKeyspace: String?) {

    val useTempLegendKeyspaces = staticKeyspace == null
    val legendKeyspace = staticKeyspace ?: "mythos_temp_" + RandomStringUtils.randomAlphanumeric(20).toLowerCase()

    companion object {
        @JvmStatic
        private val log = getLogger(javaClass.enclosingClass)
    }

    /**
     * Install the `grakn_mythos.gql` schema to the `grakn_mythos_internal` keyspace.
     *
     * @param[schema] mythos Graql schema.
     */
    fun installSchema(schema: String) {
        log.info("Installing schema")
        val session: GraknClient.Session = client.session("grakn_mythos_internal")
        val tx = session.transaction().write()
        try {
            tx.execute(parse(schema) as GraqlDefine)
        } finally {
            tx.commit()
            session.close()
        }
    }

    /**
     * Fetch random [Legend]s.
     *
     * @param[limit] max amount of [Legend]s to fetch.
     * @param[includeQuery] whether or not to include [Legend.query] in the response(s).
     * @return list of fetched [Legend]s.
     */
    fun getLegends(limit: Long, includeQuery: Boolean = true): List<Legend> {
        val session: GraknClient.Session = client.session("grakn_mythos_internal")
        val tx = session.transaction().read()
        try {
            if (includeQuery) {
                val answer = tx.execute(
                        match(`var`("l").isa("legend")
                                .has("legend_id", `var`("id"))
                                .has("legend_description", `var`("description"))
                                .has("legend_query", `var`("query"))
                                .has("legend_query_options", `var`("query_options"))
                        ).get("id", "description", "query", "query_options").limit(limit)
                )

                if (answer.isNotEmpty()) {
                    val list = ArrayList<Legend>()
                    answer.forEach {
                        val legendId = it.get("id").asAttribute<String>().value()
                        list.add(Legend(legendId,
                                StringEscapeUtils.unescapeJava(it.get("description").asAttribute<String>().value()),
                                File(imageStorageLocation, legendId).readText(),
                                StringEscapeUtils.unescapeJava(it.get("query").asAttribute<String>().value()),
                                Gson().fromJson(StringEscapeUtils.unescapeJava(it.get("query_options").asAttribute<String>().value()), QueryOptions::class.java)
                        ))
                    }
                    return list
                }
            } else {
                val answer = tx.execute(
                        match(`var`("l").isa("legend")
                                .has("legend_id", `var`("id"))
                                .has("legend_description", `var`("description"))
                                .has("legend_query_options", `var`("query_options"))
                        ).get("id", "description", "query_options").limit(limit)
                )

                if (answer.isNotEmpty()) {
                    val list = ArrayList<Legend>()
                    answer.forEach {
                        val legendId = it.get("id").asAttribute<String>().value()
                        val imageFile = File(imageStorageLocation, legendId)
                        var imageData: String? = null
                        if (imageFile.exists()) {
                            imageData = imageFile.readText()
                        }

                        list.add(Legend(legendId,
                                StringEscapeUtils.unescapeJava(it.get("description").asAttribute<String>().value()), imageData,
                                queryOptions = Gson().fromJson(StringEscapeUtils.unescapeJava(it.get("query_options").asAttribute<String>().value()), QueryOptions::class.java)
                        ))
                    }
                    return list
                }
            }
            return emptyList()
        } finally {
            tx.close()
            session.close()
        }
    }

    /**
     * Fetch specific [Legend] by [Legend.id].
     *
     * @param[legendId] the [Legend.id] of the [Legend] to fetch.
     * @param[includeImage] whether or not to include [Legend.image] in the response.
     * @return the [Legend], null if not found
     */
    fun getLegend(legendId: String, includeImage: Boolean = true): Legend? {
        val session: GraknClient.Session = client.session("grakn_mythos_internal")
        val tx = session.transaction().read()
        try {
            val answer = tx.execute(
                    match(`var`("l").isa("legend")
                            .has("legend_id", legendId)
                            .has("legend_description", `var`("description"))
                            .has("legend_query", `var`("query"))
                            .has("legend_query_options", `var`("query_options"))
                    ).get("description", "query", "query_options")
            )

            if (answer.isNotEmpty()) {
                return if (includeImage) {
                    val imageFile = File(imageStorageLocation, legendId)
                    var imageData: String? = null
                    if (imageFile.exists()) {
                        imageData = imageFile.readText()
                    }

                    Legend(legendId, StringEscapeUtils.unescapeJava(answer[0].get("description").asAttribute<String>().value()), imageData,
                            StringEscapeUtils.unescapeJava(answer[0].get("query").asAttribute<String>().value()),
                            Gson().fromJson(StringEscapeUtils.unescapeJava(answer[0].get("query_options").asAttribute<String>().value()), QueryOptions::class.java)
                    )
                } else {
                    Legend(legendId, StringEscapeUtils.unescapeJava(answer[0].get("description").asAttribute<String>().value()), null,
                            StringEscapeUtils.unescapeJava(answer[0].get("query").asAttribute<String>().value()),
                            Gson().fromJson(StringEscapeUtils.unescapeJava(answer[0].get("query_options").asAttribute<String>().value()), QueryOptions::class.java)
                    )
                }
            }
            return null
        } finally {
            tx.close()
            session.close()
        }
    }

    /**
     * Save a new [Legend].
     *
     * @param[legendId] the [Legend.id] of the [Legend] to save.
     * @param[legendDescription] the [Legend.description] of the [Legend] to save.
     * @param[legendQuery] the [Legend.query] of the [Legend] to save.
     * @param[image] the [Legend.image] of the [Legend] to save.
     * @param[queryOptions] the [Legend.queryOptions] of the [Legend] to save.
     */
    fun saveLegend(legendId: String, legendDescription: String, legendQuery: String, image: String?,
                   queryOptions: QueryOptions) {
        val session: GraknClient.Session = client.session("grakn_mythos_internal")
        val tx = session.transaction().write()
        try {
            tx.execute(
                    insert(`var`("l").isa("legend")
                            .has("legend_id", legendId)
                            .has("legend_description", StringEscapeUtils.escapeJava(legendDescription))
                            .has("legend_query", StringEscapeUtils.escapeJava(legendQuery))
                            .has("legend_query_options", StringEscapeUtils.escapeJava(Gson().toJson(queryOptions)))
                    )
            )
        } finally {
            tx.commit()
            session.close()

            if (image != null) {
                //save graph image thumbnail
                File(imageStorageLocation, legendId).writeText(image)
            }
        }
    }
}
