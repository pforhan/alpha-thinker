package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.di.PlatformContext
import androidx.room3.RoomDatabase

/**
 * Room/SQLite is not wired up on the web target yet — the shared Room layer still compiles for
 * js, but storage stays [InMemoryStorage] (data does not survive a page reload). See
 * IMPLEMENTATION-PLAN.md Phase 2.6 for the `sqlite-web` / `WebWorkerSQLiteDriver` follow-up.
 */
actual fun provideDatabaseBuilder(context: PlatformContext): RoomDatabase.Builder<AppDatabase> {
  error("Room persistence is not wired up on the web target; storage is InMemoryStorage here.")
}

private var storageInstance: Storage = InMemoryStorage()

actual fun provideStorage(context: PlatformContext): Storage = storageInstance