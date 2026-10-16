package tech.kzen.auto.client.objects.document.script.step.control.foreach

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.RefObject
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tanstack.virtual.core.ScrollToEndOptions
import tanstack.virtual.core.VirtualItem
import tanstack.virtual.core.Virtualizer
import tanstack.virtual.core.VirtualizerOptions
import tanstack.virtual.core.elementScroll
import tanstack.virtual.core.observeElementOffset
import tanstack.virtual.core.observeElementRect
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.script.model.ScriptState
import tech.kzen.auto.client.objects.document.script.model.ScriptStore
import tech.kzen.auto.client.objects.document.script.model.ScriptStoreKey
import tech.kzen.auto.client.objects.document.script.progress.TraceListRef
import tech.kzen.auto.client.util.ClientError
import tech.kzen.auto.client.util.ClientResult
import tech.kzen.auto.client.util.ClientSuccess
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.createRef
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.refCallback
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.script.model.ForEachProgress
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceRange
import tech.kzen.lib.common.exec.logic.trace.model.LogicTraceSnapshot
import web.cssom.*
import web.dom.Element
import web.html.HTMLDivElement


//---------------------------------------------------------------------------------------------------------------------
external interface ForEachJournalProps: Props {
    var list: TraceListRef
    var producedCount: Int
}


