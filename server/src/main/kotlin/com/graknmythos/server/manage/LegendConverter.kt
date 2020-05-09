package com.graknmythos.server.manage

import com.graknmythos.server.model.*
import com.graknmythos.server.model.export.Edge
import com.graknmythos.server.model.export.GraknEdge
import com.graknmythos.server.model.export.Graph
import com.graknmythos.server.model.export.Node
import grakn.client.GraknClient
import grakn.client.answer.ConceptMap
import grakn.client.concept.thing.impl.AttributeImpl
import grakn.client.concept.thing.impl.EntityImpl
import grakn.client.concept.thing.impl.RelationImpl
import graql.lang.Graql
import graql.lang.Graql.parseList
import graql.lang.pattern.Disjunction
import graql.lang.pattern.Pattern
import graql.lang.property.HasAttributeProperty
import graql.lang.property.RelationProperty
import graql.lang.query.GraqlDefine
import graql.lang.query.GraqlGet
import graql.lang.query.GraqlInsert
import graql.lang.query.GraqlQuery
import graql.lang.statement.StatementRelation
import graql.lang.statement.StatementThing
import graql.lang.statement.Variable
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.joor.Reflect
import org.slf4j.LoggerFactory
import java.util.*
import java.util.stream.Stream

/**
 * Converts Grakn query to [Graph].
 * Todo: sane regex
 * Todo: not rely on reflection
 * Todo: This code is bad. Will clean soon.
 *
 * @version 0.2.1
 * @since 0.1.0
 * @author [Brandon Fergerson](mailto:bfergerson@apache.org)
 */
