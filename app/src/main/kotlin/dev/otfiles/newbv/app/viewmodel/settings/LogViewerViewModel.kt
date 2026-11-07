package dev.otfiles.newbv.app.viewmodel.settings

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.otfiles.newbv.app.network.HttpServer
import dev.otfiles.newbv.core.log.CrashHandler
import dev.otfiles.newbv.core.log.Loggers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject

/** 写入剪贴板时显示在系统提示中的标签。 */
private const val CLIPBOARD_LABEL = "newBV 日志"

/**
 * 复制到剪贴板的单次字符上限。
 *
 * 崩溃日志动辄几百 KB，一次性写入剪贴板在低端设备上会引起明显卡顿，
 * 且手机上粘贴超长文本也几乎无法使用。截断后仍然给出完整下载地址。
 */
private const val MAX_CLIPBOARD_CHARS = 100_000

/**
 * 日志查看页 ViewModel。
 *
 * 管理 [HttpServer] 生命周期和日志文件列表。
 * 进入页面时启动 HTTP 服务器，离开时停止。
 *
 * @param application 用于获取 filesDir 和 assets。
 * @param httpServer 本地 HTTP 日志服务器。
 * @param crashHandler 崩溃处理器（提供崩溃/手动日志列表）。
 */
@HiltViewModel
class LogViewerViewModel
    @Inject
    constructor(
        application: Application,
        private val httpServer: HttpServer,
        private val crashHandler: CrashHandler,
    ) : AndroidViewModel(application) {
        private val logger = Loggers.get("LogViewerViewModel")

        private val _uiState = MutableStateFlow(LogViewerUiState())
        val uiState: StateFlow<LogViewerUiState> = _uiState.asStateFlow()

        init {
            startServer()
            refreshLogs()
        }

        /**
         * 启动 HTTP 服务器并解析本地 IP。
         *
         * 在 IO 线程启动服务器，获取端口后更新 UI 状态。
         * 同时扫描 wlan0 接口获取 IPv4 地址，用于生成二维码 URL。
         */
        private fun startServer() {
            viewModelScope.launch(Dispatchers.IO) {
                httpServer.start()
                val port = httpServer.getPort() ?: 0
                val host = getLocalIpAddress()
                _uiState.value =
                    _uiState.value.copy(
                        serverAddress = "$host:$port",
                        isServerReady = port > 0,
                    )
            }
        }

        /**
         * 刷新日志文件列表。
         *
         * 合并崩溃日志和手动日志，按最后修改时间降序排列。
         */
        fun refreshLogs() {
            viewModelScope.launch(Dispatchers.IO) {
                val logs =
                    (
                        crashHandler.listManualLogs() +
                            crashHandler.listCrashLogs()
                    ).sortedByDescending { it.lastModified() }
                _uiState.value = _uiState.value.copy(logFiles = logs)
            }
        }

        /**
         * 创建手动日志（含 logcat 输出）。
         *
         * 创建后刷新列表。
         */
        fun createManualLog() {
            viewModelScope.launch(Dispatchers.IO) {
                crashHandler.createManualLog()
                refreshLogs()
            }
        }

        /**
         * 获取当前选中日志文件的下载 URL。
         *
         * @param file 日志文件。
         * @return 形如 `http://192.168.1.100:12345/api/logs/filename.log` 的 URL。
         */
        fun getFileUrl(file: File): String {
            val addr = _uiState.value.serverAddress
            return "http://$addr/api/logs/${file.name}"
        }

        /**
         * 一键复制日志到剪贴板。
         *
         * 电视上没有方便的文件管理器与分享面板，用户排障时往往需要把日志内容或
         * 下载地址发给开发者。这里两种模式共用一条链路：
         * - [copyContent] 为 false 时只复制下载地址，方便在电脑浏览器直接打开；
         * - 为 true 时复制「文件名 + 下载地址 + 日志正文」，正文过长时截断，
         *   避免超大文本写入剪贴板导致部分低端设备卡顿。
         *
         * 文件读取与拼接在 IO 线程完成，剪贴板写入回到主线程。
         *
         * @param file 日志文件
         * @param copyContent 是否复制日志正文
         * @return 复制结果，调用方据此提示用户
         */
        suspend fun copyLog(
            file: File,
            copyContent: Boolean,
        ): CopyLogResult =
            withContext(Dispatchers.IO) {
                val url = getFileUrl(file)
                runCatching {
                    val text =
                        if (copyContent) {
                            val body = file.readText()
                            val truncated =
                                if (body.length > MAX_CLIPBOARD_CHARS) {
                                    body.take(MAX_CLIPBOARD_CHARS) +
                                        "\n…（已截断，完整日志请访问 $url）"
                                } else {
                                    body
                                }
                            buildString {
                                appendLine("# ${file.name}")
                                appendLine("# $url")
                                appendLine()
                                append(truncated)
                            }
                        } else {
                            url
                        }
                    withContext(Dispatchers.Main) {
                        writeToClipboard(text)
                    }
                    CopyLogResult.Success(withContent = copyContent, chars = text.length)
                }.getOrElse { error ->
                    logger.error(error) { "Failed to copy log: ${file.name}" }
                    CopyLogResult.Failure(error.message ?: "复制失败")
                }
            }

        /**
         * 复制日志服务器首页地址。
         *
         * 用户在手机上访问该地址即可打开日志管理页，比扫码更通用（部分电视没有
         * 摄像头扫码环境，只能手输）。
         */
        suspend fun copyServerAddress(): CopyLogResult =
            withContext(Dispatchers.Main) {
                val address = getServerUrl()
                runCatching {
                    writeToClipboard(address)
                    CopyLogResult.Success(withContent = false, chars = address.length)
                }.getOrElse { error ->
                    logger.error(error) { "Failed to copy server address" }
                    CopyLogResult.Failure(error.message ?: "复制失败")
                }
            }

        /** 写入系统剪贴板，供剪贴板写入的两条路径共用。 */
        private fun writeToClipboard(text: String) {
            val clipboard =
                getApplication<Application>()
                    .getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    ?: throw IllegalStateException("当前设备不支持剪贴板")
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(CLIPBOARD_LABEL, text))
        }

        /**
         * 获取服务器首页 URL（用于生成二维码）。
         *
         * @return 形如 `http://192.168.1.100:12345/` 的 URL。
         */
        fun getServerUrl(): String {
            val addr = _uiState.value.serverAddress
            return "http://$addr/"
        }

        override fun onCleared() {
            httpServer.stop()
            super.onCleared()
        }

        /**
         * 获取局域网 IPv4 地址。
         *
         * 优先取系统当前活动网络的地址：电视可能同时存在有线、Wi-Fi、Wi-Fi Direct、
         * VPN 等多个网卡，[NetworkInterface] 的枚举顺序不可靠，取到其他网卡的地址
         * 会让手机无法访问日志服务器。旧系统（API 21/22）没有
         * [ConnectivityManager.getLinkProperties]，回退到遍历网卡。
         *
         * @return IPv4 地址字符串，获取失败返回空字符串。
         */
        private fun getLocalIpAddress(): String {
            val activeNetworkAddress =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    runCatching {
                        val connectivityManager =
                            getApplication<Application>()
                                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                        val network = connectivityManager?.activeNetwork ?: return@runCatching null
                        connectivityManager
                            .getLinkProperties(network)
                            ?.linkAddresses
                            ?.asSequence()
                            ?.map { it.address }
                            ?.filterIsInstance<Inet4Address>()
                            ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                            ?.hostAddress
                    }.getOrNull()
                } else {
                    null
                }

            if (!activeNetworkAddress.isNullOrEmpty()) return activeNetworkAddress

            return runCatching {
                NetworkInterface
                    .getNetworkInterfaces()
                    .toList()
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { it.inetAddresses.toList() }
                    .filterIsInstance<Inet4Address>()
                    .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    ?.hostAddress ?: ""
            }.getOrDefault("")
        }

        /**
         * 获取日志文件的类型显示名。
         */
        fun getLogTypeDisplayName(file: File): String =
            when {
                file.name.startsWith(CrashHandler.MANUAL_LOG_PREFIX) -> "手动日志"
                file.name.startsWith(CrashHandler.CRASH_LOG_PREFIX) -> "崩溃日志"
                else -> "未知"
            }
    }

