package com.graknmythos.server

import com.google.gson.Gson
import com.graknmythos.server.manage.LegendConverter
import com.graknmythos.server.manage.MythosInternal
import com.graknmythos.server.model.api.ExecuteLegendRequest
import com.graknmythos.server.model.api.SaveLegendRequest
import com.graknmythos.server.model.api.SaveLegendResponse
import grakn.client.GraknClient
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.request.header
import io.ktor.request.receiveText
import io.ktor.response.header
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import org.apache.commons.lang3.RandomStringUtils
import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Serves the mythos API.
 *
 * @version 0.2.1
 * @since 0.1.0
 * @author [Brandon Fergerson](mailto:bfergerson@apache.org)
 */
fun Route.apiRouting(mythosInternal: MythosInternal, client: GraknClient) {
    val log = LoggerFactory.getLogger("MythosAPI")
    val legendConverter = LegendConverter(mythosInternal.useTempLegendKeyspaces, mythosInternal.legendKeyspace, client)

    get("/api/legend/{legendId}") {
        val date: ZonedDateTime = ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneId.of("Z"))
        call.response.header(HttpHeaders.LastModified, date)
        if (call.request.header("If-Modified-Since") != null) {
            call.response.status(HttpStatusCode.NotModified)
            return@get //todo: should be a way to automatically respond like this
        }

        try {
            val legendId = call.parameters["legendId"]!!
            log.info("Getting legend info: $legendId")

            var includeImage = false
            if (call.request.queryParameters.contains("include_image")) {
                includeImage = call.request.queryParameters["include_image"]!!.toBoolean()
            }
            val legend = mythosInternal.getLegend(legendId, includeImage)
            if (legend == null) {
                call.response.status(HttpStatusCode.NotFound)
                log.info("Failed to find legend: $legendId")
            } else {
                call.respondText(Gson().toJson(legend), ContentType.Application.Json)
                log.info("Sent legend info: $legendId")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            log.error("/legend/{legendId}/graph", e)
            call.respondText(e.message!!, ContentType.Text.Html, HttpStatusCode.InternalServerError)
        }
    }
    get("/api/legend/{legendId}/graph") {
        val date: ZonedDateTime = ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneId.of("Z"))
        call.response.header(HttpHeaders.LastModified, date)
        if (call.request.header("If-Modified-Since") != null) {
            call.response.status(HttpStatusCode.NotModified)
            return@get //todo: should be a way to automatically respond like this
        }

        try {
            val legendId = call.parameters["legendId"]!!
            log.info("Getting legend graph: $legendId")

            val legend = mythosInternal.getLegend(legendId)
            if (legend == null) {
                call.response.status(HttpStatusCode.NotFound)
                log.info("Failed to find legend graph: $legendId")
            } else {
                val result = legendConverter.convert(legend)
                call.respondText(Gson().toJson(result), ContentType.Application.Json)
                log.info("Sent legend graph: $legendId")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            log.error("/legend/{legendId}/graph", e)
            call.respondText(e.message!!, ContentType.Text.Html, HttpStatusCode.InternalServerError)
        }
    }
    post("/api/legend/execute") {
        try {
            log.info("Executing legend")
            val text: String = call.receiveText()
            val request = Gson().fromJson(text, ExecuteLegendRequest::class.java)
            val result = legendConverter.convert(request.query, request.queryOptions)
            call.respondText(Gson().toJson(result), ContentType.Application.Json)
            log.info("Executed legend")
        } catch (e: Exception) {
            e.printStackTrace()
            log.error("/legend/{legendId}", e)
            call.respondText(e.message!!, ContentType.Text.Html, HttpStatusCode.InternalServerError)
        }
    }
    post("/api/legend") {
        try {
            val legendId = RandomStringUtils.randomAlphanumeric(10)
            log.info("Saving legend: $legendId")

            val text: String = call.receiveText()
            val request = Gson().fromJson(text, SaveLegendRequest::class.java)
            legendConverter.convert(request.query, request.queryOptions) //execute again to ensure validity before saving
            mythosInternal.saveLegend(legendId, request.description, request.query, request.image, request.queryOptions)
            call.respondText(Gson().toJson(SaveLegendResponse(legendId)), ContentType.Application.Json)
            log.info("Saved legend: $legendId")
        } catch (e: Exception) {
            e.printStackTrace()
            log.error("/api/legend", e)
            call.respondText(e.message!!, ContentType.Text.Html, HttpStatusCode.InternalServerError)
        }
    }
}