package com.caseshot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FileStoreTest {
    @Test
    fun writesPngBytesAndRejectsConflicts() {
        val dir = createTempDir(prefix = "caseshot-test")
        val fileStore = FileStore()
        val bytes = byteArrayOf(1, 2, 3)
        val target = fileStore.writePng(dir, "0001.png", bytes)

        assertTrue(target.exists())
        assertArrayEquals(bytes, target.readBytes())

        try {
            fileStore.writePng(dir, "0001.png", bytes)
            throw AssertionError("Expected conflict")
        } catch (error: IllegalStateException) {
            assertTrue(error.message!!.contains("Target file already exists"))
        } finally {
            dir.deleteRecursively()
        }
    }
}