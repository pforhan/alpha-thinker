package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.di.PlatformContext
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import org.w3c.dom.Worker

private const val DATABASE_NAME = "alphathinker.db"

actual fun provideDatabaseBuilder(context: PlatformContext): RoomDatabase.Builder<AppDatabase> {
  return Room.databaseBuilder<AppDatabase>(
    name = DATABASE_NAME,
    factory = { AppDatabaseConstructor.initialize() }
  ).setDriver(WebWorkerSQLiteDriver(createSQLiteWorker()))
}

private var storageInstance: Storage? = null

actual fun provideStorage(context: PlatformContext): Storage {
  return storageInstance ?: RoomStorage(getRoomDatabase(provideDatabaseBuilder(context))).also {
    storageInstance = it
  }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun createSQLiteWorker(): Worker =
  js("""new Worker(new URL("sqlite-wasm-worker/worker.js", import.meta.url))""")
