package alphainterplanetary.thinker.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import kotlinx.coroutines.Dispatchers

/**
 * The app's primary Room database containing projects, questions, answers,
 * rounds, and settings. This is distinct from [ActivityDatabase], which stores
 * the activity log in another file.
 */
@Database(
  entities = [ProjectEntity::class, QuestionEntity::class, AnswerEntity::class, RoundEntity::class, SettingsEntity::class],
  version = 10,
  exportSchema = false
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun projectDao(): ProjectDao
  abstract fun questionDao(): QuestionDao
  abstract fun answerDao(): AnswerDao
  abstract fun roundDao(): RoundDao
  abstract fun settingsDao(): SettingsDao
}

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
  override fun initialize(): AppDatabase
}

/**
 * The app's Room database. Schema changes bump [version] and rely on
 * [fallbackToDestructiveMigration] rather than a hand-written `Migration`: a
 * migration body receives a per-target `SQLiteConnection` (JVM exposes a plain
 * `prepare`, web a suspending one), which this shared `commonMain` builder
 * cannot express, so an upgrade rebuilds the local database.
 */
fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase {
  return builder
    .fallbackToDestructiveMigration()
    .setQueryCoroutineContext(Dispatchers.Default)
    .build()
}