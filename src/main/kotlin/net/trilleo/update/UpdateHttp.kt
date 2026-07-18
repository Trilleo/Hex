package net.trilleo.update

import java.net.http.HttpClient
import java.time.Duration

/**
 * Shared [HttpClient] for every updater request (API checks and asset downloads).
 *
 * Redirects are followed because GitHub's `browser_download_url` bounces to a CDN, and a User-Agent is
 * mandatory — the GitHub API rejects requests without one.
 */
internal object UpdateHttp {
    val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    const val USER_AGENT: String = "Hex-Updater (+https://github.com/Trilleo/Hex)"
}
