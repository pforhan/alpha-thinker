package alphainterplanetary.thinker.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import kotlinx.coroutines.Dispatchers

@Database(
  entities = [ProjectEntity::class, QuestionEntity::class, AnswerEntity::class, RoundEntity::class, SettingsEntity::class],
  version = 9,
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

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
  override fun initialize(): AppDatabase
}

fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase {
  return builder
    .fallbackToDestructiveMigration()
    .setQueryCoroutineContext(Dispatchers.Default)
    .build()
}