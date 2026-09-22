package alphainterplanetary.thinker.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import kotlinx.coroutines.Dispatchers

/**
 * The engine activity event log's own database, deliberately separate from
 * [AppDatabase] (see ENG-DESIGN.md schema item 4): it grows and prunes on its
 * own schedule, and a wholesale "clear log" never touches
 * projects/questions/settings.
 */
@Database(
  entities = [ActivityEventEntity::class],
  version = 1,
  exportSchema = false,
)
@ConstructedBy(ActivityDatabaseConstructor::class)
abstract class ActivityDatabase : RoomDatabase() {
  abstract fun activityDao(): ActivityDao
}

@Suppress("KotlinNoActualForExpect")
expect object ActivityDatabaseConstructor : RoomDatabaseConstructor<ActivityDatabase> {
  override fun initialize(): ActivityDatabase
}

fun getActivityDatabase(builder: RoomDatabase.Builder<ActivityDatabase>): ActivityDatabase = builder
  .fallbackToDestructiveMigration()
  .setQueryCoroutineContext(Dispatchers.Default)
  .build()