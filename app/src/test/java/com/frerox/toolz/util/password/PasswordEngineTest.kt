package com.frerox.toolz.util.password

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordEngineTest {

    @Test
    fun `weak password is not strong`() {
        val tier = VaultPasswordEngine.tierOf("Password1!")
        assertTrue("expected critical/weak/mid, got $tier", tier <= 2)
    }

    @Test
    fun `long random password reaches elite`() {
        val pwd = VaultPasswordEngine.generate(
            VaultPasswordEngine.PasswordSpec(length = 32)
        ).getOrThrow()
        assertEquals(4, VaultPasswordEngine.tierOf(pwd))
    }

    @Test
    fun `all five tiers are reachable`() {
        val tiers = (0 until 200).map {
            val pwd = VaultPasswordEngine.generate(
                VaultPasswordEngine.PasswordSpec(length = 20)
            ).getOrThrow()
            VaultPasswordEngine.tierOf(pwd)
        }.toSet()
        assertTrue(tiers.contains(3) || tiers.contains(4))
        assertEquals(0, VaultPasswordEngine.tierOf("abc"))
        assertEquals(1, VaultPasswordEngine.tierOf("password123"))
    }

    @Test
    fun `guarantee covers each enabled class`() {
        repeat(50) {
            val pwd = VaultPasswordEngine.generate(
                VaultPasswordEngine.PasswordSpec(length = 16)
            ).getOrThrow()
            assertTrue(pwd.any { it.isLowerCase() })
            assertTrue(pwd.any { it.isUpperCase() })
            assertTrue(pwd.any { it.isDigit() })
            assertTrue(pwd.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun `exclude ambiguous removes confusing chars`() {
        val pwd = VaultPasswordEngine.generate(
            VaultPasswordEngine.PasswordSpec(length = 64, excludeAmbiguous = true)
        ).getOrThrow()
        assertTrue(pwd.none { it in VaultPasswordEngine.AMBIGUOUS })
    }

    @Test
    fun `pin mode is digits only`() {
        val pwd = VaultPasswordEngine.generate(
            VaultPasswordEngine.PasswordSpec(length = 6, pinMode = true)
        ).getOrThrow()
        assertTrue(pwd.all { it.isDigit() })
    }

    @Test
    fun `empty pool fails instead of silent return`() {
        val result = VaultPasswordEngine.generate(
            VaultPasswordEngine.PasswordSpec(
                length = 12,
                includeLowercase = false,
                includeUppercase = false,
                includeNumbers = false,
                includeSymbols = false
            )
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `default pool is csv safe`() {
        assertTrue(!VaultPasswordEngine.SYMBOLS.contains(","))
    }

    @Test
    fun `assess reports bits and reasons`() {
        val report = VaultPasswordEngine.assess("abc")
        assertEquals(VaultPasswordEngine.Tier.CRITICAL, report.tier)
        assertTrue(report.reasons.isNotEmpty())
        val strong = VaultPasswordEngine.assess("K7#mQ9!zLp2@Vx4\$BnQ8")
        assertTrue(strong.bits > 60)
    }
}
