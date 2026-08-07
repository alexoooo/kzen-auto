package tech.kzen.auto.client.objects.document.common.edit

import csstype.PropertiesBuilder
import emotion.react.css
import js.objects.unsafeJso
import mui.material.ClickAwayListenerMouseEvent
import mui.material.Paper
import mui.material.Size
import mui.material.TextField
import mui.system.sx
import react.ChildrenBuilder
import react.Key
import react.Props
import react.ReactNode
import react.RefObject
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.pre
import react.dom.html.ReactHTML.span
import react.dom.onChange
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.createRef
import tech.kzen.auto.client.wrap.htmlInputSlotProps
import tech.kzen.auto.client.wrap.material.ClickAwayListener
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.util.ExpressionUtils
import tech.kzen.auto.common.util.KotlinExpressionAnalyzer
import tech.kzen.auto.common.util.KotlinExpressionAnalyzer.Token
import tech.kzen.auto.common.util.KotlinExpressionAnalyzer.TokenKind
import web.cssom.*
import web.events.EventHandler
import web.html.HTMLDivElement
import web.html.HTMLPreElement
import web.html.HTMLSpanElement
import web.html.HTMLTextAreaElement
import web.resize.ResizeObserver


//---------------------------------------------------------------------------------------------------------------------
data class CodeCompletion(
    val insertText: String,
    val label: String,
    val detail: String?
)


external interface KotlinCodeAreaProps: Props {
    var value: String
    var onChange: (String) -> Unit
    var onBlur: (() -> Unit)?
    var label: String
    var disabled: Boolean
    var textAreaRef: RefObject<HTMLTextAreaElement>

    // The diagnostic for [value] — the only message this component prints, rendered under the field.
    var errorMessage: String?

    // Puts the outline in its error state WITHOUT printing anything, for a failure whose message is carried
    // elsewhere (a failed notation write is announced by the global banner). The outline reddens for either
    // this or [errorMessage]; the two are independent.
    var invalid: Boolean

    // The span [errorMessage] points at, as indices into [value], marked with a solid underline. Must be
    // NON-EMPTY: null is the one way to say "no marker", so an empty range would be a second encoding of it.
    // May start at [value].length — the one-past-the-end position a parse error reports — which marks the
    // end-of-text position rather than a glyph.
    //
    // Null whenever no position is known — including while the buffer is ahead of whatever was validated,
    // when an offset computed against different text would point at the wrong token.
    var errorRange: IntRange?

    // Identifier contents (back-ticks stripped, per ExpressionUtils.identifierContent) that name something
    // actually in scope, painted as resolved references.
    var knownIdentifiers: Set<String>

    // The names offerable at the caret, in the order they should be listed, each inserting the Kotlin
    // identifier an expression references it by. Nullable because a consumer wanting no completion simply
    // leaves it unset, which on an external interface is `undefined` rather than an empty list.
    var completions: List<CodeCompletion>?

    // Replaces `[start, endExclusive)` of [value] with the given text, leaving the caret after it. Completion
    // is offered only when this is set: without it an accepted item would have nowhere to go.
    var onReplaceRange: ((Int, Int, String) -> Unit)?
}


external interface KotlinCodeAreaState: State {
    // While the completion list is open, the identifier prefix an accepted item replaces: where it starts in
    // [KotlinCodeAreaProps.value], and the caret that ends it. Null together while it is closed. Held as two
    // Ints rather than one range, so the pure-component compare isn't defeated by a freshly allocated object.
    var completionPrefixStart: Int?
    var completionCaret: Int?

    var completionSelectedIndex: Int
}


