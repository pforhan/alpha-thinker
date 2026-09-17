package alphainterplanetary.thinker.util

import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

actual fun now(): Instant = Clock.System.now()

actual fun randomUUID(): String = UUID.randomUUID().toString()
