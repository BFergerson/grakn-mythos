package com.graknmythos.server

import com.google.common.io.Resources
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.graknmythos.server.manage.MythosInternal
import grakn.client.GraknClient
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.*
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.files
import io.ktor.http.content.static
import io.ktor.request.header
import io.ktor.request.receiveText
import io.ktor.request.uri
import io.ktor.response.header
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.jetty.Jetty
import kotlinx.cli.*
import org.apache.commons.lang3.RandomStringUtils
import org.apache.commons.text.StringEscapeUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Primary entry point for the mythos server.
 *
 * @version 0.2.2
 * @since 0.1.0
 * @author [Brandon Fergerson](mailto:bfergerson@apache.org)
 */
fun main(args: Array<String>) {
    val parser = ArgParser("grakn-mythos")
    val port by parser.option(ArgType.Int, shortName = "p", description = "Listen on port").default(8888)
    val websiteDisabled by parser.option(ArgType.Boolean, shortName = "wd", description = "Website disabled").default(false)

    val graknHost by parser.option(ArgType.String, shortName = "gh", description = "Grakn host").default("localhost")
    val graknPort by parser.option(ArgType.Int, shortName = "gp", description = "Grakn port").default(48555)
    val graknKeyspace by parser.option(ArgType.String, shortName = "gk", description = "Grakn keyspace (otherwise uses temporary keyspaces)")

    val sslEnabled by parser.option(ArgType.Boolean, shortName = "ssl", description = "SSL enabled").default(false)
    val keystoreLocation by parser.option(ArgType.String, shortName = "kl", description = "SSL keystore location")
    val keystorePassword by parser.option(ArgType.String, shortName = "kp", description = "SSL keystore password")
    val keystoreAlias by parser.option(ArgType.String, shortName = "ka", description = "SSL keystore alias")

    parser.parse(args)

    var env = applicationEngineEnvironment {
        module { main("""$graknHost:$graknPort""", !websiteDisabled, graknKeyspace) }
        connector {
            host = "0.0.0.0"
            this.port = port
        }
    }
    if (sslEnabled) {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        FileInputStream(keystoreLocation).use { keyStoreData ->
            keyStore.load(keyStoreData, keystorePassword!!.toCharArray())
        }
        env = applicationEngineEnvironment {
            connector {
                module { install(HttpsRedirect) }
                host = "0.0.0.0"
                this.port = 80
            }
            sslConnector(keyStore = keyStore, keyAlias = keystoreAlias!!,
                    keyStorePassword = { keystorePassword!!.toCharArray() },
                    privateKeyPassword = { keystorePassword!!.toCharArray() }) {
                module { main("""$graknHost:$graknPort""", !websiteDisabled, graknKeyspace) }
                host = "0.0.0.0"
                this.port = 443
                keyStorePath = File(keystoreLocation)
            }
        }
    }
    embeddedServer(Jetty, env).start(wait = true)
}

fun Application.main(graknUri: String, websiteEnabled: Boolean, staticKeyspace: String?) {
    install(DefaultHeaders)
    install(Compression)
    install(CallLogging)
    install(ConditionalHeaders)
    install(AutoHeadResponse)

    val imageStorageLocation = File("../data/legend-images").absoluteFile
    imageStorageLocation.mkdirs()
    val client = GraknClient(graknUri)
    val mythosInternal = MythosInternal(client, imageStorageLocation, staticKeyspace)
    mythosInternal.installSchema(Resources.getResource("grakn_mythos.gql").readText())

    if (websiteEnabled) {
        routing {
            siteRouting(mythosInternal, imageStorageLocation)
            apiRouting(mythosInternal, client)
        }
    } else {
        routing {
            apiRouting(mythosInternal, client)
        }
    }
}
