package com.indianservers.smartboard.smartboard.security

import com.indianservers.smartboard.smartboard.integration.SmartBoardMathAction

data class SmartBoardToolInvocation(val action: SmartBoardMathAction, val source: String)

object SmartBoardSecurityPolicy {
    private const val MAX_EXPRESSION_LENGTH = 4_000
    private const val MAX_CONTEXT_ELEMENTS = 24
    val allowlistedActions: Set<SmartBoardMathAction> = SmartBoardMathAction.entries.toSet()

    /**
     * Imported/recognized text is always data. It can select no application tool by itself.
     * Only a UI-authenticated action supplies [requestedAction].
     */
    fun authorizeUserAction(
        requestedAction: SmartBoardMathAction?,
        selectedContent: List<String>,
        explicitUserGesture: Boolean,
    ): Result<SmartBoardToolInvocation> = runCatching {
        require(explicitUserGesture) { "A direct user action is required." }
        val action = requireNotNull(requestedAction) { "No mathematics action was selected." }
        require(action in allowlistedActions) { "The requested tool is unavailable." }
        require(selectedContent.size in 1..MAX_CONTEXT_ELEMENTS) { "Select between 1 and $MAX_CONTEXT_ELEMENTS Board items." }
        val source = selectedContent.joinToString("\n").trim()
        require(source.isNotBlank() && source.length <= MAX_EXPRESSION_LENGTH) { "Selected mathematics is empty or too long." }
        SmartBoardToolInvocation(action, source)
    }

    fun safeError(error: Throwable): String = when (error) {
        is kotlinx.coroutines.TimeoutCancellationException -> "The mathematics engine timed out. Narrow the expression and retry."
        is IllegalArgumentException, is IllegalStateException -> error.message?.take(240) ?: "The request is invalid."
        else -> "The operation could not be completed safely."
    }
}
