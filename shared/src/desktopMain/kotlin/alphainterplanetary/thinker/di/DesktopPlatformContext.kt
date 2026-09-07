package alphainterplanetary.thinker.di

/**
 * Desktop (JVM) platform context. No Android [android.content.Context] is needed; the
 * database is backed by a SQLite file in the user's home directory.
 */
class DesktopPlatformContext : PlatformContext
