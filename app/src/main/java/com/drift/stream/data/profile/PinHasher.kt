package com.drift.stream.data.profile

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

const val PIN_PBKDF2_ITERATIONS = 600_000
const val PIN_SALT_BYTES = 16
const val PIN_KEY_BITS = 256
private const val ALGORITHM = "PBKDF2WithHmacSHA256"

open class PinHasher(
    val defaultIterations: Int = PIN_PBKDF2_ITERATIONS,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val secureRandom = SecureRandom()

    fun generateSalt(): String {
        val bytes = ByteArray(PIN_SALT_BYTES)
        secureRandom.nextBytes(bytes)
        return Base64.getEncoder().withoutPadding().encodeToString(bytes)
    }

    suspend fun hash(
        pin: String,
        salt: String,
        iterations: Int = defaultIterations,
    ): String =
        withContext(defaultDispatcher) {
            val saltBytes = Base64.getDecoder().decode(salt)
            val spec = PBEKeySpec(pin.toCharArray(), saltBytes, iterations, PIN_KEY_BITS)
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            val keyBytes = factory.generateSecret(spec).encoded
            Base64.getEncoder().withoutPadding().encodeToString(keyBytes)
        }

    open suspend fun verify(
        pin: String,
        salt: String,
        iterations: Int,
        expectedHash: String,
    ): Boolean =
        withContext(defaultDispatcher) {
            val actualHash = hash(pin, salt, iterations)
            val expectedBytes = Base64.getDecoder().decode(expectedHash)
            val actualBytes = Base64.getDecoder().decode(actualHash)
            MessageDigest.isEqual(expectedBytes, actualBytes)
        }
}
