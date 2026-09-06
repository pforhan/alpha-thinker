package alphainterplanetary.thinker.di

/**
 * Web (wasmJs) platform context. There is no Android [android.content.Context] to back the
 * database, so the web build falls back to in-memory storage.
 */
class WebPlatformContext : PlatformContext
