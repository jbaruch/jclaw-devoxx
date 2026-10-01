package jclaw.domain

import kotlinx.serialization.Serializable

/** Application-owned envelope; the model never chooses the correlation identifiers. */
@Serializable
public data class DeclineSend(
    val callId: String,
    val candidateId: String,
    val eventId: String,
    val organizerName: String,
    val message: String,
)

@Serializable
public data class DeclineReceipt(
    val delivered: Boolean,
    val callId: String,
    val candidateId: String,
    val eventId: String,
    val organizerName: String,
    val deliveredAt: String? = null,
)
