package com.rdndds.forcefastboot

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rdndds.forcefastboot.usb.UsbWorkflowController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    val logEntries = mutableStateListOf<LogEntry>()

    val usbWorkflow =
        UsbWorkflowController(
            context = application,
            scope = viewModelScope,
            logFn = ::log,
        )

    private val logChannel = Channel<String>(capacity = Channel.UNLIMITED)

    // log() is main-thread only (UsbWorkflowController.logMain contract), so a
    // plain SimpleDateFormat field is safe; allocated once instead of per call.
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val nextId = AtomicLong(0L)

    companion object {
        private const val MAX_LOG_LINES = 500
        private const val MAX_LOG_FILE_BYTES = 1_000_000L // 1MB
    }

    init {
        // Dedicated sequential consumer for file logging to eliminate concurrent write races
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(getApplication<Application>().filesDir, "force_fastboot_log.txt")
            for (text in logChannel) {
                try {
                    if (file.exists() && file.length() > MAX_LOG_FILE_BYTES) {
                        val lines = file.readLines()
                        file.writeText(lines.takeLast(lines.size / 2).joinToString("\n") + "\n")
                    }
                    file.appendText(text + "\n")
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Failed to write log file", e)
                }
            }
        }
    }

    /**
     * Add a log line to the UI state and queue it for sequential file persistence.
     * Color is resolved once here; the entry id is monotonic for stable list keys.
     * Safe to call from the main thread.
     */
    fun log(message: String) {
        val text = "${timeFormat.format(Date())} $message"
        if (logEntries.size >= MAX_LOG_LINES) {
            logEntries.removeAt(0)
        }
        logEntries.add(
            LogEntry(
                id = nextId.getAndIncrement(),
                text = text,
                color = resolveLogColor(text),
            ),
        )
        logChannel.trySend(text)
    }

    fun resetSession() {
        usbWorkflow.reset()
        logEntries.clear()
        log("[INFO] Session reset. Listening for MTK bootloop device...")
    }

    override fun onCleared() {
        super.onCleared()
        logChannel.close()
    }
}
