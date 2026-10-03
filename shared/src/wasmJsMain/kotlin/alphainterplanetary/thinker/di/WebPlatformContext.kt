package alphainterplanetary.thinker.di

/**
 * Web platform: the SQLite driver resolves a bare file name itself (in the
 * page's OPFS), so there is no path to hand it.
 */
class WebPlatformContext : PlatformContext {
  override fun databaseFile(name: String): String = name
}