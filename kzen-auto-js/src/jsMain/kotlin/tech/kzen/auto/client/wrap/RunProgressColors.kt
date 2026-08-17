package tech.kzen.auto.client.wrap

import web.cssom.Color


// The gold heat scale marking a run's active path. Shared by Flow (edge pipes, vertex cards) and Script
// (step accent bars), so it belongs to neither document type.
object RunProgressColors {
    // A pipe or input feeding the element being executed, while the run is live; an idle run uses
    // NamedColor.gold at full saturation instead.
    val goldSendingWhileRunning = Color("#ffe13f")

    val goldLight50 = Color("#ffeb7f")
    val goldLight75 = Color("#fff5bf")
    val goldLight90 = Color("#fffbe5")
    val goldLight93 = Color("#fffced")
}
