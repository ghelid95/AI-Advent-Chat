import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import data.Vendor
import presentation.App
import presentation.ChatViewModel

fun getApiKey(vendor: Vendor): String? {
    val envVarName = when (vendor) {
        Vendor.ANTHROPIC -> "CLAUDE_API_KEY"
        Vendor.PERPLEXITY -> "PERPLEXITY_API_KEY"
        Vendor.OLLAMA -> return ""  // Ollama doesn't need an API key (local)
    }
    val value = System.getenv(envVarName)
    println("Checking environment variable: $envVarName = ${if (value != null) "[SET]" else "[NOT SET]"}")
    return value
}

fun main() = application {
    // Try to get Claude API key first, fall back to Perplexity, then Ollama (local)
    val claudeApiKey = getApiKey(Vendor.ANTHROPIC)
    val perplexityApiKey = getApiKey(Vendor.PERPLEXITY)
    val ollamaApiKey = getApiKey(Vendor.OLLAMA)  // Always returns "" for Ollama

    val (initialVendor, initialApiKey) = when {
        claudeApiKey != null -> Vendor.ANTHROPIC to claudeApiKey
        perplexityApiKey != null -> Vendor.PERPLEXITY to perplexityApiKey
        ollamaApiKey != null -> Vendor.OLLAMA to ollamaApiKey  // Fallback to local Ollama
        else -> null to null
    }

    if (initialApiKey == null) {
        // Show error window if no API keys are found
        Window(
            onCloseRequest = ::exitApplication,
            title = "API Key Error",
            state = rememberWindowState(width = 600.dp, height = 500.dp)
        ) {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Missing API Keys",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "No API keys found in environment variables",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            "Windows: Set via System Properties or IDE Run Configuration",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2A2A2A), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                "Option 1: IntelliJ Run Configuration",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF03DAC6)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Run -> Edit Configurations -> Environment variables",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = Color.LightGray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "CLAUDE_API_KEY=sk-ant-...",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                "Option 2: Windows System Environment",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF03DAC6)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "setx CLAUDE_API_KEY \"sk-ant-...\"",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "(Restart IDE after using setx)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = ::exitApplication,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Exit")
                        }
                    }
                }
            }
        }
    } else {
        val viewModel = remember { ChatViewModel(initialApiKey, initialVendor!!) }

        Window(
            onCloseRequest = {
                viewModel.cleanup()
                exitApplication()
            },
            title = "AI Chat",
            state = rememberWindowState(width = 900.dp, height = 700.dp)
        ) {
            App(viewModel, ::getApiKey)
        }
    }
}
