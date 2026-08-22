package com.rdndds.forcefastboot.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Rule
import org.junit.Test

/**
 * Generates the startup Baseline Profile: cold start through the first
 * rendered MainScreen frame. Burst-loop and getvar paths are deliberately NOT
 * profiled - they are device-timing dependent runtime behavior, and startup
 * profiles must stay deterministic.
 */
class StartupBaselineProfile {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() {
        rule.collect(
            packageName = "com.rdndds.forcefastboot",
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
        }
    }
}
