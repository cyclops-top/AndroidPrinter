package top.cyclops.printer.core

import java.io.File
import kotlinx.coroutines.flow.Flow

/** All network methods are main-safe. Canceling collection stops monitoring, not the printer job. */
interface Printer {
    suspend fun capabilities(): PrinterCapabilities
    /** Sends exactly once. Requires PDF support; no implicit format conversion or retry. */
    suspend fun printPdf(file: File, options: PrintOptions = PrintOptions()): PrintJob
    suspend fun status(job: PrintJob): JobStatus
    /** Requests cancellation. Query status to determine the eventual device outcome. */
    suspend fun cancel(job: PrintJob)
    /** Cold flow: each collector polls independently; emits terminal status before completing. */
    fun observe(job: PrintJob, intervalMillis: Long = 1000, maxPolls: Int = 30): Flow<JobStatus>
}
