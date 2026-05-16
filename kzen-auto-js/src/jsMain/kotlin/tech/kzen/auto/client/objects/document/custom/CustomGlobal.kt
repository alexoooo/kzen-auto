package tech.kzen.auto.client.objects.document.custom


enum class CustomViewMode {
    View,
    Raw
}


data class CustomState(
    val viewMode: CustomViewMode,
    val editorModified: Boolean
)


object CustomGlobal {
    interface Observer {
        fun onCustomState(state: CustomState)
    }


    private val observers = mutableSetOf<Observer>()
    private var state = CustomState(CustomViewMode.View, false)


    fun observe(observer: Observer) {
        observers.add(observer)
        observer.onCustomState(state)
    }


    fun unobserve(observer: Observer) {
        observers.remove(observer)
    }


    fun current(): CustomState {
        return state
    }


    fun setViewMode(viewMode: CustomViewMode) {
        if (state.viewMode == viewMode) {
            return
        }
        state = state.copy(viewMode = viewMode)
        publish()
    }


    fun setEditorModified(editorModified: Boolean) {
        if (state.editorModified == editorModified) {
            return
        }
        state = state.copy(editorModified = editorModified)
        publish()
    }


    private fun publish() {
        for (observer in observers.toList()) {
            observer.onCustomState(state)
        }
    }
}
