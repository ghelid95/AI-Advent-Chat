package data

import kotlinx.serialization.Serializable

/**
 * Prompt template for different tasks
 */
@Serializable
data class PromptTemplate(
    val name: String,
    val category: PromptCategory,
    val template: String,
    val description: String,
    val recommendedConfig: String = "Balanced"  // References preset name
)

enum class PromptCategory {
    CODE_ANALYSIS,
    CODE_GENERATION,
    DOCUMENTATION,
    DEBUGGING,
    REFACTORING,
    CODE_REVIEW,
    TESTING,
    GENERAL
}

/**
 * Library of specialized prompt templates
 */
object PromptTemplates {

    // Code Analysis
    val ANALYZE_CODE = PromptTemplate(
        name = "Analyze Code",
        category = PromptCategory.CODE_ANALYSIS,
        template = """Analyze the following code and provide:
1. Purpose and functionality
2. Key components and their roles
3. Potential issues or improvements
4. Code quality assessment

Code:
{CODE}""",
        description = "Comprehensive code analysis with insights",
        recommendedConfig = "Quality"
    )

    val CODE_COMPLEXITY = PromptTemplate(
        name = "Assess Complexity",
        category = PromptCategory.CODE_ANALYSIS,
        template = """Assess the complexity of this code:
1. Cyclomatic complexity
2. Cognitive complexity
3. Suggestions to reduce complexity

Code:
{CODE}""",
        description = "Evaluate code complexity and suggest improvements",
        recommendedConfig = "Precise"
    )

    // Code Generation
    val GENERATE_FUNCTION = PromptTemplate(
        name = "Generate Function",
        category = PromptCategory.CODE_GENERATION,
        template = """Generate a function that {DESCRIPTION}.

Requirements:
- Language: {LANGUAGE}
- Include proper error handling
- Add inline comments for complex logic
- Follow best practices""",
        description = "Generate a well-structured function",
        recommendedConfig = "Quality"
    )

    val GENERATE_CLASS = PromptTemplate(
        name = "Generate Class",
        category = PromptCategory.CODE_GENERATION,
        template = """Create a class for {DESCRIPTION}.

Requirements:
- Language: {LANGUAGE}
- Include constructor
- Add appropriate methods
- Use proper encapsulation
- Add documentation comments""",
        description = "Generate a complete class with methods",
        recommendedConfig = "Quality"
    )

    val GENERATE_UNIT_TEST = PromptTemplate(
        name = "Generate Unit Tests",
        category = PromptCategory.TESTING,
        template = """Generate comprehensive unit tests for this code:

{CODE}

Requirements:
- Test framework: {FRAMEWORK}
- Cover edge cases
- Include setup and teardown if needed
- Add descriptive test names""",
        description = "Create thorough unit tests",
        recommendedConfig = "Quality"
    )

    // Documentation
    val ADD_DOCUMENTATION = PromptTemplate(
        name = "Add Documentation",
        category = PromptCategory.DOCUMENTATION,
        template = """Add comprehensive documentation to this code:

{CODE}

Include:
- Function/method descriptions
- Parameter descriptions
- Return value descriptions
- Usage examples
- Format: {FORMAT} (e.g., JavaDoc, KDoc, JSDoc)""",
        description = "Generate documentation for code",
        recommendedConfig = "Balanced"
    )

    val CREATE_README = PromptTemplate(
        name = "Create README",
        category = PromptCategory.DOCUMENTATION,
        template = """Create a comprehensive README.md for a project with the following:

Project name: {PROJECT_NAME}
Description: {DESCRIPTION}

Include:
1. Overview
2. Features
3. Installation instructions
4. Usage examples
5. Configuration
6. Contributing guidelines
7. License""",
        description = "Generate a project README",
        recommendedConfig = "Creative"
    )

    // Debugging
    val DEBUG_ERROR = PromptTemplate(
        name = "Debug Error",
        category = PromptCategory.DEBUGGING,
        template = """Help debug this error:

Error message:
{ERROR}

Code:
{CODE}

Provide:
1. Explanation of the error
2. Root cause analysis
3. Step-by-step fix
4. Prevention strategies""",
        description = "Analyze and fix errors",
        recommendedConfig = "Precise"
    )

    val OPTIMIZE_PERFORMANCE = PromptTemplate(
        name = "Optimize Performance",
        category = PromptCategory.DEBUGGING,
        template = """Optimize the performance of this code:

{CODE}

Analyze:
1. Performance bottlenecks
2. Memory usage
3. Time complexity
4. Suggest optimizations with code examples""",
        description = "Identify and fix performance issues",
        recommendedConfig = "Quality"
    )

