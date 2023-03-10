package tech.kzen.auto.client.wrap

//
//import kotlinx.css.CssBuilder
//import kotlinx.css.RuleSet
//import kotlin.js.Json
//import kotlin.js.json
//
//
//fun reactStyle(handler: RuleSet): Json {
//    val style = CssBuilder().apply(handler)
//
//    val reactStyles = json()
//
//    for (e in style.declarations) {
//        reactStyles[e.key] = e.value.toString()
//    }
//
//    return reactStyles
//}


//fun Color.lighten(percent: Int): Color {
//    val isHSLA = value.startsWith("hsl", ignoreCase = true)
//    val hsla = if (isHSLA) fromHSLANotation() else toRGBA().asHSLA()
//
//    val lightness = hsla.lightness + (hsla.lightness * (Color.normalizePercent(percent) / 100.0)).roundToInt()
//    val newHSLa = hsla.copy(lightness = Color.normalizePercent(lightness))
//    return if (isHSLA) {
//        hsla(newHSLa.hue, newHSLa.saturation, newHSLa.lightness, newHSLa.alpha)
//    } else {
//        with(newHSLa.asRGBA()) { rgba(red, green, blue, alpha) }
//    }
//}