package wallapp.security

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the ciphertext layout that tools/encrypt_api.py produces, so a Python-built
 * content export stays readable by [RemoteApiDefault]'s decryption path.
 *
 * The fixture below was produced by:
 *   python tools/encrypt_api.py <api dir> --fixture compat.json
 *
 * If this fails, the Python encryptor and cryptography-kotlin disagree about how the
 * nonce is carried, and tools/encrypt_api.py must be fixed — not this test.
 *
 * bkgapp addition, not upstream. See docs/CHANGES.md.
 */
class PythonCiphertextCompatTest {

    private val key = "bd446249-1c66-4a67-b49b-c605f922b5cb"
    private val plaintext = "bkgapp python ciphertext fixture"
    // RemoteApiEncryptionConfigDefault.initializationVector, passed as associated data.
    private val associatedData =
        byteArrayOf(47, -93, 98, 49, 107, 77, -74, 68, -17, -105, 89, 86)
    private val ciphertextBase64 =
        "8ElgbA6+7RTIeD/za7Lb2oDqWtlvz42Bo+4FlyD2HUZbUBySxUsG046s2axlqxRSkYpo3M9/tuZoTVPh"

    @Test
    fun decrypts_a_python_encrypted_payload() = runTest {
        val encryptionManager = createEncryptionManager()
        val result = EncryptionResult(
            data = ciphertextBase64.decodeBase64(),
            initializationVector = associatedData,
        )

        val decrypted = encryptionManager.decrypt(result, key)

        assertEquals(plaintext, decrypted.decodeToString())
    }

    @Test
    fun kotlin_ciphertext_has_the_layout_python_assumes() = runTest {
        val encryptionManager = createEncryptionManager()
        val data = plaintext.encodeToByteArray()

        val encrypted = encryptionManager.encrypt(data, key, associatedData)

        // 12-byte nonce prefix + ciphertext + 16-byte tag
        assertEquals(data.size + 12 + 16, encrypted.data.size)
        assertTrue(encryptionManager.decrypt(encrypted, key).contentEquals(data))
    }

    private fun String.decodeBase64(): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val cleaned = trimEnd('=')
        val out = ArrayList<Byte>(cleaned.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (character in cleaned) {
            buffer = (buffer shl 6) or alphabet.indexOf(character)
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer shr bits) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }
}
