package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.network.NodeInfoDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NodeCatalogCacheTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }
    private var now = 1_000_000L

    private fun cache(root: File = temp.root): NodeCatalogCache = NodeCatalogCache(
        rootDir = File(root, "node_catalog"),
        json = json,
        now = { now },
    )

    private fun catalog(name: String): NodeCatalog = NodeCatalog(mapOf(name to NodeInfoDto()))

    @Test
    fun ttlHitReturnsCachedCatalog() = runTest {
        val cache = cache()
        cache.save("server-a", "http:host:8188", catalog("KSampler"))

        val loaded = cache.load("server-a", "http:host:8188")

        assertNotNull(loaded)
        assertEquals(true, loaded?.contains("KSampler"))
    }

    @Test
    fun freshMemoryHitDoesNotNeedDisk() = runTest {
        val cache = cache()
        cache.save("server-a", "http:host:8188", catalog("KSampler"))
        File(temp.root, "node_catalog/server-a")
            .listFiles()
            .orEmpty()
            .forEach { it.delete() }

        assertNotNull(cache.load("server-a", "http:host:8188"))
    }

    @Test
    fun expiredCacheIsIgnored() = runTest {
        val cache = cache()
        cache.save("server-a", "http:host:8188", catalog("KSampler"))
        now += 5 * 60 * 1000L + 1

        assertNull(cache.load("server-a", "http:host:8188"))
    }

    @Test
    fun differentServerDoesNotSeeCache() = runTest {
        val cache = cache()
        cache.save("server-a", "http:host:8188", catalog("KSampler"))

        assertNull(cache.load("server-b", "http:host:8188"))
    }

    @Test
    fun endpointChangeDoesNotReuseOldCatalog() = runTest {
        val cache = cache()
        cache.save("server-a", "http:host:8188", catalog("KSampler"))

        assertNull(cache.load("server-a", "http:other:8188"))
    }

    @Test
    fun corruptedFileIsIgnoredWithoutDeletingOtherServerCache() = runTest {
        val cache = cache()
        cache.save("server-a", "http:host:8188", catalog("KSampler"))
        cache.save("server-b", "http:host:8188", catalog("LoadImage"))
        val cacheFile = File(temp.root, "node_catalog/server-a")
            .listFiles()
            .orEmpty()
            .first { it.isFile }
        cacheFile.writeText("{broken")

        // 新实例绕过内存层，验证损坏文件只影响自己且不会误删其他 server。
        val reloaded = cache()
        assertNull(reloaded.load("server-a", "http:host:8188"))
        assertNotNull(reloaded.load("server-b", "http:host:8188"))
    }
}
