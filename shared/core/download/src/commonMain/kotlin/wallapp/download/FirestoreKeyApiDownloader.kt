package wallapp.download

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import wallapp.data.DataHandle
import wallapp.log.Log

/**
 * Serves the content API encryption key from Firestore, and everything else from [delegate].
 *
 * Upstream keeps `key1` in Firebase Cloud Storage, where the Storage rules make it readable only
 * by a signed-in user. Cloud Storage needs the paid Blaze plan since 2026-02-03; Firestore is free
 * on Spark and its rules give the same guarantee, so the key lives there instead and the encrypted
 * content itself is served from a static host. See docs/CHANGES.md.
 *
 * The document holds the obfuscated key exactly as the `key1` file did, so
 * `RemoteApiSecretManager.getDeobfuscatedKey` still applies unchanged.
 */
class FirestoreKeyApiDownloader(
    private val delegate: FirebaseStorageDownloader,
    private val keyFileName: String = "key1",
    private val collectionPath: String = "content_config",
    private val documentPath: String = "encryption",
    private val keyField: String = "key",
) : FirebaseStorageDownloader {

    override fun downloadFile(path: String, appendData: Boolean): Flow<DownloadState> {
        // The key file is the only one fetched without the `.data` suffix.
        if (appendData || !path.endsWith(keyFileName)) {
            return delegate.downloadFile(path, appendData)
        }
        return flow {
            emit(DownloadState.DownloadStarting(path))
            val key = readKey(path)
            if (key.isNullOrBlank()) {
                emit(DownloadState.Error(url = path, message = "No content key at $collectionPath/$documentPath"))
            } else {
                emit(DownloadState.Success(url = path, dataHandle = DataHandle.fromBytesCompat(key.encodeToByteArray())))
            }
        }
    }

    private suspend fun readKey(path: String): String? {
        return try {
            val snapshot = Firebase.firestore
                .collection(collectionPath)
                .document(documentPath)
                .get()
            if (snapshot.exists) snapshot.get<String?>(keyField) else null
        } catch (e: Exception) {
            Log.e("[FirestoreKeyApiDownloader] Failed to read content key: ${e.message}")
            null
        }
    }
}
