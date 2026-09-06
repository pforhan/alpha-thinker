package alphainterplanetary.thinker.util

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.time.Instant
import kotlin.time.Clock

actual fun now(): Instant = Clock.System.now()

@OptIn(ExperimentalUuidApi::class)
actual fun randomUUID(): String = Uuid.random().toString()