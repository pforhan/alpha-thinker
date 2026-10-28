package alphainterplanetary.thinker.di

import alphainterplanetary.thinker.activitylog.ActivityLogger
import alphainterplanetary.thinker.activitylog.RoomActivityLogger
import alphainterplanetary.thinker.database.ActivityDatabase
import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.database.getActivityDatabase
import alphainterplanetary.thinker.database.provideActivityDatabaseBuilder
import alphainterplanetary.thinker.database.provideStorage
import alphainterplanetary.thinker.downloads.TaskBackedModelDownloader
import alphainterplanetary.thinker.downloads.KoogOllamaPullClient
import alphainterplanetary.thinker.downloads.ModelFileTransferClient
import alphainterplanetary.thinker.downloads.ModelDownloader
import alphainterplanetary.thinker.downloads.OllamaPullClient
import alphainterplanetary.thinker.engine.EngineRegistry
import alphainterplanetary.thinker.engine.KoogRemoteModelCatalog
import alphainterplanetary.thinker.engine.LoggingPlanningEngine
import alphainterplanetary.thinker.engine.PlanningEngineSelector
import alphainterplanetary.thinker.engine.RemoteModelCatalog
import alphainterplanetary.thinker.engine.SlowDownPlanningEngine
import alphainterplanetary.thinker.repository.EngineRepository
import alphainterplanetary.thinker.repository.ProjectRepository
import alphainterplanetary.thinker.repository.ProjectWrites
import alphainterplanetary.thinker.repository.SettingsRepository
import alphainterplanetary.thinker.tasks.TaskRunner
import alphainterplanetary.thinker.tools.ProjectSimulator
import alphainterplanetary.thinker.tools.SampleProjectGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.KmpComponentCreate
import me.tatarka.inject.annotations.Provides

@AppScope
@Component
abstract class AppComponent(@get:Provides val platformContext: PlatformContext) {
  abstract val projectRepository: ProjectRepository

  abstract val settingsRepository: SettingsRepository

  abstract val engineRepository: EngineRepository

  abstract val projectWrites: ProjectWrites

  abstract val sampleProjectGenerator: SampleProjectGenerator

  abstract val projectSimulator: ProjectSimulator

  abstract val engineSelector: PlanningEngineSelector

  abstract val remoteModelCatalog: RemoteModelCatalog

  abstract val taskRunner: TaskRunner

  abstract val activityLogger: ActivityLogger

  abstract val ollamaPullClient: OllamaPullClient

  abstract val modelFileTransferClient: ModelFileTransferClient

  abstract val modelDownloader: ModelDownloader

  abstract val appScope: CoroutineScope

  @AppScope
  @Provides
  fun providesStorage(): Storage = provideStorage(platformContext)

  @AppScope
  @Provides
  fun providesActivityDatabase(): ActivityDatabase =
    getActivityDatabase(provideActivityDatabaseBuilder(platformContext))

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
  fun providesOllamaPullClient(): OllamaPullClient = KoogOllamaPullClient()

  @AppScope
  @Provides
  fun providesLocalFileDownloader(): ModelFileTransferClient = object : ModelFileTransferClient {
    override fun download(spec: alphainterplanetary.thinker.downloads.ModelDownloadSpec): Flow<alphainterplanetary.thinker.downloads.LocalDownloadEvent> = flowOf(
      alphainterplanetary.thinker.downloads.LocalDownloadEvent(
        phase = alphainterplanetary.thinker.downloads.LocalDownloadPhase.Succeeded,
      ),
    )
  }

  @AppScope
  @Provides
  fun providesModelDownloadManager(
    taskRunner: TaskRunner,
    ollamaPullClient: OllamaPullClient,
    modelFileTransferClient: ModelFileTransferClient,
    remoteModelCatalog: RemoteModelCatalog,
    activityLogger: ActivityLogger,
  ): ModelDownloader = TaskBackedModelDownloader(
    taskRunner = taskRunner,
    ollamaPullClient = ollamaPullClient,
    modelFileTransferClient = modelFileTransferClient,
    remoteModelCatalog = remoteModelCatalog,
    activityLogger = activityLogger,
  )

  @AppScope
  @Provides
  fun providesEngineSelector(
    registry: EngineRegistry,
    repository: EngineRepository,
    settingsRepository: SettingsRepository,
    activityLogger: ActivityLogger,
  ): PlanningEngineSelector = PlanningEngineSelector {
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

@KmpComponentCreate
expect fun createAppComponent(platformContext: PlatformContext): AppComponent
