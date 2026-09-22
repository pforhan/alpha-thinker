package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.di.PlatformContext
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import org.w3c.dom.Worker

private const val ACTIVITY_DATABASE_NAME = "alphathinker-activity.db"

actual fun provideActivityDatabaseBuilder(
  context: PlatformContext,
): RoomDatabase.Builder<ActivityDatabase> {
  return Room.databaseBuilder<ActivityDatabase>(
    name = ACTIVITY_DATABASE_NAME,
    factory = { ActivityDatabaseConstructor.initialize() }
  ).setDriver(WebWorkerSQLiteDriver(createSQLiteWorker()))
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun createSQLiteWorker(): Worker =
  js("""new Worker(new URL("sqlite-wasm-worker/worker.js", import.meta.url))""")