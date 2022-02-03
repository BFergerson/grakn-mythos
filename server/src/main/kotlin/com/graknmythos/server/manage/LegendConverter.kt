package com.graknmythos.server.manage

import com.graknmythos.server.model.*
import com.graknmythos.server.model.export.Edge
import com.graknmythos.server.model.export.GraknEdge
import com.graknmythos.server.model.export.Graph
import com.graknmythos.server.model.export.Node
import com.vaticle.typedb.client.api.answer.ConceptMap
import com.vaticle.typedb.client.api.concept.Concept
import com.vaticle.typedb.client.api.connection.TypeDBClient
import com.vaticle.typedb.client.api.connection.TypeDBOptions
import com.vaticle.typedb.client.api.connection.TypeDBSession
import com.vaticle.typedb.client.api.connection.TypeDBTransaction
import com.vaticle.typedb.client.concept.thing.AttributeImpl
import com.vaticle.typedb.client.concept.thing.EntityImpl
import com.vaticle.typedb.client.concept.thing.RelationImpl
import com.vaticle.typeql.lang.TypeQL.*
import com.vaticle.typeql.lang.pattern.Disjunction
import com.vaticle.typeql.lang.pattern.Pattern
import com.vaticle.typeql.lang.pattern.constraint.ThingConstraint
import com.vaticle.typeql.lang.pattern.variable.ThingVariable
import com.vaticle.typeql.lang.pattern.variable.UnboundVariable
import com.vaticle.typeql.lang.pattern.variable.Variable
import com.vaticle.typeql.lang.query.TypeQLDefine
import com.vaticle.typeql.lang.query.TypeQLInsert
import com.vaticle.typeql.lang.query.TypeQLMatch
import com.vaticle.typeql.lang.query.TypeQLQuery
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.joor.Reflect
import org.slf4j.LoggerFactory
import java.util.*
import java.util.stream.Stream
import kotlin.streams.toList

/**
 * Converts Grakn query to [Graph].
 * Todo: sane regex
 * Todo: not rely on reflection
 * Todo: This code is bad. Will clean soon.
 *
 * @version 0.2.2
 * @since 0.1.0
 * @author [Brandon Fergerson](mailto:bfergerson@apache.org)
 */
