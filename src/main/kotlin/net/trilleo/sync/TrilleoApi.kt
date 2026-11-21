package net.trilleo.sync

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * The HTTP client for the trilleo.net API.
 *
 * Every call is blocking and is made from [SyncQueue]'s own thread, never from the game's. Nothing here logs:
 * a request carries the player's token in its `Authorization` header, and the safest way to keep a token out
 * of `latest.log` is for the one place that holds a request to never print one. Callers log codes and
 * request IDs, which identify a failure to the site's owner without exposing anything.
 */
internal object TrilleoApi {

    /** The address used when the setting is blank or unusable. */
    const val DEFAULT_URL: String = "https://api.trilleo.net"

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    private val userAgent: String by lazy {
        val version = FabricLoader.getInstance().getModContainer("hex")
            .map { it.metadata.version.friendlyString }.orElse("dev")
        "Hex/$version (+https://github.com/Trilleo/Hex)"
    }

    /**
     * [raw] as a base address — scheme and host, with any trailing slash removed — or null when it is not an
     * http(s) URL with a host. Redirects are never followed, so a token is only ever sent to exactly this.
     */
    fun normalizeBase(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        if (uri.host.isNullOrEmpty()) return null
        if (!uri.rawQuery.isNullOrEmpty() || !uri.rawFragment.isNullOrEmpty()) return null
        return trimmed
    }

    /** The address to call: the setting when it is usable, otherwise [DEFAULT_URL]. */
    fun base(): String = normalizeBase(SyncConfig.settings.apiUrl) ?: DEFAULT_URL

    /**
     * Sends one request to `base + path` and reads the answer. [token], when given, goes in the
     * `Authorization` header and nowhere else.
     */
    fun call(method: String, path: String, token: String? = null, body: JsonObject? = null): ApiResult {
        val request = runCatching {
            HttpRequest.newBuilder(URI(base() + path))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .apply {
                    if (token != null) header("Authorization", "Bearer $token")
                    if (body != null) header("Content-Type", "application/json")
                }
                .method(
                    method,
                    if (body != null) HttpRequest.BodyPublishers.ofString(body.toString())
                    else HttpRequest.BodyPublishers.noBody(),
                )
                .build()
        }.getOrElse { return ApiResult.Network(it.javaClass.simpleName) }

        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return ApiResult.Network("interrupted")
        } catch (e: Exception) {
            return ApiResult.Network(e.javaClass.simpleName)
        }

        val status = response.statusCode()
        if (status in 200..299) {
            val json = runCatching { JsonParser.parseString(response.body()).asJsonObject }.getOrNull() ?: JsonObject()
            return ApiResult.Ok(status, json)
        }
        return ApiResult.Error(
            ApiError.parse(status, response.body(), response.headers().firstValue("Retry-After").orElse(null)),
        )
    }
}
