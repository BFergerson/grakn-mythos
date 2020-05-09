package com.graknmythos.server

import com.google.gson.Gson
import com.graknmythos.server.manage.MythosInternal
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.files
import io.ktor.http.content.static
import io.ktor.request.uri
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.get
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Serves the mythos website.
 *
 * @version 0.2.1
 * @since 0.1.0
 * @author [Brandon Fergerson](mailto:bfergerson@apache.org)
 */
fun Route.siteRouting(mythosInternal: MythosInternal, imageStorageLocation: File) {
    val log = LoggerFactory.getLogger("MythosSite")

    static("favicon.ico") {
        files("../client/src/main/html/assets/favicon.ico")
    }
    static("css") {
        files("../client/src/main/css")
    }
    static("assets") {
        files("../client/src/main/html/assets")
        files(imageStorageLocation)
    }
    static("js") {
        files("../client/dist")
    }
    static("fonts") {
        files("../client/dist")
    }

    get("/") {
        try {
            log.info("Getting legends")
            val legends = mythosInternal.getLegends(8, false)
            log.info("Got legends")

            var text = File("../client/src/main/html/index.html").readText(Charsets.UTF_8)
            text = text.replace("legends = null;", "legends = JSON.parse('" + Gson().toJson(legends) + "');")
            call.respondText(text, ContentType.Text.Html)
        } catch (e: Exception) {
            e.printStackTrace()
            log.error("/", e)
            call.respondText(e.message!!, ContentType.Text.Html, HttpStatusCode.InternalServerError)
        }
    }
    get("/legend/clone") {
        if (call.request.queryParameters["legendId"] == null) {
            call.respondRedirect("/legend/create")
        } else {
            var text = File("../client/src/main/html/create-legend.html").readText(Charsets.UTF_8)
            val cloneLegendId = call.request.queryParameters["legendId"]!!
            val legend = mythosInternal.getLegend(cloneLegendId)
            if (legend == null) {
                call.respondRedirect("/legend/create")
            } else {
                text = text.replace("clonedLegend = null;", "clonedLegend = " + Gson().toJson(legend.copy(image = null)) + ";")
                call.respondText(text, ContentType.Text.Html)
            }
        }
    }
    get("/legend/create") {
        try {
            val text = File("../client/src/main/html/create-legend.html").readText(Charsets.UTF_8)
            call.respondText(text, ContentType.Text.Html)
        } catch (e: Exception) {
            e.printStackTrace()
            log.error("/legend/create", e)
            call.respondText(e.message!!, ContentType.Text.Html, HttpStatusCode.InternalServerError)
        }
    }
    get("/legend/{legendId}/clone") {
        try {
            val legendId = call.parameters["legendId"]!!
            log.info("Getting legend [clone]: $legendId")
            call.respondRedirect("/legend/clone?legendId=$legendId")
            log.info("Redirected legend clone request: $legendId")
        } catch (e: Exception) {
            e.printStackTrace()
            log.error("/legend/{legendId}/clone", e)
            call.respondText(e.message!!, ContentType.Text.Html, HttpStatusCode.InternalServerError)
        }
    }
    get("/legend/{legendId}/full-screen") {
        try {
            log.info("Getting legend [full screen]")
            val text = File("../client/src/main/html/get-legend-full-screen.html").readText(Charsets.UTF_8)
            call.respondText(text, ContentType.Text.Html)
            log.info("Sent legend [full screen]")
        } catch (e: Exception) {
            e.printStackTrace()
            log.error("/legend/{legendId}/full-screen", e)
            call.respondText(e.message!!, ContentType.Text.Html, HttpStatusCode.InternalServerError)
        }
    }
    get("/legend/{legendId}") {
        try {
            val legendId = call.parameters["legendId"]!!
            if (call.request.uri.endsWith("/")) {
                call.respondRedirect("/legend/$legendId")
                return@get
            } else if (call.request.uri.endsWith(".ttf")) {
                //todo: definitely doing this wrong (monaco editor isn't using right location)
                call.respondRedirect("/fonts/$legendId")
                return@get
            }

            log.info("Getting legend: $legendId")
            val text = File("../client/src/main/html/get-legend.html").readText(Charsets.UTF_8)
            call.respondText(text, ContentType.Text.Html)
            log.info("Sent legend: $legendId")
        } catch (e: Exception) {
            e.printStackTrace()
            log.error("/legend/{legendId}", e)
            call.respondText(e.message!!, ContentType.Text.Html, HttpStatusCode.InternalServerError)
        }
    }
}