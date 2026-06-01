package tech.kzen.auto.client.wrap.iconify

import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.util.httpGet
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.platform.encodeURIComponent


/**
 * Wires Iconify's `<Icon>` to fetch icon data on demand from the JVM backend's icon endpoint, reusing
 * [ClientContext.baseUrl] so requests ride the same kzen-shell reverse-proxy prefix as every other API
 * call (no absolute URLs, no Iconify public-API fallback).
 */
object IconLoader {
    // Always-visible chrome icons fetched up front so the first paint has them (avoids async pop-in).
    private val preloadNames = listOf(
        "folder-open", "more-vert", "more-horiz", "save", "delete", "edit",
        "add-circle-outline", "play-arrow", "pause", "stop", "replay", "refresh",
        "settings", "expand-more", "expand-less", "close", "cancel", "check", "drag-indicator")

    // Registers the on-demand batch loader for the material-symbols prefix. Must run before the first
    // <Icon> renders — called synchronously from ClientContext.init().
    fun install() {
        setCustomIconsLoader(
            { names, _, _ -> async { fetch(names.toList()) } },
            IconNames.defaultSet)
    }

    // Best-effort startup preload; on failure the icons simply fetch lazily on first render.
    suspend fun preload() {
        try {
            addCollection(fetch(preloadNames))
        }
        catch (e: Throwable) {
            console.warn("Icon preload failed", e)
        }
    }

    private suspend fun fetch(names: List<String>): dynamic {
        // Sort the names so the same set of icons always yields the same URL regardless of the order Iconify
        // batches them in. The browser cache keys on the full URL (path + query), so canonicalizing it lets
        // two screens that need the same glyphs share one cached response instead of fetching twice.
        val url = ClientContext.baseUrl +
                CommonRestApi.iconCollectionPrefix + IconNames.defaultSet + ".json" +
                "?" + CommonRestApi.paramIcons + "=" + encodeURIComponent(names.sorted().joinToString(","))
        return JSON.parse(httpGet(url))
    }
}
