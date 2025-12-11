package data

import kotlinx.serialization.json.Json

val DefaultJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = false
    encodeDefaults = true
}
