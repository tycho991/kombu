package io.github.tycho991.kombu.ui.recipes

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.tycho991.kombu.R
import io.github.tycho991.kombu.cookbookclient.RecipeTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val MINUTES_PER_HOUR = 60
private val HalfMinute = 30.seconds

/**
 * A recipe time in the units of the app language (design D4 of recipe-browse-edit): `1 Std. 5 Min.` in German,
 * `1 hr 5 min` in English. Seconds are rounded to the nearest minute; a time under half a minute shows as
 * `< 1 min`. A time Kombu cannot read is shown as it is stored.
 */
@Composable
fun recipeTimeText(time: RecipeTime): String = when (time) {
    is RecipeTime.Raw -> time.text
    is RecipeTime.Known -> durationText(time.duration)
}

@Composable
private fun durationText(duration: Duration): String {
    if (duration < HalfMinute) return stringResource(R.string.duration_under_minute)
    val totalMinutes = (duration + HalfMinute).inWholeMinutes.toInt()
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return when {
        hours == 0 -> stringResource(R.string.duration_minutes, minutes)
        minutes == 0 -> stringResource(R.string.duration_hours, hours)
        else -> stringResource(R.string.duration_hours_minutes, hours, minutes)
    }
}
