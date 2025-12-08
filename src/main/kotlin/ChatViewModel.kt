import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class Message(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class ChatViewModel(private val apiKey: String) {
    private val client = OpenAIClient(apiKey)
    val messages = mutableStateListOf<Message>()
    val isLoading = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)
    val joke = mutableStateOf<String?>(null)
    val systemPrompt = mutableStateOf("""
        You are professional chef. You can prepare a list of recipes, but only after you gather necessary information.
        Information you need to do this is:
            1. Ingredients in user fridge.
            2. Spices user have.
            3. Kitchenware user owns.
            4. Kitchen appliances user have.
            5. Time he have for cooking.
        Ask questions gradually, step by step. Any response must contain only one question, to not confuse user.
        After you gather all this information, respond to user in valid JSON format with the following structure:
        {"answer": "<final result with list of recipes with calories and nutritional values>","joke" :<joke about one of the recipes>"}

        Always make final response with list of recipes with this JSON structure. Do not include any text outside the JSON. Masking is obsolete. JSON must must not contain any formating symbols, just plain string as it be in any REST API response.
    """.trimIndent())
    val temperature = mutableStateOf(1.0f)

    private val scope = CoroutineScope(Dispatchers.IO)

    fun sendMessage(content: String) {
        if (content.isBlank()) return

        messages.add(Message(content, isUser = true))
        isLoading.value = true
        errorMessage.value = null

        scope.launch {
            try {
                val chatMessages = messages.map {
                    ChatMessage(
                        role = if (it.isUser) "user" else "assistant",
                        content = it.content
                    )
                }

                val result = client.sendMessage(chatMessages, systemPrompt.value, temperature.value)

                result.onSuccess { response ->
                    messages.add(Message(response.answer, isUser = false))
                    joke.value = response.joke
                }.onFailure { error ->
                    errorMessage.value = "Error: ${error.message}"
                }
            } catch (e: Exception) {
                errorMessage.value = "Error: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun clearChat() {
        messages.clear()
        errorMessage.value = null
    }

    fun getChatHistory(): String {
        return messages.joinToString("\n\n") { message ->
            val role = if (message.isUser) "User" else "Assistant"
            "<$role>: ${message.content}"
        }
    }

    fun cleanup() {
        client.close()
    }
}