/**
 * 日志查看页 UI 状态。
 *
 * @property logFiles 日志文件列表（按时间降序）。
 * @property serverAddress HTTP 服务器地址（`host:port`）。
 * @property isServerReady 服务器是否已启动并获取到端口。
 */
data class LogViewerUiState(
    val logFiles: List<File> = emptyList(),
    val serverAddress: String = "",
    val isServerReady: Boolean = false,
)

/**
 * 一键复制日志的结果。
 *
 * UI 只关心「成功提示什么」和「失败提示什么」，因此把两者收敛成一个密封类型，
 * 避免界面层再去判断异常类型。
 */
sealed interface CopyLogResult {
    /**
     * 复制成功。
     *
     * @property withContent 是否复制了日志正文（false 表示只复制了下载地址）
     * @property chars 写入剪贴板的字符数
     */
    data class Success(
        val withContent: Boolean,
        val chars: Int,
    ) : CopyLogResult {
        /** 用于 Toast 的提示文案。 */
        val message: String
            get() =
                if (withContent) {
                    "已复制日志内容（$chars 字）"
                } else {
                    "已复制下载地址"
                }
    }

    /**
     * 复制失败。
     *
     * @property reason 失败原因，可直接展示给用户
     */
    data class Failure(
        val reason: String,
    ) : CopyLogResult
}
