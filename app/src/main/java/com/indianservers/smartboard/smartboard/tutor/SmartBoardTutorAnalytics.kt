package com.indianservers.smartboard.smartboard.tutor

import com.indianservers.smartboard.smartboard.models.SmartBoardSubject

enum class SmartBoardTutorEventType {
    TUTOR_OPENED,
    SUBJECT_CONTEXT_SELECTED,
    MODE_SELECTED,
    HINT_REQUESTED,
    NEXT_STEP_REQUESTED,
    VERIFICATION_RUN,
    FIRST_ERROR_DETECTED,
    OUTPUT_INSERTED,
    OFFLINE_FALLBACK,
    CROSS_SUBJECT_CONFIRMED,
}

data class SmartBoardTutorEvent(
    val type: SmartBoardTutorEventType,
    val subject: SmartBoardSubject?,
    val supportingSubjectCount: Int,
    val mode: UnifiedTutorMode?,
    val verificationStatus: SmartBoardTutorVerificationStatus?,
    val selectedElementCount: Int,
    val occurredAt: Long,
) {
    init {
        require(supportingSubjectCount in 0..5)
        require(selectedElementCount in 0..32)
    }
}

/**
 * Content-free local boundary. It deliberately cannot accept handwriting, recognized text,
 * images, tutor messages, equations, labels, or imported document content.
 */
class BoundedLocalSmartBoardTutorAnalytics(private val maximumEvents: Int = 200) {
    init { require(maximumEvents in 10..1_000) }
    private val values = ArrayDeque<SmartBoardTutorEvent>()
    val events: List<SmartBoardTutorEvent> get() = values.toList()

    fun record(event: SmartBoardTutorEvent) {
        values += event
        while (values.size > maximumEvents) values.removeFirst()
    }
}
