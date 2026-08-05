package com.indianservers.smartboard.smartboard.recognition.ensemble

/**
 * Cancellation is an optimization; fingerprint acceptance is the correctness
 * boundary for native providers that return after coroutine cancellation.
 */
class RecognitionRequestGate {
    private data class ActiveRequest(
        val fingerprint: String,
        val generation: Long,
        val cancelled: Boolean,
    )

    private val active = mutableMapOf<String, ActiveRequest>()
    private var nextGeneration = 1L

    @Synchronized
    fun begin(requestId: String, fingerprint: String): Long {
        require(requestId.isNotBlank() && fingerprint.isNotBlank())
        val generation = nextGeneration++
        active[requestId] = ActiveRequest(fingerprint, generation, cancelled = false)
        return generation
    }

    @Synchronized
    fun cancel(requestId: String) {
        active[requestId]?.let { active[requestId] = it.copy(cancelled = true) }
    }

    @Synchronized
    fun accepts(
        requestId: String,
        fingerprint: String,
        generation: Long,
    ): Boolean {
        val request = active[requestId] ?: return false
        return !request.cancelled &&
            request.fingerprint == fingerprint &&
            request.generation == generation
    }

    @Synchronized
    fun finish(requestId: String, generation: Long) {
        if (active[requestId]?.generation == generation) active.remove(requestId)
    }
}
