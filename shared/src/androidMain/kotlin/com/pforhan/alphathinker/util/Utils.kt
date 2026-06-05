package com.pforhan.alphathinker.util

import java.util.UUID

actual fun randomUUID(): String = UUID.randomUUID().toString()
