package com.caseshot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    @Test
    fun conflictResolutionSkip() {
        val dir = createTempDir(prefix = "caseshot-test")
        val fileStore = FileStore()
        val bytes1 = byteArrayOf(1, 2, 3)
        val bytes2 = byteArrayOf(4, 5, 6)

        val result1 = fileStore.writePngWithConflictResolution(dir, "0001.png", bytes1, ConflictResolution.SKIP)
        assertTrue(result1.success)
        assertFalse(result1.skipped)
        assertNotNull(result1.file)

        val result2 = fileStore.writePngWithConflictResolution(dir, "0001.png", bytes2, ConflictResolution.SKIP)
        assertTrue(result2.success)
        assertTrue(result2.skipped)

        assertEquals(3, result1.file?.readBytes()?.size)

        dir.deleteRecursively()
    }

    @Test
    fun conflictResolutionOverwrite() {
        val dir = createTempDir(prefix = "caseshot-test")
        val fileStore = FileStore()
        val bytes1 = byteArrayOf(1, 2, 3)
        val bytes2 = byteArrayOf(4, 5, 6, 7)

        val result1 = fileStore.writePngWithConflictResolution(dir, "0001.png", bytes1, ConflictResolution.OVERWRITE)
        assertTrue(result1.success)
        assertNotNull(result1.file)

        val result2 = fileStore.writePngWithConflictResolution(dir, "0001.png", bytes2, ConflictResolution.OVERWRITE)
        assertTrue(result2.success)
        assertNotNull(result2.file)

        assertEquals(4, result2.file?.readBytes()?.size)

        dir.deleteRecursively()
    }

    @Test
    fun conflictResolutionAsk() {
        val dir = createTempDir(prefix = "caseshot-test")
        val fileStore = FileStore()
        val bytes = byteArrayOf(1, 2, 3)

        val result1 = fileStore.writePngWithConflictResolution(dir, "0001.png", bytes, ConflictResolution.ASK)
        assertTrue(result1.success)

        val result2 = fileStore.writePngWithConflictResolution(dir, "0001.png", bytes, ConflictResolution.ASK)
        assertFalse(result2.success)
        assertTrue(result2.error?.startsWith("CONFLICT:") == true)

        dir.deleteRecursively()
    }
}