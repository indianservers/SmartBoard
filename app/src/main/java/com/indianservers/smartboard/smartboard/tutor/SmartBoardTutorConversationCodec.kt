package com.indianservers.smartboard.smartboard.tutor

import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import java.util.Base64

/** Bounded, local-only persistence. Board content is not copied into the conversation record. */
object SmartBoardTutorConversationCodec {
    fun encode(value: SmartBoardTutorConversation): String = buildString {
        appendLine(
            listOf(
                "SBT", 1, pack(value.boardId), value.activeSubject?.name.orEmpty(), value.activeMode.name,
                pack(value.activeProblemId.orEmpty()), value.updatedAt,
            ).joinToString("|"),
        )
        value.shownHintLevels.forEach { (problemId, level) ->
            appendLine("H|${pack(problemId)}|${level.coerceIn(1, 7)}")
        }
        value.messages.takeLast(100).forEach { message ->
            appendLine(
                listOf(
                    "M", pack(message.id), message.role, pack(message.text), message.subject.name,
                    message.verificationStatus?.name.orEmpty(),
                    pack(message.referencedElementIds.take(32).joinToString(",")), message.createdAt,
                ).joinToString("|"),
            )
        }
    }

    fun decode(source: String): SmartBoardTutorConversation? = runCatching {
        val lines = source.lineSequence().filter(String::isNotBlank).toList()
        val header = lines.first().split('|')
        require(header.size >= 7 && header[0] == "SBT" && header[1] == "1")
        val hints = linkedMapOf<String, Int>()
        val messages = mutableListOf<SmartBoardTutorMessage>()
        lines.drop(1).forEach { line ->
            val fields = line.split('|')
            when (fields.firstOrNull()) {
                "H" -> hints[unpack(fields[1])] = fields[2].toInt().coerceIn(1, 7)
                "M" -> messages += SmartBoardTutorMessage(
                    id = unpack(fields[1]),
                    role = fields[2],
                    text = unpack(fields[3]).take(4_000),
                    subject = enumValueOrDefault(fields[4], SmartBoardSubject.GENERAL),
                    verificationStatus = fields[5].takeIf(String::isNotBlank)
                        ?.let { enumValueOrDefault(it, SmartBoardTutorVerificationStatus.INCONCLUSIVE) },
                    referencedElementIds = unpack(fields[6]).split(',').filter(String::isNotBlank).take(32),
                    createdAt = fields[7].toLong(),
                )
            }
        }
        SmartBoardTutorConversation(
            boardId = unpack(header[2]),
            activeSubject = header[3].takeIf(String::isNotBlank)
                ?.let { enumValueOrDefault(it, SmartBoardSubject.GENERAL) },
            activeMode = enumValueOrDefault(header[4], UnifiedTutorMode.ASK),
            activeProblemId = unpack(header[5]).takeIf(String::isNotBlank),
            messages = messages.takeLast(100),
            shownHintLevels = hints,
            updatedAt = header[6].toLong(),
        )
    }.getOrNull()

    private fun pack(value: String) = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun unpack(value: String) = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T) =
        runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
}
