package tech.kzen.auto.server.api

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode


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

    private val mapper = JsonMapper.builder().build()

    // Only material-symbols is hosted today; keyed by set name so adding a collection is a one-line change.
    private val collections: MutableMap<String, JsonNode?> = HashMap()

    private fun collection(set: String): JsonNode? =
        collections.getOrPut(set) {
            IconCollectionHandler::class.java
                .getResourceAsStream("$resourcePathPrefix$set.json")
                ?.use { mapper.readTree(it) }
        }

    fun query(set: String, names: List<String>): String {
        val collection = collection(set)
            ?: return mapper.writeValueAsString(
                mapper.createObjectNode().apply {
                    put("prefix", set)
                    set("icons", mapper.createObjectNode())
                    set("not_found", mapper.createArrayNode().apply { names.forEach { add(it) } })
                })

        val icons = collection.get("icons") as ObjectNode
        val aliases = collection.get("aliases") as? ObjectNode

        val resultIcons = mapper.createObjectNode()
        val resultAliases = mapper.createObjectNode()
        val notFound = mapper.createArrayNode()

        for (name in names) {
            when {
                icons.has(name) ->
                    resultIcons.set(name, icons.get(name))

                aliases != null && aliases.has(name) -> {
                    // Carry every alias hop plus the concrete parent icon so the client resolves it.
                    var cursor = name
                    while (aliases.has(cursor)) {
                        resultAliases.set(cursor, aliases.get(cursor))
                        cursor = aliases.get(cursor).get("parent")?.asString() ?: break
                    }
                    if (icons.has(cursor)) {
                        resultIcons.set(cursor, icons.get(cursor))
                    }
                    else {
                        addFallback(resultIcons, name, icons)
                        notFound.add(name)
                    }
                }

                else -> {
                    addFallback(resultIcons, name, icons)
                    notFound.add(name)
                }
            }
        }

        val response = mapper.createObjectNode()
        response.put("prefix", collection.get("prefix").asString())
        response.set("icons", resultIcons)
        response.set("aliases", resultAliases)
        collection.get("width")?.let { response.set("width", it) }
        collection.get("height")?.let { response.set("height", it) }
        response.set("not_found", notFound)

        return mapper.writeValueAsString(response)
    }

    private fun addFallback(resultIcons: ObjectNode, name: String, icons: ObjectNode) {
        if (icons.has(fallbackIconName)) {
            resultIcons.set(name, icons.get(fallbackIconName))
        }
    }
}
