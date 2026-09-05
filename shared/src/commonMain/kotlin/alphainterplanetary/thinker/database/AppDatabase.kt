package alphainterplanetary.thinker.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import alphainterplanetary.thinker.di.PlatformContext
import androidx.room.migration.Migration
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
  entities = [ProjectEntity::class, QuestionEntity::class, AnswerEntity::class],
  version = 3,
  exportSchema = false
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun projectDao(): ProjectDao
  abstract fun questionDao(): QuestionDao
  abstract fun answerDao(): AnswerDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
  override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
    connection.prepare("ALTER TABLE questions ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
      .step()
    connection.prepare(
      """
      UPDATE questions SET sortOrder = (
        SELECT COUNT(*) FROM questions AS q2
        WHERE q2.projectId = questions.projectId AND q2.createdAt < questions.createdAt
      )
      """.trimIndent()
    ).step()
  }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
  override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
    connection.prepare("ALTER TABLE questions ADD COLUMN contextId TEXT NOT NULL DEFAULT ''")
      .step()
  }
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>

expect fun provideDatabaseBuilder(context: PlatformContext): RoomDatabase.Builder<AppDatabase>

fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase {
  return builder
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
}
