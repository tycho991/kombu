package io.github.tycho991.kombu.durationparser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class IsoDurationTest {
    @Nested
    inner class Parse {
        @Test
        fun `reads the forms recipes use`() {
            assertEquals(1.hours + 5.minutes, IsoDuration.parse("PT1H5M"))
            assertEquals(45.minutes, IsoDuration.parse("PT45M"))
            assertEquals(2.hours, IsoDuration.parse("PT2H"))
            assertEquals(30.seconds, IsoDuration.parse("PT30S"))
        }

        @Test
        fun `accepts leading zeros, days, lower case and fractions`() {
            assertEquals(1.hours + 5.minutes, IsoDuration.parse("PT01H05M00S"))
            assertEquals(26.hours, IsoDuration.parse("P1DT2H"))
            assertEquals(24.hours, IsoDuration.parse("P1D"))
            assertEquals(30.minutes, IsoDuration.parse("pt30m"))
            assertEquals(1500.milliseconds, IsoDuration.parse("PT1.5S"))
            assertEquals(1500.milliseconds, IsoDuration.parse("PT1,5S"))
            assertEquals(1.seconds + 1.nanoseconds, IsoDuration.parse("PT1.0000000019S"))
        }

        @Test
        fun `ignores surrounding whitespace`() {
            assertEquals(10.minutes, IsoDuration.parse(" PT10M\n"))
        }

        @Test
        fun `reads an all-zero duration as zero`() {
            assertEquals(Duration.ZERO, IsoDuration.parse("PT0S"))
            assertEquals(Duration.ZERO, IsoDuration.parse("PT0H0M"))
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "", " ", "P", "PT", "P1DT", "1H", "-PT5M", "PT5X", "PT1.5M", "P1W", "P1Y", "PT1H5M30", "10 min",
                "PT99999999999999999999H",
            ],
        )
        fun `rejects anything else`(text: String) {
            assertNull(IsoDuration.parse(text))
        }

        @Test
        fun `rejects durations too long to represent`() {
            assertNull(IsoDuration.parse("P9223372036854775807D"))
        }
    }

    @Nested
    inner class Format {
        @Test
        fun `writes the shortest form in hours and minutes`() {
            assertEquals("PT0M", IsoDuration.format(Duration.ZERO))
            assertEquals("PT45M", IsoDuration.format(45.minutes))
            assertEquals("PT1H5M", IsoDuration.format(1.hours + 5.minutes))
            assertEquals("PT2H", IsoDuration.format(2.hours))
            assertEquals("PT26H", IsoDuration.format(26.hours))
        }

        @Test
        fun `rounds seconds to the nearest minute`() {
            assertEquals("PT0M", IsoDuration.format(29.seconds))
            assertEquals("PT1M", IsoDuration.format(30.seconds))
            assertEquals("PT1H", IsoDuration.format(59.minutes + 45.seconds))
        }

        @Test
        fun `refuses negative and infinite durations`() {
            assertThrows<IllegalArgumentException> { IsoDuration.format((-1).minutes) }
            assertThrows<IllegalArgumentException> { IsoDuration.format(Duration.INFINITE) }
        }
    }

    @ParameterizedTest
    @CsvSource("PT1H5M", "PT45M", "PT2H", "PT26H", "PT0M", "PT10H59M")
    fun `parse and format round-trip`(text: String) {
        assertEquals(text, IsoDuration.format(IsoDuration.parse(text)!!))
    }

    @Test
    fun `formats and parses minutes back to the same duration`() {
        for (minutes in listOf(0, 1, 59, 60, 61, 125, 1440, 1565)) {
            assertEquals(minutes.minutes, IsoDuration.parse(IsoDuration.format(minutes.minutes)), "$minutes min")
        }
    }
}
