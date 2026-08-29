package alphainterplanetary.thinker.di

import alphainterplanetary.thinker.database.AppDatabase
import alphainterplanetary.thinker.database.ProjectDao
import alphainterplanetary.thinker.database.QuestionDao
import alphainterplanetary.thinker.database.AnswerDao
import alphainterplanetary.thinker.database.RoomStorage
import alphainterplanetary.thinker.llm.QuestionGenerator
import alphainterplanetary.thinker.llm.SeedQuestionsGenerator
import alphainterplanetary.thinker.repository.ProjectRepository
import com.jakewharton.inject.Component
import com.jakewharton.inject.Provides

@Component
interface AppComponent {
    val projectRepository: ProjectRepository

    val appDatabase: AppDatabase

    val projectDao: ProjectDao

    val questionDao: QuestionDao

    val answerDao: AnswerDao

    val storage: ProjectRepository.Storage

    val questionGenerator: QuestionGenerator

    @Provides
    fun provideProjectRepository(
        storage: ProjectRepository.Storage,
        generator: QuestionGenerator
    ): ProjectRepository = ProjectRepository(storage, generator)

    @Provides
    fun provideStorage(database: AppDatabase): ProjectRepository.Storage = RoomStorage(database)

    @Provides
    fun provideQuestionGenerator(): QuestionGenerator = SeedQuestionsGenerator()
}
