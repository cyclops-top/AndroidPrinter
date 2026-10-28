package top.cyclops.printer.core

import com.hp.jipp.encoding.IppPacket
import com.hp.jipp.encoding.Tag
import com.hp.jipp.model.Types
import java.io.File
import java.net.URI
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class IppPrinter internal constructor(private val uri: URI, private val transport: IppTransport) : Printer {
    constructor(endpoint: String) : this(parseEndpoint(endpoint), HttpIppTransport())

    override suspend fun capabilities(): PrinterCapabilities {
        val response = transport.exchange(uri, IppPacket.getPrinterAttributes(uri).build())
        return PrinterCapabilities(
            name = response.getString(Tag.printerAttributes, Types.printerName),
            media = response.getStrings(Tag.printerAttributes, Types.mediaSupported).toSet(),
            readyMedia = response.getStrings(Tag.printerAttributes, Types.mediaReady).toSet(),
            documentFormats = response.getStrings(Tag.printerAttributes, Types.documentFormatSupported).toSet(),
            sides = response.getStrings(Tag.printerAttributes, Types.sidesSupported).toSet(),
        )
    }

    override suspend fun printPdf(file: File, options: PrintOptions): PrintJob {
        require(file.isFile && file.canRead()) { "PDF file must be readable" }
        require(options.jobName.isNotBlank() && options.userName.isNotBlank()) { "Names must not be blank" }
        val capabilities = capabilities()
        if ("application/pdf" !in capabilities.documentFormats) throw PrinterException.Unsupported("application/pdf")
        options.media?.let { if (it !in capabilities.media) throw PrinterException.Unsupported("media=$it") }
        val builder = IppPacket.printJob(uri).putOperationAttributes(
            Types.documentFormat.of("application/pdf"),
            Types.requestingUserName.of(options.userName),
            Types.jobName.of(options.jobName),
        )
        options.media?.let { builder.putJobAttributes(Types.media.of(it)) }
        val response = transport.exchange(uri, builder.build(), file)
        val id = response.getValue(Tag.jobAttributes, Types.jobId)
        if (id == null || id <= 0) throw PrinterException.InvalidResponse("Missing or invalid job-id; submission outcome unknown")
        return PrintJob(uri.toString(), id)
    }

    override suspend fun status(job: PrintJob): JobStatus {
        validateJob(job)
        val response = transport.exchange(uri, IppPacket.getJobAttributes(uri, job.id).build())
        val code = response.getValue(Tag.jobAttributes, Types.jobState)?.code
            ?: throw PrinterException.InvalidResponse("Missing job-state")
        return JobStatus(job, JobState.fromCode(code), response.getStrings(Tag.jobAttributes, Types.jobStateReasons))
    }

    override suspend fun cancel(job: PrintJob) {
        validateJob(job)
        transport.exchange(uri, IppPacket.cancelJob(uri, job.id).build())
    }

    override fun observe(job: PrintJob, intervalMillis: Long, maxPolls: Int): Flow<JobStatus> {
        validateJob(job)
        require(intervalMillis > 0 && maxPolls > 0) { "Polling interval and count must be positive" }
        return flow {
            repeat(maxPolls) { index ->
                val current = status(job)
                emit(current)
                if (current.state.isTerminal) return@flow
                if (index < maxPolls - 1) delay(intervalMillis)
            }
            throw PrinterException.PollingLimit(job)
        }
    }

    private fun validateJob(job: PrintJob) {
        require(job.printerUri == uri.toString()) { "Job belongs to a different printer" }
    }

    companion object {
        internal fun parseEndpoint(endpoint: String): URI {
            val uri = URI(endpoint)
            require(uri.scheme in setOf("ipp", "ipps", "http", "https") && uri.host != null &&
                uri.userInfo == null && uri.fragment == null && uri.query == null &&
                uri.port in -1..65535 && uri.port != 0) { "Invalid printer URI" }
            return uri
        }
    }
}
