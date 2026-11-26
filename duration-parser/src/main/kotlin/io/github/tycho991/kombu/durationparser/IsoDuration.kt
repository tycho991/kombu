package io.github.tycho991.kombu.durationparser

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Reads and writes the ISO 8601 durations that schema.org recipes use for their times, such as `PT1H5M` for
 * a cooking time of one hour and five minutes.
 *
 * Only the forms recipes use are supported: days, hours, minutes and seconds, `P[nD][T[nH][nM][n[.n]S]]`.
 * Years, months and weeks are not, because their length is not fixed. Both functions are stateless and
 * thread-safe.
 */
public object IsoDuration {
    private val PATTERN = Regex(
        """P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)(?:[.,](\d+))?S)?)?""",
        RegexOption.IGNORE_CASE,
    )
    private const val DAYS = 1
    private const val HOURS = 2
    private const val MINUTES = 3
    private const val SECONDS = 4
    private const val FRACTION = 5
    private const val NANO_DIGITS = 9
    private const val MINUTES_PER_HOUR = 60
    private val HALF_MINUTE = 30.seconds

    /**
     * Parses [text] as an ISO 8601 duration.
     *
     * Letters may be in either case, numbers may have leading zeros (`PT01H05M00S`), days count as 24 hours
     * (`P1DT2H` is 26 hours), and seconds may have a fraction (`PT1.5S`). Surrounding whitespace is ignored.
     *
     * @return the duration, or `null` when [text] is not such a duration. This includes the empty text, a
     *   designator without any component (`P`, `PT`), a negative duration (`-PT5M`), a missing `P` (`1H`),
     *   an unknown designator (`PT5X`) and numbers too large to represent.
     */
    public fun parse(text: String): Duration? {
        val trimmed = text.trim()
        val groups = PATTERN.matchEntire(trimmed)?.groupValues.orEmpty()
        val hasComponent = groups.isNotEmpty() && (DAYS..SECONDS).any { groups[it].isNotEmpty() }
        // A "T" must be followed by at least one time component: "P1DT" is as invalid as "PT".
        val danglingTime = trimmed.endsWith("T", ignoreCase = true)
        if (!hasComponent || danglingTime) return null
        return runCatching {
            component(groups[DAYS]).days +
                component(groups[HOURS]).hours +
                component(groups[MINUTES]).minutes +
                component(groups[SECONDS]).seconds +
                fraction(groups[FRACTION])
        }.getOrNull()?.takeIf(Duration::isFinite)
    }

    /**
     * Writes [duration] in the shortest ISO 8601 form in hours and minutes, for example `PT1H5M`, `PT45M`
     * or `PT26H`. A zero duration is written as `PT0M`.
     *
     * Seconds are rounded to the nearest minute, half a minute rounding up, because recipe times are
     * entered and shown in minutes.
     *
     * @throws IllegalArgumentException if [duration] is negative or infinite.
     */
    public fun format(duration: Duration): String {
        require(duration.isFinite() && !duration.isNegative()) { "Recipe times are finite and not negative" }
        val totalMinutes = (duration + HALF_MINUTE).inWholeMinutes
        val hours = totalMinutes / MINUTES_PER_HOUR
        val minutes = totalMinutes % MINUTES_PER_HOUR
        return buildString {
            append("PT")
            if (hours > 0) append(hours).append('H')
            if (minutes > 0 || hours == 0L) append(minutes).append('M')
        }
    }

    private fun component(digits: String): Long = if (digits.isEmpty()) 0 else digits.toLong()

    private fun fraction(digits: String): Duration =
        if (digits.isEmpty()) Duration.ZERO else digits.take(NANO_DIGITS).padEnd(NANO_DIGITS, '0').toLong().nanoseconds
}
