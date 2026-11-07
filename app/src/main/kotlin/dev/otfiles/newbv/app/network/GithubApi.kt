package dev.otfiles.newbv.app.network

import dev.otfiles.newbv.app.network.entity.GithubRelease
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLProtocol
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * GitHub Releases API 封装。
 *
 * 用于检查更新和下载 APK。仓库地址：`OTFiles/newBV`（本 fork 自己的 Release）。
 * 下载通过 `ghfast.top` 代理加速国内访问。
 */
object GithubApi {
    private const val OWNER = "OTFiles"
    private const val REPO = "newBV"

    private val json =
        Json {
            coerceInputValues = true
            ignoreUnknownKeys = true
            prettyPrint = true
        }

    private val client: HttpClient =
        HttpClient(OkHttp) {
            BrowserUserAgent()
            install(ContentNegotiation) {
                json(json)
            }
            install(ContentEncoding) {
                deflate(1.0f)
                gzip(0.9f)
            }
            defaultRequest {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "api.github.com"
                }
            }
        }

    /** 获取最新 Release（非预发布）。 */
    suspend fun getLatestBuild(): GithubRelease {
        val response = client.get("repos/$OWNER/$REPO/releases/latest").bodyAsText()
        checkErrorMessage(response)
        return json.decodeFromString(response)
    }

    /** 获取所有 Releases。 */
    suspend fun getReleases(
        pageSize: Int = 30,
        page: Int = 1,
    ): List<GithubRelease> {
        val response =
            client
                .get("repos/$OWNER/$REPO/releases") {
                    parameter("per_page", pageSize)
                    parameter("page", page)
                }.bodyAsText()
        checkErrorMessage(response)
        return json.decodeFromString(response)
    }

    /**
     * 下载 Release 中的 APK 附件。
     *
     * @param release Release 信息。
     * @param file 目标文件路径。
     * @param onProgress 下载进度回调（已下载字节, 总字节）。
     */
    suspend fun downloadUpdate(
        release: GithubRelease,
        file: File,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ) {
        // 只认 .apk，避免误把 mapping、源码包等附件当成安装包。
        // 上游用文件名里的 release/alpha 区分渠道，本 fork 的产物名形如
        // newBV_210_1.3.0.r210.abc1234_release.apk，同样满足前缀匹配；
        // 但为了兼容后续改名，这里放宽为「以 .apk 结尾」并优先 release。
        val apkAssets = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val downloadUrl =
            (
                apkAssets.firstOrNull { it.name.contains("release") }
                    ?: apkAssets.firstOrNull()
            )?.browserDownloadUrl
                ?: throw IllegalStateException("Didn't find download url")

        client
            .prepareRequest {
                url(toGhProxyUrl(downloadUrl))
                onDownload { downloaded, total ->
                    onProgress(downloaded, total ?: 0)
                }
            }.execute { response ->
                response.bodyAsChannel().copyTo(file.outputStream())
            }
    }

    private fun checkErrorMessage(data: String) {
        val responseElement = json.parseToJsonElement(data)
        if (responseElement !is JsonObject) return
        val responseObject = responseElement.jsonObject
        if (responseObject["message"] != null) {
            error(responseObject["message"]!!.jsonPrimitive.content)
        }
    }

    private fun toGhProxyUrl(originalUrl: String): String = "https://ghfast.top/$originalUrl"
}
