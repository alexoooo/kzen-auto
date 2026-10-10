package tech.kzen.auto.server.util


/**
 * How long a test waits for something it expects before failing instead of hanging. A hang guard, not a
 * performance assertion: a run or a step can compile Kotlin on its way (a Formula, an expression source, an edited
 * Job), and a cold compile on a loaded machine takes tens of seconds. A wait returns as soon as what it awaits
 * happens, so the size costs nothing unless the test is failing anyway.
 */
const val hangGuardMillis = 120_000L
