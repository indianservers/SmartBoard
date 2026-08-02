package com.indianservers.smartboard.smartboard.multisubject

import com.indianservers.smartboard.smartboard.models.SmartBoardSubject

enum class SmartBoardMultiSubjectEventType {
    SUBJECT_MODE_SELECTED,
    AUTO_DETECT_USED,
    SUBJECT_DETECTED,
    DETECTION_CORRECTED,
    DETECTION_UNRESOLVED,
    CONCEPT_SELECTED,
    MIXED_SUBJECT_BOARD_USED,
    SUBJECT_LOCK_CHANGED,
    SUBJECT_RECOGNITION_INVOKED,
    OFFLINE_DETECTION_USED,
}

data class SmartBoardMultiSubjectEvent(
    val type: SmartBoardMultiSubjectEventType,
    val subject: SmartBoardSubject?,
    val confidenceLevel: String?,
    val cacheHit: Boolean?,
    val latencyBucket: String?,
    val occurredAt: Long,
)

fun interface SmartBoardMultiSubjectAnalytics {
    fun record(event: SmartBoardMultiSubjectEvent)
}

/** Content-free local fallback; an approved product analytics adapter can replace it later. */
class BoundedLocalSmartBoardMultiSubjectAnalytics(private val capacity: Int = 100) : SmartBoardMultiSubjectAnalytics {
    private val events = ArrayDeque<SmartBoardMultiSubjectEvent>()
    override fun record(event: SmartBoardMultiSubjectEvent) {
        synchronized(events) {
            events.addLast(event)
            while (events.size > capacity.coerceAtLeast(1)) events.removeFirst()
        }
    }
    fun snapshot(): List<SmartBoardMultiSubjectEvent> = synchronized(events) { events.toList() }
}
