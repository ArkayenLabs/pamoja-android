package com.pamoja.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordPolicyTest {

    @Test
    fun rejectsPasswordsShorterThanEightCharacters() {
        assertFalse(PasswordPolicy.isAccepted("Walk1!"))
    }

    @Test
    fun acceptsEightCharacterPasswords() {
        assertTrue(PasswordPolicy.isAccepted("walkings"))
    }

    @Test
    fun acceptsLongerPassphrases() {
        assertTrue(PasswordPolicy.isAccepted("Walk together 7 days"))
    }
}
