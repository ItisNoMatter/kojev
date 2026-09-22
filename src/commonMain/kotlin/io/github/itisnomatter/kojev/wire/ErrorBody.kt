package io.github.itisnomatter.kojev.wire

import io.github.itisnomatter.kojev.ValidationError
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * What can be read out of a non-2xx body. Only 422's shape is documented by the API
 * (`HTTPValidationError`); for everything else this follows the official SDKs' own heuristics,
 * recorded in `docs/api-notes.md`: a plain string, `message`, `error`, `error.message`,
 * `detail`, or `detail.message`.
 */
internal class ErrorBody(
    val message: String?,
    val validationErrors: List<ValidationError>,
) {
    companion object {
        fun parse(body: String?): ErrorBody {
            if (body.isNullOrBlank()) return ErrorBody(null, emptyList())
            val json = runCatching { jevJson.parseToJsonElement(body) }.getOrNull() ?: return ErrorBody(body, emptyList())
            if (json is JsonPrimitive && json.isString) return ErrorBody(json.content.takeIf { it.isNotEmpty() }, emptyList())
            if (json !is JsonObject) return ErrorBody(body, emptyList())
            val detail = json["detail"]
            val validationErrors = if (detail is JsonArray) detail.mapNotNull(::validationError) else emptyList()
            val message =
                json["error"].asText()
                    ?: (json["error"] as? JsonObject)?.get("message").asText()
                    ?: json["message"].asText()
                    ?: detail.asText()
                    ?: (detail as? JsonObject)?.get("message").asText()
                    ?: validationErrors.takeIf { it.isNotEmpty() }?.joinToString("; ")
                    ?: body
            return ErrorBody(message, validationErrors)
        }

        private fun validationError(element: JsonElement): ValidationError? {
            val entry = element as? JsonObject ?: return null
            val message = entry["msg"].asText() ?: return null
            val location = (entry["loc"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()
            return ValidationError(location, message, entry["type"].asText() ?: "")
        }

        private fun JsonElement?.asText(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }
    }
}
