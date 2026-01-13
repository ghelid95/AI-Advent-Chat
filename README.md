# AI Advent Chat

A feature-rich desktop chat application built with Kotlin and Jetbrains Compose that enables interaction with multiple AI language models. Developed as an incremental Advent Calendar project, showcasing advanced features including conversation compaction, RAG (Retrieval-Augmented Generation), Git integration, Code Assistant, and MCP (Model Context Protocol) tool integration.

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

### Code Assistant & Project Context 🆕
- **Working Directory**: Configure project directory for intelligent code analysis
- **File Search**: Pattern-based (glob) and fuzzy file name matching with exclusion filters
- **Content Search**: Regex-based search within files with context lines
- **Project Analysis**: Auto-detect project type (Gradle, Maven, npm, Python, Rust, Go)
- **Auto-Context Enrichment**: Automatically includes relevant code context based on detected references
- **Smart Reference Detection**: Identifies file names, paths, class names, function names, and error lines in queries
- **Configurable**: Adjust max files, patterns, and file size limits

### Chat Commands 🆕
Execute powerful commands directly in chat:
- **`/help`** - Generate comprehensive project report (architecture, file structure, code samples)
- **`/search <query>`** - Search files and code content instantly
- **`/analyze <file>`** - Get detailed AI-powered analysis of specific files
- **`/context on|off`** - Toggle auto-context enrichment
- **`/git status`** - Show repository status with branch and changes
- **`/git diff [file]`** - View uncommitted changes
- **`/git log`** - Display commit history
- **`/git branch`** - Show current branch and remote info

### Git Integration 🆕
**Three complementary ways to work with git:**

1. **Auto-Context Enrichment** (Automatic)
   - Detects git-related keywords in queries ("branch", "commit", "changes", etc.)
   - Automatically includes repository status, diffs, and commit history
   - Smart keyword detection with 20+ git-related terms

2. **MCP Tools** (LLM-Driven)
   - `git_status` - Repository status and file changes
   - `git_diff` - View uncommitted changes
   - `git_log` - Commit history with authors and dates
   - `git_branch` - Current branch and remote information
   - `git_show` - Detailed commit inspection
   - LLM can autonomously decide when to use git tools

3. **Chat Commands** (Manual)
   - Direct `/git` commands for explicit git operations
   - Formatted output with clear status indicators

**Features:**
- Smart caching (status: 30s, diffs: 60s, history: 5min)
- Configurable size limits (max diff lines, max commits)
- Error handling (git not installed, not a repo, timeouts)
- Cross-platform support (Windows, macOS, Linux)

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
- **Built-in Servers**:
  - **Shell Command Server**: Execute shell commands via MCP interface
  - **Git Server**: Complete git operations as MCP tools 🆕
- **Auto-Registration**: Servers automatically configured on first startup
- **Pipeline Mode**: Sequential multi-step tool execution (up to 10 iterations)
- **Tool Use Tracking**: Complete tool request/response flow in conversations

### Model Configuration
- **Multi-Model Selection**: Choose from various Claude and Perplexity models
- **Temperature Control**: Adjust creativity (0.0-1.0 scale)
- **Max Tokens**: Configure response length limits
- **Custom System Prompts**: Set context-specific instructions

## Recent Improvements

### Git Integration (Latest)
Complete git repository awareness with three complementary access methods:
- **MCP Tools**: LLM can autonomously interact with git repositories
- **Auto-Context**: Smart keyword detection automatically enriches queries
- **Manual Commands**: Direct `/git` commands for explicit control
- Comprehensive error handling and cross-platform support

### Code Assistant System
Intelligent code-aware conversation with:
- Project type detection and analysis
- File and content search with glob patterns
- Auto-context enrichment based on reference detection
- Configurable file inclusion/exclusion patterns
- Integration with chat commands for direct file analysis

### Chat Commands Framework
Hybrid execution model with:
- **Client-side commands**: Instant results for search and status
- **LLM-assisted commands**: Intelligent formatting for help and analysis
- Extensible command parser and handler architecture
- Special handling for settings modifications

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

### AI Providers & Tools
- **Anthropic Claude API**: Primary LLM provider
- **Perplexity API**: Secondary LLM provider
- **Ollama**: Local embedding generation
- **Git CLI**: Repository operations

## Architecture

The application follows a **three-layer MVVM architecture** with **unified state management**:

### Presentation Layer (`presentation/`)
- **App.kt**: Main UI composition with theme, vendor switcher, and session sidebar
- **ChatViewModel.kt**: State management using unified state pattern, message flow, vendor coordination
  - **Unified State Pattern**: Single immutable `ChatUiState` data class containing all 26 UI state fields
  - **Immutable Updates**: State changes use `copy()` for thread-safe, predictable updates
  - **Performance Optimized**: Lists kept separate for efficient recomposition
