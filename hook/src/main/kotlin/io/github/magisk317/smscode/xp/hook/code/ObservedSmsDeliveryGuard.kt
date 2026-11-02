package io.github.magisk317.smscode.xp.hook.code

/** Keeps inbox update notifications from delivering expired verification messages. */
internal class ObservedSmsDeliveryGuard(
    private val recentSmsWindowMs: Long,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    init {
        require(recentSmsWindowMs > 0L) { "The recent SMS window must be positive" }
    }

    fun deliver(messageDate: Long, action: () -> Unit): Boolean {
        val now = currentTimeMillis()
        // Check these before subtraction so extreme/future dates cannot overflow
        // into a seemingly recent age. Match the scanner's strict date > cutoff.
        if (messageDate <= 0L || messageDate > now || now - messageDate >= recentSmsWindowMs) {
            return false
        }
        action()
        return true
    }
}
