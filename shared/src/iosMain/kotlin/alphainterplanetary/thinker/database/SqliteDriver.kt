package alphainterplanetary.thinker.database

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

actual fun sqliteDriver(): SQLiteDriver = BundledSQLiteDriver()
