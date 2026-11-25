package io.github.tycho991.kombu.cookbookclient

/**
 * Recognises image data by its first bytes (design D8). Cookbook serves every recipe image as `image/jpeg`, but
 * keeps the bytes it downloaded, so the body can also be PNG, WebP or GIF. An HTML or SVG page is not an image.
 */
internal object ImageBytes {
    private val JPEG = signature(0xFF, 0xD8, 0xFF)
    private val PNG = signature(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val GIF87 = "GIF87a".toByteArray()
    private val GIF89 = "GIF89a".toByteArray()
    private val RIFF = "RIFF".toByteArray()
    private val WEBP = "WEBP".toByteArray()

    /** The offset of the `WEBP` tag after the `RIFF` tag and the four-byte chunk size. */
    private const val WEBP_TAG_OFFSET = 8

    fun isImage(bytes: ByteArray): Boolean = listOf(JPEG, PNG, GIF87, GIF89).any { bytes.startsWith(it) } ||
        (bytes.startsWith(RIFF) && bytes.startsWith(WEBP, WEBP_TAG_OFFSET))

    private fun ByteArray.startsWith(prefix: ByteArray, offset: Int = 0): Boolean =
        size >= offset + prefix.size && prefix.indices.all { this[offset + it] == prefix[it] }

    private fun signature(vararg bytes: Int) = ByteArray(bytes.size) { bytes[it].toByte() }
}
