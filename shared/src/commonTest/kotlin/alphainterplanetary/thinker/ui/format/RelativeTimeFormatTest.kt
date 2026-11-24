package alphainterplanetary.thinker.ui.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RelativeTimeFormatTest {
  private val nowMillis = 1_700_000_000_000L // 2023-11-14T22:13:20Z
  private val now = Instant.fromEpochMilliseconds(nowMillis) // 2023-11-14T22:13:20Z

  @Test
  fun formatsSecondsMinutesOrHoursAgoUnderOneDay() {
    checkFormat("0s ago", 0.seconds)
    checkFormat("1s ago", 1.seconds)
    checkFormat("59s ago", 59.seconds)
    checkFormat("1m ago", 60.seconds)
    checkFormat("1m ago", 60.5.seconds)
    checkFormat("59m ago", 59.minutes)
    checkFormat("1h ago", 60.minutes)
    checkFormat("1h ago", 60.5.minutes)
    checkFormat("23h ago", 23.hours)
  }

  @Test
  fun formatsDay_from1To7Days() {
    checkFormat("1d ago", 1.days)
    checkFormat("1d ago", 1.01.days)
    checkFormat("6d ago", 6.95.days)
    checkFormat("7d ago", 7.days)
  }

  @Test
  fun formatsMonthDay_from8To90Days() {
    checkFormat("MonthDay", 8.days)
    checkFormat("MonthDay", 89.days)
    checkFormat("MonthDay", 90.days)
  }

  @Test
  fun formatsMonthYear_from90DaysTo18Months() {
    checkFormat("MonthYear", 91.days)
    checkFormat("MonthYear", 544.days)
    checkFormat("MonthYear", 545.days)
  }

  @Test
  fun formatsYear_after18Months() {
    checkFormat("YearOnly", 546.days)
    checkFormat("YearOnly", 10_000.days)
  }

  private fun checkFormat(expected: String, toRewind: Duration) {
    assertEquals(
      expected = expected,
      actual = formatRelativeTime(
        instant = Instant.fromEpochMilliseconds(nowMillis - toRewind.inWholeMilliseconds),
        now = now,
        absoluteFormatter = { _, style -> style.name },
      )
    )
  }
}
