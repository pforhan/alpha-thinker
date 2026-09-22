package alphainterplanetary.thinker.di

import alphainterplanetary.thinker.activitylog.EngineActivityLog
import alphainterplanetary.thinker.activitylog.RoomEngineActivityLog
import alphainterplanetary.thinker.database.ActivityDatabase
import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.database.getActivityDatabase
import alphainterplanetary.thinker.database.provideActivityDatabaseBuilder
import alphainterplanetary.thinker.database.provideStorage
import alphainterplanetary.thinker.engine.HardcodedPlanningEngine
import alphainterplanetary.thinker.engine.LoggingPlanningEngine
import alphainterplanetary.thinker.engine.PlanningEngine
import alphainterplanetary.thinker.engine.SlowDownPlanningEngine
import alphainterplanetary.thinker.repository.ProjectRepository
import alphainterplanetary.thinker.repository.SettingsRepository
import alphainterplanetary.thinker.tasks.TaskRunner
import alphainterplanetary.thinker.tools.SampleProjectGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.KmpComponentCreate
import me.tatarka.inject.annotations.Provides

@AppScope
@Component
abstract class AppComponent(@get:Provides val platformContext: PlatformContext) {
  abstract val projectRepository: ProjectRepository

  abstract val settingsRepository: SettingsRepository

  abstract val sampleProjectGenerator: SampleProjectGenerator

  abstract val planningEngine: PlanningEngine

  abstract val taskRunner: TaskRunner

  abstract val engineActivityLog: EngineActivityLog

  abstract val appScope: CoroutineScope

  @AppScope
  @Provides
  fun providesStorage(): Storage = provideStorage(platformContext)

  @AppScope
  @Provides
  fun providesActivityDatabase(): ActivityDatabase = getActivityDatabase(provideActivityDatabaseBuilder(platformContext))

  @AppScope
  @Provides
  fun providesAppCoroutineScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Default)

  @AppScope
  @Provides
  fun providesEngineActivityLog(
    database: ActivityDatabase,
    storage: Storage,
    scope: CoroutineScope,
  ): EngineActivityLog = RoomEngineActivityLog(database, storage, scope)

  @AppScope
  @Provides
  fun providesTaskRunner(
    scope: CoroutineScope,
    engineActivityLog: EngineActivityLog,
  ): TaskRunner = TaskRunner(scope, engineActivityLog)

  @Provides
  fun providesPlanningEngine(
    settingsRepository: SettingsRepository,
    engineActivityLog: EngineActivityLog,
  ): PlanningEngine = SlowDownPlanningEngine(
    delegate = LoggingPlanningEngine(
      delegate = HardcodedPlanningEngine(),
      log = engineActivityLog,
    ),
    config = settingsRepository.engineDelay,
  )
}

@KmpComponentCreate
expect fun createAppComponent(platformContext: PlatformContext): AppComponent
