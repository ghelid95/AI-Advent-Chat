package data

data class SessionStats(
    val totalInputTokens: Int = 0,
    val totalOutputTokens: Int = 0,
    val totalCost: Double = 0.0,
    val lastRequestTimeMs: Long = 0
) {
    val totalTokens: Int
        get() = totalInputTokens + totalOutputTokens
}
