package tech.kzen.auto.client.objects.document.custom


enum class CustomDocumentViewMode {
    View,
    Raw
}


data class CustomDocumentState(
    val viewMode: CustomDocumentViewMode,
    val editorModified: Boolean
)


object CustomDocumentGlobal {
    interface Observer {
        fun onCustomDocumentState(state: CustomDocumentState)
    }


    private val observers = mutableSetOf<Observer>()
    private var state = CustomDocumentState(CustomDocumentViewMode.View, false)


    fun observe(observer: Observer) {
        observers.add(observer)
        observer.onCustomDocumentState(state)
    }


    fun unobserve(observer: Observer) {
        observers.remove(observer)
    }


    fun current(): CustomDocumentState {
        return state
    }


    fun setViewMode(viewMode: CustomDocumentViewMode) {
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
            observer.onCustomDocumentState(state)
        }
    }
}
