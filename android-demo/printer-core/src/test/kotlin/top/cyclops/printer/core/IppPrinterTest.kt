package top.cyclops.printer.core

import com.hp.jipp.encoding.IppPacket
import com.hp.jipp.model.Types
import com.hp.jipp.model.JobState as IppJobState
import java.net.URI
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class IppPrinterTest {
    private val uri = URI("ipp://localhost:8631/ipp/print")
    private val job = PrintJob(uri.toString(), 7)
    private fun response(state: IppJobState) = IppPacket.Builder(0).putJobAttributes(Types.jobState.of(state)).build()

    @Test fun `monitor emits terminal state then stops`() = runTest {
        val states = ArrayDeque(listOf(IppJobState.pending, IppJobState.processing, IppJobState.completed))
        val printer = IppPrinter(uri, IppTransport { _, _, _ -> response(states.removeFirst()) })
        assertEquals(listOf(JobState.Pending, JobState.Processing, JobState.Completed), printer.observe(job).toList().map { it.state })
        assertTrue(states.isEmpty())
    }

    @Test fun `aborted and canceled end monitoring without being treated as success`() = runTest {
        for (state in listOf(IppJobState.aborted, IppJobState.canceled)) {
            val printer = IppPrinter(uri, IppTransport { _, _, _ -> response(state) })
            val result = printer.observe(job).toList().single()
            assertTrue(result.state.isTerminal)
            assertNotEquals(JobState.Completed, result.state)
        }
    }

    @Test fun `poll limit preserves job handle and does not submit again`() = runTest {
        var calls = 0
        val printer = IppPrinter(uri, IppTransport { _, request, _ ->
            assertEquals(9, request.code)
            calls++
            response(IppJobState.processing)
        })
        try {
            printer.observe(job, maxPolls = 2).toList()
            fail("Expected polling limit")
        } catch (e: PrinterException.PollingLimit) { assertEquals(job, e.job) }
        assertEquals(2, calls)
    }

    @Test fun `stopping collection never sends Cancel-Job`() = runTest {
        val operations = mutableListOf<Int>()
        val printer = IppPrinter(uri, IppTransport { _, request, _ ->
            operations += request.code
            response(IppJobState.processing)
        })
        printer.observe(job).take(1).toList()
        assertEquals(listOf(9), operations)
        printer.cancel(job)
        assertEquals(listOf(9, 8), operations)
    }

    @Test fun `unsupported PDF prevents Print-Job`() = runTest {
        val file = File.createTempFile("printer-test", ".pdf")
        try {
            var calls = 0
            val printer = IppPrinter(uri, IppTransport { _, request, _ ->
                calls++
                assertEquals(11, request.code)
                IppPacket.Builder(0).build()
            })
            try { printer.printPdf(file); fail("Expected unsupported format") }
            catch (e: PrinterException.Unsupported) { assertEquals("application/pdf", e.capability) }
            assertEquals(1, calls)
        } finally { file.delete() }
    }

    @Test fun `foreign jobs and missing states are rejected`() = runTest {
        val printer = IppPrinter(uri, IppTransport { _, _, _ -> IppPacket.Builder(0).build() })
        try { printer.status(PrintJob("ipp://other/ipp/print", 7)); fail("Expected foreign job rejection") }
        catch (_: IllegalArgumentException) { }
        try { printer.status(job); fail("Expected invalid response") }
        catch (_: PrinterException.InvalidResponse) { }
        assertEquals(JobState.Unknown(123), JobState.fromCode(123))
        assertFalse(JobState.Stopped.isTerminal)
    }
}
