package data.codeassistant

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class ProjectAnalysisService(private val fileSearchService: FileSearchService) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Analyze the project in the working directory
     */
    fun analyzeProject(workingDir: File): ProjectInfo? {
        if (!workingDir.exists() || !workingDir.isDirectory) {
            return null
        }

        val projectType = detectProjectType(workingDir)
        val metadata = parseProjectMetadata(workingDir, projectType)
        val readmeContent = extractReadmeContent(workingDir)

        // Get file structure from FileSearchService
        val fileStructure = mutableMapOf<String, Int>()
        var totalFiles = 0
        var totalSize = 0L

        workingDir.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                totalFiles++
                totalSize += file.length()

                val extension = file.extension.ifEmpty { "(no extension)" }
                fileStructure[extension] = fileStructure.getOrDefault(extension, 0) + 1
            }

        return ProjectInfo(
            projectType = projectType,
            rootDirectory = workingDir,
            readmeContent = readmeContent,
            metadata = metadata,
            fileStructure = fileStructure,
            totalFiles = totalFiles,
            totalSize = totalSize
        )
    }

    /**
     * Detect the type of project
     */
    fun detectProjectType(workingDir: File): ProjectType {
        return when {
            // Gradle Kotlin
            File(workingDir, "build.gradle.kts").exists() ||
                    File(workingDir, "settings.gradle.kts").exists() -> ProjectType.GRADLE_KOTLIN

            // Gradle Groovy
            File(workingDir, "build.gradle").exists() ||
                    File(workingDir, "settings.gradle").exists() -> ProjectType.GRADLE_GROOVY

            // Maven
            File(workingDir, "pom.xml").exists() -> ProjectType.MAVEN

            // Node.js / npm
            File(workingDir, "package.json").exists() -> ProjectType.NPM

            // Python
            File(workingDir, "requirements.txt").exists() ||
                    File(workingDir, "setup.py").exists() ||
                    File(workingDir, "pyproject.toml").exists() -> ProjectType.PYTHON

            // Rust
            File(workingDir, "Cargo.toml").exists() -> ProjectType.RUST

            // Go
            File(workingDir, "go.mod").exists() -> ProjectType.GO

            else -> ProjectType.UNKNOWN
        }
    }

    /**
     * Parse project metadata from build files
     */
    fun parseProjectMetadata(workingDir: File, projectType: ProjectType): Map<String, String> {
        val metadata = mutableMapOf<String, String>()

        when (projectType) {
            ProjectType.GRADLE_KOTLIN -> parseGradleKotlin(workingDir, metadata)
            ProjectType.GRADLE_GROOVY -> parseGradleGroovy(workingDir, metadata)
            ProjectType.MAVEN -> parseMaven(workingDir, metadata)
            ProjectType.NPM -> parsePackageJson(workingDir, metadata)
            ProjectType.PYTHON -> parsePython(workingDir, metadata)
            ProjectType.RUST -> parseCargoToml(workingDir, metadata)
            ProjectType.GO -> parseGoMod(workingDir, metadata)
            ProjectType.UNKNOWN -> {}
        }

        return metadata
    }

    /**
     * Extract README content
     */
    fun extractReadmeContent(workingDir: File): String? {
        val readmeFiles = listOf("README.md", "README.txt", "README", "readme.md", "Readme.md")

        for (filename in readmeFiles) {
            val file = File(workingDir, filename)
            if (file.exists() && file.isFile) {
                return try {
                    file.readText().take(2000) // Limit to 2000 characters
                } catch (e: Exception) {
                    null
                }
            }
        }

        return null
    }

    private fun parseGradleKotlin(workingDir: File, metadata: MutableMap<String, String>) {
        val buildFile = File(workingDir, "build.gradle.kts")
        if (!buildFile.exists()) return

        try {
            val content = buildFile.readText()

            // Extract group
            Regex("""group\s*=\s*"([^"]+)"""").find(content)?.let {
                metadata["group"] = it.groupValues[1]
            }

            // Extract version
            Regex("""version\s*=\s*"([^"]+)"""").find(content)?.let {
                metadata["version"] = it.groupValues[1]
            }

            // Extract application plugin name
            Regex("""application\s*\{[^}]*mainClass\.set\("([^"]+)"\)""", RegexOption.DOT_MATCHES_ALL).find(content)?.let {
                metadata["mainClass"] = it.groupValues[1]
            }

            metadata["buildSystem"] = "Gradle (Kotlin DSL)"
        } catch (e: Exception) {
            // Ignore parsing errors
        }
    }

    private fun parseGradleGroovy(workingDir: File, metadata: MutableMap<String, String>) {
        val buildFile = File(workingDir, "build.gradle")
        if (!buildFile.exists()) return

        try {
            val content = buildFile.readText()

            // Extract group
            Regex("""group\s*=?\s*['"]([^'"]+)['"]""").find(content)?.let {
                metadata["group"] = it.groupValues[1]
            }

            // Extract version
            Regex("""version\s*=?\s*['"]([^'"]+)['"]""").find(content)?.let {
                metadata["version"] = it.groupValues[1]
            }

            metadata["buildSystem"] = "Gradle (Groovy)"
        } catch (e: Exception) {
            // Ignore parsing errors
        }
    }

    private fun parseMaven(workingDir: File, metadata: MutableMap<String, String>) {
        val pomFile = File(workingDir, "pom.xml")
        if (!pomFile.exists()) return

        try {
            val content = pomFile.readText()

            // Extract artifactId
            Regex("""<artifactId>([^<]+)</artifactId>""").find(content)?.let {
                metadata["artifactId"] = it.groupValues[1]
            }

            // Extract groupId
            Regex("""<groupId>([^<]+)</groupId>""").find(content)?.let {
                metadata["groupId"] = it.groupValues[1]
            }

            // Extract version
            Regex("""<version>([^<]+)</version>""").find(content)?.let {
                metadata["version"] = it.groupValues[1]
            }

            metadata["buildSystem"] = "Maven"
        } catch (e: Exception) {
            // Ignore parsing errors
        }
    }

    private fun parsePackageJson(workingDir: File, metadata: MutableMap<String, String>) {
        val packageFile = File(workingDir, "package.json")
        if (!packageFile.exists()) return

        try {
            val content = packageFile.readText()
            val jsonObject = json.parseToJsonElement(content).jsonObject

            jsonObject["name"]?.jsonPrimitive?.content?.let {
                metadata["name"] = it
            }

            jsonObject["version"]?.jsonPrimitive?.content?.let {
                metadata["version"] = it
            }

            jsonObject["description"]?.jsonPrimitive?.content?.let {
                metadata["description"] = it
            }

            metadata["packageManager"] = "npm"
        } catch (e: Exception) {
            // Ignore parsing errors
        }
    }

    private fun parsePython(workingDir: File, metadata: MutableMap<String, String>) {
        // Try pyproject.toml first
        val pyprojectFile = File(workingDir, "pyproject.toml")
        if (pyprojectFile.exists()) {
            try {
                val content = pyprojectFile.readText()

                Regex("""name\s*=\s*"([^"]+)"""").find(content)?.let {
                    metadata["name"] = it.groupValues[1]
                }

                Regex("""version\s*=\s*"([^"]+)"""").find(content)?.let {
                    metadata["version"] = it.groupValues[1]
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        // Try setup.py
        val setupFile = File(workingDir, "setup.py")
        if (setupFile.exists()) {
            try {
                val content = setupFile.readText()

                Regex("""name\s*=\s*['"]([^'"]+)['"]""").find(content)?.let {
                    metadata["name"] = it.groupValues[1]
                }

                Regex("""version\s*=\s*['"]([^'"]+)['"]""").find(content)?.let {
                    metadata["version"] = it.groupValues[1]
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        metadata["language"] = "Python"
    }

    private fun parseCargoToml(workingDir: File, metadata: MutableMap<String, String>) {
        val cargoFile = File(workingDir, "Cargo.toml")
        if (!cargoFile.exists()) return

        try {
            val content = cargoFile.readText()

            Regex("""name\s*=\s*"([^"]+)"""").find(content)?.let {
                metadata["name"] = it.groupValues[1]
            }

            Regex("""version\s*=\s*"([^"]+)"""").find(content)?.let {
                metadata["version"] = it.groupValues[1]
            }

            metadata["language"] = "Rust"
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun parseGoMod(workingDir: File, metadata: MutableMap<String, String>) {
        val goModFile = File(workingDir, "go.mod")
        if (!goModFile.exists()) return

        try {
            val content = goModFile.readText()

            Regex("""module\s+([^\s]+)""").find(content)?.let {
                metadata["module"] = it.groupValues[1]
            }

            Regex("""go\s+([\d.]+)""").find(content)?.let {
                metadata["goVersion"] = it.groupValues[1]
            }

            metadata["language"] = "Go"
        } catch (e: Exception) {
            // Ignore
        }
    }
}
