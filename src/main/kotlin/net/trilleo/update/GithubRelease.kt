package net.trilleo.update

import com.google.gson.annotations.SerializedName

/**
 * A GitHub release as returned by the `repos/{owner}/{repo}/releases` API, deserialized by GSON.
 *
 * Only the fields the updater needs are modelled; GSON ignores the rest. All fields are nullable /
 * defaulted because GSON leaves anything missing from the JSON at its JVM default.
 */
data class GithubRelease(
    @SerializedName("tag_name") val tagName: String? = null,
    @SerializedName("html_url") val htmlUrl: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
)

/** A single downloadable file attached to a [GithubRelease]. */
data class GithubAsset(
    val name: String? = null,
    @SerializedName("browser_download_url") val browserDownloadUrl: String? = null,
    val size: Long = 0,
)
