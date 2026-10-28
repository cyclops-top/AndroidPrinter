# Android IPP 打印 Demo

目标：不经过 Android 系统打印弹窗，直接查询 IPP 打印机能力，生成并提交两页 A4 PDF，轮询任务状态，在 Mac 端保留收到的文件。

## 目录

- `android-demo/`：Android Studio 可打开的 Kotlin / Compose 工程。
- `android-demo/printer-core/`：可复用 Kotlin/JVM 模块，封装 HP JIPP 0.7.18；公开 suspend 方法、Flow 和类型化模型，Demo 通过模块调用打印。
- `tools/ippsample/`：PWG 官方 ippserver 源码和本地构建结果。
- `scripts/`：安装、启动和端到端验证脚本。
- `config/printer.attrs`：显式纸张配置，避免该版本模拟器默认 A4 属性校验失败。
- `runtime/spool/`：模拟打印机实际收到的文件；启动时使用 `-k` 保留。

## 运行

第一次构建打印机模拟器（需要 Xcode Command Line Tools、Homebrew 的 `openssl@3` 和 `pkg-config`；本机已具备）：

```sh
./scripts/install-printer.sh
```

启动模拟器（前台运行，Ctrl-C 停止）：

```sh
./scripts/start-printer.sh
```

Android Studio 打开 `android-demo`，运行到 Android 模拟器。默认地址为 `ipp://10.0.2.2:8631/ipp/print`。`10.0.2.2` 指向 Mac 宿主机；当前脚本用 `-n localhost` 仅监听本机。若连接真机，需将脚本中的主机名改为 Mac 的局域网 IP，再在 App 中填写相同 IP，并确保网络可达。

1. 点击“查询纸张能力”，查看纸张、已装载纸张、格式和单双面能力。
2. 点击“打印测试 PDF”，生成两页 A4 文档，检查设备支持 PDF / A4 后提交。
3. 查看任务状态及第 1 页预览；Mac 端检查 `runtime/spool`。

Mac 上可打开 `http://localhost:8631` 查看服务器页面。

命令行构建：

```sh
cd android-demo
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleDebug lintDebug
android run --apks="$PWD/app/build/outputs/apk/debug/app-debug.apk" --device=emulator-5554 --activity=top.cyclops.printer.MainActivity
```

## 范围

这是最小端到端验证：手动指定打印机地址、读取能力、提交一个多页 PDF、轮询任务状态、预览首张。模块已提供取消接口，Demo 暂无取消按钮。尚未实现 mDNS 发现、可编辑排版、持久化队列或 PDF → PWG Raster 转换。不支持 PDF 的设备会显示错误。

模拟器报告 completed 证明协议流程完成，不代表真实打印机的纸张和色彩效果。Demo 开启明文 HTTP 以连接本机 IPP 模拟器；HTTPS 使用系统证书校验，不绕过证书验证。任务轮询最多 30 次，超时不自动重新提交，避免重复打印。

## 参考

- [HP JIPP](https://github.com/HPInc/jipp)
- [PWG ippsample](https://github.com/istopwg/ippsample)，固定源码提交 `ce11296293da30a111615a2deddaea51268b49ce`。
- 已读取聊天《Android网络打印机接入方案》《获取打印机信息》《分析网络打印方案》。部分历史回复仅返回引用占位符。本 Demo 采用其中的 IPP + PDF 方向，先验证通信闭环。

## 本机验证结果（2026-10-28）

- `assembleDebug` 和 `lintDebug` 通过；Lint 0 errors，18 条模板依赖更新/风格等 warnings。
- Android 模拟器 `emulator-5554` 上执行 `./scripts/verify-demo.sh` 成功。
- 任务 `job-id=1`：`processing(5)` → `completed(9)`。
- 服务端文件：`runtime/spool/Android Demo Printer/1-android_demo_2_pages.pdf`。
- Android 原文件与服务器文件均为 45,486 字节，SHA-256 均为 `6d9dfbfd23fe2480b6c37d07043d088944aed8266285d26120649fe3b5cd5ae1`。
- PDF 为 2 页 A4；两页已渲染核对，中文、灰阶、页码正常。
- 截图：`runtime/demo-success.png`；日志：`runtime/android-print.log`。

只构建 libcups 和 ippserver，不构建需要 PDFio 的额外格式转换工具。源码构建使用 `--enable-debug` 避免默认 Universal 构建与 ARM Homebrew 库冲突。

## Kotlin 模块

接口与调用示例见 [printer-core/README.md](android-demo/printer-core/README.md)。模块无 Android/Compose 依赖，底层 JIPP 不向业务 API 泄漏。重构后已在 Android 模拟器验证任务 `job-id=2` 到达 `Completed`。
