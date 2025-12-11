package data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Model(
    val id: String,
    val type: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)