//---------------------------------------------------------------------------------------------------------------------
// A Kotlin expression field: MUI's outlined multiline TextField with its own text rendered transparent, over a
// syntax-coloured <pre> painted underneath in exactly the same metrics. The textarea stays the sole accessible
// and focusable control — the backdrop is aria-hidden and takes no pointer events — so caret, selection,
// autosize and the floating label all remain MUI's.
class KotlinCodeArea(
    props: KotlinCodeAreaProps
):
    RPureComponent<KotlinCodeAreaProps, KotlinCodeAreaState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        // Hues follow IntelliJ's light editor scheme, so an expression reads the same way here as in the IDE,
        // and each is dark enough to stay legible on the card's white fill.
        private val defaultTextColor = Color("rgba(0, 0, 0, 0.87)")
        private val commentColor = Color("#8c8c8c")
        private val stringColor = Color("#067d17")
        private val numberColor = Color("#1750eb")
        private val keywordColor = Color("#0033b3")
        private val memberColor = Color("#871094")

        // Reserved for an identifier that names something in scope: a hint that the name resolves, never a
        // claim that an undecorated one doesn't (a local `val` resolves to nothing the client can see).
        private val resolvedReferenceColor = Color("#00627a")

        // MUI's error.main, matching the outline the field turns when `error` is set.
        private val errorColor = Color("#d32f2f")

        // Solid, and heavier than a default underline, because a red WAVY underline is what every browser
        // draws for a misspelled word — as a marker it would read as "this word isn't in the dictionary"
        // rather than "the compiler rejected this". The offset clears the descenders the rule runs through.
        private val errorMarkerThickness = 2.px
        private val errorMarkerUnderlineOffset = 2.px

        // The textarea's selection highlight paints over the backdrop, so it has to be translucent for the
        // coloured text to stay readable while selected.
        private val selectionColor = rgb(51, 144, 255, 0.3)

        // Copied verbatim off the textarea's computed style rather than restated here, so the backdrop's glyph
        // positions cannot drift from the field's: MUI sets the input font through slotProps and its padding
        // through the theme, and both move between MUI versions.
        private val syncedStyleProperties = listOf(
            "font-family", "font-size", "font-weight", "line-height", "letter-spacing", "tab-size",
            "white-space", "word-break", "overflow-wrap", "padding")

        // A trailing newline gets no line box of its own, so without a following character the last (empty)
        // line goes unpainted and the caret sits past the end of the backdrop. Zero-width, so it never shifts
        // anything when the text does not end in a newline.
        private const val trailingLineSentinel = "\u200B"

        // An error position at the end of the text has no glyph to underline, so the marker supplies one
        // character's worth of blank advance. It follows every real glyph, so nothing shifts, and in a
        // pre-wrap box a trailing space hangs rather than pushing the line to wrap.
        private const val endOfTextMarkerGlyph = " "

        // The `.` and `::` of `a.b` / `a::b` put the following name in the receiver's namespace, where nothing
        // the client knows about is in scope. `..` is the range operator and is deliberately absent.
        private val memberSelectors = setOf(".", "::")

        // Opens and closes a back-tick-quoted identifier; while one is being typed only the opening tick is
        // there, so the prefix is taken by dropping it rather than through ExpressionUtils.identifierContent
        // (which assumes both).
        private const val identifierQuote = "`"

        private const val arrowDownKey = "ArrowDown"
        private const val arrowUpKey = "ArrowUp"
        private const val enterKey = "Enter"
        private const val tabKey = "Tab"
        private const val escapeKey = "Escape"

        // KeyboardEvent.key for the space bar is the character it stands for.
        private const val spaceKey = " "

        // Above the card's own content, matching the step-reference popover it shares the field with.
        private val completionListZIndex = integer(100)
        private const val completionListElevation = 8
        private val completionListMinWidth = 12.em
        private val completionListMaxWidth = 32.em
        private val completionListMaxHeight = 14.em
        private val completionRowPadding = Padding(2.px, 8.px)
        private val completionFontSize = 13.px
        private val completionDetailFontSize = 11.px
        private val completionDetailPadding = 1.5.em
        private val completionSelectedBackgroundColor = Color("rgba(0, 98, 122, 0.12)")
        private val completionDetailColor = Color("rgba(0, 0, 0, 0.55)")

        // The caret anchor is an empty inline box, so its height is the font's content area rather than the
        // line box's — a couple of pixels short of the line's bottom, which this clears.
        private const val completionListCaretGap = 3.0
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val backdropRef: RefObject<HTMLPreElement> = createRef()
    private val caretAnchorRef: RefObject<HTMLSpanElement> = createRef()
    private val completionListRef: RefObject<HTMLDivElement> = createRef()

    private var resizeObserver: ResizeObserver? = null
    private var observedTextArea: HTMLTextAreaElement? = null

    // The caret position at which the list has already been dismissed — where an item was accepted, or where
    // Escape was pressed. At exactly that position the open rule would fire again (an accepted name is an
    // identifier ending at the caret), so the list would pop straight back up; any other position clears it.
    private var completionDismissedCaret: Int? = null

    // Set by a keydown this component acted on, and cleared by that key's keyup. The keydown already decided
    // the list's state, and re-deriving it from the caret on the way back up would undo the decision: Ctrl+Space
    // opens the list where the caret rule alone would not, and an accept has not yet put the caret back where
    // the inserted text ends.
    private var completionHandledKeyDown = false


    //-----------------------------------------------------------------------------------------------------------------
    override fun KotlinCodeAreaState.init(props: KotlinCodeAreaProps) {
        completionPrefixStart = null
        completionCaret = null
        completionSelectedIndex = 0
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        attachTextArea()
        syncBackdropMetrics()
    }


    override fun componentDidUpdate(
        prevProps: KotlinCodeAreaProps,
        prevState: KotlinCodeAreaState,
        snapshot: Any
    ) {
        attachTextArea()
        syncBackdropMetrics()
    }


    override fun componentWillUnmount() {
        detachTextArea()
    }


    //-----------------------------------------------------------------------------------------------------------------
    // TextareaAutosize grows the textarea from its own measurement pass, which does not re-render this component,
    // so componentDidUpdate alone leaves the backdrop at the previous line count's height.
    private fun attachTextArea() {
        val textArea = props.textAreaRef.current
        if (textArea === observedTextArea) {
            return
        }

        detachTextArea()

        if (textArea == null) {
            return
        }

        observedTextArea = textArea
        textArea.onscroll = EventHandler { syncBackdropScroll() }

        val observer = ResizeObserver { _, _ -> syncBackdropMetrics() }
        observer.observe(textArea)
        resizeObserver = observer
    }


    private fun detachTextArea() {
        resizeObserver?.disconnect()
        resizeObserver = null

        observedTextArea?.onscroll = null
        observedTextArea = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun syncBackdropMetrics() {
        val textArea = props.textAreaRef.current
            ?: return
        val backdrop = backdropRef.current
            ?: return

        val computed = getComputedStyle(textArea)
        for (property in syncedStyleProperties) {
            backdrop.style.setProperty(property, computed.getPropertyValue(property))
        }

        // The textarea's own offsetLeft/offsetTop are measured against ITS offsetParent (MUI's
        // .MuiInputBase-root), which is not the backdrop's containing block — so the origin is the delta
        // between two viewport rects read in the same frame, exact wherever the theme puts either wrapper.
        // clientLeft/clientTop reduce the containing block's border box to the padding box that `left`/`top`
        // resolve against. offsetParent is null only while the field has no layout box (a hidden ancestor),
        // when its position is moot and the next update re-measures.
        val containingBlock = backdrop.offsetParent
            ?: return

        val containingBlockRect = containingBlock.getBoundingClientRect()
        val textAreaRect = textArea.getBoundingClientRect()

        backdrop.style.setProperty(
            "left", "${textAreaRect.left - containingBlockRect.left - containingBlock.clientLeft}px")
        backdrop.style.setProperty(
            "top", "${textAreaRect.top - containingBlockRect.top - containingBlock.clientTop}px")

        // clientWidth/clientHeight are the textarea's padding box, which the backdrop matches by being
        // border-less and border-box sized.
        backdrop.style.setProperty("width", "${textArea.clientWidth}px")
        backdrop.style.setProperty("height", "${textArea.clientHeight}px")

        syncBackdropScroll()
        syncCompletionPosition()
    }


    private fun syncBackdropScroll() {
        val textArea = props.textAreaRef.current
            ?: return
        val backdrop = backdropRef.current
            ?: return

        backdrop.scrollTop = textArea.scrollTop
        backdrop.scrollLeft = textArea.scrollLeft
    }


    // Places the list from the backdrop's own caret anchor: the backdrop already renders the same glyphs in the
    // same metrics as the field, so a position read out of it cannot drift from what the user sees, and there
    // is no second measuring surface to keep in step. Written straight to the element during the commit phase
    // rather than through state, so the list is positioned in the frame it first paints. When the anchor can't
    // be measured — no layout box yet — the inline overrides come off and the stylesheet's below-the-field
    // fallback applies.
    private fun syncCompletionPosition() {
        val list = completionListRef.current
            ?: return

        val anchor = caretAnchorRef.current
        val containingBlock = list.offsetParent

        if (anchor == null || containingBlock == null) {
            list.style.removeProperty("left")
            list.style.removeProperty("top")
            return
        }

        val containingBlockRect = containingBlock.getBoundingClientRect()
        val anchorRect = anchor.getBoundingClientRect()

        list.style.setProperty(
            "left", "${anchorRect.left - containingBlockRect.left - containingBlock.clientLeft}px")
        list.style.setProperty(
            "top",
            "${anchorRect.bottom - containingBlockRect.top - containingBlock.clientTop + completionListCaretGap}px")
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Recomputed from the live textarea rather than from props: a key or change event carries the new caret (and
    // the just-typed character) on the element before React has re-rendered this component with them.
    private fun refreshCompletion(forceOpen: Boolean) {
        val textArea = props.textAreaRef.current
            ?: return

        val code = textArea.value
        val caret = textArea.selectionStart
        val prefixStart = prefixStartAtCaret(code, caret, textArea.selectionEnd, forceOpen)

        if (prefixStart == null) {
            closeCompletion()
            return
        }

        if (state.completionPrefixStart == prefixStart && state.completionCaret == caret) {
            return
        }

        setState {
            completionPrefixStart = prefixStart
            completionCaret = caret
            // A different span is a different match list, so an index into the previous one means nothing.
            completionSelectedIndex = 0
        }
    }


    // Where the name being typed at [caret] starts, or null where offering in-scope names would be wrong:
    // over a selection, at a member selector (the client cannot resolve a receiver's members), where the list
    // was just dismissed, or — unless completion was asked for explicitly — anywhere the caret is not at the
    // end of a name.
    private fun prefixStartAtCaret(code: String, caret: Int, selectionEnd: Int, forceOpen: Boolean): Int? {
        if (props.disabled || props.onReplaceRange == null || caret != selectionEnd) {
            return null
        }

        if (!forceOpen && caret == completionDismissedCaret) {
            return null
        }

        val tokens = KotlinExpressionAnalyzer.tokens(code)
        if (isMemberPosition(code, tokens, caret)) {
            return null
        }

        // A hard keyword is a legitimate prefix of a step name, so it opens the list too: gating on the token
        // being an identifier would make the list vanish and reappear at the `in` of `index`.
        val tokenAtCaret = tokens.firstOrNull { it.endExclusive == caret }
        if (tokenAtCaret != null &&
                (tokenAtCaret.kind == TokenKind.Identifier || tokenAtCaret.kind == TokenKind.Keyword)
        ) {
            return tokenAtCaret.start
        }

        return if (forceOpen) { caret } else { null }
    }


    // Mirrors the lexer's own member-selector rule: a `.` or `::` puts the next name in the receiver's
    // namespace, and whitespace or a comment between the two does not break the chain.
    private fun isMemberPosition(code: String, tokens: List<Token>, caret: Int): Boolean {
        var index = tokens.indexOfFirst { it.endExclusive == caret }

        while (index >= 0) {
            val token = tokens[index]
            when (token.kind) {
                TokenKind.Member ->
                    return true

                TokenKind.Operator ->
                    return code.substring(token.start, token.endExclusive) in memberSelectors

                TokenKind.Whitespace, TokenKind.Comment ->
                    index--

                else ->
                    return false
            }
        }

        return false
    }


    // The in-scope names still matching what has been typed, in the order the source offered them (scope order,
    // which is meaningful and free). Empty means the list is closed — there is no second open flag.
    private fun completionMatches(): List<CodeCompletion> {
        val prefix = completionPrefix()
            ?: return listOf()

        return (props.completions ?: listOf()).filter {
            // Matched on the identifier an expression names it by, not on the label: escaping rewrites some
            // names, and a completion that inserts something the analyzer then reads as anything other than a
            // reference is a bug. Case-insensitively, because step names are capitalized inconsistently.
            ExpressionUtils.identifierContent(it.insertText).startsWith(prefix, ignoreCase = true)
        }
    }


    private fun completionPrefix(): String? {
        val start = state.completionPrefixStart
            ?: return null
        val caret = state.completionCaret
            ?: return null

        if (start > caret || caret > props.value.length) {
            return null
        }

        return props.value.substring(start, caret).removePrefix(identifierQuote)
    }


    private fun selectedCompletionIndex(matchCount: Int): Int {
        return state.completionSelectedIndex.coerceIn(0, matchCount - 1)
    }


    private fun moveCompletionSelection(delta: Int, matchCount: Int) {
        // Wraps, so a held arrow key cycles the list instead of sticking at an end.
        val moved = (selectedCompletionIndex(matchCount) + delta + matchCount) % matchCount
        setState {
            completionSelectedIndex = moved
        }
    }


    private fun acceptCompletion(index: Int) {
        val matches = completionMatches()
        val completion = matches.getOrNull(index)
            ?: return
        val start = state.completionPrefixStart
            ?: return
        val caret = state.completionCaret
            ?: return
        val onReplaceRange = props.onReplaceRange
            ?: return

        dismissCompletion(start + completion.insertText.length)
        onReplaceRange(start, caret, completion.insertText)
    }


    // Closes the list AND remembers where, so the open rule doesn't fire again at the same caret — after an
    // accept it would offer the name just inserted, and after Escape it would undo the dismissal outright.
    private fun dismissCompletion(caret: Int?) {
        completionDismissedCaret = caret
        closeCompletion()
    }


    private fun closeCompletion() {
        if (state.completionPrefixStart == null) {
            return
        }

        setState {
            completionPrefixStart = null
            completionCaret = null
            completionSelectedIndex = 0
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun tokenColor(token: Token): Color {
        return when (token.kind) {
            TokenKind.Comment ->
                commentColor

            TokenKind.StringLiteral, TokenKind.CharLiteral ->
                stringColor

            TokenKind.Number ->
                numberColor

            TokenKind.Keyword ->
                keywordColor

            TokenKind.Member ->
                memberColor

            TokenKind.Identifier -> {
                val content = ExpressionUtils.identifierContent(
                    props.value.substring(token.start, token.endExclusive))

                if (content in props.knownIdentifiers) {
                    resolvedReferenceColor
                }
                else {
                    defaultTextColor
                }
            }

            TokenKind.Whitespace, TokenKind.Operator ->
                defaultTextColor
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val matches = completionMatches()
        val caretAnchor = state.completionCaret?.takeIf { matches.isNotEmpty() }

        div {
            css {
                position = Position.relative
            }

            // Rendered before the field so it paints underneath it: the caret and the selection highlight are
            // drawn by the textarea, and an overlay after it would cover both.
            renderBackdrop(caretAnchor)
            renderTextField()
            renderErrorMessage()
            renderCompletionList(matches)
        }
    }


    private fun ChildrenBuilder.renderBackdrop(caretAnchor: Int?) {
        pre {
            ref = backdropRef
            ariaHidden = true

            css {
                position = Position.absolute
                margin = 0.px
                overflow = Overflow.hidden
                boxSizing = BoxSizing.borderBox
                pointerEvents = None.none
                userSelect = None.none
                color = defaultTextColor
            }

            renderTokenSpans(caretAnchor)
            renderEndOfText(caretAnchor)
        }
    }


    private fun ChildrenBuilder.renderTokenSpans(caretAnchor: Int?) {
        val code = props.value
        val errorRange = props.errorRange

        for (token in KotlinExpressionAnalyzer.tokens(code)) {
            val spanColor = tokenColor(token)

            // The marked range and the caret anchor each begin at an arbitrary index, splitting a token into
            // runs painted the same way.
            val cuts = listOfNotNull(
                    token.start, errorRange?.first, errorRange?.let { it.last + 1 }, caretAnchor,
                    token.endExclusive)
                .filter { it in token.start..token.endExclusive }
                .distinct()
                .sorted()

            for (cutIndex in 0 until cuts.size - 1) {
                val from = cuts[cutIndex]
                val marked = errorRange != null && from in errorRange

                if (from == caretAnchor) {
                    renderCaretAnchor()
                }

                span {
                    css {
                        color = spanColor

                        if (marked) {
                            errorMarkerDecoration()
                        }
                    }

                    +code.substring(from, cuts[cutIndex + 1])
                }
            }
        }
    }


    // Closes the painted text: the last line's sentinel, preceded by the caret anchor and the error marker when
    // either lies past the final token. Tokens cover exactly `0 until value.length`, so an offset AT the end of
    // the text — where the caret sits in an empty field, and where a parse error one past the last line lands —
    // belongs to no token span and can only be placed here.
    private fun ChildrenBuilder.renderEndOfText(caretAnchor: Int?) {
        val errorRange = props.errorRange

        if (caretAnchor != null && caretAnchor >= props.value.length) {
            renderCaretAnchor()
        }

        if (errorRange != null && errorRange.first >= props.value.length) {
            span {
                css {
                    errorMarkerDecoration()
                }

                +endOfTextMarkerGlyph
            }
        }

        +trailingLineSentinel
    }


    // An empty inline box at the caret, measured to place the completion list. Empty rather than holding a
    // zero-width character: U+200B offers a soft-wrap opportunity and U+2060 removes one, either of which could
    // wrap the backdrop where the textarea does not, while a childless inline element adds no break opportunity
    // at all — so the painted text is identical whether or not the list is open.
    private fun ChildrenBuilder.renderCaretAnchor() {
        span {
            ref = caretAnchorRef
        }
    }


    private fun PropertiesBuilder.errorMarkerDecoration() {
        textDecorationLine = TextDecorationLine.underline
        textDecorationStyle = TextDecorationStyle.solid
        textDecorationColor = errorColor
        textDecorationThickness = errorMarkerThickness
        textUnderlineOffset = errorMarkerUnderlineOffset
        // Otherwise the rule is interrupted around descenders, which at the field's font size reads as a
        // rendering fault rather than a marker.
        textDecorationSkipInk = None.none
    }


    private fun ChildrenBuilder.renderTextField() {
        TextField {
            fullWidth = true
            multiline = true
            size = Size.small

            label = ReactNode(props.label)
            this.value = props.value
            this.inputRef = props.textAreaRef
            this.disabled = props.disabled
            this.error = props.invalid || props.errorMessage != null

            // The textarea's own glyphs are transparent, but the spelling squiggle the browser draws under
            // them is not — so every identifier the dictionary doesn't know (`listOf`, and most step names)
            // wore a red wavy underline indistinguishable from this component's error marker. The field
            // holds code, which no spell checker has an opinion worth showing on.
            htmlInputSlotProps = unsafeJso {
                spellCheck = false
            }

            sx {
                "& .MuiInputBase-input" {
                    fontFamily = FontFamily.monospace
                    // The backdrop paints the text; the textarea contributes only the caret and the selection.
                    color = Color.transparent
                    caretColor = defaultTextColor
                    // MUI fills a disabled input through -webkit-text-fill-color, which overrides `color` and
                    // would print a second, grey copy of the text on top of the backdrop.
                    asDynamic()["WebkitTextFillColor"] = "transparent"
                }

                "& .MuiInputBase-input::selection" {
                    backgroundColor = selectionColor
                }
            }

            onChange = {
                props.onChange((it.target as HTMLTextAreaElement).value)
                refreshCompletion(forceOpen = false)
            }

            onBlur = {
                props.onBlur?.invoke()
            }

            // Keydown bubbles here from the textarea, which MUI renders inside this field's root. While the
            // list is closed the field must behave as an ordinary textarea — Enter inserts a newline, Tab
            // leaves the field — so nothing but the explicit open request is intercepted then.
            onKeyDown = handler@ { event ->
                if (event.ctrlKey && !event.altKey && !event.metaKey && !event.shiftKey &&
                        event.key == spaceKey
                ) {
                    refreshCompletion(forceOpen = true)
                    completionHandledKeyDown = true
                    event.preventDefault()
                    return@handler
                }

                val matchCount = completionMatches().size
                if (matchCount == 0) {
                    return@handler
                }

                when (event.key) {
                    arrowDownKey ->
                        moveCompletionSelection(1, matchCount)

                    arrowUpKey ->
                        moveCompletionSelection(-1, matchCount)

                    enterKey, tabKey ->
                        acceptCompletion(selectedCompletionIndex(matchCount))

                    escapeKey ->
                        dismissCompletion(state.completionCaret)

                    else ->
                        return@handler
                }

                completionHandledKeyDown = true
                event.preventDefault()
            }

            onKeyUp = handler@ {
                if (completionHandledKeyDown) {
                    completionHandledKeyDown = false
                    return@handler
                }
                refreshCompletion(forceOpen = false)
            }

            onClick = {
                refreshCompletion(forceOpen = false)
            }

            onSelect = {
                refreshCompletion(forceOpen = false)
            }
        }
    }


    private fun ChildrenBuilder.renderErrorMessage() {
        val errorMessage = props.errorMessage
            ?: return

        pre {
            css {
                margin = 0.px
                marginTop = 4.px
                fontFamily = FontFamily.monospace
                fontSize = 12.px
                color = errorColor
                // Compiler messages are already multi-line, and carry type names long enough to overflow.
                whiteSpace = WhiteSpace.preWrap
                overflowWrap = OverflowWrap.breakWord
            }

            +errorMessage
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Plain rows, driven entirely from the textarea's keyboard handlers: a focusable list would take the caret
    // out of the field, stopping the user typing mid-completion and fighting the caret restore an accept
    // performs.
    private fun ChildrenBuilder.renderCompletionList(matches: List<CodeCompletion>) {
        if (matches.isEmpty()) {
            return
        }

        val selectedIndex = selectedCompletionIndex(matches.size)

        // Closed on mousedown rather than click, for the reason StepReferenceController.renderPopover
        // documents: a press that shifts layout between mousedown and mouseup never produces a click, which
        // would leave this open until the next one.
        ClickAwayListener {
            onClickAway = { _ -> closeCompletion() }
            mouseEvent = ClickAwayListenerMouseEvent.onMouseDown

            div {
                ref = completionListRef

                css {
                    position = Position.absolute
                    // Overwritten from the caret anchor as soon as it can be measured (syncCompletionPosition);
                    // this is where the list lands when it cannot.
                    left = 0.px
                    top = 100.pct
                    zIndex = completionListZIndex
                    minWidth = completionListMinWidth
                    maxWidth = completionListMaxWidth
                }

                Paper {
                    elevation = completionListElevation

                    sx {
                        maxHeight = completionListMaxHeight
                        overflowY = Auto.auto
                        // Nothing here is focusable, and a drag inside the list would only take the textarea's
                        // selection away from it.
                        userSelect = None.none
                    }

                    for ((index, completion) in matches.withIndex()) {
                        renderCompletionRow(completion, index, index == selectedIndex)
                    }
                }
            }
        }
    }


    private fun ChildrenBuilder.renderCompletionRow(completion: CodeCompletion, index: Int, selected: Boolean) {
        div {
            key = Key(completion.insertText)

            css {
                padding = completionRowPadding
                cursor = Cursor.pointer
                fontFamily = FontFamily.monospace
                fontSize = completionFontSize
                // A name too long for the list is cut with an ellipsis rather than wrapped, so every row stays
                // one line high and the keyboard selection moves predictably.
                whiteSpace = WhiteSpace.nowrap
                overflow = Overflow.hidden
                textOverflow = TextOverflow.ellipsis

                if (selected) {
                    backgroundColor = completionSelectedBackgroundColor
                }
            }

            // Mousedown's default action moves focus, which would blur the textarea — committing the pending
            // edit and fighting the caret restore the accept then performs. Propagation is left alone: the
            // surrounding ClickAwayListener tells inside from outside by containment, not by propagation.
            onMouseDown = { it.preventDefault() }
            onClick = { acceptCompletion(index) }

            +completion.label

            val detail = completion.detail
                ?: return@div

            span {
                css {
                    paddingLeft = completionDetailPadding
                    color = completionDetailColor
                    fontSize = completionDetailFontSize
                }

                +detail
            }
        }
    }
}
