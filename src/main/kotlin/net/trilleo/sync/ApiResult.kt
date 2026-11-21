package net.trilleo.sync

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * What one call to the trilleo.net API came back with.
 *
 * Pure, so the error rules are tested without a network.
 */
sealed interface ApiResult {
    /** A 2xx answer and its body (an empty object when the body was not JSON). */
    data class Ok(val status: Int, val body: JsonObject) : ApiResult

    /** A 4xx or 5xx answer, read from the API's error envelope. */
    data class Error(val error: ApiError) : ApiResult

    /** The request never got an answer: offline, DNS, a refused connection, a timeout. */
    data class Network(val reason: String) : ApiResult
}

/**
 * The API's error envelope: `{"error": {"code", "message", "details", "requestId"}}`.
 *
 * Callers switch on [code], never on [message], which is English text for people and may change.
 *
 * @property retryAfterSeconds from the `Retry-After` header, or `details.retryAfter`, when the API sent one.
 */
data class ApiError(
    val status: Int,
    val code: String,
    val message: String,
    val details: JsonObject?,
    val requestId: String?,
    val retryAfterSeconds: Long?,
) {
    /**
     * The token is no use any more and must be forgotten: revoked, expired, unknown, or the account blocked.
     * An unknown code is judged by its status, as the API's docs ask — any 401 means the credential failed.
     */
    val forgetsToken: Boolean get() = code in TOKEN_DEAD || status == 401

    /** Worth sending again unchanged later: rate-limited, or the server failed. */
    val retryable: Boolean get() = status == 429 || status >= 500

    /** `details.interval` on a `slow_down`, in seconds. */
    val interval: Long? get() = details?.longOrNull("interval")

    /** `details.limit` on a `profile_limit`: how many profiles the account may have. */
    val limit: Long? get() = details?.longOrNull("limit")

    companion object {
        private val TOKEN_DEAD = setOf("token_required", "invalid_key", "key_revoked", "key_expired", "account_blocked")

        /**
         * Reads an error answer. Never throws: a body that is not the envelope (a proxy's HTML page, say)
         * still gives an error, coded by status.
         */
        fun parse(status: Int, body: String?, retryAfterHeader: String?): ApiError {
            val envelope = runCatching { JsonParser.parseString(body.orEmpty()).asJsonObject.getAsJsonObject("error") }
                .getOrNull()
            val details = envelope?.get("details")?.takeIf { it.isJsonObject }?.asJsonObject
            val retryAfter = retryAfterHeader?.trim()?.toLongOrNull() ?: details?.longOrNull("retryAfter")
            return ApiError(
                status = status,
                code = envelope?.stringOrNull("code") ?: "http_$status",
                message = envelope?.stringOrNull("message").orEmpty(),
                details = details,
                requestId = envelope?.stringOrNull("requestId"),
                retryAfterSeconds = retryAfter?.coerceAtLeast(0),
            )
        }
    }
}

/** A string member, or null when absent, null, or not a string. */
internal fun JsonObject.stringOrNull(key: String): String? =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

/** A numeric member, or null when absent or not a number. */
internal fun JsonObject.longOrNull(key: String): Long? =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong
