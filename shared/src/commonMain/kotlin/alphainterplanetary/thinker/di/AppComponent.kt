package alphainterplanetary.thinker.di

import alphainterplanetary.thinker.database.AnswerDao
import alphainterplanetary.thinker.database.AppDatabase
import alphainterplanetary.thinker.database.ProjectDao
import alphainterplanetary.thinker.database.QuestionDao
import alphainterplanetary.thinker.database.RoomStorage
import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.database.getRoomDatabase
import alphainterplanetary.thinker.database.provideDatabaseBuilder
import alphainterplanetary.thinker.llm.HardcodedQuestionGenerator
import alphainterplanetary.thinker.llm.QuestionGenerator
import alphainterplanetary.thinker.repository.ProjectRepository
import alphainterplanetary.thinker.tools.SampleProjectGenerator
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.KmpComponentCreate
import me.tatarka.inject.annotations.Provides

@Component
abstract class AppComponent(@get:Provides val platformContext: PlatformContext) {
  abstract val projectRepository: ProjectRepository

  abstract val sampleProjectGenerator: SampleProjectGenerator

  abstract val appDatabase: AppDatabase

  abstract val projectDao: ProjectDao

  abstract val questionDao: QuestionDao

  abstract val answerDao: AnswerDao

  abstract val questionGenerator: QuestionGenerator

  private val database by lazy { getRoomDatabase(provideDatabaseBuilder(platformContext)) }

  @Provides
  fun providesStorage(database: AppDatabase): Storage = RoomStorage(database)

  @Provides
  fun providesQuestionGenerator(): QuestionGenerator = HardcodedQuestionGenerator()

  @Provides
  fun providesDatabase(): AppDatabase = database

  @Provides
  fun providesProjectDao(database: AppDatabase): ProjectDao = database.projectDao()

  @Provides
  fun providesQuestionDao(database: AppDatabase): QuestionDao = database.questionDao()

  @Provides
  fun providesAnswerDao(database: AppDatabase): AnswerDao = database.answerDao()
}

@KmpComponentCreate
expect fun createAppComponent(platformContext: PlatformContext): AppComponent