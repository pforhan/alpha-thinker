package alphainterplanetary.thinker.util

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

actual fun randomUUID(): String {
  @OptIn(ExperimentalUuidApi::class)
  return Uuid.random().toString()
}

actual fun now(): Instant = Clock.System.now()
