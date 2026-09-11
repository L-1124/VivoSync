package com.app.vivosync.parser.adapter

import android.content.Context
import android.util.Log
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 从拾光仓库按需加载学校、适配器配置和 JS，并按上游 commit 快照缓存。
 */
class AdapterRepository(private val context: Context) {

    private val cacheRoot = File(context.filesDir, "shiguang_cache")
    private val snapshotsRoot = File(cacheRoot, "snapshots")
    private val metadataFile = File(cacheRoot, "metadata.json")
    private val mutex = Mutex()
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            strictMode = false,
            codePointLimit = ROOT_INDEX_MAX_BYTES
        )
    )
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /** 当前生效的网络镜像偏好设置，默认为智能自动优选 */
    @Volatile
    var selectedMirrorNode: MirrorNode = MirrorNode.AUTO


    /**
     * 返回远程快照中的学校列表；远程不可用时使用 APK 内置根索引。
     */
    suspend fun getSchools(forceRefresh: Boolean = false): Result<List<SchoolItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val rootText = runCatching {
                val sha = ensureSnapshot(forceRefresh)
                rootIndexFile(sha).readText(Charsets.UTF_8)
            }.getOrElse { remoteError ->
                runCatching {
                    context.assets.open(BUNDLED_ROOT_INDEX).use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    }
                }.getOrElse {
                    throw remoteError
                }
            }
            parseSchools(rootText)
        }
    }

    /**
     * 用户选择学校后，按需加载该学校的 adapters.yaml。
     */
    suspend fun getAdapters(
        school: SchoolItem,
        forceRefresh: Boolean = false
    ): Result<List<AdapterItem>> = withContext(Dispatchers.IO) {
        runCatching {
            mutex.withLock {
                val sha = ensureSnapshotLocked(forceRefresh = false)
                val folder = validatedResourceFolder(school.resourceFolder)
                val target = snapshotFile(sha, "resources/$folder/adapters.yaml")
                if (forceRefresh || !target.isFile) {
                    val text = downloadTextWithFallback(
                        candidateRawUrls(sha, "resources/$folder/adapters.yaml"),
                        ADAPTER_INDEX_MAX_BYTES
                    )
                    val adapters = parseAdapters(text)
                    atomicWrite(target, text)
                    adapters
                } else {
                    parseAdapters(target.readText(Charsets.UTF_8))
                }
            }
        }
    }

    /**
     * 用户执行导入时，按需下载并缓存当前 commit 下的适配 JS。
     */
    suspend fun loadScriptContent(
        school: SchoolItem,
        adapter: AdapterItem
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            mutex.withLock {
                val sha = ensureSnapshotLocked(forceRefresh = false)
                val folder = validatedResourceFolder(school.resourceFolder)
                val scriptPath = validatedRelativePath(adapter.scriptPath)
                val target = snapshotFile(sha, "resources/$folder/$scriptPath")
                if (target.isFile) {
                    return@withLock target.readText(Charsets.UTF_8).also {
                        require(it.isNotBlank()) { "缓存的适配脚本为空" }
                    }
                }

                val script = downloadTextWithFallback(
                    candidateRawUrls(sha, "resources/$folder/$scriptPath"),
                    SCRIPT_MAX_BYTES
                )
                require(script.isNotBlank()) { "远程适配脚本为空" }
                atomicWrite(target, script)
                script
            }
        }
    }

    private suspend fun ensureSnapshot(forceRefresh: Boolean): String = mutex.withLock {
        ensureSnapshotLocked(forceRefresh)
    }

    private fun ensureSnapshotLocked(forceRefresh: Boolean): String {
        val metadata = readMetadata()
        val currentSha = metadata.currentSha
        val currentRoot = currentSha?.let(::rootIndexFile)
        val checkedAt = metadata.lastCheckedAt
        val cacheFresh = System.currentTimeMillis() - checkedAt < CACHE_TTL_MS

        if (!forceRefresh && currentSha != null && currentRoot?.isFile == true && cacheFresh) {
            return currentSha
        }

        return runCatching {
            val remoteSha = resolveMainCommitSha()
            val remoteRoot = rootIndexFile(remoteSha)
            if (remoteSha == currentSha && remoteRoot.isFile) {
                writeMetadata(metadata.copy(lastCheckedAt = System.currentTimeMillis()))
                return@runCatching remoteSha
            }

            if (!remoteRoot.isFile) {
                val rootText = downloadTextWithFallback(
                    candidateRawUrls(remoteSha, "index/root_index.yaml"),
                    ROOT_INDEX_MAX_BYTES
                )
                parseSchools(rootText)
                atomicWrite(remoteRoot, rootText)
            }

            writeMetadata(
                CacheMetadata(
                    currentSha = remoteSha,
                    previousSha = currentSha.takeIf { it != remoteSha } ?: metadata.previousSha,
                    lastCheckedAt = System.currentTimeMillis()
                )
            )
            cleanupSnapshots(remoteSha, currentSha)
            remoteSha
        }.getOrElse { error ->
            if (currentSha != null && currentRoot?.isFile == true) {
                currentSha
            } else {
                val previousSha = metadata.previousSha
                val previousRoot = previousSha?.let(::rootIndexFile)
                if (previousSha != null && previousRoot?.isFile == true) {
                    previousSha
                } else {
                    throw error
                }
            }
        }
    }

    private fun readMetadata(): CacheMetadata {
        if (!metadataFile.isFile) return CacheMetadata()
        return runCatching {
            json.decodeFromString<CacheMetadata>(metadataFile.readText(Charsets.UTF_8))
        }.getOrDefault(CacheMetadata())
    }

    private fun writeMetadata(metadata: CacheMetadata) {
        val text = json.encodeToString(metadata)
        atomicWrite(metadataFile, text)
    }

    private fun resolveMainCommitSha(): String {
        val apis = when (selectedMirrorNode) {
            MirrorNode.GITHUB -> listOf(GITHUB_COMMIT_API_URL, GITEE_COMMIT_API_URL)
            else -> listOf(GITEE_COMMIT_API_URL, GITHUB_COMMIT_API_URL)
        }
        var lastError: Throwable? = null
        for (apiUrl in apis) {
            try {
                val body = downloadText(apiUrl, COMMIT_RESPONSE_MAX_BYTES)
                val sha = json.parseToJsonElement(body).jsonObject["sha"]?.jsonPrimitive?.content.orEmpty()
                if (SHA_REGEX.matches(sha)) {
                    return sha
                } else {
                    Log.w(TAG, "从接口 $apiUrl 获取的 SHA 格式不符合预期: $sha")
                }
            } catch (e: Exception) {
                Log.w(TAG, "从接口 $apiUrl 获取 commit SHA 失败: ${e.message}")
                lastError = e
            }
        }
        throw lastError ?: IOException("所有可用接口均无法获取远程仓库 commit SHA")
    }

    private fun parseSchools(text: String): List<SchoolItem> {
        return yaml.decodeFromString<RootIndexDocument>(text).schools.onEach { school ->
            require(school.id.isNotBlank()) { "学校 ID 为空" }
            require(school.name.isNotBlank()) { "学校名称为空" }
            require(school.initial.isNotBlank()) { "学校首字母为空" }
            validatedResourceFolder(school.resourceFolder)
        }
    }

    private fun parseAdapters(text: String): List<AdapterItem> {
        return yaml.decodeFromString<AdapterIndexDocument>(text).adapters.onEach { adapter ->
            require(adapter.adapterId.isNotBlank()) { "适配器 ID 为空" }
            require(adapter.adapterName.isNotBlank()) { "适配器名称为空" }
            require(adapter.category.isNotBlank()) { "适配器类别为空" }
            validatedRelativePath(adapter.scriptPath)
        }
    }

    private fun validatedResourceFolder(value: String): String {
        require(value.isNotBlank()) { "资源目录不能为空" }
        require(!value.contains("..") && !value.contains('/') && !value.contains('\\') && !value.contains(':')) {
            "非法资源目录: $value"
        }
        require(RESOURCE_FOLDER_REGEX.matches(value)) { "无效的资源目录: $value" }
        return value
    }

    private fun validatedRelativePath(value: String): String {
        require(value.isNotBlank()) { "脚本路径不能为空" }
        require(!value.contains('\\') && !value.contains(':') && !value.startsWith('/') && !value.contains("://")) {
            "非法脚本路径: $value"
        }
        val segments = value.split('/')
        require(segments.none { it == ".." || it == "." || it.isEmpty() }) {
            "脚本路径禁止越级访问或空分段: $value"
        }
        require(RELATIVE_PATH_REGEX.matches(value)) { "无效的脚本路径: $value" }
        return value
    }

    private fun snapshotFile(sha: String, relativePath: String): File {
        val snapshot = File(snapshotsRoot, sha)
        val target = File(snapshot, relativePath)
        val rootPath = snapshot.canonicalFile.toPath()
        require(target.canonicalFile.toPath().startsWith(rootPath)) { "缓存路径越界" }
        return target
    }

    private fun rootIndexFile(sha: String): File = snapshotFile(sha, "root_index.yaml")

    private fun candidateRawUrls(sha: String, relativePath: String): List<String> {
        val jsdelivr = "https://cdn.jsdelivr.net/gh/XingHeYuZhuan/shiguang_warehouse@$sha/$relativePath"
        val gitee = "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse/raw/$sha/$relativePath"
        val ghfast = "https://ghfast.top/https://raw.githubusercontent.com/XingHeYuZhuan/shiguang_warehouse/$sha/$relativePath"
        val github = "https://raw.githubusercontent.com/XingHeYuZhuan/shiguang_warehouse/$sha/$relativePath"

        return when (selectedMirrorNode) {
            MirrorNode.AUTO -> listOf(jsdelivr, gitee, ghfast, github)
            MirrorNode.GITEE -> listOf(gitee, jsdelivr, ghfast, github)
            MirrorNode.JSDELIVR -> listOf(jsdelivr, gitee, ghfast, github)
            MirrorNode.GHFAST -> listOf(ghfast, jsdelivr, gitee, github)
            MirrorNode.GITHUB -> listOf(github, jsdelivr, gitee, ghfast)
        }
    }

    private fun downloadTextWithFallback(urls: List<String>, maxBytes: Int): String {
        var lastError: Throwable? = null
        for (url in urls) {
            try {
                return downloadText(url, maxBytes)
            } catch (e: Exception) {
                Log.w(TAG, "从镜像节点下载资源失败 [$url]: ${e.message}")
                lastError = e
            }
        }
        throw lastError ?: IOException("所有可用镜像节点均无法下载目标资源")
    }

    private fun downloadText(url: String, maxBytes: Int): String {
        var currentUrl = url
        var redirects = 0
        val maxRedirects = 5

        while (true) {
            val parsedUrl = URL(currentUrl)
            require(parsedUrl.protocol == "https") { "仅支持 HTTPS 协议: $currentUrl" }
            require(ALLOWED_HOSTS.contains(parsedUrl.host)) { "非法的远程主机: ${parsedUrl.host}" }

            val connection = parsedUrl.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false // 手动校验重定向以严格防止非白名单越权
            connection.setRequestProperty("Accept", "application/json, text/plain, application/yaml")
            connection.setRequestProperty("User-Agent", USER_AGENT)

            try {
                val status = connection.responseCode
                if (status in 300..399) {
                    redirects++
                    require(redirects <= maxRedirects) { "重定向次数过多: $currentUrl" }
                    val location = connection.getHeaderField("Location")
                    require(!location.isNullOrBlank()) { "重定向响应缺少 Location 标头" }
                    currentUrl = URL(parsedUrl, location).toString()
                    continue
                }

                require(status == HttpURLConnection.HTTP_OK) { "远程请求失败: HTTP $status ($currentUrl)" }
                val declaredLength = connection.contentLengthLong
                require(declaredLength < 0 || declaredLength <= maxBytes) { "远程文件超过大小限制" }

                connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= maxBytes) { "远程文件超过大小限制: $total > $maxBytes" }
                        output.write(buffer, 0, read)
                    }
                    return output.toString(Charsets.UTF_8.name())
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun atomicWrite(target: File, content: String) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        try {
            temporary.writeText(content, Charsets.UTF_8)
            runCatching {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            }.getOrElse {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            temporary.delete()
            throw e
        }
    }

    private fun cleanupSnapshots(currentSha: String, previousSha: String?) {
        val keep = setOfNotNull(currentSha, previousSha)
        snapshotsRoot.listFiles()
            ?.filter { it.isDirectory && it.name !in keep }
            ?.forEach(File::deleteRecursively)
    }

    private companion object {
        const val TAG = "AdapterRepository"
        const val BUNDLED_ROOT_INDEX = "shiguang/root_index.yaml"
        const val GITEE_COMMIT_API_URL = "https://gitee.com/api/v5/repos/XingHeYuZhuan-gh/shiguang_warehouse/commits/main"
        const val GITHUB_COMMIT_API_URL = "https://api.github.com/repos/XingHeYuZhuan/shiguang_warehouse/commits/main"
        const val USER_AGENT = "VivoSync/1.0"
        const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 15_000
        const val COMMIT_RESPONSE_MAX_BYTES = 64 * 1024
        const val ROOT_INDEX_MAX_BYTES = 1024 * 1024
        const val ADAPTER_INDEX_MAX_BYTES = 256 * 1024
        const val SCRIPT_MAX_BYTES = 2 * 1024 * 1024

        val ALLOWED_HOSTS = setOf(
            "cdn.jsdelivr.net",
            "gitee.com",
            "raw.giteeusercontent.com",
            "raw.githubusercontent.com",
            "api.github.com",
            "ghfast.top",
            "ghproxy.net"
        )

        val SHA_REGEX = Regex("^[0-9a-f]{40}$")
        val RESOURCE_FOLDER_REGEX = Regex("^[A-Za-z0-9_-]+$")
        val RELATIVE_PATH_REGEX = Regex("^[A-Za-z0-9._/-]+$")
    }
}
