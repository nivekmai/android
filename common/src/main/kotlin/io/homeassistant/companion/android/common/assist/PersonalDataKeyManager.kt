package io.homeassistant.companion.android.common.assist

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val KEY_ALIAS = "AssistPersonalDataSigningKeyV1"
private const val KEYSTORE = "AndroidKeyStore"
private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

/** Owns the non-exportable key used to authorize personal-data Assist sessions. */
object PersonalDataKeyManager {

    suspend fun publicKeyBase64(): String = withContext(Dispatchers.IO) {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        ensureKey(keyStore)
        val publicKey = requireNotNull(keyStore.getCertificate(KEY_ALIAS)).publicKey
        Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    suspend fun signBase64(payload: String): String = withContext(Dispatchers.IO) {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        ensureKey(keyStore)
        val privateKey = requireNotNull(keyStore.getKey(KEY_ALIAS, null)) as PrivateKey
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM).apply {
            initSign(privateKey)
            update(payload.toByteArray(Charsets.UTF_8))
        }.sign()
        Base64.encodeToString(signature, Base64.NO_WRAP)
    }

    private fun ensureKey(keyStore: KeyStore) {
        if (keyStore.containsAlias(KEY_ALIAS)) return

        fun generate(strongBox: Boolean) {
            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN,
            )
                .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
            if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setIsStrongBoxBacked(true)
            }
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE).apply {
                initialize(builder.build())
                generateKeyPair()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                generate(strongBox = true)
                return
            } catch (_: Exception) {
                // StrongBox is optional even on devices that report Android 9+.
            }
        }
        generate(strongBox = false)
    }
}
