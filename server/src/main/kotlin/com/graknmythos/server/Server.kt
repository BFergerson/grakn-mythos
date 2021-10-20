package com.graknmythos.server

import com.google.common.io.Resources
import com.graknmythos.server.manage.MythosInternal
import com.vaticle.typedb.client.connection.core.CoreClient
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlinx.cli.*
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore

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
    val graknPort by parser.option(ArgType.Int, shortName = "gp", description = "Grakn port").default(1729)
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
    val client = CoreClient(graknUri)
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
