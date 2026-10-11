package com.siteledger.app.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun `parseKes accepts plain and grouped whole shillings`() {
        assertEquals(12500L, Money.parseKes("12,500"))
        assertEquals(12500L, Money.parseKes("12500"))
        assertEquals(0L, Money.parseKes("0"))
        assertEquals(100L, Money.parseKes("  100  "))
    }

    @Test
    fun `parseKes rejects invalid or negative input`() {
        assertNull(Money.parseKes(""))
        assertNull(Money.parseKes("  "))
        assertNull(Money.parseKes("abc"))
        assertNull(Money.parseKes("-5"))
        assertNull(Money.parseKes("12.5"))
    }

    @Test
    fun `parseQuantityMilli scales decimals by one thousand`() {
        assertEquals(3500L, Money.parseQuantityMilli("3.5"))
        assertEquals(250L, Money.parseQuantityMilli("0.25"))
        assertEquals(1000L, Money.parseQuantityMilli("1"))
        assertEquals(500L, Money.parseQuantityMilli(".5"))
        assertEquals(1200L, Money.parseQuantityMilli("1.20"))
        assertEquals(0L, Money.parseQuantityMilli("0"))
    }

    @Test
    fun `parseQuantityMilli rejects malformed input`() {
        assertNull(Money.parseQuantityMilli(""))
        assertNull(Money.parseQuantityMilli("abc"))
        assertNull(Money.parseQuantityMilli("1.2345"))
        assertNull(Money.parseQuantityMilli("1.2.3"))
        assertNull(Money.parseQuantityMilli("-2"))
    }

    @Test
    fun `formatQuantity renders trimmed decimals`() {
        assertEquals("1", Money.formatQuantity(1000))
        assertEquals("3.5", Money.formatQuantity(3500))
        assertEquals("0.25", Money.formatQuantity(250))
        assertEquals("1.234", Money.formatQuantity(1234))
    }

    @Test
    fun `amountFromQuantity multiplies and rounds to the nearest shilling`() {
        assertEquals(350L, Money.amountFromQuantity(3500, 100))
        assertEquals(500L, Money.amountFromQuantity(1500, 333))
        assertEquals(0L, Money.amountFromQuantity(0, 100))
    }

    @Test
    fun `formatKes groups thousands and prefixes the currency`() {
        assertEquals("KSh 12,500", Money.formatKes(12500))
        assertEquals("KSh 0", Money.formatKes(0))
        assertEquals("KSh 1,234,567", Money.formatKes(1_234_567))
    }
}
