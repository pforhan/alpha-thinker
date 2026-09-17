package alphainterplanetary.thinker.util

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

actual fun now(): Instant = Clock.System.now()

@OptIn(ExperimentalUuidApi::class)
actual fun randomUUID(): String = Uuid.random().toString()