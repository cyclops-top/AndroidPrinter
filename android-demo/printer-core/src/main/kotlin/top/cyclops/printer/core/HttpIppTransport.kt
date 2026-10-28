package top.cyclops.printer.core

import com.hp.jipp.encoding.IppInputStream
import com.hp.jipp.encoding.IppOutputStream
import com.hp.jipp.encoding.IppPacket
import com.hp.jipp.encoding.Tag
import com.hp.jipp.model.Types
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal fun interface IppTransport {
    suspend fun exchange(uri: URI, packet: IppPacket, document: File?): IppPacket
}

internal suspend fun IppTransport.exchange(uri: URI, packet: IppPacket): IppPacket = exchange(uri, packet, null)

/** Blocking socket operations are bounded by timeouts; cancellation is checked between chunks. */
internal class HttpIppTransport : IppTransport {
    override suspend fun exchange(uri: URI, packet: IppPacket, document: File?): IppPacket = withContext(Dispatchers.IO) {
        val context = currentCoroutineContext()
        context.ensureActive()
        val scheme = if (uri.scheme in setOf("ipps", "https")) "https" else "http"
        val port = if (uri.port >= 0) uri.port else when (uri.scheme) {
            "http" -> 80
            "https" -> 443
            else -> 631
        }
        val url = URI(scheme, null, uri.host, port, uri.path.ifEmpty { "/" }, null, null).toURL()
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Content-Type", "application/ipp")
            connection.setChunkedStreamingMode(8192)
            IppOutputStream(connection.outputStream).use { output ->
                output.write(packet)
                document?.inputStream()?.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        context.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            context.ensureActive()
            if (connection.responseCode != 200) throw PrinterException.Http(connection.responseCode)
            val response = IppInputStream(connection.inputStream).use { it.readPacket() }
            if (response.requestId != packet.requestId) throw PrinterException.InvalidResponse("IPP request-id mismatch")
            if (response.code !in 0..255) throw PrinterException.Protocol(response.code, response.getString(Tag.operationAttributes, Types.statusMessage))
            response
        } catch (e: IOException) {
            context.ensureActive()
            if (e is PrinterException) throw e
            throw PrinterException.Network(e)
        } finally { connection.disconnect() }
    }
}
