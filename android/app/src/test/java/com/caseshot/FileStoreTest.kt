package com.caseshot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileStoreTest {
    @Test
    fun writePngAtomicallyReturnsConflictWithoutOverwriting() {
        val dir = createTempDir(prefix = "caseshot-test")
        val fileStore = FileStore()
        val bytes1 = byteArrayOf(1, 2, 3)
        val bytes2 = byteArrayOf(4, 5, 6)

        val target = fileStore.targetFile(dir, "0001.png")
        val result1 = fileStore.writePngAtomically(target, bytes1, overwrite = false)
        assertTrue(result1 is ScreenshotSaveResult.Saved)

        val result2 = fileStore.writePngAtomically(target, bytes2, overwrite = false)
        assertTrue(result2 is ScreenshotSaveResult.Failed)

        assertArrayEquals(bytes1, target.readBytes())

        dir.deleteRecursively()
    }

    @Test
    fun existingScreenshotPolicySkip() {
        val dir = createTempDir(prefix = "caseshot-test")
        val fileStore = FileStore()
        val bytes1 = byteArrayOf(1, 2, 3)

        val target = fileStore.targetFile(dir, "0001.png")
        val result1 = fileStore.writePngAtomically(target, bytes1, overwrite = false)
        assertTrue(result1 is ScreenshotSaveResult.Saved)

        val result2 = fileStore.resolveExistingScreenshot(target, ExistingScreenshotPolicy.SKIP)
        assertTrue(result2 is ScreenshotSaveResult.Skipped)

        assertArrayEquals(bytes1, target.readBytes())

        dir.deleteRecursively()
    }

    @Test
    fun writePngAtomicallyOverwritesExistingFileWhenAllowed() {
        val dir = createTempDir(prefix = "caseshot-test")
        val fileStore = FileStore()
        val bytes1 = byteArrayOf(1, 2, 3)
        val bytes2 = byteArrayOf(4, 5, 6, 7)

        val target = fileStore.targetFile(dir, "0001.png")
        val result1 = fileStore.writePngAtomically(target, bytes1, overwrite = false)
        assertTrue(result1 is ScreenshotSaveResult.Saved)

        val result2 = fileStore.writePngAtomically(target, bytes2, overwrite = true)
        assertTrue(result2 is ScreenshotSaveResult.Overwritten)

        assertArrayEquals(bytes2, target.readBytes())

        dir.deleteRecursively()
    }

    @Test
    fun existingScreenshotPolicyAskReturnsFailureForCallerDecision() {
        val dir = createTempDir(prefix = "caseshot-test")
        val fileStore = FileStore()
        val bytes = byteArrayOf(1, 2, 3)

        val target = fileStore.targetFile(dir, "0001.png")
        val result1 = fileStore.writePngAtomically(target, bytes, overwrite = false)
        assertTrue(result1 is ScreenshotSaveResult.Saved)

        val result2 = fileStore.resolveExistingScreenshot(target, ExistingScreenshotPolicy.ASK)
        assertTrue(result2 is ScreenshotSaveResult.Failed)

        dir.deleteRecursively()
    }
}
