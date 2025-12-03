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

                val result = client.sendMessage(chatMessages)

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

    fun cleanup() {
        client.close()
    }
}