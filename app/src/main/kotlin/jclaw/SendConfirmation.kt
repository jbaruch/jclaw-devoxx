package jclaw

/** Sending needs an exact affirmative. Other substantive replies are human critic feedback. */
internal sealed interface SendReply {
    data object Send : SendReply
    data object Hold : SendReply
    data class FollowUp(val text: String) : SendReply
}

internal fun sendReply(answer: String): SendReply {
    val text = answer.trim()
    if (text.lowercase() in setOf("send", "y", "yes")) return SendReply.Send
    return when (text.lowercase().trimEnd('.', '!')) {
        "", "n", "no", "hold", "cancel", "stop", "no thanks", "no thank you", "don't send", "do not send" -> SendReply.Hold
        else -> SendReply.FollowUp(text)
    }
}

enum class HumanChoice { APPROVE, REJECT, HOLD }

data class HumanReview(val choice: HumanChoice, val feedback: String)

/** A substantive answer critiques the current candidate; it never becomes a new request. */
internal fun humanReview(answer: String): HumanReview = when (val reply = sendReply(answer)) {
    SendReply.Send -> HumanReview(HumanChoice.APPROVE, answer.trim())
    SendReply.Hold -> HumanReview(HumanChoice.HOLD, answer.trim())
    is SendReply.FollowUp -> HumanReview(HumanChoice.REJECT, reply.text)
}
