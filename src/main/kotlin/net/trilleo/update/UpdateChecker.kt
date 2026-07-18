package net.trilleo.update

import com.google.gson.Gson
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.SemanticVersion
import java.io.IOException
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Outcome of an update check. */
sealed interface UpdateStatus {
    /** The installed version is the newest published release. */
    data object UpToDate : UpdateStatus

    /** A newer release exists. [version] is the tag without a leading `v`. */
    data class Available(val version: String, val release: GithubRelease) : UpdateStatus

    /** The check could not complete (offline, rate-limited, parse error, …). */
    data class Failed(val reason: String) : UpdateStatus
}

/**
 * Queries the GitHub releases API for `Trilleo/Hex` and compares the newest release against the running
 * mod version. All calls are synchronous and blocking — invoke them off the client thread.
 */
object UpdateChecker {
    private const val REPO = "Trilleo/Hex"
    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val LIST_URL = "https://api.github.com/repos/$REPO/releases?per_page=10"

    private val gson = Gson()

    /** The friendly version string of the currently running Hex mod, e.g. `1.1.0`. */
    fun currentVersion(): String =
        FabricLoader.getInstance().getModContainer("hex")
            .map { it.metadata.version.friendlyString }
            .orElse("0.0.0")

    /**
     * Fetch the newest applicable release and compare it to the running version.
     *
     * @param includePrereleases when `true`, prereleases also count as updates; otherwise only the latest
     *   stable release is considered.
     */
    fun check(includePrereleases: Boolean): UpdateStatus =
        try {
            val release = fetchLatest(includePrereleases)
                ?: return UpdateStatus.Failed("no release found")
            val tag = release.tagName?.removePrefix("v")
                ?: return UpdateStatus.Failed("release is missing a tag")
            val remote = SemanticVersion.parse(tag)
            val current = SemanticVersion.parse(currentVersion())
            if (remote.compareTo(current) > 0) UpdateStatus.Available(tag, release) else UpdateStatus.UpToDate
        } catch (e: Exception) {
            UpdateStatus.Failed(e.message ?: e.javaClass.simpleName)
        }

    /**
     * The latest stable release (via the `/releases/latest` endpoint, which already excludes prereleases
     * and drafts), or — when [includePrereleases] is set — the newest non-draft release from the list.
     */
    private fun fetchLatest(includePrereleases: Boolean): GithubRelease? {
        if (!includePrereleases) {
            return getJson(LATEST_URL, GithubRelease::class.java)
        }
        val releases = getJson(LIST_URL, Array<GithubRelease>::class.java) ?: return null
        // The list endpoint returns releases newest-first.
        return releases.firstOrNull { !it.draft }
    }

    private fun <T> getJson(url: String, type: Class<T>): T? {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", UpdateHttp.USER_AGENT)
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build()
        val response = UpdateHttp.client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw IOException("GitHub API returned HTTP ${response.statusCode()}")
        }
        return gson.fromJson(response.body(), type)
    }
}
