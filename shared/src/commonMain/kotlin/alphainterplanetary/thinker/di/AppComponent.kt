package alphainterplanetary.thinker.di

import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.database.provideStorage
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

  abstract val questionGenerator: QuestionGenerator

  @Provides
  fun providesStorage(): Storage = provideStorage(platformContext)

  @Provides
  fun providesQuestionGenerator(): QuestionGenerator = HardcodedQuestionGenerator()
}

@KmpComponentCreate
expect fun createAppComponent(platformContext: PlatformContext): AppComponent