class LegendConverter(
    private val usingTemporaryKeyspaces: Boolean, private val keyspace: String,
    private val client: TypeDBClient
) {
    companion object {
        val cache = HashMap<String, Graph>() //todo: optional redis

        @JvmStatic
        private val log = LoggerFactory.getLogger(javaClass.enclosingClass)
    }

    private val queryComment = Regex("[#].*\$", RegexOption.MULTILINE)
    private val unnecessaryWhitespace = Regex("\\s\\s+")
    private val mythosRelationVariable = Regex("mythos_internal_(.+)_relation_(.+)")
    private val mythosAttributeVariable = Regex("mythos_internal_(.+)_attribute_(.+)")

    fun convert(legend: Legend): Graph {
        return convert(legend.query!!, legend.queryOptions)
    }

    fun convert(query: String, options: QueryOptions): Graph {
        log.info("Keyspace: $keyspace - Query options: " + options.toString().replace("\n", " "))
        val uniqueQueryText = toUniqueQueryString(query)
        if (cache.containsKey(uniqueQueryText + "options-" + options.toString())) {
            return cache[uniqueQueryText + "options-" + options.toString()]!!
        }
        if (!client.databases().contains(keyspace)) {
            client.databases().create(keyspace)
        }
        val session = client.session(keyspace, TypeDBSession.Type.SCHEMA)
        var tx = session.transaction(TypeDBTransaction.Type.WRITE)

        log.info("Executing query: $uniqueQueryText")
        try {
            val resultStreams: MutableList<Stream<ConceptMap>> = ArrayList()
            parseQueries<TypeQLQuery>(uniqueQueryText).forEach { typeqlQuery ->
                when (typeqlQuery) {
                    is TypeQLDefine -> {
                        log.info("Executing TypeQL define")
                        tx.query().define(typeqlQuery)
                        tx.commit() //todo: only commit if contains rule
                        tx.close()
                        session.close()
                        tx = client.session(keyspace, TypeDBSession.Type.DATA).transaction(TypeDBTransaction.Type.WRITE) //todo: check for multiple define?
                    }
                    is TypeQLMatch -> {
                        tx.commit() //todo: only commit if contains rule
                        tx.close()
                        session.close()
                        tx = client.session(keyspace, TypeDBSession.Type.DATA, TypeDBOptions.core().infer(true)).transaction(TypeDBTransaction.Type.READ) //todo: check for multiple define?

                        val varMap = HashMap<Variable, Variable>()
                        typeqlQuery.asMatch().conjunction().patterns().forEach { statement ->
                            replacePatternVars(options, varMap, statement)
                        }
//                        val getVars = HashSet(typeqlQuery.variables())
//                        getVars.removeAll(typeqlQuery.asMatch().variables().toSet())
//                        getVars.forEach {
//                            println(it)
////                            if (varMap.containsKey(it.asReturnedVar())) {
////                                typeqlQuery.vars().add(varMap[it.asReturnedVar()])
////                                typeqlQuery.vars().remove(it)
////                            }
//                        }

                        log.info("Executing TypeQL query")
                        log.debug("Converted TypeQL query: " + toUniqueQueryString(typeqlQuery.toString()))
                        resultStreams.add(tx.query().match(typeqlQuery))
                    }
                    is TypeQLMatch.Group -> {
                        log.info("Executing TypeQL group")
                        tx.query().match(typeqlQuery)
                    }
                    is TypeQLMatch.Group.Aggregate -> {
                        log.info("Executing TypeQL group aggregate")
                        tx.query().match(typeqlQuery)
                    }
                    is TypeQLMatch.Aggregate -> {
                        log.info("Executing TypeQL aggregate")
                        tx.query().match(typeqlQuery)
                    }
                    is TypeQLInsert -> {
                        log.info("Executing TypeQL insert")
                        tx.query().insert(typeqlQuery)
                    }
                    else -> throw UnsupportedOperationException(query)
                }
            }

            val nodes = ArrayList<Node>()
            val edges = HashSet<Any>()
            log.info("Parsing ${resultStreams.size} query result(s)")
            resultStreams.forEach {
                val idToIndexMap = HashMap<String, Int>()
                it.forEach {
                    val varToIdMap = HashMap<String, String>()
                    val hyperRelationVarSet = HashSet<String>()
                    val regularRelationTypeMap = HashMap<String, String>()

                    //add entities, attributes, and hyper-relations
                    it.map().forEach {
                        firstOne(
                            it,
                            options,
                            idToIndexMap,
                            nodes,
                            varToIdMap,
                            hyperRelationVarSet,
                            regularRelationTypeMap
                        )
                    }

                    //link entities, attributes, and hyper-relations
                    it.map().forEach {
                        secondOne(
                            it,
                            varToIdMap,
                            options,
                            edges,
                            idToIndexMap,
                            regularRelationTypeMap,
                            nodes,
                            hyperRelationVarSet
                        )
                    }
                }
            }
            log.info("Finished parsing query result(s)")

            cache[uniqueQueryText + "options-" + options.toString()] = Graph(nodes, edges.toList())
            return cache[uniqueQueryText + "options-" + options.toString()]!!
        } finally {
            try {
                //tx.commit()
                tx.close()
            } finally {
                if (usingTemporaryKeyspaces) {
                    GlobalScope.launch {
                        deleteTemporaryKeyspace(session)
                    }
                } else {
                    session.close()
                }
            }
        }
    }

    private fun secondOne(
        it: Map.Entry<String, Concept>,
        varToIdMap: HashMap<String, String>,
        options: QueryOptions,
        edges: HashSet<Any>,
        idToIndexMap: HashMap<String, Int>,
        regularRelationTypeMap: HashMap<String, String>,
        nodes: ArrayList<Node>,
        hyperRelationVarSet: HashSet<String>
    ) {
        if (isAttribute(it.value) && it.key.toString().contains("mythos_internal")) {
            val attributeId = it.value.asAttribute().iid
            val matches = mythosAttributeVariable.find(it.key)!!
            val attributeEntityVar = matches.groupValues[1]
            if (varToIdMap.containsKey(attributeEntityVar)) {
                val name = when (options.displayOptions.relationNamingScheme) {
                    RelationNamingScheme.BY_VARIABLE -> {
                        "$" + matches.groupValues.last()
                    }
                    RelationNamingScheme.BY_TYPE -> {
                        it.value.asAttribute().type.label.name()
                    }
                    RelationNamingScheme.BY_ID -> {
                        attributeId
                    }
                }

                if (options.displayOptions.linkNodesById) {
                    edges.add(GraknEdge(varToIdMap[attributeEntityVar]!!, attributeId, name, "attribute"))
                } else {
                    edges.add(
                        Edge(
                            idToIndexMap[varToIdMap[attributeEntityVar]]!!,
                            idToIndexMap[attributeId]!!,
                            name,
                            "attribute"
                        )
                    )
                }
            }
        } else if (isRelation(it.value) && it.key.toString().contains("mythos_internal")) {
            val relationId = it.value.asRelation().iid
            val matches = mythosRelationVariable.find(it.key)!!
            val name = when (options.displayOptions.relationNamingScheme) {
                RelationNamingScheme.BY_VARIABLE -> {
                    "$" + matches.groupValues.last()
                }
                RelationNamingScheme.BY_TYPE -> {
                    it.value.asRelation().type.label.name()
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
                    val hyperRelationId = varToIdMap[hyperRelationVar]!!
                    val hyperRelationNodeName = when (options.displayOptions.relationNamingScheme) {
                        RelationNamingScheme.BY_VARIABLE -> {
                            "$$userHyperRelationName"
                        }
                        RelationNamingScheme.BY_TYPE -> {
                            hyperRelationType
                        }
                        RelationNamingScheme.BY_ID -> {
                            hyperRelationId
                        }
                    }
                    val node = Node(hyperRelationNodeName, hyperRelationId, "relation", hyperRelationType)
                    if (!idToIndexMap.containsKey(node.id)) {
                        idToIndexMap[node.id] = nodes.size
                        nodes.add(node)
                    }
                    varToIdMap[it.key] = node.id

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
                                "$$source"
                            }
                            RelationNamingScheme.BY_TYPE -> {
                                hyperRelationType
                            }
                            RelationNamingScheme.BY_ID -> {
                                varToIdMap[source]!!
                            }
                        }

                        if (options.displayOptions.linkNodesById) {
                            edges.add(
                                GraknEdge(
                                    varToIdMap[source]!!,
                                    hyperRelationId,
                                    hyperRelationPlayerName,
                                    "relation"
                                )
                            )
                        } else {
                            edges.add(
                                Edge(
                                    idToIndexMap[varToIdMap[source]]!!,
                                    idToIndexMap[hyperRelationId]!!,
                                    hyperRelationPlayerName,
                                    "relation"
                                )
                            )
                        }
                    }
                    text = text.replace("""mythos${tempHyperRelationName}_relation_${userHyperRelationName}_""", "")
                }
            }
            if (hyperRelation || !hyperRelationVarSet.contains(it.key)) {
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
                                edges.add(GraknEdge(varToIdMap[source]!!, varToIdMap[target]!!, name, "relation"))
                            } else {
                                edges.add(
                                    Edge(
                                        idToIndexMap[varToIdMap[source]]!!,
                                        idToIndexMap[varToIdMap[target]]!!,
                                        name,
                                        "relation"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun firstOne(
        it: Map.Entry<String, Concept>,
        options: QueryOptions,
        idToIndexMap: HashMap<String, Int>,
        nodes: ArrayList<Node>,
        varToIdMap: HashMap<String, String>,
        hyperRelationVarSet: HashSet<String>,
        regularRelationTypeMap: HashMap<String, String>
    ) {
        if (isEntity(it.value)) {
            val id = it.value.asEntity().iid
            val entityType = it.value.asEntity().type.label.toString()
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
            if (!idToIndexMap.containsKey(node.id)) {
                idToIndexMap[node.id] = nodes.size
                nodes.add(node)
            }
            varToIdMap[it.key] = node.id
        } else if (isAttribute(it.value)) {
            val id = it.value.asAttribute().iid
            val value = it.value.asAttribute().value.toString()
            val name = when (options.displayOptions.attributeNamingScheme) {
                AttributeNamingScheme.BY_VALUE -> {
                    value
                }
                AttributeNamingScheme.BY_ID -> {
                    id
                }
            }

            val node = if (options.displayOptions.attributeNamingScheme == AttributeNamingScheme.BY_ID) {
                Node(name, id, "attribute", it.value.asAttribute().type.label.toString(), value)
            } else {
                Node(name, id, "attribute", it.value.asAttribute().type.label.toString())
            }
            if (!idToIndexMap.containsKey(node.id)) {
                idToIndexMap[node.id] = nodes.size
                nodes.add(node)
            }
            varToIdMap[it.key] = node.id
        } else if (isRelation(it.value)) {
            val matches = mythosRelationVariable.find(it.key)!!
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
                varToIdMap[it.key] = it.value.asRelation().iid
                regularRelationTypeMap[it.key] = it.value.asRelation().type.label.name()
            }
        }
    }

    private fun deleteTemporaryKeyspace(session: TypeDBSession) {
        session.use {
            log.info("Deleting keyspace: " + it.database().name())
            it.database().delete()
            log.info("Active keyspaces: " + client.databases().all().size)
        }
    }

    private fun replacePatternVars(options: QueryOptions, varMap: HashMap<Variable, Variable>, pattern: Pattern) {
        if (pattern is ThingVariable.Thing) {
            val entityVar = pattern.reference().name()
            for (it in pattern.constraints().toSet()) {
                if (it is ThingConstraint.Has) {
                    val attribute = it.attribute()
                    if (attribute.variables().toList().size > 1) {
                        replacePatternVars(options, varMap, attribute)
                    } else {
                        val attributeVar = attribute.reference()
                        if (attributeVar.isName || options.includeAnonymousVariables) {
                            var attrVarName = attributeVar.name()
                            val copyHas = if (attributeVar.isName) {
                                `var`().has(it.type().get().toString(), UnboundVariable.named(attribute.name())).constraints().first()
                            } else {
                                attrVarName = UUID.randomUUID().toString()
                                `var`().has(it.type().get().toString(), UnboundVariable.anonymous()).constraints().first()
                            }
                            val replaceVarName = "mythos_internal_" + entityVar + "_attribute_" + attrVarName
                            val replaceVar = UnboundVariable.named(replaceVarName)
                            varMap[UnboundVariable.named(attrVarName)] = replaceVar
                            Reflect.on(attribute).set("reference", replaceVar.reference())
                            if (!attributeVar.isName) pattern.constraints().add(copyHas)
                        }
                    }
                }
            }
        } else if (pattern is Disjunction<*>) {
            pattern.patterns().forEach {
                replacePatternVars(options, varMap, it)
            }
        } else if (pattern is ThingVariable.Relation) {
            var replaceVarName = ""
            val relationVar = pattern.reference()
            if (relationVar.isName) {
                val players = ArrayList<Variable>()
                for (it in pattern.constraints()) {
                    if (it is ThingConstraint.Relation) {
                        players.addAll(it.players().map {
                            if (varMap.containsKey(UnboundVariable.named(it.player().reference().name()))) {
                                val hyperRelation = varMap[UnboundVariable.named(it.player().reference().name())]!!
                                Reflect.on(it.player()).set("reference", hyperRelation.reference())
                                replaceVarName += "mythos" + hyperRelation.name() + "_"
                                hyperRelation
                            } else {
                                replaceVarName += "mythos" + it.player().reference().name() + "_"
                                it.player()
                            }
                        })
                    }
                }

                val fullReplaceVarName = "mythos_internal_" + replaceVarName + "relation_" + relationVar.name()
                val replaceVar = UnboundVariable.named(fullReplaceVarName)
                varMap[UnboundVariable.named(relationVar.name())] = replaceVar
                Reflect.on(pattern).set("reference", replaceVar.reference())
            }
        } else {
            for (it in pattern.patterns().flatMap { it.patterns() }) {
                val unboundVar = UnboundVariable.named(it.asVariable().reference().name())
                if (it.isVariable && varMap.containsKey(unboundVar)) {
                    Reflect.on(it).set("reference", varMap[unboundVar]!!.reference())
                }
            }
        }
    }

    private fun isEntity(it: Any): Boolean {
        return it is EntityImpl
    }

    private fun isAttribute(it: Any): Boolean {
        return it is AttributeImpl<*>
    }

    private fun isRelation(it: Any): Boolean {
        return it is RelationImpl
    }

    private fun queryToOneLine(query: String): String {
        return query.replace(queryComment, "").replace(unnecessaryWhitespace, " ")
            .replace("\n", " ")
    }

    fun toUniqueQueryString(query: String): String {
        val sb = StringBuilder()
        parseQueries<TypeQLQuery>(query).forEach {
            sb.append(queryToOneLine(it.toString())).append(" ")
        }
        return sb.toString()
    }
}
