package alphainterplanetary.thinker.database

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import org.w3c.dom.Worker

actual fun sqliteDriver(): SQLiteDriver = WebWorkerSQLiteDriver(createSQLiteWorker())

@OptIn(ExperimentalWasmJsInterop::class)
private fun createSQLiteWorker(): Worker =
  js("""new Worker(new URL("sqlite-wasm-worker/worker.js", import.meta.url))""")
