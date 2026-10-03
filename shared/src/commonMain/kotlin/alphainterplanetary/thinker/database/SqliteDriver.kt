package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.di.PlatformContext
import androidx.sqlite.SQLiteDriver

/**
 * The one genuinely per-target piece of the database builder: every native
 * target bundles SQLite, while the web targets run it in a worker so the UI
 * thread stays free.
 */
expect fun sqliteDriver(): SQLiteDriver