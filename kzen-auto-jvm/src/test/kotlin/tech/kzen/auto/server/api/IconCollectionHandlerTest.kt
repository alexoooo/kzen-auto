package tech.kzen.auto.server.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


// Exercises the SER5 kotlinx port of IconCollectionHandler against a small fixture collection on the test
// classpath (src/test/resources/icons/test-symbols.json), covering every branch of the subset walk:
// direct hit, alias-chain resolution, dead-end alias -> texture fallback, unknown name, missing collection.
class IconCollectionHandlerTest {
    private fun query(set: String, vararg names: String) =
        Json.parseToJsonElement(IconCollectionHandler.query(set, names.toList())).jsonObject


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun directHit() {
        val result = query("test-symbols", "home")

        assertEquals("test-symbols", result.getValue("prefix").jsonPrimitive.content)
        assertTrue("home" in result.getValue("icons").jsonObject)
        assertTrue(result.getValue("not_found").jsonArray.isEmpty())
        // width/height copied verbatim when present.
        assertEquals("24", result.getValue("width").jsonPrimitive.content)
        assertEquals("24", result.getValue("height").jsonPrimitive.content)
    }


    @Test
    fun aliasChainResolvesToConcreteParent() {
        val result = query("test-symbols", "house-alt")

        val aliases = result.getValue("aliases").jsonObject
        // Every hop of the chain is carried...
        assertTrue("house-alt" in aliases)
        assertTrue("house" in aliases)
        // ...plus the concrete parent icon it resolves to.
        assertTrue("home" in result.getValue("icons").jsonObject)
        assertTrue(result.getValue("not_found").jsonArray.isEmpty())
    }


    @Test
    fun deadEndAliasFallsBackAndReportsNotFound() {
        // Request the concrete `texture` glyph too, so the result carries it as a reference to compare against
        // (the fallback substitutes texture's BODY under the requested name, never the name "texture" itself).
        val result = query("test-symbols", "texture", "dangling")
        val icons = result.getValue("icons").jsonObject

        // The dangling hop is still recorded, but its parent is not a real icon.
        assertTrue("dangling" in result.getValue("aliases").jsonObject)
        // Fallback glyph substituted under the requested name...
        assertEquals(icons.getValue("texture"), icons.getValue("dangling"))
        // ...and only the dead-end name is reported as not found (texture is a direct hit).
        val notFound = result.getValue("not_found").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("dangling"), notFound)
    }


    @Test
    fun unknownNameFallsBackAndReportsNotFound() {
        val result = query("test-symbols", "texture", "no-such-icon")
        val icons = result.getValue("icons").jsonObject

        assertEquals(icons.getValue("texture"), icons.getValue("no-such-icon"))
        val notFound = result.getValue("not_found").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("no-such-icon"), notFound)
    }


    @Test
    fun missingCollectionEmitsEmptyIconsAndAllNotFound() {
        val result = query("no-such-set", "home", "star")

        assertEquals("no-such-set", result.getValue("prefix").jsonPrimitive.content)
        assertTrue(result.getValue("icons").jsonObject.isEmpty())
        val notFound = result.getValue("not_found").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("home", "star"), notFound)
        // The empty-collection branch omits aliases/width/height entirely.
        assertFalse("aliases" in result)
        assertFalse("width" in result)
    }
}
