package alphainterplanetary.thinker.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
  entities = [ProjectEntity::class, QuestionEntity::class, AnswerEntity::class],
  version = 1,
  exportSchema = false
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun projectDao(): ProjectDao
  abstract fun questionDao(): QuestionDao
  abstract fun answerDao(): AnswerDao
}

expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
