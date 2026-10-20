package com.llewtonsi.aneonyx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ArithmeticParserTest {
    @Test
    fun appliesOperatorPrecedenceAndParentheses() {
        assertEquals(14.0, ArithmeticParser("2 + 3 * 4").parse(), 0.0)
        assertEquals(20.0, ArithmeticParser("(2 + 3) * 4").parse(), 0.0)
    }

    @Test
    fun supportsUnarySignsAndRemainder() {
        assertEquals(-2.0, ArithmeticParser("-(7 % 5)").parse(), 0.0)
        assertEquals(3.0, ArithmeticParser("+8 % 5").parse(), 0.0)
    }

    @Test
    fun rejectsDivisionByZeroAndTrailingInput() {
        assertThrows(IllegalArgumentException::class.java) {
            ArithmeticParser("1 / 0").parse()
        }
        assertThrows(IllegalArgumentException::class.java) {
            ArithmeticParser("2 3").parse()
        }
    }

    @Test
    fun rejectsNonFiniteResults() {
        assertThrows(IllegalArgumentException::class.java) {
            ArithmeticParser("9".repeat(400)).parse()
        }
    }

    @Test
    fun rejectsExcessiveNesting() {
        val expression = "(".repeat(100) + "1" + ")".repeat(100)
        assertThrows(IllegalArgumentException::class.java) {
            ArithmeticParser(expression).parse()
        }
    }
}
