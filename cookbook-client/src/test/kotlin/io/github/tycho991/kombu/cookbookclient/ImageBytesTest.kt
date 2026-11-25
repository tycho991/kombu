package io.github.tycho991.kombu.cookbookclient

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImageBytesTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `recognises the formats Cookbook serves`() {
        listOf(
            bytes(0xFF, 0xD8, 0xFF, 0xE0),
            bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0),
            "GIF87a..".toByteArray(),
            "GIF89a..".toByteArray(),
            "RIFF".toByteArray() + bytes(0x1A, 0x12, 0x02, 0x00) + "WEBPVP8 ".toByteArray(),
        ).forEach { assertTrue(ImageBytes.isImage(it), it.decodeToString()) }
    }

    @Test
    fun `rejects pages, other RIFF files and truncated signatures`() {
        listOf(
            ByteArray(0),
            bytes(0xFF, 0xD8),
            bytes(0x89, 0x50, 0x4E, 0x47),
            "<svg></svg>".toByteArray(),
            "<!DOCTYPE html>".toByteArray(),
            """{"msg":"no image"}""".toByteArray(),
            "RIFF".toByteArray() + bytes(0x24, 0, 0, 0) + "WAVEfmt ".toByteArray(),
            "RIFF".toByteArray() + bytes(0x24, 0, 0, 0) + "WEB".toByteArray(),
            "GIF90a".toByteArray(),
        ).forEach { assertFalse(ImageBytes.isImage(it), it.decodeToString()) }
    }
}
