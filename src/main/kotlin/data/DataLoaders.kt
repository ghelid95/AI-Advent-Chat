package data

import kotlinx.serialization.json.*
import java.io.File
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Service for loading and parsing different data formats
 */
class DataLoader {

    /**
     * Load dataset from file
     */
    fun loadDataset(file: File): Result<Dataset> {
        return try {
            val format = detectFormat(file)
            val dataset = when (format) {
                DataFormat.CSV -> loadCSV(file)
                DataFormat.JSON -> loadJSON(file)
                DataFormat.LOG -> loadLogs(file)
            }
            Result.success(dataset)
        } catch (e: Exception) {
            println("[DataLoader] Error loading dataset: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Detect data format from file extension
     */
    private fun detectFormat(file: File): DataFormat {
        return when (file.extension.lowercase()) {
            "csv" -> DataFormat.CSV
            "json" -> DataFormat.JSON
            "log", "txt" -> DataFormat.LOG
            else -> throw IllegalArgumentException("Unsupported file format: ${file.extension}")
        }
    }

    /**
     * Load CSV file
     */
    private fun loadCSV(file: File): Dataset {
        val lines = file.readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            throw IllegalArgumentException("CSV file is empty")
        }

        // Parse header
        val header = lines.first().split(",").map { it.trim() }

        // Parse rows
        val rows = lines.drop(1).mapIndexed { index, line ->
            val values = line.split(",").map { it.trim() }
            if (values.size != header.size) {
                println("[DataLoader] Warning: Row $index has ${values.size} values, expected ${header.size}")
            }

            val rowMap = header.zip(values).toMap()
            DataRow(rowMap)
        }

        val summary = generateSummary(rows, header)

        return Dataset(
            name = file.nameWithoutExtension,
            format = DataFormat.CSV,
            filePath = file.absolutePath,
            rows = rows,
            columns = header,
            rowCount = rows.size,
            summary = summary
        )
    }

    /**
     * Load JSON file (array of objects)
     */
    private fun loadJSON(file: File): Dataset {
        val jsonText = file.readText()
        val json = Json { ignoreUnknownKeys = true }

        val jsonElement = json.parseToJsonElement(jsonText)

        // Handle both array of objects and single object
        val jsonArray = when (jsonElement) {
            is JsonArray -> jsonElement
            is JsonObject -> JsonArray(listOf(jsonElement))
            else -> throw IllegalArgumentException("JSON must be an array or object")
        }

        if (jsonArray.isEmpty()) {
            throw IllegalArgumentException("JSON array is empty")
        }

        // Extract all keys from all objects
        val allKeys = mutableSetOf<String>()
        jsonArray.forEach { element ->
            if (element is JsonObject) {
                allKeys.addAll(element.keys)
            }
        }
        val columns = allKeys.sorted()

        // Parse rows
        val rows = jsonArray.mapNotNull { element ->
            if (element is JsonObject) {
                val rowMap = columns.associateWith { key ->
                    element[key]?.toString()?.removeSurrounding("\"") ?: ""
                }
                DataRow(rowMap)
            } else null
        }

        val summary = generateSummary(rows, columns)

        return Dataset(
            name = file.nameWithoutExtension,
            format = DataFormat.JSON,
            filePath = file.absolutePath,
            rows = rows,
            columns = columns,
            rowCount = rows.size,
            summary = summary
        )
    }

    /**
     * Load log file
     */
    private fun loadLogs(file: File): Dataset {
        val lines = file.readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            throw IllegalArgumentException("Log file is empty")
        }

        val logEntries = lines.mapNotNull { parseLogLine(it) }

        if (logEntries.isEmpty()) {
            throw IllegalArgumentException("No valid log entries found")
        }

        // Collect all unique keys from metadata
        val allKeys = mutableSetOf("timestamp", "level", "message")
        logEntries.forEach { entry ->
            allKeys.addAll(entry.metadata.keys)
        }
        val columns = allKeys.sorted()

        val rows = logEntries.map { it.toDataRow() }
        val summary = generateSummary(rows, columns)

        return Dataset(
            name = file.nameWithoutExtension,
            format = DataFormat.LOG,
            filePath = file.absolutePath,
            rows = rows,
            columns = columns,
            rowCount = rows.size,
            summary = summary
        )
    }

    /**
     * Parse single log line using common patterns
     */
    private fun parseLogLine(line: String): LogEntry? {
        // Try timestamped level pattern
        LogPatterns.TIMESTAMPED_LEVEL.matchEntire(line)?.let { match ->
            return LogEntry(
                timestamp = match.groupValues[1],
                level = match.groupValues[2],
                message = match.groupValues[3]
            )
        }

        // Try space-separated pattern
        LogPatterns.SPACE_SEPARATED.matchEntire(line)?.let { match ->
            return LogEntry(
                timestamp = match.groupValues[1],
                level = match.groupValues[2],
                message = match.groupValues[3]
            )
        }

        // Try level-first pattern
        LogPatterns.LEVEL_FIRST.matchEntire(line)?.let { match ->
            return LogEntry(
                timestamp = match.groupValues[2],
                level = match.groupValues[1],
                message = match.groupValues[3]
            )
        }

        // Try JSON log
        if (line.trim().startsWith("{")) {
            try {
                val json = Json { ignoreUnknownKeys = true }
                val jsonObj = json.parseToJsonElement(line).jsonObject

                val timestamp = jsonObj["timestamp"]?.toString()?.removeSurrounding("\"") ?: ""
                val level = jsonObj["level"]?.toString()?.removeSurrounding("\"") ?: "INFO"
                val message = jsonObj["message"]?.toString()?.removeSurrounding("\"") ?: line

                val metadata = jsonObj
                    .filterKeys { it !in setOf("timestamp", "level", "message") }
                    .mapValues { it.value.toString().removeSurrounding("\"") }

                return LogEntry(timestamp, level, message, metadata)
            } catch (e: Exception) {
                // Not valid JSON, fall through
            }
        }

        // Fallback: treat entire line as message
        return LogEntry(
            timestamp = "",
            level = "INFO",
            message = line
        )
    }

    /**
     * Generate statistical summary of dataset
     */
    private fun generateSummary(rows: List<DataRow>, columns: List<String>): DataSummary {
        val columnTypes = mutableMapOf<String, ColumnType>()
        val numericStats = mutableMapOf<String, NumericStats>()
        val categoricalStats = mutableMapOf<String, CategoricalStats>()
        val nullCounts = mutableMapOf<String, Int>()

        columns.forEach { column ->
            val values = rows.map { it.values[column] ?: "" }
            val nonEmptyValues = values.filter { it.isNotBlank() }

            // Count nulls/empties
            nullCounts[column] = values.size - nonEmptyValues.size

            // Detect column type
            val type = detectColumnType(nonEmptyValues)
            columnTypes[column] = type

            // Calculate statistics based on type
            when (type) {
                ColumnType.NUMERIC -> {
                    val numbers = nonEmptyValues.mapNotNull { it.toDoubleOrNull() }
                    if (numbers.isNotEmpty()) {
                        numericStats[column] = calculateNumericStats(numbers)
                    }
                }
                ColumnType.CATEGORICAL, ColumnType.TEXT -> {
                    categoricalStats[column] = calculateCategoricalStats(nonEmptyValues)
                }
                else -> {
                    // Boolean, DateTime - treat as categorical for now
                    categoricalStats[column] = calculateCategoricalStats(nonEmptyValues)
                }
            }
        }

        return DataSummary(
            totalRows = rows.size,
            totalColumns = columns.size,
            columnTypes = columnTypes,
            numericStats = numericStats,
            categoricalStats = categoricalStats,
            nullCounts = nullCounts
        )
    }

    /**
     * Detect column type from sample values
     */
    private fun detectColumnType(values: List<String>): ColumnType {
        if (values.isEmpty()) return ColumnType.TEXT

        val sample = values.take(100)

        // Check if numeric
        val numericCount = sample.count { it.toDoubleOrNull() != null }
        if (numericCount > sample.size * 0.8) {
            return ColumnType.NUMERIC
        }

        // Check if boolean
        val booleanValues = setOf("true", "false", "yes", "no", "0", "1")
        val booleanCount = sample.count { it.lowercase() in booleanValues }
        if (booleanCount > sample.size * 0.8) {
            return ColumnType.BOOLEAN
        }

        // Check if datetime (simple check)
        val datetimePatterns = listOf(
            Regex("""\d{4}-\d{2}-\d{2}"""),
            Regex("""\d{2}/\d{2}/\d{4}"""),
            Regex("""\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}""")
        )
        val datetimeCount = sample.count { value ->
            datetimePatterns.any { it.containsMatchIn(value) }
        }
        if (datetimeCount > sample.size * 0.8) {
            return ColumnType.DATETIME
        }

        // Check if categorical (limited unique values)
        val uniqueValues = sample.toSet().size
        if (uniqueValues < sample.size * 0.5 && uniqueValues < 20) {
            return ColumnType.CATEGORICAL
        }

        return ColumnType.TEXT
    }

    /**
     * Calculate numeric statistics
     */
    private fun calculateNumericStats(numbers: List<Double>): NumericStats {
        val sorted = numbers.sorted()
        val min = sorted.first()
        val max = sorted.last()
        val average = numbers.average()
        val median = if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        } else {
            sorted[sorted.size / 2]
        }

        val variance = numbers.map { (it - average).pow(2) }.average()
        val stdDev = sqrt(variance)

        return NumericStats(min, max, average, median, stdDev)
    }

    /**
     * Calculate categorical statistics
     */
    private fun calculateCategoricalStats(values: List<String>): CategoricalStats {
        val uniqueValues = values.toSet().size
        val valueCounts = values.groupingBy { it }.eachCount()
        val topValues = valueCounts.entries
            .sortedByDescending { it.value }
            .take(10)
            .associate { it.key to it.value }

        return CategoricalStats(uniqueValues, topValues)
    }
}