class LegendConverter(private val usingTemporaryKeyspaces: Boolean, private val keyspace: String,
                      private val client: GraknClient) {
    companion object {
        val cache = HashMap<String, Graph>() //todo: optional redis

        @JvmStatic
        private val log = LoggerFactory.getLogger(javaClass.enclosingClass)
    }

    fun convert(legend: Legend): Graph {
        return convert(legend.query!!, legend.queryOptions)
    }

    fun convert(query: String, options: QueryOptions): Graph {
        if (cache.containsKey(query + "options-" + options.toString())) {
            return cache[query + "options-" + options.toString()]!!
        }
        val session: GraknClient.Session = client.session(keyspace)
        var tx = session.transaction().write()

        log.info("Executing query: " + query.replace("\n", " "))
        try {
            val resultStreams: MutableList<Stream<ConceptMap>> = ArrayList()
            parseList<GraqlQuery>(query).forEach { graqlQuery ->
                when (graqlQuery) {
                    is GraqlDefine -> {
                        log.info("Executing Graql define")
                        tx.execute(graqlQuery)
                        tx.commit() //todo: only commit if contains rule
                        tx = session.transaction().write()
                    }
                    is GraqlGet -> {
                        val varMap = HashMap<Variable, Variable>()
                        graqlQuery.match().patterns.patterns.forEach { statement ->
                            replacePatternVars(options, varMap, statement)
                        }
                        val getVars = HashSet(graqlQuery.vars())
                        getVars.removeAll(graqlQuery.match().patterns.variables())
                        getVars.forEach {
                            if (varMap.containsKey(it.asReturnedVar())) {
                                graqlQuery.vars().add(varMap[it.asReturnedVar()])
                                graqlQuery.vars().remove(it)
                            }
                        }

                        log.info("Executing Graql query")
                        resultStreams.add(tx.stream(graqlQuery))
                    }
                    is GraqlGet.Group -> {
                        log.info("Executing Graql group")
                        tx.execute(graqlQuery)
                    }
                    is GraqlGet.Group.Aggregate -> {
                        log.info("Executing Graql group aggregate")
                        tx.execute(graqlQuery)
                    }
                    is GraqlGet.Aggregate -> {
                        log.info("Executing Graql aggregate")
                        tx.execute(graqlQuery)
                    }
                    is GraqlInsert -> {
                        log.info("Executing Graql insert")
                        tx.execute(graqlQuery)
                    }
                    else -> throw UnsupportedOperationException(query)
                }
            }

            log.info("Parsing query result(s)")
            val nodes = ArrayList<Node>()
            val edges = HashSet<Any>()
            resultStreams.forEach {
                val indexMap = HashMap<String, Int>()
                it.forEach {
                    val varMap = HashMap<String, String>()
                    val hyperRelationVarSet = HashSet<String>()
                    val regularRelationTypeMap = HashMap<String, String>()
                    if (it.map().isNotEmpty()) {
                        it.map().forEach {
                            if (it.value is EntityImpl.Local || it.value is EntityImpl.Remote) {
                                val id = it.value.asEntity().id().toString()
                                val entityType = it.value.asEntity().type().label().toString()
                                val name = when (options.displayOptions.entityNamingScheme) {
                                    EntityNamingScheme.BY_VARIABLE -> {
                                        it.key.toString()
                                    }
                                    EntityNamingScheme.BY_TYPE -> {
                                        entityType
                                    }
                                    EntityNamingScheme.BY_ID -> {
                                        id
                                    }
                                }
                                val node = Node(name, id, "entity", entityType)
                                if (!indexMap.containsKey(node.id)) {
                                    indexMap[node.id] = nodes.size
                                    nodes.add(node)
                                }
                                varMap[it.key.name()] = node.id
                            } else if (isAttribute(it.value)) {
                                val id = it.value.asAttribute<Any>().id().toString()
                                val value = it.value.asAttribute<Any>().value().toString()
                                val name = when (options.displayOptions.attributeNamingScheme) {
                                    AttributeNamingScheme.BY_VALUE -> {
                                        value
                                    }
                                    AttributeNamingScheme.BY_ID -> {
                                        id
                                    }
                                }

                                val node = if (options.displayOptions.attributeNamingScheme == AttributeNamingScheme.BY_ID) {
                                    Node(name, id, "attribute", it.value.asAttribute<Any>().type().label().toString(), value)
                                } else {
                                    Node(name, id, "attribute", it.value.asAttribute<Any>().type().label().toString())
                                }
                                if (!indexMap.containsKey(node.id)) {
                                    indexMap[node.id] = nodes.size
                                    nodes.add(node)
                                }
                                varMap[it.key.name()] = node.id
                            } else if (isRelation(it.value) && it.key.toString().contains("mythos_internal")) {
                                val matches = "mythos_internal_(.+)_relation_(.+)".toRegex().find(it.key.name())!!
                                val tmpPlayers = matches.groups[1]!!.value
                                var text = tmpPlayers + "_"
                                if (text.contains("mythos_internal")) {
                                    while (text.contains("mythos_internal")) {
                                        //todo: this, but competently
                                        val start = text.indexOf("mythos_internal")
                                        val end = text.indexOf("_relation_")
                                        var afterRelation = text.substring(end).indexOf("mythos")
                                        if (afterRelation != -1) {
                                            afterRelation += end
                                        } else {
                                            afterRelation = text.length
                                        }
                                        val userHyperRelationName = text.substring(end + 10, afterRelation - 1)
                                        val hyperRelationName = text.substring(start, end)
                                        val hyperRelationVar = """${hyperRelationName}_relation_${userHyperRelationName}"""
                                        hyperRelationVarSet.add(hyperRelationVar)
                                        text = text.replace("""mythos${hyperRelationName}_relation_${userHyperRelationName}_""", "")
                                    }
                                } else {
                                    varMap[it.key.name()] = it.value.asRelation().id().value
                                    regularRelationTypeMap[it.key.name()] = it.value.asRelation().type().label().value
                                }
                            }
                        }

                        it.map().forEach {
                            if (isAttribute(it.value) && it.key.toString().contains("mythos_internal")) {
                                val id = it.value.asAttribute<Any>().id().toString()
                                val matches = "mythos_internal_(.+)_attribute_(.+)".toRegex().find(it.key.name())!!
                                val entityVar = matches.groupValues[1]
                                if (varMap.containsKey(entityVar)) {
                                    val name = when (options.displayOptions.relationNamingScheme) {
                                        RelationNamingScheme.BY_VARIABLE -> {
                                            "$" + matches.groupValues.last()
                                        }
                                        RelationNamingScheme.BY_TYPE -> {
                                            it.value.asAttribute<Any>().type().label().value
                                        }
                                        RelationNamingScheme.BY_ID -> {
                                            id
                                        }
                                    }

                                    if (options.displayOptions.linkNodesById) {
                                        edges.add(GraknEdge(varMap[entityVar]!!, id, name, "attribute"))
                                    } else {
                                        edges.add(Edge(indexMap[varMap[entityVar]]!!, indexMap[id]!!, name, "attribute"))
                                    }
                                }
                            } else if (isRelation(it.value) && it.key.toString().contains("mythos_internal")) {
                                val matches = "mythos_internal_(.+)_relation_(.+)".toRegex().find(it.key.name())!!
                                val relationId = it.value.asRelation().id().toString()
                                val name = when (options.displayOptions.relationNamingScheme) {
                                    RelationNamingScheme.BY_VARIABLE -> {
                                        "$" + matches.groupValues.last()
                                    }
                                    RelationNamingScheme.BY_TYPE -> {
                                        it.value.asRelation().type().label().value
                                    }
                                    RelationNamingScheme.BY_ID -> {
                                        relationId
                                    }
                                }
                                val tmpPlayers = matches.groups[1]!!.value

                                var hyperRelation = false
                                var text = tmpPlayers + "_"
                                val finalPlayers = ArrayList<String>()
                                if (text.contains("mythos_internal")) {
                                    hyperRelation = true
                                    while (text.contains("mythos_internal")) {
                                        //todo: this, but competently
                                        val start = text.indexOf("mythos_internal")
                                        val end = text.indexOf("_relation_")
                                        var afterRelation = text.substring(end).indexOf("mythos")
                                        if (afterRelation != -1) {
                                            afterRelation += end
                                        } else {
                                            afterRelation = text.length
                                        }
                                        val userHyperRelationName = text.substring(end + 10, afterRelation - 1)
                                        val tempHyperRelationName = text.substring(start, end)
                                        val hyperRelationVar = """${tempHyperRelationName}_relation_${userHyperRelationName}"""
                                        finalPlayers.add(hyperRelationVar)

                                        val hyperRelationType = regularRelationTypeMap[hyperRelationVar]!!
                                        val hyperRelationId = varMap[hyperRelationVar]!!
                                        val hyperRelationNodeName = when (options.displayOptions.relationNamingScheme) {
                                            RelationNamingScheme.BY_VARIABLE -> {
                                                "$" + userHyperRelationName
                                            }
                                            RelationNamingScheme.BY_TYPE -> {
                                                hyperRelationType
                                            }
                                            RelationNamingScheme.BY_ID -> {
                                                hyperRelationId
                                            }
                                        }
                                        val node = Node(hyperRelationNodeName, hyperRelationId, "relation", hyperRelationType)
                                        if (!indexMap.containsKey(node.id)) {
                                            indexMap[node.id] = nodes.size
                                            nodes.add(node)
                                        }
                                        varMap[it.key.name()] = node.id

                                        val hyperPlayers = ArrayList<String>()
                                        val players = (tempHyperRelationName + "_").split("mythos")
                                        players.forEach {
                                            if (!it.isEmpty() && it != "_internal_") {
                                                hyperPlayers.add(it.substring(0, it.length - 1))
                                            }
                                        }
                                        for (i in hyperPlayers.indices) {
                                            val source = hyperPlayers[i]
                                            val hyperRelationPlayerName = when (options.displayOptions.relationNamingScheme) {
                                                RelationNamingScheme.BY_VARIABLE -> {
                                                    "$" + source
                                                }
                                                RelationNamingScheme.BY_TYPE -> {
                                                    hyperRelationType
                                                }
                                                RelationNamingScheme.BY_ID -> {
                                                    varMap[source]!!
                                                }
                                            }

                                            if (options.displayOptions.linkNodesById) {
                                                edges.add(GraknEdge(varMap[source]!!, hyperRelationId, hyperRelationPlayerName, "relation"))
                                            } else {
                                                edges.add(Edge(indexMap[varMap[source]]!!, indexMap[hyperRelationId]!!, hyperRelationPlayerName, "relation"))
                                            }
                                        }
                                        text = text.replace("""mythos${tempHyperRelationName}_relation_${userHyperRelationName}_""", "")
                                    }
                                }
                                if (hyperRelation || !hyperRelationVarSet.contains(it.key.name())) {
                                    val players = text.split("mythos")
                                    players.forEach {
                                        if (!it.isEmpty()) {
                                            finalPlayers.add(it.substring(0, it.length - 1))
                                        }
                                    }

                                    for (i in finalPlayers.indices) {
                                        if (i + 1 < finalPlayers.size) {
                                            for (z in i + 1 until finalPlayers.size) {
                                                val source = finalPlayers[i]
                                                val target = finalPlayers[z]

                                                if (options.displayOptions.linkNodesById) {
                                                    edges.add(GraknEdge(varMap[source]!!, varMap[target]!!, name, "relation"))
                                                } else {
                                                    edges.add(Edge(indexMap[varMap[source]]!!, indexMap[varMap[target]]!!, name, "relation"))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            log.info("Finished parsing query result(s)")

            cache[query + "options-" + options.toString()] = Graph(nodes, edges.toList())
            return cache[query + "options-" + options.toString()]!!
        } finally {
            tx.commit()

            if (usingTemporaryKeyspaces) {
                GlobalScope.launch {
                    deleteTemporaryKeyspace(session)
                }
            } else {
                session.close()
            }
        }
    }

    private fun deleteTemporaryKeyspace(session: GraknClient.Session) {
        session.use {
            log.info("Deleting keyspace: " + it.keyspace().name())
            client.keyspaces().delete(it.keyspace().name())
            log.info("Active keyspaces: " + client.keyspaces().retrieve().size)
        }
    }

    private fun replacePatternVars(options: QueryOptions, varMap: HashMap<Variable, Variable>, pattern: Pattern) {
        if (pattern is StatementThing) {
            val v = pattern.`var`()
            pattern.properties().forEach {
                if (it is HasAttributeProperty) {
                    if (it.attribute().variables().size > 1) {
                        replacePatternVars(options, varMap, it.attribute())
                    } else {
                        if (it.attribute().`var`().type() == Variable.Type.NAMED || options.includeAnonymousVariables) {
                            val copyHas = Graql.`var`().has(it.type(), it.attribute()).properties().first()
                            val replaceVar = Variable("mythos_internal_" + v.name() + "_attribute_" + it.attribute().`var`().name())
                            varMap[Variable(it.attribute().`var`().name())] = replaceVar
                            Reflect.on(it.attribute()).set("var", replaceVar)
                            pattern.properties().add(copyHas)
                        }
                    }
                }
            }
        } else if (pattern is Disjunction<*>) {
            pattern.patterns.forEach {
                replacePatternVars(options, varMap, it)
            }
        } else if (pattern is StatementRelation) {
            var replaceVarName = ""
            val relationVar = pattern.`var`()
            if (relationVar.type() == Variable.Type.NAMED) {
                val players = ArrayList<Variable>()
                pattern.properties().forEach {
                    if (it is RelationProperty) {
                        players.addAll(it.relationPlayers().map {
                            if (varMap.containsKey(it.player.`var`())) {
                                val hyperRelation = varMap[it.player.`var`()]!!
                                Reflect.on(it.player).set("var", hyperRelation)
                                replaceVarName += "mythos" + hyperRelation.name() + "_"
                                hyperRelation
                            } else {
                                replaceVarName += "mythos" + it.player.`var`().name() + "_"
                                it.player.`var`()
                            }
                        })
                    }
                }

                val fullReplaceVarName = "mythos_internal_" + replaceVarName + "relation_" + relationVar.name()
                val replaceVar = Variable(fullReplaceVarName)
                varMap[Variable(relationVar.name())] = replaceVar
                Reflect.on(pattern).set("var", replaceVar)
            }
        } else {
            pattern.statements().forEach { innerStatement ->
                innerStatement.variables().forEach {
                    if (varMap.containsKey(it.asReturnedVar())) {
                        Reflect.on(innerStatement).set("var", varMap[it.asReturnedVar()])
                    }
                }
            }
        }
    }

    private fun isAttribute(it: Any): Boolean {
        return it is AttributeImpl.Local<*> || it is AttributeImpl.Remote<*>
    }

    private fun isRelation(it: Any): Boolean {
        return it is RelationImpl.Local || it is RelationImpl.Remote
    }
}
