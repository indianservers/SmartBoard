package com.indianservers.smartboard.smartboard.intelligence

import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import java.util.Base64

object SmartBoardSessionMemoryCodec {
    fun encode(memory: SmartBoardSessionMemory): String = buildString {
        appendLine(
            listOf(
                "SBI", 1, pack(memory.boardId), pack(memory.activeProblemId.orEmpty()), memory.lastUpdatedAt,
                memory.userPreferences.outputStyle.name,
                memory.userPreferences.suggestionSnoozedUntil?.toString().orEmpty(),
                memory.userPreferences.suggestionsDisabledForBoard,
                pack(memory.userPreferences.lastGraphRange.orEmpty()),
                pack(memory.userPreferences.lastSelectedSubjectTool.orEmpty()),
            ).joinToString("|"),
        )
        memory.resolvedAmbiguities.forEach { (key, value) -> appendLine("A|${pack(key)}|${pack(value)}") }
        memory.completedActionIds.forEach { appendLine("C|${pack(it)}") }
        memory.dismissedRecommendationIds.forEach { appendLine("D|${pack(it)}") }
        memory.shownHintLevels.forEach { (key, value) -> appendLine("H|${pack(key)}|$value") }
        memory.recentActions.forEach { action ->
            appendLine(
                listOf(
                    "R", pack(action.id), pack(action.actionId), pack(action.targetElementIds.joinToString(",")),
                    action.succeeded, action.occurredAt,
                ).joinToString("|"),
            )
        }
        memory.activeWorkflow?.let { workflow ->
            appendLine(
                listOf(
                    "W", pack(workflow.id), pack(workflow.title), workflow.goal.type.name, workflow.goal.subject.name,
                    pack(workflow.goal.targetElementIds.joinToString(",")), workflow.goal.confidence,
                    pack(workflow.goal.inferredFrom.joinToString(",") { it.name }), workflow.goal.userConfirmed,
                    workflow.requiresUserApproval, pack(workflow.estimatedCapabilities.joinToString(",") { it.name }),
                    pack(workflow.warnings.joinToString("\u001f")),
                ).joinToString("|"),
            )
            workflow.steps.forEach { step ->
                appendLine(
                    listOf(
                        "T", pack(step.id), step.order, step.type.name, pack(step.title), pack(step.tool?.id.orEmpty()),
                        pack(step.inputElementIds.joinToString(",")), pack(step.dependsOnStepIds.joinToString(",")),
                        step.status.name, step.requiresConfirmation, step.canRetry, step.canSkip,
                    ).joinToString("|"),
                )
            }
        }
    }

    fun decode(source: String): SmartBoardSessionMemory? = runCatching {
        val lines = source.lineSequence().filter(String::isNotBlank).toList()
        val header = lines.first().split('|')
        require(header.size >= 10 && header[0] == "SBI" && header[1] == "1")
        val ambiguities = linkedMapOf<String, String>()
        val completed = linkedSetOf<String>()
        val dismissed = linkedSetOf<String>()
        val hints = linkedMapOf<String, Int>()
        val actions = mutableListOf<SmartBoardActionHistoryEntry>()
        var workflowFields: List<String>? = null
        val stepFields = mutableListOf<List<String>>()
        lines.drop(1).forEach { line ->
            val fields = line.split('|')
            when (fields[0]) {
                "A" -> ambiguities[unpack(fields[1])] = unpack(fields[2])
                "C" -> completed += unpack(fields[1])
                "D" -> dismissed += unpack(fields[1])
                "H" -> hints[unpack(fields[1])] = fields[2].toInt()
                "R" -> actions += SmartBoardActionHistoryEntry(
                    unpack(fields[1]), unpack(fields[2]), unpack(fields[3]).split(',').filter(String::isNotBlank),
                    fields[4].toBooleanStrict(), fields[5].toLong(),
                )
                "W" -> workflowFields = fields
                "T" -> stepFields += fields
            }
        }
        val workflow = workflowFields?.let { fields ->
            val goal = SmartBoardGoal(
                enumValueOf(fields[3]), enumValueOf(fields[4]),
                unpack(fields[5]).split(',').filter(String::isNotBlank), fields[6].toFloat(),
                unpack(fields[7]).split(',').filter(String::isNotBlank).mapTo(linkedSetOf()) { enumValueOf<GoalEvidence>(it) },
                userConfirmed = fields[8].toBooleanStrict(),
            )
            SmartBoardWorkflowPlan(
                unpack(fields[1]), unpack(fields[2]), goal,
                stepFields.map { step ->
                    SmartBoardWorkflowStep(
                        unpack(step[1]), step[2].toInt(), enumValueOf(step[3]), unpack(step[4]),
                        unpack(step[5]).takeIf(String::isNotBlank)?.let(::SmartBoardToolReference),
                        unpack(step[6]).split(',').filter(String::isNotBlank),
                        unpack(step[7]).split(',').filter(String::isNotBlank),
                        enumValueOf(step[8]), step[9].toBooleanStrict(), step[10].toBooleanStrict(), step[11].toBooleanStrict(),
                    )
                }.sortedBy(SmartBoardWorkflowStep::order),
                fields[9].toBooleanStrict(),
                unpack(fields[10]).split(',').filter(String::isNotBlank).mapTo(linkedSetOf()) { enumValueOf<SmartBoardCapability>(it) },
                unpack(fields[11]).split('\u001f').filter(String::isNotBlank),
            )
        }
        SmartBoardSessionMemory(
            boardId = unpack(header[2]),
            activeProblemId = unpack(header[3]).takeIf(String::isNotBlank),
            activeWorkflow = workflow,
            resolvedAmbiguities = ambiguities,
            completedActionIds = completed,
            dismissedRecommendationIds = dismissed,
            shownHintLevels = hints,
            recentActions = actions,
            userPreferences = SmartBoardSessionPreferences(
                enumValueOf(header[5]), header[6].toLongOrNull(), header[7].toBooleanStrict(),
                unpack(header[8]).takeIf(String::isNotBlank), unpack(header[9]).takeIf(String::isNotBlank),
            ),
            lastUpdatedAt = header[4].toLong(),
        )
    }.getOrNull()

    private fun pack(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun unpack(value: String) = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
}