- **UI Components**: SessionSidebar, McpSettingsDialog, EmbeddingsDialog, AssistantSettingsDialog
- **Message Models**: Message.kt (UI) / InternalMessage.kt (internal state)

### Data/Business Logic Layer (`data/`)
- **API Clients**: Abstract ApiClient interface with ClaudeClient and PerplexityClient implementations
- **Code Assistant System**:
  - FileSearchService for pattern-based file discovery
  - ContentSearchService for code content search
  - ProjectAnalysisService for project type detection
  - AutoContextService for smart context enrichment
- **Git Integration**:
  - GitRepositoryService for git command execution with caching
  - GitContextService for LLM context formatting
- **Commands System**:
  - CommandParser for command interpretation
  - CommandExecutor for handler orchestration
  - Individual handlers (Help, Search, Analyze, Context, Git)
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
- **GitMcpServer**: Built-in MCP server for git operations 🆕

### Data Models
- **ChatMessage**: Polymorphic content (text/blocks) compatible with Claude API
- **ContentBlock**: Tool use, tool results, text blocks
- **LlmMessage**: Standardized LLM responses
- **SessionData**: Complete session state structure
- **GitModels**: GitStatus, GitDiff, GitCommit, GitContext

## Installation & Setup

### Prerequisites
- **Java Development Kit (JDK)**: 11 or higher
- **Git**: Required for git integration features
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

### Code Assistant Configuration

Configure via UI (Code Assistant icon in toolbar):

- **Working Directory**: Path to your project
- **Auto-Context**: Enable/disable automatic context enrichment
- **Max Files**: Number of files to include in context (default: 5)
- **File Patterns**:
  - Include: `*.kt`, `*.java`, `*.py`, `*.js`, `*.ts`, `*.md`
  - Exclude: `**/build/**`, `**/node_modules/**`, `**/.git/**`, `**/.idea/**`
- **Max File Size**: Maximum file size to read (default: 100,000 chars)

### Git Integration Configuration

Configure via Code Assistant settings:

- **Git Enabled**: Enable/disable git integration
- **Auto-Detect**: Automatically detect git keywords and include context
- **Include Diffs**: Include uncommitted changes in context
- **Include History**: Include recent commits in context
- **Max Diff Lines**: Maximum diff lines to include (default: 500)
- **Max Commits**: Number of commits in history (default: 5)

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

**Built-in Servers** (automatically configured):
- **Shell Command Server**: Execute shell commands
- **Git Server**: Git repository operations

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
- **Git cache TTL**: Status 30s, Diffs 60s, History 5min

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

#### Code Assistant
1. Click Code Assistant icon in toolbar
2. Set working directory to your project
3. Enable auto-context enrichment
4. Configure file patterns as needed
5. Ask questions about your code - context is automatically included!

**Example queries:**
- "What does the ChatViewModel class do?"
- "Show me the implementation of sendMessage"
- "Explain the error in line 42"
- "How is the project structured?"

#### Chat Commands

**Project Information:**
```
/help
```
Generates comprehensive project report with architecture, file structure, and code samples.

**Search:**
```
/search ChatViewModel
/search sendMessage
```
Instantly finds files and code content matching your query.

**File Analysis:**
```
/analyze src/main/kotlin/ChatViewModel.kt
```
Provides AI-powered analysis of the specified file including purpose, components, and code quality.

**Context Control:**
```
/context off    # Disable auto-context enrichment
/context on     # Enable auto-context enrichment
```

**Git Operations:**
```
/git status              # Show repository status
/git diff                # View all uncommitted changes
/git diff ChatViewModel.kt  # View specific file changes
/git log                 # Show commit history
/git branch              # Show current branch
```

#### Git Integration

**Auto-Detection (Automatic):**
Just ask git-related questions naturally:
- "What branch am I on?"
- "What files have I modified?"
- "Show me recent changes"
- "What's in the last commit?"

Context is automatically enriched when git keywords are detected!

**MCP Tools (LLM-Driven):**
The LLM can autonomously call git tools when appropriate:
- Checking repository status before suggesting changes
- Viewing diffs to understand recent modifications
- Inspecting commit history for context
- Combining git info with code analysis

**Manual Commands:**
Use explicit `/git` commands for direct control:
- `/git status` - Formatted status output
- `/git diff [file]` - View specific or all diffs
- `/git log` - Commit history with authors
- `/git branch` - Branch and remote information

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
2. Add or configure MCP servers (or use built-in servers)
3. Enable desired servers
4. Use natural language to request tool execution
5. Tools execute in pipeline mode (up to 10 steps)

**Built-in MCP Tools:**
- **Shell Commands**: execute_command, read_file, write_file, list_directory
- **Git Operations**: git_status, git_diff, git_log, git_branch, git_show

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

