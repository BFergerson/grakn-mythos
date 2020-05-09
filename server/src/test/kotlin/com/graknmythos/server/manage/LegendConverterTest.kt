package com.graknmythos.server.manage

import com.google.common.io.Resources
import com.graknmythos.server.model.QueryOptions
import com.graknmythos.server.model.export.Edge
import grakn.client.GraknClient
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("UNCHECKED_CAST")
internal class LegendConverterTest {
    companion object {
        private lateinit var client: GraknClient

        @JvmStatic
        @BeforeClass
        fun setup() {
            client = GraknClient("localhost:48555")
        }

        @JvmStatic
        @AfterClass
        fun teardown() {
            client.close()
        }
    }

    @Test
    fun basicAttributesTest() {
        val converter = LegendConverter(true, "mythos_test_" + System.currentTimeMillis(), client)
        val result = converter.convert(Resources.getResource("BasicAttributes.gql").readText(), QueryOptions())

        assertEquals(5, result.nodes.size)
        assertTrue(result.nodes.any { it.name == "99" && it.type == "attribute" && it.category == "age" })
        assertTrue(result.nodes.any { it.name == "2020-02-02T00:00" && it.type == "attribute" && it.category == "birth" })
        assertTrue(result.nodes.any { it.name == "true" && it.type == "attribute" && it.category == "citizen" })
        assertTrue(result.nodes.any { it.name == "Brandon" && it.type == "attribute" && it.category == "name" })
        assertTrue(result.nodes.any { it.name == "\$m" && it.type == "entity" && it.category == "man" })

        assertEquals(4, result.links.size)
        (result.links as List<Edge>).find { it.name == "\$a" }.also {
            assertEquals(result.nodes.indexOfFirst { it.name == "\$m" }, it!!.source)
            assertEquals(result.nodes.indexOfFirst { it.name == "99" }, it.target)
        }
        (result.links as List<Edge>).find { it.name == "\$b" }.also {
            assertEquals(result.nodes.indexOfFirst { it.name == "\$m" }, it!!.source)
            assertEquals(result.nodes.indexOfFirst { it.name == "2020-02-02T00:00" }, it.target)
        }
        (result.links as List<Edge>).find { it.name == "\$c" }.also {
            assertEquals(result.nodes.indexOfFirst { it.name == "\$m" }, it!!.source)
            assertEquals(result.nodes.indexOfFirst { it.name == "true" }, it.target)
        }
        (result.links as List<Edge>).find { it.name == "\$n" }.also {
            assertEquals(result.nodes.indexOfFirst { it.name == "\$m" }, it!!.source)
            assertEquals(result.nodes.indexOfFirst { it.name == "Brandon" }, it.target)
        }
    }

    @Test
    fun hyperRelationTest() {
        val converter = LegendConverter(true, "mythos_test_" + System.currentTimeMillis(), client)
        val result = converter.convert(Resources.getResource("HyperRelation.gql").readText(), QueryOptions())

        assertEquals(7, result.nodes.size)
        assertTrue(result.nodes.any { it.name == "Vanessa" && it.type == "attribute" })
        assertTrue(result.nodes.any { it.name == "\$c" && it.type == "entity" && it.category == "city" })
        assertTrue(result.nodes.any { it.name == "Denver" && it.type == "attribute" && it.category == "name" })
        assertTrue(result.nodes.any { it.name == "John" && it.type == "attribute" && it.category == "name" })
        assertTrue(result.nodes.any { it.name == "\$w" && it.type == "entity" && it.category == "wife" })
        assertTrue(result.nodes.any { it.name == "\$h" && it.type == "entity" && it.category == "husband" })
        assertTrue(result.nodes.any { it.name == "\$m" && it.type == "relation" && it.category == "marriage" })

        assertEquals(6, result.links.size)
        (result.links as List<Edge>).find { it.name == "\$cn" }.also {
            assertEquals(result.nodes.indexOfFirst { it.name == "\$c" }, it!!.source)
            assertEquals(result.nodes.indexOfFirst { it.name == "Denver" }, it.target)
        }
        (result.links as List<Edge>).find { it.name == "\$wn" }.also {
            assertEquals(result.nodes.indexOfFirst { it.name == "\$w" }, it!!.source)
            assertEquals(result.nodes.indexOfFirst { it.name == "Vanessa" }, it.target)
        }
        (result.links as List<Edge>).find { it.name == "\$hn" }.also {
            assertEquals(result.nodes.indexOfFirst { it.name == "\$h" }, it!!.source)
            assertEquals(result.nodes.indexOfFirst { it.name == "John" }, it.target)
        }
        (result.links as List<Edge>).find { it.name == "\$ml" }.also {
            assertEquals(result.nodes.indexOfFirst { it.name == "\$m" }, it!!.source)
            assertEquals(result.nodes.indexOfFirst { it.name == "\$c" }, it.target)
        }
        (result.links as List<Edge>).find { it.name == "\$w" }.also {
            assertEquals(result.nodes.indexOfFirst { it.name == "\$w" }, it!!.source)
            assertEquals(result.nodes.indexOfFirst { it.name == "\$m" }, it.target)
        }
        (result.links as List<Edge>).find { it.name == "\$h" }.also {
            assertEquals(result.nodes.indexOfFirst { it.name == "\$h" }, it!!.source)
            assertEquals(result.nodes.indexOfFirst { it.name == "\$m" }, it.target)
        }
    }
}