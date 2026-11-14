package com.drift.stream.data.profile

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

@OptIn(ExperimentalCoroutinesApi::class)
class PinHasherTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val hasher = PinHasher(defaultIterations = 1_000, defaultDispatcher = testDispatcher)

    @Test
    fun HS1_hash_isDeterministic_forSamePinSaltIterations() =
        runTest {
            val salt = hasher.generateSalt()
            val hash1 = hasher.hash("1234", salt, 1_000)
            val hash2 = hasher.hash("1234", salt, 1_000)
            assertEquals(hash1, hash2)
        }

    @Test
    fun HS2_hash_differs_forDifferentSalt() =
        runTest {
            val salt1 = hasher.generateSalt()
            val salt2 = hasher.generateSalt()
            val hash1 = hasher.hash("1234", salt1, 1_000)
            val hash2 = hasher.hash("1234", salt2, 1_000)
            assertNotEquals(hash1, hash2)
        }

    @Test
    fun HS3_verify_correctPin_true() =
        runTest {
            val salt = hasher.generateSalt()
            val hash = hasher.hash("1234", salt, 1_000)
            assertTrue(hasher.verify("1234", salt, 1_000, hash))
        }

    @Test
    fun HS4_verify_wrongPin_false() =
        runTest {
            val salt = hasher.generateSalt()
            val hash = hasher.hash("1234", salt, 1_000)
            assertFalse(hasher.verify("9999", salt, 1_000, hash))
        }

    @Test
    fun HS5_hash_decodesTo32Bytes() =
        runTest {
            val salt = hasher.generateSalt()
            val hash = hasher.hash("1234", salt, 1_000)
            val decoded = Base64.getDecoder().decode(hash)
            assertEquals(32, decoded.size)
        }

    @Test
    fun HS6_productionConstants() {
        assertEquals(10_000, PIN_PBKDF2_ITERATIONS)
        assertEquals(16, PIN_SALT_BYTES)
        assertEquals(256, PIN_KEY_BITS)
    }

    @Test
    fun HS7_generateSalt_is16Bytes_andDiffersBetweenCalls() {
        val salt1 = hasher.generateSalt()
        val salt2 = hasher.generateSalt()

        val bytes1 = Base64.getDecoder().decode(salt1)
        val bytes2 = Base64.getDecoder().decode(salt2)

        assertEquals(16, bytes1.size)
        assertEquals(16, bytes2.size)
        assertNotEquals(salt1, salt2)
    }
}
