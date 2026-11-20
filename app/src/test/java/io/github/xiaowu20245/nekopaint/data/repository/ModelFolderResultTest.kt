package io.github.xiaowu20245.nekopaint.data.repository

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ModelFolderResultTest {

    @Test
    fun networkSuccessAnd404MapToNetworkAndUnsupported() {
        val success = ModelFolderResultPolicy.snapshot(
            cached = listOf("old.safetensors"),
            network = Result.success(listOf("new.safetensors")),
        )
        assertEquals(listOf("new.safetensors"), success.models)
        assertEquals(ModelSource.NETWORK, success.source)

        val notFound = ModelFolderResultPolicy.snapshot(
            cached = listOf("cached.safetensors"),
            network = Result.failure(http404()),
        )
        assertEquals(listOf("cached.safetensors"), notFound.models)
        assertEquals(ModelSource.UNSUPPORTED, notFound.source)
    }

    @Test
    fun apiCreationFailureMapsEveryFolderToError() {
        val result = ModelFolderResultPolicy.errorResult(
            folders = setOf("checkpoints", "loras"),
            cached = mapOf("checkpoints" to listOf("cached.safetensors")),
        )

        assertEquals(ModelSource.ERROR, result.folders.getValue("checkpoints").source)
        assertEquals(
            listOf("cached.safetensors"),
            result.folders.getValue("checkpoints").models,
        )
        assertEquals(ModelSource.ERROR, result.folders.getValue("loras").source)
    }

    @Test
    fun genericFailureFallsBackToCacheOrError() {
        val cached = ModelFolderResultPolicy.snapshot(
            cached = listOf("cached.safetensors"),
            network = Result.failure(IllegalStateException("timeout")),
        )
        assertEquals(ModelSource.CACHE, cached.source)
        assertEquals(listOf("cached.safetensors"), cached.models)

        val noCache = ModelFolderResultPolicy.snapshot(
            cached = emptyList(),
            network = Result.failure(IllegalStateException("timeout")),
        )
        assertEquals(ModelSource.ERROR, noCache.source)
        assertEquals(emptyList<String>(), noCache.models)
    }

    private fun http404(): HttpException = HttpException(
        Response.error<Any>(404, "".toResponseBody()),
    )
}