# Run MCP git server standalone
./gradlew runMcpGitServer
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
│   │   ├── CodeAssistantSettings.kt # Code assistant configuration
│   │   ├── codeassistant/           # Code assistant services
│   │   │   ├── FileSearchService.kt
│   │   │   ├── ContentSearchService.kt
│   │   │   ├── ProjectAnalysisService.kt
│   │   │   └── AutoContextService.kt
│   │   ├── commands/                # Chat command system
│   │   │   ├── CommandParser.kt
│   │   │   ├── CommandExecutor.kt
│   │   │   ├── HelpCommandHandler.kt
│   │   │   ├── SearchCommandHandler.kt
│   │   │   ├── AnalyzeCommandHandler.kt
│   │   │   ├── ContextCommandHandler.kt
│   │   │   └── GitCommandHandler.kt
│   │   ├── git/                     # Git integration
│   │   │   ├── GitModels.kt
│   │   │   ├── GitRepositoryService.kt
│   │   │   └── GitContextService.kt
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
│   │   ├── EmbeddingsDialog.kt      # Embedding generation UI
│   │   └── AssistantSettingsDialog.kt  # Code assistant config UI
│   └── mcp/                         # Built-in MCP tools
│       ├── ShellCommandMcpServer.kt
│       └── GitMcpServer.kt          # Git MCP server
├── run-mcp-shell-server.sh/.bat    # Shell server launchers
├── run-mcp-git-server.sh/.bat      # Git server launchers
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
    val codeAssistantEnabled: Boolean = false,
    val codeAssistantWorkingDir: String? = null,
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
2. Command detection → CommandParser → CommandExecutor
3. Context enrichment → Code Assistant + Git Integration
4. ViewModel → ApiClient (Claude/Perplexity)
5. Streaming response → Real-time UI updates
6. Tool calls → MCP Pipeline execution
7. Embeddings → Semantic search → Context injection
8. Final response → SessionStorage persistence

**Code Assistant Context Enrichment:**
1. Detect code references in query (files, classes, functions, paths)
2. Search for matching files using FileSearchService
3. Search content using ContentSearchService
4. Collect up to maxFilesInContext matches
5. Format context with file contents
6. Prepend to user message before LLM

**Git Context Enrichment:**
1. Detect git keywords in query (branch, commit, diff, changes, etc.)
2. Check if working directory is a git repository
3. Fetch git status (cached 30s)
4. Fetch diffs if enabled (cached 60s)
5. Fetch commit history if enabled (cached 5min)
6. Format context for LLM
7. Prepend to user message

**MCP Tool Execution:**
1. LLM requests tool use in response
2. McpPipeline routes to appropriate MCP server
3. Server executes tool and returns result
4. Result injected back into conversation
5. LLM continues with tool output
6. Supports up to 10 iterations for complex workflows

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

### Architecture Highlights

**Hybrid Command Execution:**
- **Client-side**: Instant results (search, status)
- **LLM-assisted**: Intelligent formatting (help, analysis)
- **Extensible**: Easy to add new commands via handler pattern

**Three-Layer Git Integration:**
- **MCP Tools**: LLM autonomy for git operations
- **Auto-Context**: Smart keyword-based enrichment
- **Manual Commands**: User-driven explicit control

**Service-Based Architecture:**
- Each concern has dedicated service class
- Clean separation of responsibilities
- Easy to test and maintain
- Consistent error handling patterns

## Troubleshooting

### Common Issues

**"No API keys found" error:**
- Ensure CLAUDE_API_KEY or PERPLEXITY_API_KEY environment variables are set
- Restart the application after setting environment variables

**Git integration not working:**
- Verify git is installed and accessible: `git --version`
- Check working directory is a git repository
- Enable git integration in Code Assistant settings
- Check git server status in MCP settings

**Code Assistant not finding files:**
- Verify working directory is set correctly
- Check file include/exclude patterns
- Ensure files match configured patterns (*.kt, *.java, etc.)
- Check max file size limit

**"Not a git repository" error:**
- Ensure working directory is set to a git repository
- Check that .git directory exists in working directory
- Verify git initialization: `git rev-parse --is-inside-work-tree`

**Ollama connection failed:**
- Verify Ollama is running: `ollama list`
- Check endpoint configuration in settings
- Ensure nomic-embed-text model is pulled: `ollama pull nomic-embed-text`

**MCP server not responding:**
- Check server command and arguments in MCP Settings
- Verify server binary is executable
- Review server logs in the UI
- For git server: ensure git CLI is installed

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
- Git integration via Git CLI

---

**Version**: 1.0.0
**Last Updated**: January 2026
**Kotlin Version**: 2.2.20
**Compose Version**: 1.8.0
