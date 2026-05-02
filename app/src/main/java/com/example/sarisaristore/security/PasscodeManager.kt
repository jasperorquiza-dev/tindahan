package com.example.sarisaristore.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PasscodeManager {
    private val secureRandom = SecureRandom()

    fun generateSalt(): String {
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)
        return Base64.getEncoder().withoutPadding().encodeToString(salt)
    }

    fun hashPasscode(passcode: String, saltBase64: String): String {
        require(passcode.matches(PIN_REGEX)) { "Passcode must be 4 digits." }
        val salt = Base64.getDecoder().decode(saltBase64)
        val algorithm = runCatching {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        }.getOrElse {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        }
        val spec = PBEKeySpec(passcode.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return Base64.getEncoder().withoutPadding().encodeToString(algorithm.generateSecret(spec).encoded)
    }

    fun verifyPasscode(passcode: String, saltBase64: String, expectedHash: String): Boolean =
        runCatching { hashPasscode(passcode, saltBase64) == expectedHash }.getOrDefault(false)

    companion object {
        private const val SALT_BYTES = 24
        private const val ITERATIONS = 120_000
        private const val KEY_LENGTH_BITS = 256
        private val PIN_REGEX = Regex("""\d{4}""")
    }
}
