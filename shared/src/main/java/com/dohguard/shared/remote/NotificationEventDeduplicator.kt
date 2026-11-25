package com.dohguard.shared.remote

/**
 * Gerencia eventos de notificação para o MonitorDad Parent com deduplicação por chave
 * e janela mínima de repetição para evitar spam quando regras ou estados oscilam.
 */
class NotificationEventDeduplicator(
    private val minIntervalMillis: Long = 60_000L,
    private val maxStoredEvents: Int = 60
) {
    private val lastSeenByKey = mutableMapOf<String, Long>()
    private val events = mutableListOf<ParentNotificationEvent>()

    fun recordIfNew(
        event: ParentNotificationEvent,
        nowMillis: Long = event.timestampMillis
    ): Boolean {
        val prev = lastSeenByKey[event.dedupKey]
        if (prev != null && (nowMillis - prev) < minIntervalMillis) {
            return false
        }
        if (events.any { it.eventId == event.eventId }) {
            return false
        }
        lastSeenByKey[event.dedupKey] = nowMillis
        events.add(0, event)
        if (events.size > maxStoredEvents) {
            while (events.size > maxStoredEvents) {
                events.removeAt(events.lastIndex)
            }
        }
        return true
    }

    fun getEvents(): List<ParentNotificationEvent> = events.toList()

    fun markAllRead() {
        for (i in events.indices) {
            events[i] = events[i].copy(isRead = true)
        }
    }

    fun clear() {
        lastSeenByKey.clear()
        events.clear()
    }
}
