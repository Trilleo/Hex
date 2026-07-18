package net.trilleo.update

import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.zip.ZipFile

/**
 * Downloads a release's mod jar into the staging folder (`config/hex/update/`) and verifies it before it
 * is ever swapped into `mods/`. A staged jar is only returned once it matches the advertised size and
 * opens as a valid zip, so a truncated or corrupt download can never replace a working install.
 */
object UpdateDownloader {
	private val LOGGER = LoggerFactory.getLogger("hex/update")

	/** The `config/hex/update/` folder where downloads are staged. */
	fun stagingDir(): Path =
		FabricLoader.getInstance().configDir.resolve("hex").resolve("update")

	/**
	 * Pick the mod jar asset from a release: a `.jar` that is not the sources jar and looks like the Hex
	 * build output (`hex-<version>.jar`). Returns `null` when no suitable asset is attached.
	 */
	fun selectAsset(release: GithubRelease): GithubAsset? =
		release.assets.firstOrNull { asset ->
			val name = asset.name?.lowercase() ?: return@firstOrNull false
			name.endsWith(".jar") && !name.contains("sources") && name.startsWith("hex") &&
				asset.browserDownloadUrl != null
		}

	/**
	 * Download [asset] to the staging folder and verify it. Returns the staged path on success, or `null`
	 * if the download failed or the file did not verify (the partial file is cleaned up in that case).
	 */
	fun download(asset: GithubAsset): Path? {
		val url = asset.browserDownloadUrl ?: return null
		val name = asset.name ?: return null
		val target = stagingDir().resolve(name)
		return try {
			Files.createDirectories(target.parent)
			val request = HttpRequest.newBuilder(URI.create(url))
				.header("User-Agent", UpdateHttp.USER_AGENT)
				.timeout(Duration.ofMinutes(5))
				.GET()
				.build()
			val response = UpdateHttp.client.send(request, HttpResponse.BodyHandlers.ofInputStream())
			if (response.statusCode() != 200) {
				LOGGER.error("Download of {} failed: HTTP {}", name, response.statusCode())
				return null
			}
			response.body().use { input ->
				Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
			}
			if (!verify(target, asset.size)) {
				Files.deleteIfExists(target)
				return null
			}
			target
		} catch (e: Exception) {
			LOGGER.error("Failed to download update {}", name, e)
			runCatching { Files.deleteIfExists(target) }
			null
		}
	}

	/** A staged jar is valid only if it matches the advertised size and opens as a zip archive. */
	private fun verify(file: Path, expectedSize: Long): Boolean {
		if (expectedSize > 0 && Files.size(file) != expectedSize) {
			LOGGER.error("Downloaded jar size {} != expected {}", Files.size(file), expectedSize)
			return false
		}
		return try {
			ZipFile(file.toFile()).close()
			true
		} catch (e: Exception) {
			LOGGER.error("Downloaded jar {} is not a valid archive", file, e)
			false
		}
	}
}
