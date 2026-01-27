package data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class SessionStorage {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val sessionsDir: File = run {
        val userHome = System.getProperty("user.home")
        val appDir = File(userHome, ".ai-advent-chat")
        val sessionsFolder = File(appDir, "sessions")

        if (!sessionsFolder.exists()) {
            sessionsFolder.mkdirs()
        }

        sessionsFolder
    }

    private val metadataFile = File(sessionsDir, "metadata.json")

    // Save session to file
    fun saveSession(session: SessionData) {
        try {
            val sessionFile = File(sessionsDir, "${session.id}.json")
            val jsonString = json.encodeToString(session)
            sessionFile.writeText(jsonString, Charsets.UTF_8)

            // Update metadata
            updateMetadata(session.id)
        } catch (e: Exception) {
            println("Error saving session ${session.id}: ${e.message}")
            e.printStackTrace()
        }
    }

    // Load session from file
    fun loadSession(sessionId: String): SessionData? {
        return try {
            val sessionFile = File(sessionsDir, "$sessionId.json")
            if (!sessionFile.exists()) {
                return null
            }

            val jsonString = sessionFile.readText(Charsets.UTF_8)
            json.decodeFromString<SessionData>(jsonString)
        } catch (e: Exception) {
            println("Error loading session $sessionId: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    // Get all session summaries (id, name, lastModified)
    fun getAllSessions(): List<SessionSummary> {
        return try {
            sessionsDir.listFiles { file -> file.extension == "json" && file.name != "metadata.json" }
                ?.mapNotNull { file ->
                    try {
                        val jsonString = file.readText(Charsets.UTF_8)
                        val session = json.decodeFromString<SessionData>(jsonString)
                        SessionSummary(
                            id = session.id,
                            name = session.name,
                            createdAt = session.createdAt,
                            lastModified = session.lastModified,
                            messageCount = session.messages.size,
                            totalCost = session.sessionStats.totalCost
                        )
                    } catch (e: Exception) {
                        println("Error reading session file ${file.name}: ${e.message}")
                        null
                    }
                }
                ?.sortedByDescending { it.lastModified }
                ?: emptyList()
        } catch (e: Exception) {
            println("Error getting all sessions: ${e.message}")
            emptyList()
        }
    }

    // Delete session
    fun deleteSession(sessionId: String): Boolean {
        return try {
            val sessionFile = File(sessionsDir, "$sessionId.json")
            val deleted = sessionFile.delete()

            if (deleted) {
                // Remove from metadata
                removeFromMetadata(sessionId)
            }

            deleted
        } catch (e: Exception) {
            println("Error deleting session $sessionId: ${e.message}")
            false
        }
    }

    // Get last active session ID
    fun getLastActiveSessionId(): String? {
        return try {
            if (!metadataFile.exists()) {
                return null
            }

            val metadata = json.decodeFromString<SessionMetadata>(metadataFile.readText(Charsets.UTF_8))
            metadata.lastActiveSessionId
        } catch (e: Exception) {
            println("Error reading metadata: ${e.message}")
            null
        }
    }

    // Update metadata with last active session
    private fun updateMetadata(sessionId: String) {
        try {
            val metadata = SessionMetadata(lastActiveSessionId = sessionId)
            val jsonString = json.encodeToString(metadata)
            metadataFile.writeText(jsonString, Charsets.UTF_8)
        } catch (e: Exception) {
            println("Error updating metadata: ${e.message}")
        }
    }

    // Remove session from metadata
    private fun removeFromMetadata(sessionId: String) {
        try {
            if (!metadataFile.exists()) return

            val metadata = json.decodeFromString<SessionMetadata>(metadataFile.readText(Charsets.UTF_8))
            if (metadata.lastActiveSessionId == sessionId) {
                metadataFile.delete()
            }
        } catch (e: Exception) {
            println("Error removing from metadata: ${e.message}")
        }
    }
}

@kotlinx.serialization.Serializable
data class SessionSummary(
    val id: String,
    val name: String,
    val createdAt: Long,
    val lastModified: Long,
    val messageCount: Int,
    val totalCost: Double
)

@kotlinx.serialization.Serializable
private data class SessionMetadata(
    val lastActiveSessionId: String?
)