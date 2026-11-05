package io.github.xiaowu20245.nekopaint.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComfyImageUrlBuilderTest {

    @Test
    fun buildsViewUrlWithSubfolderAndType() {
        val url = ComfyImageUrlBuilder.build(
            baseUrl = "http://192.168.1.10:8188/",
            filename = "ComfyUI_00001_.png",
            subfolder = "Anima",
            type = "output",
        )

        assertEquals(
            "http://192.168.1.10:8188/view?filename=ComfyUI_00001_.png&subfolder=Anima&type=output",
            url,
        )
    }

    @Test
    fun encodesSpacesChineseAndReservedCharacters() {
        val url = ComfyImageUrlBuilder.build(
            baseUrl = "http://127.0.0.1:8188/",
            filename = "结果 #1 ?.png",
            subfolder = "我的 图片",
            type = "temp",
        )

        assertEquals(
            "http://127.0.0.1:8188/view?filename=%E7%BB%93%E6%9E%9C%20%231%20%3F.png" +
                "&subfolder=%E6%88%91%E7%9A%84%20%E5%9B%BE%E7%89%87&type=temp",
            url,
        )
    }

    @Test
    fun supportsIpv6Host() {
        val url = ComfyImageUrlBuilder.build(
            baseUrl = "http://[fe80::1]:8188/",
            filename = "a.png",
            subfolder = "",
            type = "output",
        )

        assertEquals(
            "http://[fe80::1]:8188/view?filename=a.png&subfolder=&type=output",
            url,
        )
    }

    @Test
    fun returnsNullForInvalidBaseUrl() {
        assertNull(
            ComfyImageUrlBuilder.build(
                baseUrl = "not a url",
                filename = "a.png",
                subfolder = "",
                type = "output",
            ),
        )
    }
}
