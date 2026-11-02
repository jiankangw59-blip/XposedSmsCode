package io.github.magisk317.smscode.xp.hook.code

import android.content.Context
import io.github.magisk317.smscode.xp.hook.helper.XscHookHostCapabilities
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.smscode.xposed.hook.telephony.SmsInboxObserver as SharedSmsInboxObserver
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * XSC wiring for the shared inbox observer.
 *
 * Scanning, routing repair and telemetry live in core. XSC supplies its host
 * capabilities ([XscHookHostCapabilities] over the runtime bridge) and what to
 * do with a scanned record.
 */
internal class SmsInboxObserver(
    private val pluginContext: Context,
    private val phoneContext: Context,
) {
    private val queryExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val deliveryGuard = ObservedSmsDeliveryGuard(RECENT_SMS_WINDOW_MS)
    private val observedSmsHandler = ObservedSmsHandler(
        pluginContext = pluginContext,
        phoneContext = phoneContext,
        actionExecutor = queryExecutor,
        roleStateLogger = { eventId ->
            XLog.w(message = "Diag observer sms role: event_id=%s", eventId)
        },
    )

    private val delegate = SharedSmsInboxObserver(
        pluginContext = pluginContext,
        phoneContext = phoneContext,
        recordHandler = { record ->
            // A row-specific provider change can return an old unread message.
            // Validate at this host boundary before it reaches automated actions.
            if (!deliveryGuard.deliver(record.date) { observedSmsHandler.handle(record) }) {
                XLog.w(message = "Diag observer skip: stale or invalid date sms_id=%d", record.smsId)
            }
        },
        capabilities = XscHookHostCapabilities,
    )

    fun register() {
        delegate.setRoutingRepair { record -> observedSmsHandler.repairRouting(record) }
        delegate.register()
    }

    fun unregister() {
        delegate.unregister()
    }

    private companion object {
        // Match the shared observer's recent-message window. Its separate
        // 24-hour routing-repair callback deliberately bypasses this guard.
        const val RECENT_SMS_WINDOW_MS = 10 * 60 * 1000L
    }
}
