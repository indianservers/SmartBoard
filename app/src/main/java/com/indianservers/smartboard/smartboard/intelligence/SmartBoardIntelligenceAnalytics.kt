package com.indianservers.smartboard.smartboard.intelligence

/**
 * Privacy-safe Phase 4 analytics boundary. Payloads are deliberately typed and may never contain
 * recognized text, equations, handwriting, images, tutor content, or learner identifiers.
 * A product analytics adapter can consume this interface when one is available.
 */
enum class SmartBoardIntelligenceEventType {
    MODE_SELECTED,
    RECOMMENDATIONS_SHOWN,
    RECOMMENDATION_ACCEPTED,
    RECOMMENDATION_DISMISSED,
    WORKFLOW_STARTED,
    WORKFLOW_COMPLETED,
    WORKFLOW_CANCELLED,
    CLARIFICATION_REQUESTED,
    VERIFICATION_COMPLETED,
    OFFLINE_FALLBACK_USED,
    INTELLIGENT_VISUAL_OPENED,
    PRACTICE_RECOMMENDED,
}

data class SmartBoardIntelligenceEvent(
    val type: SmartBoardIntelligenceEventType,
    val subject: String,
    val mode: String,
    val capability: String? = null,
    val succeeded: Boolean? = null,
    val occurredAt: Long,
)

fun interface SmartBoardIntelligenceAnalytics {
    fun record(event: SmartBoardIntelligenceEvent)
}

/** Bounded, process-local fallback. It performs no upload and stores no Board content. */
class BoundedLocalSmartBoardIntelligenceAnalytics(
    private val capacity: Int = 100,
) : SmartBoardIntelligenceAnalytics {
    private val events = ArrayDeque<SmartBoardIntelligenceEvent>()

    override fun record(event: SmartBoardIntelligenceEvent) {
        synchronized(events) {
            events.addLast(event)
            while (events.size > capacity.coerceAtLeast(1)) events.removeFirst()
        }
    }

    fun snapshot(): List<SmartBoardIntelligenceEvent> = synchronized(events) { events.toList() }
}
