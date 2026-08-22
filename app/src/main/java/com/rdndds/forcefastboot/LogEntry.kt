package com.rdndds.forcefastboot

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * A single terminal log line with its display color resolved once at creation
 * time so row composables never rescan tags during recomposition.
 *
 * [id] is monotonic and unique per entry; it survives oldest-entry trimming
 * (the list caps at [MainViewModel.MAX_LOG_LINES] and drops from the front),
 * which makes it safe as a LazyColumn item key while indices shift.
 */
@Immutable
data class LogEntry(
    val id: Long,
    val text: String,
    val color: Color,
)

/**
 * Pure tag-to-color mapping for a log line. Called exactly once when a
 * LogEntry is created (previously re-ran up to 13 substring scans per row on
 * every recomposition).
 *
 * Colors chosen for dark terminal background (#1E1E24):
 *   - Green (#66BB6A): success states
 *   - Cyan (#29B6F6): actions, bursts, sent commands
 *   - Amber (#FFB74D): getvar responses
 *   - Purple (#BA68C8): device attached
 *   - Slate (#90A4AE): device detached
 *   - Red (#EF5350): failures, timeouts, errors
 *   - Gray (#B0BEC5): informational messages
 *   - Teal (#80CBC4): raw responses
 *   - Light (#ECEFF1): default/unrecognized
 */
fun resolveLogColor(line: String): Color = when {
    line.contains("[SUCCESS]") -> Color(0xFF66BB6A) // Green
    line.contains("[ACTION]") || line.contains("[BURST]") || line.contains("[SENT]") -> Color(0xFF29B6F6) // Cyan / Blue
    line.contains("[GETVAR]") -> Color(0xFFFFB74D) // Amber / Orange
    line.contains("[ATTACHED]") -> Color(0xFFBA68C8) // Magenta / Purple
    line.contains("[DETACHED]") -> Color(0xFF90A4AE) // Slate
    line.contains("[FAILED]") ||
        line.contains("[TIMEOUT]") ||
        line.contains("[ERROR]") ||
        line.contains("✗") -> Color(0xFFEF5350) // Red
    line.contains("[INFO]") -> Color(0xFFB0BEC5) // Muted Gray
    line.contains("[RESP]") -> Color(0xFF80CBC4) // Teal
    else -> Color(0xFFECEFF1) // Default Light
}