    // Refactoring
    val REFACTOR_CODE = PromptTemplate(
        name = "Refactor Code",
        category = PromptCategory.REFACTORING,
        template = """Refactor this code to improve {GOAL}:

{CODE}

Focus on:
1. Code readability
2. Maintainability
3. Following best practices
4. Removing code smells

Provide the refactored code with explanations.""",
        description = "Refactor code for better quality",
        recommendedConfig = "Quality"
    )

    val EXTRACT_METHOD = PromptTemplate(
        name = "Extract Method",
        category = PromptCategory.REFACTORING,
        template = """Extract reusable methods from this code:

{CODE}

Identify:
1. Repeated code patterns
2. Long methods that can be split
3. Suggest extracted methods with names and parameters""",
        description = "Extract methods for better organization",
        recommendedConfig = "Balanced"
    )

    // Code Review
    val CODE_REVIEW = PromptTemplate(
        name = "Code Review",
        category = PromptCategory.CODE_REVIEW,
        template = """Perform a code review on this code:

{CODE}

Review aspects:
1. Code style and conventions
2. Potential bugs
3. Security issues
4. Performance concerns
5. Best practices adherence
6. Suggestions for improvement

Provide detailed feedback with severity levels (Critical, Major, Minor, Suggestion).""",
        description = "Comprehensive code review",
        recommendedConfig = "Quality"
    )

    val SECURITY_AUDIT = PromptTemplate(
        name = "Security Audit",
        category = PromptCategory.CODE_REVIEW,
        template = """Perform a security audit on this code:

{CODE}

Check for:
1. SQL injection vulnerabilities
2. XSS vulnerabilities
3. Authentication/authorization issues
4. Data exposure risks
5. Input validation problems
6. Dependency vulnerabilities

Provide severity ratings and remediation steps.""",
        description = "Security-focused code audit",
        recommendedConfig = "Precise"
    )

    // General
    val EXPLAIN_CODE = PromptTemplate(
        name = "Explain Code",
        category = PromptCategory.GENERAL,
        template = """Explain this code in simple terms:

{CODE}

Provide:
1. High-level overview
2. Step-by-step breakdown
3. Key concepts used
4. Analogies if helpful""",
        description = "Clear explanation of code",
        recommendedConfig = "Balanced"
    )

    val COMPARE_APPROACHES = PromptTemplate(
        name = "Compare Approaches",
        category = PromptCategory.GENERAL,
        template = """Compare these two approaches for {TASK}:

Approach 1:
{APPROACH1}

Approach 2:
{APPROACH2}

Compare on:
1. Performance
2. Readability
3. Maintainability
4. Scalability
5. Recommend the better approach with reasoning""",
        description = "Compare different implementation approaches",
        recommendedConfig = "Quality"
    )

    /**
     * Get all templates
     */
    fun getAllTemplates(): List<PromptTemplate> {
        return listOf(
            // Code Analysis
            ANALYZE_CODE,
            CODE_COMPLEXITY,
            // Code Generation
            GENERATE_FUNCTION,
            GENERATE_CLASS,
            GENERATE_UNIT_TEST,
            // Documentation
            ADD_DOCUMENTATION,
            CREATE_README,
            // Debugging
            DEBUG_ERROR,
            OPTIMIZE_PERFORMANCE,
            // Refactoring
            REFACTOR_CODE,
            EXTRACT_METHOD,
            // Code Review
            CODE_REVIEW,
            SECURITY_AUDIT,
            // General
            EXPLAIN_CODE,
            COMPARE_APPROACHES
        )
    }

    /**
     * Get templates by category
     */
    fun getTemplatesByCategory(category: PromptCategory): List<PromptTemplate> {
        return getAllTemplates().filter { it.category == category }
    }

    /**
     * Fill template with parameters
     */
    fun fillTemplate(template: PromptTemplate, parameters: Map<String, String>): String {
        var filled = template.template
        parameters.forEach { (key, value) ->
            filled = filled.replace("{$key}", value)
        }
        return filled
    }

    /**
     * Get recommended config for template
     */
    fun getRecommendedConfig(template: PromptTemplate): OllamaOptimizationConfig {
        return when (template.recommendedConfig) {
            "Fast" -> OllamaPresets.FAST
            "Quality" -> OllamaPresets.QUALITY
            "Creative" -> OllamaPresets.CREATIVE
            "Precise" -> OllamaPresets.PRECISE
            else -> OllamaPresets.BALANCED
        }
    }
}
