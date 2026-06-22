@file:JsModule("@mui/material/ClickAwayListener")
package tech.kzen.auto.client.wrap.material

import mui.material.ClickAwayListenerProps
import react.FC


//---------------------------------------------------------------------------------------------------------------------
// kotlin-wrappers' generated `mui.material.ClickAwayListener` (2026.6.5) is missing the `@JsName("default")`
// that every other component carries (compare its IconButton.kt), so it imports a non-existent NAMED export
// from `@mui/material/ClickAwayListener` and is `undefined` at runtime -> React #130 ("element type is
// invalid") the moment the popover renders. The module's only export is its default, so re-bind it here with
// the exact shape the wrappers use for working components: file-level @file:JsModule + @JsName("default") on
// the val. (A declaration-level @JsModule instead binds the whole module-namespace OBJECT — which surfaces as
// #130 with `args[]=object` rather than `undefined`, equally invalid as an element type.)
//
// The props interface is type-only, so reusing the wrappers' `mui.material.ClickAwayListenerProps` is safe.
@JsName("default")
external val ClickAwayListener: FC<ClickAwayListenerProps>
