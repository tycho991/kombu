package io.github.tycho991.kombu.data.recipes

import io.github.tycho991.kombu.cookbookclient.ImageSize
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ImageStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val root: File get() = File(folder.root, "recipe-images")
    private val store: ImageStore get() = ImageStore(root)

    @Test
    fun readsWhatItWrote() = runTest {
        store.write(7, ImageSize.FULL, byteArrayOf(1, 2, 3))
        store.write(7, ImageSize.THUMB, byteArrayOf(4))

        assertArrayEquals(byteArrayOf(1, 2, 3), store.file(7, ImageSize.FULL)?.readBytes())
        assertArrayEquals(byteArrayOf(4), store.file(7, ImageSize.THUMB)?.readBytes())
        assertEquals(File(root, "7/full.jpg"), store.file(7, ImageSize.FULL))
        assertNull(store.file(8, ImageSize.FULL))
    }

    @Test
    fun replacesAnOlderImage() = runTest {
        store.write(7, ImageSize.FULL, byteArrayOf(1))
        store.write(7, ImageSize.FULL, byteArrayOf(2))

        assertArrayEquals(byteArrayOf(2), store.file(7, ImageSize.FULL)?.readBytes())
    }

    @Test
    fun anInterruptedWriteKeepsTheOldImageAndLeavesNoPartialFile() = runTest {
        store.write(7, ImageSize.FULL, byteArrayOf(1, 2, 3))

        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking {
                store.write(7, ImageSize.FULL) { output ->
                    output.write(byteArrayOf(9, 9))
                    throw IOException("connection lost")
                }
            }
        }

        assertArrayEquals(byteArrayOf(1, 2, 3), store.file(7, ImageSize.FULL)?.readBytes())
        assertEquals(listOf("full.jpg"), File(root, "7").list()?.toList())
    }

    @Test
    fun anInterruptedFirstWriteLeavesNoFile() = runTest {
        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking {
                store.write(7, ImageSize.THUMB) { throw IOException("connection lost") }
            }
        }

        assertNull(store.file(7, ImageSize.THUMB))
        assertEquals(emptyList<String>(), File(root, "7").list()?.toList())
    }

    @Test
    fun deletesTheImagesOfOneRecipe() = runTest {
        store.write(7, ImageSize.FULL, byteArrayOf(1))
        store.write(8, ImageSize.FULL, byteArrayOf(2))

        store.delete(7)

        assertNull(store.file(7, ImageSize.FULL))
        assertFalse(File(root, "7").exists())
        assertArrayEquals(byteArrayOf(2), store.file(8, ImageSize.FULL)?.readBytes())
    }

    @Test
    fun deletesEveryImage() = runTest {
        store.write(7, ImageSize.FULL, byteArrayOf(1))
        store.write(8, ImageSize.THUMB, byteArrayOf(2))

        store.deleteAll()

        assertFalse(root.exists())
    }

    @Test
    fun reportsADirectoryThatCannotBeCreated() = runTest {
        root.writeText("a file where the directory should be")

        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking { store.write(7, ImageSize.FULL, byteArrayOf(1)) }
        }
    }
}
