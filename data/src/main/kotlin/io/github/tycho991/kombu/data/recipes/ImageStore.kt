package io.github.tycho991.kombu.data.recipes

import io.github.tycho991.kombu.cookbookclient.ImageSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Recipe images on the device (design D8): `<root>/<localId>/full.jpg` and `thumb.jpg`.
 *
 * The app passes `noBackupFilesDir/recipe-images` as [root]: Android neither backs that directory up nor evicts
 * it. Writes go to a temporary file that is renamed into place, so a reader sees the old image or the new one,
 * never a partial file.
 */
class ImageStore(private val root: File) {
    /** The stored image of the recipe with [localId] in [size], or `null` when there is none. */
    fun file(localId: Long, size: ImageSize): File? = target(localId, size).takeIf(File::isFile)

    /** Stores [bytes] as the image of the recipe with [localId] in [size], replacing an older one. */
    suspend fun write(localId: Long, size: ImageSize, bytes: ByteArray) {
        write(localId, size) { it.write(bytes) }
    }

    /**
     * Stores what [writeContent] writes as the image of the recipe with [localId] in [size]. If [writeContent]
     * throws, nothing changes: the temporary file is removed and an older image stays in place.
     */
    suspend fun write(localId: Long, size: ImageSize, writeContent: (OutputStream) -> Unit) {
        withContext(Dispatchers.IO) {
            val target = target(localId, size)
            val directory = checkNotNull(target.parentFile)
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create $directory")
            val temporary = File.createTempFile("${size.fileName}-", ".tmp", directory)
            try {
                temporary.outputStream().use(writeContent)
                moveIntoPlace(temporary, target)
            } finally {
                temporary.delete()
            }
        }
    }

    /** Deletes the images of the recipe with [localId]. */
    suspend fun delete(localId: Long) {
        withContext(Dispatchers.IO) { File(root, localId.toString()).deleteRecursively() }
    }

    /** Deletes every stored image. */
    suspend fun deleteAll() {
        withContext(Dispatchers.IO) { root.deleteRecursively() }
    }

    private fun target(localId: Long, size: ImageSize): File = File(File(root, localId.toString()), size.fileName)

    private fun moveIntoPlace(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (ignored: AtomicMoveNotSupportedException) {
            // Same directory, so this only happens on unusual file systems; a plain replace is the best left.
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private val ImageSize.fileName: String
        get() =
            when (this) {
                ImageSize.FULL -> "full.jpg"
                ImageSize.THUMB -> "thumb.jpg"
            }
}
