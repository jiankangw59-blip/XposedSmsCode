package io.github.magisk317.smscode.xp.hook.code

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ObservedSmsDeliveryGuardTest {
    private val now = 1_793_611_200_000L
    private val recentSmsWindowMs = 10 * 60 * 1_000L

    @Test
    fun `fresh message from an explicit inbox update is delivered once`() {
        val guard = ObservedSmsDeliveryGuard(recentSmsWindowMs) { now }
        var deliveries = 0

        val delivered = guard.deliver(now - 1_000L) { deliveries++ }

        assertTrue(delivered)
        assertEquals(1, deliveries)
    }

    @Test
    fun `months old unread message cannot be delivered by an inbox update`() {
        val guard = ObservedSmsDeliveryGuard(recentSmsWindowMs) { now }
        val monthsOldDate = now - 240L * 24 * 60 * 60 * 1_000
        var deliveries = 0

        val delivered = guard.deliver(monthsOldDate) { deliveries++ }

        assertFalse(delivered)
        assertEquals(0, deliveries)
    }

    @Test
    fun `invalid and extreme message dates never reach delivery`() {
        val guard = ObservedSmsDeliveryGuard(recentSmsWindowMs) { now }
        var deliveries = 0

        for (messageDate in listOf(0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE)) {
            assertFalse(guard.deliver(messageDate) { deliveries++ }, "messageDate=$messageDate")
        }

        assertEquals(0, deliveries)
    }

    @Test
    fun `future message cannot reach delivery`() {
        val guard = ObservedSmsDeliveryGuard(recentSmsWindowMs) { now }
        var deliveries = 0

        val delivered = guard.deliver(now + 1L) { deliveries++ }

        assertFalse(delivered)
        assertEquals(0, deliveries)
    }

    @Test
    fun `message just inside the window is delivered`() {
        val guard = ObservedSmsDeliveryGuard(recentSmsWindowMs) { now }
        var deliveries = 0

        val delivered = guard.deliver(now - 599_999L) { deliveries++ }

        assertTrue(delivered)
        assertEquals(1, deliveries)
    }

    @Test
    fun `message at the strict cutoff is not delivered`() {
        val guard = ObservedSmsDeliveryGuard(recentSmsWindowMs) { now }
        var deliveries = 0

        val delivered = guard.deliver(now - 600_000L) { deliveries++ }

        assertFalse(delivered)
        assertEquals(0, deliveries)
    }

    @Test
    fun `message just outside the window is not delivered`() {
        val guard = ObservedSmsDeliveryGuard(recentSmsWindowMs) { now }
        var deliveries = 0

        val delivered = guard.deliver(now - 600_001L) { deliveries++ }

        assertFalse(delivered)
        assertEquals(0, deliveries)
    }

    @Test
    fun `queued delivery checks the clock when it executes`() {
        var clock = now
        val guard = ObservedSmsDeliveryGuard(recentSmsWindowMs) { clock }
        val messageDate = clock - 1_000L
        var deliveries = 0
        val queuedDelivery = { guard.deliver(messageDate) { deliveries++ } }

        clock += recentSmsWindowMs

        assertFalse(queuedDelivery())
        assertEquals(0, deliveries)
    }

    @Test
    fun `extreme clock values cannot make an old or future message recent`() {
        var clock = Long.MIN_VALUE
        val guard = ObservedSmsDeliveryGuard(recentSmsWindowMs) { clock }
        var deliveries = 0

        assertFalse(guard.deliver(Long.MAX_VALUE) { deliveries++ })
        clock = Long.MAX_VALUE
        assertFalse(guard.deliver(1L) { deliveries++ })

        assertEquals(0, deliveries)
    }

    @Test
    fun `window must be positive`() {
        for (window in listOf(0L, -1L, Long.MIN_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                ObservedSmsDeliveryGuard(window) { now }
            }
        }
    }
}
