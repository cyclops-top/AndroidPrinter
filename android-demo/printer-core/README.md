# printer-core

独立 Kotlin/JVM 打印模块，无 Android、Compose 或日志框架依赖。内部使用 JIPP；对外只暴露本模块模型、标准 Java 文件类型和 Kotlin 协程 API。当前支持手动地址连接的 IPP 打印机。

## 接入

```kotlin
dependencies {
    implementation(project(":printer-core"))
}
```

Android 宿主需声明 INTERNET 权限；连接明文 IPP 时自行配置 HTTP 访问策略。IPPS/HTTPS 使用系统证书校验。

## 用法

```kotlin
val printer: Printer = IppPrinter("ipp://10.0.2.2:8631/ipp/print")
val capabilities = printer.capabilities()
val job = printer.printPdf(
    pdfFile,
    PrintOptions(jobName = "学习资料", media = "iso_a4_210x297mm"),
)
printer.observe(job).collect { status ->
    when (status.state) {
        JobState.Completed -> { /* 成功 */ }
        JobState.Aborted, JobState.Canceled -> { /* 终止但非成功 */ }
        else -> { /* 更新进度 */ }
    }
}
// 单次查询：printer.status(job)
// 主动取消：printer.cancel(job)，然后查询最终状态
```

以上调用放在调用方的协程中。网络方法内部切换到 IO；轮询使用可取消的 delay，不阻塞调用方线程。模块不创建长期存活的 CoroutineScope。

## 接口与约定

| 方法 | 返回值 | 行为 |
| --- | --- | --- |
| `capabilities()` | `PrinterCapabilities` | 名称、纸张、已装载纸张、文档格式、单双面关键字 |
| `printPdf(file, options)` | `PrintJob` | 检查 PDF/纸张能力后发送一次，返回任务句柄 |
| `status(job)` | `JobStatus` | 类型化状态和设备上报的原因关键字 |
| `cancel(job)` | `Unit` | 请求取消，成功响应不等于已进入最终取消状态 |
| `observe(job)` | `Flow<JobStatus>` | 冷流，默认每秒查询，最多 30 次，终态发出后结束 |

- `JobState` 保留 Pending、Held、Processing、Stopped、Canceled、Aborted、Completed，未识别值保留在 `Unknown(code)` 中。Stopped 并非终态。
- `PrinterException` 区分 Network、Http、Protocol、InvalidResponse、Unsupported、PollingLimit。参数错误使用 IllegalArgumentException；协程取消保持 CancellationException。
- `PollingLimit` 携带原任务句柄，允许稍后继续查询，不代表任务失败。设备中止/取消通过 JobStatus 返回。
- 取消 Flow 只停止监听，不发送 Cancel-Job；多个 collector 会独立轮询。
- 不自动重试 Print-Job。网络中断或响应无效时，服务器可能已经接收了任务，盲目重试可能重复打印。
- 网络连接/读取超时分别为 10/20 秒；阻塞网络操作的协程取消不是即时中断，需等待当前 I/O 返回或超时。上传按块检查取消。
- 能力中的纸张、格式等保留协议关键字，避免封闭枚举丢掉厂家扩展。目前未把 media-col 纸盒/边距组合建模。
- 未包含设备发现、连接常驻状态、持久化队列、排版预览、PDF 转 Raster、账号认证。PDF 生成/预览留在 Demo；IPP 请求是无会话 HTTP，不虚构“已连接”状态。

## 验证

```sh
./gradlew :printer-core:test assembleDebug lintDebug
```

从项目根目录运行 `./scripts/verify-demo.sh`，验证使用该模块的 Android Demo 与 ippserver 之间的实际打印链路。
