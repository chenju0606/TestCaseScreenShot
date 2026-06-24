package com.caseshot

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FrameFreshnessGateTest {
    @Test
    fun waitsForAFrameNewerThanTheBaselineVersion() {
        val gate = FrameFreshnessGate()
        val baseline = gate.currentVersion()
        val waiterStarted = CountDownLatch(1)

        val waiter = Thread {
            waiterStarted.countDown()
            gate.awaitFrameAfter(baseline, 1_000L)
        }

        waiter.start()
        waiterStarted.await(1, TimeUnit.SECONDS)
        Thread.sleep(50)
        gate.markFrameAvailable()
        waiter.join(1_000L)

        assertEquals(1L, gate.currentVersion())
    }

    @Test(expected = IllegalStateException::class)
    fun timesOutWhenNoNewFrameArrivesAfterBaselineVersion() {
        val gate = FrameFreshnessGate()

        gate.awaitFrameAfter(gate.currentVersion(), 10L)
    }
}
