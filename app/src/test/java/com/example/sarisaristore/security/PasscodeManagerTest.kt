package com.example.sarisaristore.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasscodeManagerTest {
    private val passcodeManager = PasscodeManager()

    @Test
    fun `same passcode and salt produce stable hash`() {
        val salt = passcodeManager.generateSalt()

        val firstHash = passcodeManager.hashPasscode("1234", salt)
        val secondHash = passcodeManager.hashPasscode("1234", salt)

        assertTrue(firstHash.isNotBlank())
        assertTrue(firstHash == secondHash)
    }

    @Test
    fun `different salts produce different hashes`() {
        val firstSalt = passcodeManager.generateSalt()
        val secondSalt = passcodeManager.generateSalt()

        val firstHash = passcodeManager.hashPasscode("1234", firstSalt)
        val secondHash = passcodeManager.hashPasscode("1234", secondSalt)

        assertNotEquals(firstHash, secondHash)
    }

    @Test
    fun `verifyPasscode returns true only for correct pin`() {
        val salt = passcodeManager.generateSalt()
        val hash = passcodeManager.hashPasscode("1234", salt)

        assertTrue(passcodeManager.verifyPasscode("1234", salt, hash))
        assertFalse(passcodeManager.verifyPasscode("1111", salt, hash))
    }
}
