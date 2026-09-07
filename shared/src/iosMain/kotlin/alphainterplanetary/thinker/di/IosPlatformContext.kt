package alphainterplanetary.thinker.di

/**
 * iOS platform context. No Android [android.content.Context] is needed; the
 * database is backed by a SQLite file in the app's Documents directory.
 */
class IosPlatformContext : PlatformContext
