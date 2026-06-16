package tech.kzen.auto.client.service.storage

import kotlinx.browser.window


//---------------------------------------------------------------------------------------------------------------------
// First (and currently only) client-side persisted UI state: the sidebar's width and collapsed flag. A thin
// wrapper over window.localStorage so the keys live in one place and absent / malformed values fall back to the
// caller's default.
object SidebarPreferences {
    @Suppress("ConstPropertyName")
    private const val widthKey = "kzen-auto-sidebar-width"

    @Suppress("ConstPropertyName")
    private const val collapsedKey = "kzen-auto-sidebar-collapsed"


    //-----------------------------------------------------------------------------------------------------------------
    fun loadWidth(default: Double): Double {
        val raw = window.localStorage.getItem(widthKey)
            ?: return default
        return raw.toDoubleOrNull() ?: default
    }


    fun saveWidth(width: Double) {
        window.localStorage.setItem(widthKey, width.toString())
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun loadCollapsed(default: Boolean): Boolean {
        val raw = window.localStorage.getItem(collapsedKey)
            ?: return default
        return raw.toBooleanStrictOrNull() ?: default
    }


    fun saveCollapsed(collapsed: Boolean) {
        window.localStorage.setItem(collapsedKey, collapsed.toString())
    }
}
