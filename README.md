# AI Advent Chat

A feature-rich desktop chat application built with Kotlin and Jetbrains Compose that enables interaction with multiple AI language models. Developed as an incremental Advent Calendar project, showcasing advanced features including conversation compaction, RAG (Retrieval-Augmented Generation), and MCP (Model Context Protocol) tool integration.

![Version](https://img.shields.io/badge/version-1.0.0-blue)
![Kotlin](https://img.shields.io/badge/kotlin-2.2.20-purple)
![Platform](https://img.shields.io/badge/platform-Windows%20%7C%20macOS%20%7C%20Linux-lightgrey)

## Features

### Core Chat Functionality
- **Multi-Vendor Support**: Seamlessly switch between Claude (Anthropic) and Perplexity AI
- **Real-time Streaming**: Live message streaming for responsive user experience
- **Session Management**: Create, load, delete, and rename chat sessions
- **Persistent Storage**: Automatic chat history persistence to disk
- **Dark Theme UI**: Material Design 3 with custom dark color scheme

### Advanced Message Management
- **Auto-Compaction**: Automatically summarizes first 10 messages after every 10 new messages
- **Manual Compaction**: User-triggered conversation summarization to reduce context length
- **Message Expansion**: Click to expand compacted message placeholders
- **Token & Cost Tracking**: Real-time monitoring of input/output tokens and estimated costs
- **Copy History**: Export chat history to clipboard

### RAG & Semantic Search
- **Local Embeddings**: Generate embeddings using Ollama (nomic-embed-text model)
- **Document Chunking**: Fixed-size with overlap or paragraph-based chunking strategies
- **Semantic Search**: Query embeddings to retrieve relevant context
- **MMR Algorithm**: Maximal Marginal Relevance for diverse, non-redundant results
- **Configurable Search**: Adjustable similarity threshold and top-K results
- **LRU Cache**: In-memory embedding cache (5-file limit) for performance

### MCP Integration (Model Context Protocol)
- **JSON-RPC 2.0**: Full protocol implementation for tool communication
- **Multi-Server Support**: Manage multiple MCP server processes
- **Built-in Shell Server**: Execute shell commands via MCP interface
- **Pipeline Mode**: Sequential multi-step tool execution (up to 10 iterations)
- **Tool Use Tracking**: Complete tool request/response flow in conversations

### Model Configuration
- **Multi-Model Selection**: Choose from various Claude and Perplexity models
- **Temperature Control**: Adjust creativity (0.0-1.0 scale)
- **Max Tokens**: Configure response length limits
- **Custom System Prompts**: Set context-specific instructions

## Recent Improvements

### Unified State Pattern Refactoring
The application has been refactored to use a modern unified state management pattern:

**What Changed:**
- Consolidated 26 separate `mutableStateOf()` fields into single `ChatUiState` data class
- All state updates now use immutable `copy()` operations
- Added helper methods for safe state modifications

**Benefits:**
- ✅ **Type Safety**: Compile-time verification of all state fields
- ✅ **Thread Safety**: Immutable updates prevent race conditions
- ✅ **Better Performance**: Compose can optimize recompositions more effectively
- ✅ **Easier Testing**: Can snapshot entire application state at once
- ✅ **Maintainability**: Clear, centralized state structure

**Technical Details:**
```kotlin
// Before: Scattered state fields
val isLoading = mutableStateOf(false)
val errorMessage = mutableStateOf<String?>(null)
// ... 24 more fields

// After: Unified state
val uiState = mutableStateOf(ChatUiState(
    isLoading = false,
    errorMessage = null,
    // ... all 26 fields in one place
))
```

## Technologies & Frameworks

### Core Stack
- **Language**: Kotlin 2.2.20 (JVM)
- **Build System**: Gradle with Kotlin DSL
- **UI Framework**: Jetbrains Compose Desktop 1.8.0
- **Design System**: Material Design 3
- **Concurrency**: Kotlin Coroutines 1.10.1

### Networking & Serialization
- **HTTP Client**: Ktor 3.0.2 (with content negotiation & logging)
- **Serialization**: Kotlin Serialization JSON

### AI Providers
- **Anthropic Claude API**: Primary LLM provider
- **Perplexity API**: Secondary LLM provider
- **Ollama**: Local embedding generation

## Architecture

The application follows a **three-layer MVVM architecture** with **unified state management**:

### Presentation Layer (`presentation/`)
- **App.kt**: Main UI composition with theme, vendor switcher, and session sidebar
- **ChatViewModel.kt**: State management using unified state pattern, message flow, vendor coordination
  - **Unified State Pattern**: Single immutable `ChatUiState` data class containing all 26 UI state fields
  - **Immutable Updates**: State changes use `copy()` for thread-safe, predictable updates
  - **Performance Optimized**: Lists kept separate for efficient recomposition
- **UI Components**: SessionSidebar, McpSettingsDialog, EmbeddingsDialog
- **Message Models**: Message.kt (UI) / InternalMessage.kt (internal state)

### Data/Business Logic Layer (`data/`)
- **API Clients**: Abstract ApiClient interface with ClaudeClient and PerplexityClient implementations
- **Embedding System**:
  - OllamaClient for local embedding generation
  - EmbeddingSearch with MMR algorithm
  - FileChunking for document preprocessing
  - EmbeddingStorage for persistence
- **MCP Integration**:
  - McpServerManager for process lifecycle
  - McpPipeline for sequential tool execution
  - McpModels for JSON-RPC 2.0 protocol
- **Storage**: SessionStorage (JSON), AppSettingsStorage

### MCP Tools (`mcp/`)
- **ShellCommandMcpServer**: Built-in MCP server for shell command execution

### Data Models
- **ChatMessage**: Polymorphic content (text/blocks) compatible with Claude API
- **ContentBlock**: Tool use, tool results, text blocks
- **LlmMessage**: Standardized LLM responses
- **SessionData**: Complete session state structure

## Installation & Setup

### Prerequisites
- **Java Development Kit (JDK)**: 11 or higher
- **API Keys**:
  - `CLAUDE_API_KEY` environment variable (required for Anthropic)
  - `PERPLEXITY_API_KEY` environment variable (optional for Perplexity)
- **Ollama** (optional): For local embedding generation
  - Install from [ollama.ai](https://ollama.ai)
  - Pull the embedding model: `ollama pull nomic-embed-text`

### Environment Setup

**Windows (PowerShell):**
```powershell
$env:CLAUDE_API_KEY="your-api-key-here"
$env:PERPLEXITY_API_KEY="your-api-key-here"
```

**macOS/Linux (Bash):**
```bash
export CLAUDE_API_KEY="your-api-key-here"
export PERPLEXITY_API_KEY="your-api-key-here"
```

**Permanent Setup:**
Add the exports to your shell profile (`~/.bashrc`, `~/.zshrc`, or Windows Environment Variables).

### Running the Application

#### Option 1: Pre-built Package
Download and install the distribution package for your platform:
- Windows: `.msi` installer
- macOS: `.dmg` disk image
- Linux: `.deb` package

#### Option 2: Run from Source
```bash
./gradlew run
```

## Configuration

### Storage Locations
All configuration and data files are stored in `~/.ai-advent-chat/`:

```
~/.ai-advent-chat/
├── sessions/              # Chat session JSON files
│   └── metadata.json      # Session metadata
├── embeddings/            # Embedding vector storage
└── app-settings.json      # Application settings
```

### MCP Server Configuration

Configure MCP servers via the UI (Settings → MCP Servers):

```kotlin
{
  "id": "unique-id",
  "name": "My MCP Server",
  "command": "node",              // or "python", "java", etc.
  "args": ["/path/to/server.js"],
  "env": {                        // Environment variables
    "API_KEY": "secret"
  },
  "enabled": true
}
```

The built-in **Shell Command Server** is automatically configured.

### Embedding Configuration

Adjust embedding settings in the UI:
- **Ollama Endpoint**: Default `http://localhost:11434`
- **Model**: Default `nomic-embed-text`
- **Chunking Strategy**: Fixed-size (1000 chars, 200 overlap) or paragraph-based
- **Search Settings**: Top-K (default 3), similarity threshold (default 0.5)

### Default Settings
- **Auto-compaction frequency**: Every 10 messages
- **Pipeline max iterations**: 10
- **Embedding cache size**: 5 files (LRU)
- **Default temperature**: 0.7
- **Default max tokens**: 4096

## Usage

### Basic Chat
1. Launch the application
2. Select a vendor (Claude/Perplexity) and model from the dropdown
3. Type your message in the input field
4. Press Enter or click Send

### Session Management
- **New Session**: Click the "+" button in the sidebar
- **Load Session**: Click on any session in the sidebar
- **Rename Session**: Right-click → Rename
- **Delete Session**: Right-click → Delete

### Advanced Features

#### Conversation Compaction
- **Auto**: Happens automatically after every 10 messages
- **Manual**: Click "Compact" button to summarize current conversation
- **Expand**: Click on compacted message placeholders to view original content

#### Embeddings & RAG
1. Click "Embeddings" button
2. Select files to embed
3. Choose chunking strategy
4. Generate embeddings
5. Enable "Use Embeddings" in settings
6. Ask questions - relevant chunks are automatically retrieved

#### MCP Tools
1. Open Settings → MCP Servers
2. Add or configure MCP servers
3. Enable desired servers
4. Use natural language to request tool execution
5. Tools execute in pipeline mode (up to 10 steps)

#### Custom System Prompts
1. Open Settings
2. Enter custom instructions in the System Prompt field
3. Applies to all new messages in the session

## Building from Source

### Build Distributable Package
```bash
# Create package for current OS
./gradlew packageDistributionForCurrentOS

# Outputs to: build/compose/binaries/main/
```

### Custom Gradle Tasks
```bash
# Test MCP client implementation
./gradlew testMcp

# Run MCP shell server standalone
./gradlew runMcpShellServer
```

### Build Configuration
- **Package Name**: AI Chat
- **Version**: 1.0.0
- **Main Class**: `MainKt`
- **Target Formats**: DMG (macOS), MSI (Windows), Deb (Linux), Exe (Windows)

## Project Structure

```
AI-Advent-Chat/
├── src/main/kotlin/
│   ├── Main.kt                      # Application entry point
│   ├── data/                        # Business logic & data layer
│   │   ├── ApiClient.kt             # Abstract LLM client interface
│   │   ├── ClaudeClient.kt          # Anthropic Claude implementation
│   │   ├── PerplexityClient.kt      # Perplexity API implementation
│   │   ├── OllamaClient.kt          # Local embeddings client
│   │   ├── EmbeddingSearch.kt       # Semantic search with MMR
│   │   ├── FileChunking.kt          # Document chunking strategies
│   │   ├── EmbeddingStorage.kt      # Embedding persistence
│   │   ├── SessionStorage.kt        # Session persistence
│   │   ├── McpPipeline.kt           # Multi-tool execution
│   │   ├── mcp/                     # MCP protocol implementation
│   │   │   ├── McpServerManager.kt
│   │   │   └── McpModels.kt
│   │   └── model/                   # Data models
│   │       ├── ChatMessage.kt
│   │       ├── ContentBlock.kt
│   │       ├── LlmMessage.kt
│   │       └── SessionData.kt
│   ├── presentation/                # UI layer
│   │   ├── App.kt                   # Main composable
│   │   ├── ChatViewModel.kt         # MVVM ViewModel
│   │   ├── SessionSidebar.kt        # Session management UI
│   │   ├── McpSettingsDialog.kt     # MCP configuration UI
│   │   └── EmbeddingsDialog.kt      # Embedding generation UI
│   └── mcp/                         # Built-in MCP tools
│       └── ShellCommandMcpServer.kt
├── build.gradle.kts                 # Build configuration
└── gradle.properties                # Gradle properties
```

## Development Notes

### Key Implementation Details

**Unified State Pattern:**
The application uses a unified state management pattern for predictable, type-safe UI state handling:
```kotlin
data class ChatUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val systemPrompt: String = "",
    val temperature: Float = 0.1f,
    val selectedModel: String = "claude-sonnet-4-20250514",
    // ... 26 total fields
)

class ChatViewModel {
    val uiState = mutableStateOf(ChatUiState())

    private fun updateState(block: ChatUiState.() -> ChatUiState) {
        uiState.value = block(uiState.value)
    }
}
```

Benefits:
- **Single Source of Truth**: All UI state centralized in one data class
- **Immutability**: Updates use `copy()` preventing accidental mutations
- **Type Safety**: Compile-time verification of all state fields
- **Performance**: Compose optimizes recompositions with immutable state
- **Testability**: Easy to snapshot and verify entire state
- **Maintainability**: Clear structure for all application state

**Message Flow:**
1. User input → ChatViewModel
2. ViewModel → ApiClient (Claude/Perplexity)
3. Streaming response → Real-time UI updates
4. Tool calls → MCP Pipeline execution
5. Embeddings → Semantic search → Context injection
6. Final response → SessionStorage persistence

**Compaction Strategy:**
- Triggered after 10 messages (auto) or on-demand (manual)
- Uses LLM to summarize first 10 messages into single message
- Replaces original messages with placeholder
- Original content preserved for expansion

**MMR Algorithm:**
- Balances relevance and diversity in search results
- Configurable λ parameter (relevance vs. diversity trade-off)
- Prevents redundant context in RAG queries

**Thread Safety:**
- **Unified State Pattern**: Immutable state updates prevent race conditions
- **Atomic State Updates**: `updateState()` ensures single point of state modification
- **Mutex Locks**: Concurrent session operations protected by mutex
- **Coroutine-Safe**: All state operations safe across coroutine contexts
- **Atomic Operations**: Embedding cache uses atomic operations

### Future Enhancements (Commented Out)
- **Task Reminders**: Experimental feature (see TaskReminderManager.kt)
- **Additional Vendors**: Architecture supports easy addition of new LLM providers
- **Advanced RAG**: Potential for hybrid search (keyword + semantic)

## Troubleshooting

### Common Issues

**"No API keys found" error:**
- Ensure CLAUDE_API_KEY or PERPLEXITY_API_KEY environment variables are set
- Restart the application after setting environment variables

**Ollama connection failed:**
- Verify Ollama is running: `ollama list`
- Check endpoint configuration in settings
- Ensure nomic-embed-text model is pulled: `ollama pull nomic-embed-text`

**MCP server not responding:**
- Check server command and arguments in MCP Settings
- Verify server binary is executable
- Review server logs in the UI

**Build errors:**
- Ensure JDK 11+ is installed
- Run `./gradlew clean build` to clear cache
- Check internet connection for dependency downloads

## License

This project is provided as-is for educational and personal use.

## Acknowledgments

- Built with [Jetbrains Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- Uses [Anthropic Claude API](https://www.anthropic.com/api)
- Uses [Perplexity API](https://www.perplexity.ai)
- Embedding generation via [Ollama](https://ollama.ai)
- MCP protocol by Anthropic

---

**Version**: 1.0.0
**Last Updated**: January 2026
**Kotlin Version**: 2.2.20
**Compose Version**: 1.8.0