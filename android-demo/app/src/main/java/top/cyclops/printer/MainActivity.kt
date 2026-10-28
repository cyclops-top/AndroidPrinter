package top.cyclops.printer

import android.os.Bundle
import top.cyclops.printer.core.*
import kotlinx.coroutines.CancellationException
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import top.cyclops.printer.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val scope = rememberCoroutineScope()
                var endpoint by remember { mutableStateOf("ipp://10.0.2.2:8631/ipp/print") }
                var output by remember { mutableStateOf("先查询能力，再提交两页 A4 PDF。") }
                var busy by remember { mutableStateOf(false) }
                var preview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                fun execute(print: Boolean) {
                    if (busy) return
                    busy = true
                    val target = endpoint
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                val client: Printer = IppPrinter(target)
                                if (print) {
                                    val file = DemoDocument.create(filesDir)
                                    val bitmap = DemoDocument.preview(file)
                                    val job = client.printPdf(file, PrintOptions(jobName = "Android Demo 2 pages", media = "iso_a4_210x297mm"))
                                    var finalStatus: JobStatus? = null
                                    client.observe(job).collect { status ->
                                        finalStatus = status
                                        android.util.Log.i("PrinterDemo", "job-id=${job.id} state=${status.state}")
                                    }
                                    val state = finalStatus?.state
                                    if (state == JobState.Completed) android.util.Log.i("PrinterDemo", "DEMO_SUCCESS job-id=${job.id}")
                                    "任务 ${job.id}：$state\n两页 A4 PDF，${file.length()} bytes" to bitmap
                                } else {
                                    val caps = client.capabilities()
                                    "打印机：${caps.name}\n支持纸张：${caps.media.joinToString()}\n已装载：${caps.readyMedia.joinToString()}\n格式：${caps.documentFormats.joinToString()}\n双面：${caps.sides.joinToString()}" to null
                                }
                            }
                            output = result.first
                            result.second?.let { preview = it }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            android.util.Log.e("PrinterDemo", "DEMO_FAILED", e)
                            output = "失败：${e.message}"
                        } finally { busy = false }
                    }
                }
                LaunchedEffect(Unit) {
                    if (intent.getBooleanExtra("auto_print", false)) {
                        intent.removeExtra("auto_print")
                        execute(true)
                    }
                }
                Scaffold { padding ->
                    Column(Modifier.padding(padding).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("IPP 打印 Demo", style = MaterialTheme.typography.headlineMedium)
                        Text("Android → IPP → Mac ippserver")
                        OutlinedTextField(endpoint, { endpoint = it }, label = { Text("打印机 URI") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { execute(false) }, enabled = !busy) { Text("查询纸张能力") }
                            Button(onClick = { execute(true) }, enabled = !busy) { Text("打印测试 PDF") }
                        }
                        if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("正在通信，请稍候…") }
                        Text(output, style = MaterialTheme.typography.bodySmall)
                        preview?.let {
                            Text("已提交 PDF · 第 1 页预览")
                            Image(it.asImageBitmap(), "PDF 第一页", Modifier.fillMaxWidth().height(420.dp))
                        }
                    }
                }
            }
        }
    }
}
