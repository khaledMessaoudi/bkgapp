package wallapp.download

import kotlinx.coroutines.flow.Flow

/**
 * Base URL serving the encrypted content API over plain HTTPS, without a trailing slash, e.g.
 * "https://content.example.com" or "http://10.0.2.2:8787" for the local loop. Blank means the
 * app downloads the API through Firebase Cloud Storage the way upstream does.
 *
 * Cloud Storage for Firebase requires the paid Blaze plan since 2026-02-03, so bkgapp serves the
 * same `api/<version>/` layout from a static host instead. See docs/CHANGES.md.
 */
const val CONTENT_HTTP_BASE_URL = ""

/**
 * Whether the content API encryption key is read from Firestore instead of being served alongside
 * the encrypted content. Only consulted when [CONTENT_HTTP_BASE_URL] is set. Keeping it true means
 * the key stays readable only by a signed-in user, which is what upstream's Storage rules gave us.
 */
const val CONTENT_KEY_FROM_FIRESTORE = true

/**
 * Fetches the encrypted content API over HTTP instead of Firebase Cloud Storage.
 *
 * The paths the app asks for (`api/v0/key1`, `api/v0/content-1a`, ...) are already relative, so
 * they map onto a static host unchanged: no change to `tools/content_build.py` output layout.
 * `appendData` keeps upstream's meaning — the encrypted files carry a `.data` suffix, the
 * encryption key file does not.
 */
class HttpApiDownloader(
    private val urlDownloader: UrlDownloader,
    private val baseUrl: String,
) : FirebaseStorageDownloader {

    override fun downloadFile(path: String, appendData: Boolean): Flow<DownloadState> {
        val finalPath = if (appendData) "$path.data" else path
        return urlDownloader.downloadUrl(baseUrl.trimEnd('/') + "/" + finalPath.trimStart('/'))
    }
}
