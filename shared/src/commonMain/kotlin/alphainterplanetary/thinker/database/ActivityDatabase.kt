package alphainterplanetary.thinker.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import kotlinx.coroutines.Dispatchers

/**
 * The app's activity log database. Stores runtime/activity events separately
 * from [AppDatabase] (which holds projects, questions, answers, rounds, and
 * settings). Deliberately isolated so that clearing the log never affects core
 * project data.
 */
@Database(
  entities = [LogEntryEntity::class],
  version = 3,
  exportSchema = false,
)
@ConstructedBy(ActivityDatabaseConstructor::class)
abstract class ActivityDatabase : RoomDatabase() {
  abstract fun logDao(): LogDao
}

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object ActivityDatabaseConstructor : RoomDatabaseConstructor<ActivityDatabase> {
  override fun initialize(): ActivityDatabase
}

fun getActivityDatabase(builder: RoomDatabase.Builder<ActivityDatabase>): ActivityDatabase = builder
  .fallbackToDestructiveMigration()
  .setQueryCoroutineContext(Dispatchers.Default)
  .build()