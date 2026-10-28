package top.cyclops.printer.core

import java.io.IOException

/** Printer keywords are preserved: vendors may define additional media/formats. */
data class PrinterCapabilities(
    val name: String?,
    val media: Set<String>,
    val readyMedia: Set<String>,
    val documentFormats: Set<String>,
    val sides: Set<String>,
)

data class PrintOptions(
    val jobName: String = "Print job",
    val userName: String = "printer-client",
    val media: String? = null,
)

data class PrintJob(val printerUri: String, val id: Int) {
    init { require(id > 0) { "Job id must be positive" } }
}

sealed interface JobState {
    data object Pending : JobState
    data object Held : JobState
    data object Processing : JobState
    data object Stopped : JobState
    data object Canceled : JobState
    data object Aborted : JobState
    data object Completed : JobState
    data class Unknown(val code: Int) : JobState

    val isTerminal: Boolean get() = this == Canceled || this == Aborted || this == Completed

    companion object {
        fun fromCode(code: Int): JobState = when (code) {
            3 -> Pending
            4 -> Held
            5 -> Processing
            6 -> Stopped
            7 -> Canceled
            8 -> Aborted
            9 -> Completed
            else -> Unknown(code)
        }
    }
}

data class JobStatus(val job: PrintJob, val state: JobState, val reasons: List<String>)

/** Transport failures after submission may have an unknown outcome. Never blindly retry a print. */
sealed class PrinterException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    class Network(cause: IOException) : PrinterException("Printer communication failed", cause)
    class Http(val status: Int) : PrinterException("HTTP $status")
    class Protocol(val status: Int, val detail: String?) : PrinterException("IPP $status: $detail")
    class InvalidResponse(detail: String) : PrinterException(detail)
    class Unsupported(val capability: String) : PrinterException("Unsupported: $capability")
    class PollingLimit(val job: PrintJob) : PrinterException("Polling limit reached for job ${job.id}; job may still be running")
}
