package tech.kzen.auto.client.service.global

import kotlinx.browser.window
import tech.kzen.auto.client.service.logic.ClientLogicGlobal
import tech.kzen.auto.client.service.logic.ClientLogicState
import tech.kzen.auto.client.util.NavigationRoute
import tech.kzen.auto.client.util.async
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.service.store.LocalGraphStore


class ClientStateGlobal:
    NavigationGlobal.Observer,
    ClientLogicGlobal.Observer,
    LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val runningKey = "running"
    }

    interface Observer {
        fun onClientState(clientState: ClientState)

        /**
         * The object whose notation [onClientState] reads, or null to see every publish.
         *
         * Deliberately abstract: an observer holding an `objectLocation` MUST NOT receive a publish whose
         * graph no longer contains it. A publish runs before React re-renders, so the callback can fire while
         * the location points at a step that was just deleted or renamed — the parent hasn't handed it the
         * new location yet, and mount order (children first) means the parent can't unsubscribe it in time
         * either. Every notation lookup at such a location then throws
         * `IllegalArgumentException("Missing: ...")` from `GraphNotation.inheritanceChain`.
         *
         * Declaring the scope makes [deliver] skip those broadcasts, so the callback body never has to guard.
         * Having no default is the point — it forces each new observer to answer the question rather than
         * inherit a silent `null`. Document-scoped observers (controllers, stores) say so by implementing
         * [DocumentScopedObserver]; object-scoped React components get it from `ObjectScopedComponent`.
         *
         * Covers [onClientState] only. Commit paths that read notation on a timer (AttributeCommitter's
         * debounce, flushed from componentWillUnmount) sit outside this contract and still guard by hand.
         */
        fun observedObjectLocation(): ObjectLocation?
    }


    /** An observer of the document/session as a whole, not of one object — it sees every publish. */
    interface DocumentScopedObserver: Observer {
        override fun observedObjectLocation(): ObjectLocation? = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val observers = mutableSetOf<Observer>()
    private var sessionState: ClientState? = null

    private var graphDefinitionAttempt: GraphDefinitionAttempt? = null
    private var navigationRoute: NavigationRoute? = null

    private var clientLogicState: ClientLogicState? = null


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun postConstruct(
        navigationGlobal: NavigationGlobal,
        localGraphStore: LocalGraphStore,
        clientLogicGlobal: ClientLogicGlobal
    ) {
        localGraphStore.observe(this)
        navigationGlobal.observe(this)
        clientLogicGlobal.observe(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun handleNavigation(documentPath: DocumentPath?, parameters: RequestParams) {
        navigationRoute = NavigationRoute(documentPath, parameters)

        val selected = parameters.get(runningKey)?.let { DocumentPath.parse(it) }
        if (selected != null) {
            async {
                publishIfReady()
            }
        }
        else {
            publishIfReady()
        }
    }


    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {

    }


    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
        graphDefinitionAttempt = graphDefinition
        publishIfReady()
    }


    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {
        this@ClientStateGlobal.graphDefinitionAttempt = graphDefinitionAttempt
        publishIfReady()
    }


    override fun onLogic(clientLogicState: ClientLogicState) {
        this.clientLogicState = clientLogicState
        publishIfReady()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun observe(observer: Observer) {
        observers.add(observer)

        val initialState = sessionState
            ?: return

        // NB: the replay-on-subscribe delivery needs the same scope filter as the broadcast — a component can
        // mount against an already-stale location (its parent re-render is what will replace it).
        deliver(observer, initialState)
    }


    fun unobserve(observer: Observer) {
        observers.remove(observer)
    }


    private fun publishIfReady() {
        val definition = graphDefinitionAttempt
            ?: return

        val navigation = navigationRoute
            ?: return

        val logicState = clientLogicState
            ?: return

        val nextSessionState = ClientState(
            definition,
            navigation,
            logicState
        )

        sessionState = nextSessionState

        // Snapshot: an observer's callback can subscribe or unsubscribe another one (a store update that
        // re-renders synchronously), which would otherwise mutate the set mid-iteration.
        for (observer in observers.toList()) {
            deliver(observer, nextSessionState)
        }
    }


    private fun deliver(observer: Observer, clientState: ClientState) {
        val observedObjectLocation = observer.observedObjectLocation()
        if (observedObjectLocation != null &&
                observedObjectLocation !in clientState.graphStructure().graphNotation.coalesce) {
            // NB: the observer addresses an object this graph no longer has — it was just deleted or renamed
            // and React hasn't re-rendered the subtree yet. Delivering would throw on the first notation
            // lookup; the imminent re-render either swaps in the new location or unmounts the observer.
            return
        }

        try {
            observer.onClientState(clientState)
        }
        catch (e: Throwable) {
            e.printStackTrace()
            window.alert("Observer error in ${observer::class.simpleName}: ${e.message}")
        }
    }


    fun current(): ClientState? {
        return sessionState
    }
}