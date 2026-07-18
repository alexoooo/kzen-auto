package tech.kzen.auto.server.api

import kotlinx.serialization.json.*


/**
 * Serves Iconify icon data on demand, implementing the subset of the Iconify API protocol the client
 * uses: GET /icon/{set}.json?icons=a,b,c → IconifyJSON containing only the requested icons.
 *
 * The full collection JSON (material-symbols, ~16k icons) ships as a JVM resource — see kzen-auto-jvm's
 * copyIconCollection Gradle task — so the JS bundle carries no icon data; the client fetches only the
 * names it actually renders and caches them. Hosting additional sets later costs server disk, not bundle.
 */
object IconCollectionHandler {
    private const val resourcePathPrefix = "/icons/"

    // An unknown name renders the Texture glyph (matching the previous @mui registry's fallback) rather
    // than a blank box, while still being reported under not_found for diagnostics.
    private const val fallbackIconName = "texture"

    // SER5: kotlinx JsonObject tree (was a Jackson tools.jackson JsonNode) — kzen-auto-jvm is Jackson-free.
    // Each set's whole collection is parsed once, lazily, and the tree is cached for the process lifetime.
    private val collections: MutableMap<String, JsonObject?> = HashMap()

    private fun collection(set: String): JsonObject? =
        collections.getOrPut(set) {
            IconCollectionHandler::class.java
                .getResourceAsStream("$resourcePathPrefix$set.json")
                ?.use { Json.parseToJsonElement(it.readBytes().decodeToString()).jsonObject }
        }

    fun query(set: String, names: List<String>): String {
        val collection = collection(set)
            ?: return buildJsonObject {
                put("prefix", set)
                put("icons", JsonObject(emptyMap()))
                put("not_found", JsonArray(names.map { JsonPrimitive(it) }))
            }.toString()

        val icons = collection.getValue("icons").jsonObject
        val aliases = collection["aliases"]?.jsonObject

        // kotlinx JsonObject/JsonArray are immutable, so accumulate into mutable containers across the loop
        // and freeze them into the response at the end (the Jackson version mutated ObjectNode/ArrayNode in place).
        val resultIcons = mutableMapOf<String, JsonElement>()
        val resultAliases = mutableMapOf<String, JsonElement>()
        val notFound = mutableListOf<JsonElement>()

        for (name in names) {
            when {
                name in icons ->
                    resultIcons[name] = icons.getValue(name)

                aliases != null && name in aliases -> {
                    // Carry every alias hop plus the concrete parent icon so the client resolves it.
                    var cursor = name
                    while (cursor in aliases) {
                        val aliasNode = aliases.getValue(cursor).jsonObject
                        resultAliases[cursor] = aliasNode
                        cursor = aliasNode["parent"]?.jsonPrimitive?.content ?: break
                    }
                    if (cursor in icons) {
                        resultIcons[cursor] = icons.getValue(cursor)
                    }
                    else {
                        addFallback(resultIcons, name, icons)
                        notFound.add(JsonPrimitive(name))
                    }
                }

                else -> {
                    addFallback(resultIcons, name, icons)
                    notFound.add(JsonPrimitive(name))
                }
            }
        }

        val response = buildJsonObject {
            put("prefix", collection.getValue("prefix").jsonPrimitive.content)
            put("icons", JsonObject(resultIcons))
            put("aliases", JsonObject(resultAliases))
            collection["width"]?.let { put("width", it) }
            collection["height"]?.let { put("height", it) }
            put("not_found", JsonArray(notFound))
        }

        return response.toString()
    }

    private fun addFallback(resultIcons: MutableMap<String, JsonElement>, name: String, icons: JsonObject) {
        icons[fallbackIconName]?.let { resultIcons[name] = it }
    }
}
