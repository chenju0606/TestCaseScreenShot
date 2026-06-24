package com.caseshot

class FrameFreshnessGate {
    private val monitor = Object()
    private var version: Long = 0L

    fun currentVersion(): Long = synchronized(monitor) {
        version
    }

    fun markFrameAvailable(): Long = synchronized(monitor) {
        version += 1L
        monitor.notifyAll()
        version
    }

    fun awaitFrameAfter(baselineVersion: Long, timeoutMs: Long): Long {
        val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(0L)
        synchronized(monitor) {
            while (version <= baselineVersion) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0L) {
                    throw IllegalStateException("No fresh screen frame arrived before timeout.")
                }
                monitor.wait(remaining)
            }
            return version
        }
    }
}
