package data

import kotlinx.serialization.Serializable

@Serializable
data class UsageInfo(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val estimatedInputCost: Double,
    val estimatedOutputCost: Double,
    val estimatedCost: Double,
    val requestTimeMs: Long
)
