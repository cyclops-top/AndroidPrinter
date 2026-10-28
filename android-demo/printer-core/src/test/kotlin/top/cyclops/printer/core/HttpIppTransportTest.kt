package top.cyclops.printer.core

import com.hp.jipp.encoding.IppPacket
import com.hp.jipp.encoding.IppOutputStream
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class HttpIppTransportTest {
    @Test fun `HTTP IPP and mismatched responses have distinct error types`() = runTest {
        val request = IppPacket.getPrinterAttributes(URI("ipp://localhost/ipp/print")).build()
        for ((httpCode, response, expected) in listOf(
            Triple(503, IppPacket.Builder(0).build(), PrinterException.Http::class.java),
            Triple(200, IppPacket.Builder(0x0400).build().copy(requestId = request.requestId), PrinterException.Protocol::class.java),
            Triple(200, IppPacket.Builder(0).build().copy(requestId = request.requestId + 1), PrinterException.InvalidResponse::class.java),
        )) {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/ipp/print") { exchange ->
                exchange.requestBody.use { it.readBytes() }
                val bytes = ByteArrayOutputStream().also { buffer -> IppOutputStream(buffer).use { it.write(response) } }.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/ipp")
                exchange.sendResponseHeaders(httpCode, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            try {
                val endpoint = URI("ipp://127.0.0.1:${server.address.port}/ipp/print")
                try { HttpIppTransport().exchange(endpoint, request); fail("Expected ${expected.simpleName}") }
                catch (error: PrinterException) { assertTrue(error.toString(), expected.isInstance(error)) }
            } finally { server.stop(0) }
        }
    }
}
