package data

/**
 * Helper class to initialize developer personalization with collected preferences.
 */
object PersonalizationInitializer {

    /**
     * Creates a personalization configuration based on user responses.
     * This can be used to generate the initial configuration or update it.
     */
    fun createFromUserPreferences(
        architecture: String,
        errorHandling: String,
        codingStyle: String,
        tools: List<String>,
        projectStructure: String,
        testingApproaches: List<String>,
        documentation: String,
        dependencyInjection: String,
        principles: List<String>,
        namingConventions: List<String>,
        asyncApproach: String,
        responseDetailLevel: String
    ): DeveloperPersonalization {
        return DeveloperPersonalization(
            enabled = true,
            architecture = ArchitecturePreferences(
                primaryPattern = architecture,
                errorHandling = errorHandling,
                projectStructure = projectStructure,
                dependencyInjection = dependencyInjection
            ),
            codingStyle = CodingStylePreferences(
                programmingParadigm = codingStyle,
                asyncApproach = asyncApproach,
                namingConventions = namingConventions
            ),
            tools = ToolsPreferences(
                primaryLanguage = "Kotlin",
                frameworks = tools,
                buildSystem = "Gradle Kotlin DSL"
            ),
            practices = DevelopmentPractices(
                principles = principles,
                testingApproach = testingApproaches,
                documentationLevel = documentation
            ),
            responseStyle = ResponseStylePreferences(
                detailLevel = responseDetailLevel,
                includeExamples = true,
                explainTradeoffs = true
            )
        )
    }

    /**
     * Creates a personalization configuration with default values.
     * These can be modified later by editing the app-settings.json file.
     */
    fun createDefaultPersonalization(): DeveloperPersonalization {
        return DeveloperPersonalization(
            enabled = false,
            architecture = ArchitecturePreferences(
                primaryPattern = "MVVM",
                errorHandling = "Combined approach",
                projectStructure = "Modular architecture",
                dependencyInjection = "Dagger 2"
            ),
            codingStyle = CodingStylePreferences(
                programmingParadigm = "Mixed style",
                asyncApproach = "Coroutines with suspend functions",
                namingConventions = listOf(
                    "camelCase for variables",
                    "Descriptive names without abbreviations",
                    "No interfaces for single implementation unless hiding implementation is needed"
                )
            ),
            tools = ToolsPreferences(
                primaryLanguage = "Kotlin",
                frameworks = listOf(
                    "Kotlin Coroutines",
                    "Jetpack Compose / Compose Desktop",
                    "Ktor",
                    "Gradle Kotlin DSL",
                    "Dagger 2"
                ),
                buildSystem = "Gradle Kotlin DSL"
            ),
            practices = DevelopmentPractices(
                principles = listOf("DRY", "KISS", "YAGNI"),
                testingApproach = listOf("Integration tests"),
                documentationLevel = "KDoc for public API"
            ),
            responseStyle = ResponseStylePreferences(
                detailLevel = "Balanced responses",
                includeExamples = true,
                explainTradeoffs = true
            )
        )
    }
}
