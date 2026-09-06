package alphainterplanetary.thinker.util

import kotlinx.datetime.Instant
import java.util.UUID
import kotlin.time.Clock

actual fun now(): Instant = Clock.System.now()

actual fun randomUUID(): String = UUID.randomUUID().toString()
