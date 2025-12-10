import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val DefaultJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = false
    encodeDefaults = true
}

@Serializable
data class LlmMessage(
    val answer: String,
    val joke: String?,
    val usage: UsageInfo? = null
)

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

data class SessionStats(
    val totalInputTokens: Int = 0,
    val totalOutputTokens: Int = 0,
    val totalCost: Double = 0.0,
    val lastRequestTimeMs: Long = 0
) {
    val totalTokens: Int
        get() = totalInputTokens + totalOutputTokens
}