external interface ForEachJournalState: State {
    // The entries held for display, and the list they were read from: one window of the journal around what is
    // in view, never the whole of it.
    var window: LogicTraceRange?
    var windowList: TraceListRef?

    // Why the last read of [errorList] failed, until a read of it succeeds.
    var error: String?
    var errorList: TraceListRef?
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * A ForEach loop's journal, one row per completed iteration, shown in [ForEachProgressButton]'s popover.
 *
 * The journal can be any length (it keeps every iteration), so only the rows in view are rendered and only a
 * window around them is fetched. A TanStack `Virtualizer` (virtual-core, driven from this class component the way
 * its React adapter drives it from hooks) places the rows in a scroll box sized to the whole journal, measuring
 * each so a long value still wraps; [ScriptStore]'s progress store fetches the window by range
 * (`LogicTrace.lookupRange`).
 *
 * The window is re-read when the view leaves it, and with every trace refresh while the run is live: a list
 * can change in place (a nested loop's journal resets with each outer iteration and may refill to the same
 * length between two refreshes), so the rows are only as current as the last read, like the rest of the trace.
 * Reads wait for scrolling to stop and are one at a time, the latest wanted read going next. A failed read is
 * shown above the rows (which keep what was last read) until a read succeeds.
 *
 * It opens at the end and follows new rows while the last row is among those rendered (the view is at the end,
 * give or take the overscan); scrolling up stops the following and scrolling back to the end resumes it. A row
 * test rather than TanStack's own `followOnAppend`, which needs the view within a pixel of the end and so stops
 * following as soon as measuring the newest rows (a long value wraps) moves the end out from under it.
 */
class ForEachJournal(
    props: ForEachJournalProps
):
    RPureComponent<ForEachJournalProps, ForEachJournalState>(props),
    ScriptStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val mutedColour = Color("gray")
        private val hoverColour = Color("#eeeeee")

        // A one-line row, until it is measured.
        private const val estimatedRowPx = 22

        // Rows rendered beyond the view on each side, so a short scroll shows rows rather than gaps.
        private const val overscanRows = 10
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val scrollRef: RefObject<HTMLDivElement> = createRef()

    private val virtualizer = Virtualizer(options())
    private var unmountVirtualizer: (() -> Unit)? = null

    // The rows in view at the last render, which the window must cover.
    private var viewItems: List<VirtualItem> = listOf()

    // Whether the last row was among those rendered before the count last changed: new rows are then followed.
    private var atEnd = false

    private var lastSnapshot: LogicTraceSnapshot? = null
    private var reading = false
    private var readWanted = false

    private val measureRow = refCallback<HTMLDivElement> { element ->
        virtualizer.measureElement(element)
        null
    }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        installContextType(DocumentBridgeContext)
    }


    override fun ForEachJournalState.init(props: ForEachJournalProps) {
        window = null
        windowList = null
        error = null
        errorList = null
    }


    private fun scriptStore(): ScriptStore? =
        contextValue<DocumentBridge?>()?.lookup(ScriptStoreKey)


    //-----------------------------------------------------------------------------------------------------------------
    private fun options(): VirtualizerOptions<HTMLDivElement, Element> {
        return VirtualizerOptions(
            count = props.producedCount,
            getScrollElement = { scrollRef.current },
            estimateSize = { estimatedRowPx },
            scrollToFn = elementScroll,
            observeElementRect = observeElementRect,
            observeElementOffset = observeElementOffset,
            overscan = overscanRows,
            onChange = { _, _ -> forceUpdate() })
    }


    // The adapter's lifecycle (TanStack's useVirtualizer): options on every render, attach to the scroll element
    // after every commit, detach on unmount. The two lifecycle methods are not in the Kotlin wrapper.
    override fun componentDidMount() {
        unmountVirtualizer = virtualizer.asDynamic()._didMount().unsafeCast<() -> Unit>()
        virtualizer.asDynamic()._willUpdate()
        virtualizer.scrollToEnd(ScrollToEndOptions())

        scriptStore()?.observe(this)
        readIfNeeded()
    }


    override fun componentDidUpdate(prevProps: ForEachJournalProps, prevState: ForEachJournalState, snapshot: Any) {
        virtualizer.asDynamic()._willUpdate()
        if (atEnd && props.producedCount > prevProps.producedCount) {
            virtualizer.scrollToEnd(ScrollToEndOptions())
        }
        readIfNeeded()
    }


    override fun componentWillUnmount() {
        scriptStore()?.unobserve(this)
        unmountVirtualizer?.invoke()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onScriptState(scriptState: ScriptState) {
        val snapshot = scriptState.progress.logicTraceSnapshot
        if (snapshot === lastSnapshot) {
            return
        }
        lastSnapshot = snapshot
        read()
    }


    private fun readIfNeeded() {
        val first = viewItems.firstOrNull()?.index
            ?: return
        val last = viewItems.last().index

        val window = state.window
        val covered =
            window != null &&
            state.windowList == props.list &&
            first >= window.offset &&
            last < window.offset + window.entries.size

        if (! covered) {
            read()
        }
    }


    private fun read() {
        if (virtualizer.isScrolling) {
            // Re-checked when scrolling stops: the virtualizer's change notice re-renders, and the update reads.
            return
        }
        if (reading) {
            readWanted = true
            return
        }

        val first = viewItems.firstOrNull()?.index
            ?: return
        val last = viewItems.last().index
        val store = scriptStore()
            ?: return

        // The view and a view's worth either side, so a short scroll stays inside the window.
        val page = last - first + 1
        val offset = (first - page).coerceAtLeast(0)
        val count = last + 1 + page - offset
        val list = props.list

        reading = true
        async {
            val result =
                try {
                    store.progressStore.lookupRange(list, offset, count)
                }
                catch (e: Throwable) {
                    // The request failed outright (no answer): shown like a refused read, and the reads go on.
                    ClientResult.ofError(e.message ?: e.toString())
                }
            reading = false

            if (list == props.list) {
                when (result) {
                    is ClientSuccess -> setState {
                        window = result.value
                        windowList = list
                        error = null
                        errorList = null
                    }

                    is ClientError -> setState {
                        error = result.message
                        errorList = list
                    }
                }
            }

            if (readWanted) {
                readWanted = false
                read()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        // Read against the count the rows were rendered for, before the new options replace it.
        atEnd = viewItems.lastOrNull()?.index == virtualizer.options.count - 1

        virtualizer.setOptions(options())
        val items = virtualizer.getVirtualItems().toList()
        viewItems = items

        state.error?.takeIf { state.errorList == props.list }?.let { error ->
            div {
                css {
                    width = 36.em
                    marginBottom = 0.5.em
                    overflowWrap = OverflowWrap.anywhere
                    color = NamedColor.red
                }
                +"Could not read the journal: $error"
            }
        }

        div {
            ref = scrollRef

            css {
                // Bounded height, so the journal scrolls inside the popover rather than growing it past the
                // viewport; the rows inside are only the ones in view. A set width: the rows are positioned out
                // of the flow, so they cannot size the popover, and a wrapped row is measured at this width.
                width = 36.em
                maxHeight = 24.em
                overflowY = Auto.auto
            }

            div {
                css {
                    position = Position.relative
                    width = 100.pct
                    height = virtualizer.getTotalSize().px
                }

                div {
                    css {
                        position = Position.absolute
                        top = 0.px
                        left = 0.px
                        width = 100.pct
                        transform = translatey((items.firstOrNull()?.start ?: 0).px)
                    }

                    for (item in items) {
                        renderRow(item)
                    }
                }
            }
        }
    }


    private fun ChildrenBuilder.renderRow(item: VirtualItem) {
        val window = state.window?.takeIf { state.windowList == props.list }
        val entry = window
            ?.entries
            ?.getOrNull(item.index - window.offset)
            ?.let { ForEachProgress.entryOfExecutionValueOrNull(it) }

        div {
            key = react.Key("${item.key}")
            ref = measureRow
            asDynamic()["data-index"] = item.index

            css {
                display = Display.flex
                alignItems = AlignItems.baseline
                paddingTop = 0.15.em
                paddingBottom = 0.15.em
                borderRadius = 2.px

                "&:hover" {
                    backgroundColor = hoverColour
                }
            }

            span {
                css {
                    flexShrink = number(0.0)
                    minWidth = 2.5.em
                    textAlign = TextAlign.right
                    marginRight = 0.75.em
                    color = mutedColour
                }
                +"${item.index + 1}"
            }

            if (entry == null) {
                // Not read yet: filled in by the read that follows scrolling here.
                span {
                    css {
                        color = mutedColour
                    }
                    +"…"
                }
            }
            else {
                renderEntry(entry)
            }
        }
    }


    private fun ChildrenBuilder.renderEntry(entry: ForEachProgress.Entry) {
        span {
            css {
                // Its own width up to a share of the row, so a long value beside it cannot squeeze it to a
                // character a line; a long item wraps within that share.
                flexShrink = number(0.0)
                maxWidth = 40.pct
                overflowWrap = OverflowWrap.anywhere
                color = mutedColour
            }
            +entry.item
        }

        span {
            css {
                flexShrink = number(0.0)
                marginLeft = 0.5.em
                marginRight = 0.5.em
                color = mutedColour
            }
            +"→"
        }

        span {
            css {
                overflowWrap = OverflowWrap.anywhere
                fontWeight = FontWeight.bold
            }
            +entry.value
        }
    }
}
