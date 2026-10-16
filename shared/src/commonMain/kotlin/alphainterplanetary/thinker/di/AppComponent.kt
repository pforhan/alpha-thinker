package alphainterplanetary.thinker.di

import alphainterplanetary.thinker.activitylog.ActivityLogger
import alphainterplanetary.thinker.activitylog.RoomActivityLogger
import alphainterplanetary.thinker.database.ActivityDatabase
import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.database.getActivityDatabase
import alphainterplanetary.thinker.database.provideActivityDatabaseBuilder
import alphainterplanetary.thinker.database.provideStorage
import alphainterplanetary.thinker.engine.EngineKind
import alphainterplanetary.thinker.engine.EngineRegistry
import alphainterplanetary.thinker.engine.KoogPlanningEngine
import alphainterplanetary.thinker.engine.KoogRemoteModelCatalog
import alphainterplanetary.thinker.engine.LoggingPlanningEngine
import alphainterplanetary.thinker.engine.OfflinePlanningEngine
import alphainterplanetary.thinker.engine.PlanningEngineSelector
import alphainterplanetary.thinker.engine.RemoteKoogPlanningBackend
import alphainterplanetary.thinker.engine.RemoteModelCatalog
import alphainterplanetary.thinker.engine.SlowDownPlanningEngine
import alphainterplanetary.thinker.engine.resolveSelectedEngine
import alphainterplanetary.thinker.repository.ProjectRepository
import alphainterplanetary.thinker.repository.ProjectWrites
import alphainterplanetary.thinker.repository.SettingsRepository
import alphainterplanetary.thinker.tasks.TaskRunner
import alphainterplanetary.thinker.tools.ProjectSimulator
import alphainterplanetary.thinker.tools.SampleProjectGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.KmpComponentCreate
import me.tatarka.inject.annotations.Provides

@AppScope
@Component
abstract class AppComponent(@get:Provides val platformContext: PlatformContext) {
  abstract val projectRepository: ProjectRepository

  abstract val settingsRepository: SettingsRepository

  abstract val engineRepository: alphainterplanetary.thinker.repository.EngineRepository

  abstract val projectWrites: ProjectWrites

  abstract val sampleProjectGenerator: SampleProjectGenerator

  abstract val projectSimulator: ProjectSimulator

  abstract val engineSelector: PlanningEngineSelector

  abstract val remoteModelCatalog: RemoteModelCatalog

  abstract val taskRunner: TaskRunner

  abstract val activityLogger: ActivityLogger

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
  fun providesActivityLogger(
    database: ActivityDatabase,
    storage: Storage,
    scope: CoroutineScope,
  ): ActivityLogger = RoomActivityLogger(database, storage).also { logger ->
    // The startup sweep is the composition root's call, not the constructor's:
    // anything constructing a logger directly (tests) gets an inert one.
    scope.launch { runCatching { logger.runStartupSweep() } }
  }

  @AppScope
  @Provides
  fun providesTaskRunner(
    scope: CoroutineScope,
    activityLogger: ActivityLogger,
  ): TaskRunner = TaskRunner(scope, activityLogger)

  @AppScope
  @Provides
  fun providesRemoteModelCatalog(): RemoteModelCatalog = KoogRemoteModelCatalog()

  @AppScope
  @Provides
  fun providesEngineSelector(
    registry: EngineRegistry,
    repository: alphainterplanetary.thinker.repository.EngineRepository,
    settingsRepository: SettingsRepository,
    activityLogger: ActivityLogger,
  ): PlanningEngineSelector {
    return PlanningEngineSelector {
      SlowDownPlanningEngine(
        delegate = LoggingPlanningEngine(
          delegate = registry.getEngine(
            repository.selected.value,
          ),
          log = activityLogger,
        ),
        config = settingsRepository.engineDelay,
      )
    }
  }
}

@KmpComponentCreate
expect fun createAppComponent(platformContext: PlatformContext): AppComponent
