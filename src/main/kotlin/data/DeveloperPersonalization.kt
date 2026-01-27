package data

import kotlinx.serialization.Serializable

/**
 * Developer personalization settings for customizing LLM interactions.
 * These settings help the LLM understand developer preferences and provide
 * more contextually appropriate responses.
 */
@Serializable
data class DeveloperPersonalization(
    val enabled: Boolean = false,
    val architecture: ArchitecturePreferences = ArchitecturePreferences(),
    val codingStyle: CodingStylePreferences = CodingStylePreferences(),
    val tools: ToolsPreferences = ToolsPreferences(),
    val practices: DevelopmentPractices = DevelopmentPractices(),
    val responseStyle: ResponseStylePreferences = ResponseStylePreferences()
)

@Serializable
data class ArchitecturePreferences(
    val primaryPattern: String = "MVVM",
    val errorHandling: String = "Combined approach",
    val projectStructure: String = "Modular architecture",
    val dependencyInjection: String = "Dagger 2"
)

@Serializable
data class CodingStylePreferences(
    val programmingParadigm: String = "Mixed style",
    val asyncApproach: String = "Coroutines with suspend functions",
    val namingConventions: List<String> = listOf(
        "camelCase for variables",
        "Descriptive names without abbreviations",
        "No interfaces for single implementation unless hiding implementation is needed"
    )
)

@Serializable
data class ToolsPreferences(
    val primaryLanguage: String = "Kotlin",
    val frameworks: List<String> = listOf(
        "Kotlin Coroutines",
        "Jetpack Compose / Compose Desktop",
        "Ktor",
        "Gradle Kotlin DSL",
        "Dagger 2"
    ),
    val buildSystem: String = "Gradle Kotlin DSL"
)

@Serializable
data class DevelopmentPractices(
    val principles: List<String> = listOf("DRY", "KISS", "YAGNI"),
    val testingApproach: List<String> = listOf("Integration tests"),
    val documentationLevel: String = "KDoc for public API"
)

@Serializable
data class ResponseStylePreferences(
    val detailLevel: String = "Balanced responses",
    val includeExamples: Boolean = true,
    val explainTradeoffs: Boolean = true
)

/**
 * Service for generating personalized system prompts based on developer preferences.
 */
class PersonalizationService {

    /**
     * Generates a system prompt that incorporates developer preferences.
     * @param personalization The developer personalization settings
     * @param basePrompt Optional base prompt to append personalization to
     * @return Personalized system prompt
     */
    fun generatePersonalizedPrompt(
        personalization: DeveloperPersonalization,
        basePrompt: String? = null
    ): String {
        if (!personalization.enabled) {
            return basePrompt ?: ""
        }

        val sections = mutableListOf<String>()

        // Base prompt first (if provided)
        if (!basePrompt.isNullOrBlank()) {
            sections.add(basePrompt.trim())
        }

        // Architecture preferences
        sections.add(buildArchitectureSection(personalization.architecture))

        // Coding style
        sections.add(buildCodingStyleSection(personalization.codingStyle))

        // Tools and technologies
        sections.add(buildToolsSection(personalization.tools))

        // Development practices
        sections.add(buildPracticesSection(personalization.practices))

        // Response style
        sections.add(buildResponseStyleSection(personalization.responseStyle))

        return sections.joinToString("\n\n")
    }

    private fun buildArchitectureSection(arch: ArchitecturePreferences): String {
        return """
            ## Architecture Preferences
            - Primary architectural pattern: ${arch.primaryPattern}
            - Error handling approach: ${arch.errorHandling}
            - Project structure: ${arch.projectStructure}
            - Dependency injection: ${arch.dependencyInjection}

            When suggesting code changes or new features, follow these architectural patterns and maintain consistency with existing codebase structure.
        """.trimIndent()
    }

    private fun buildCodingStyleSection(style: CodingStylePreferences): String {
        val conventions = style.namingConventions.joinToString("\n") { "  - $it" }
        return """
            ## Coding Style Preferences
            - Programming paradigm: ${style.programmingParadigm}
            - Asynchronous operations: ${style.asyncApproach}
            - Naming conventions:
            $conventions

            Follow these style conventions when generating code examples and suggestions.
        """.trimIndent()
    }

    private fun buildToolsSection(tools: ToolsPreferences): String {
        val frameworks = tools.frameworks.joinToString("\n") { "  - $it" }
        return """
            ## Tools & Technologies
            - Primary language: ${tools.primaryLanguage}
            - Build system: ${tools.buildSystem}
            - Frameworks and libraries:
            $frameworks

            Prefer using these tools and technologies in code examples and recommendations.
        """.trimIndent()
    }

    private fun buildPracticesSection(practices: DevelopmentPractices): String {
        val principles = practices.principles.joinToString(", ")
        val testing = practices.testingApproach.joinToString(", ")
        return """
            ## Development Practices
            - Key principles: $principles
            - Testing approach: $testing
            - Documentation: ${practices.documentationLevel}

            Apply these principles when reviewing code, suggesting improvements, or generating new code.
        """.trimIndent()
    }

    private fun buildResponseStyleSection(style: ResponseStylePreferences): String {
        return """
            ## Response Style
            - Detail level: ${style.detailLevel}
            - Include code examples: ${if (style.includeExamples) "Yes" else "No"}
            - Explain trade-offs: ${if (style.explainTradeoffs) "Yes, explain pros/cons of different approaches" else "No"}

            Adapt your responses according to these preferences while maintaining clarity and helpfulness.
        """.trimIndent()
    }
}
