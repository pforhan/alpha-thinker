package alphainterplanetary.thinker.util

import kotlinx.datetime.Instant
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
actual fun now(): Instant = Clock.System.now()

actual fun randomUUID(): String = UUID.randomUUID().toString()
