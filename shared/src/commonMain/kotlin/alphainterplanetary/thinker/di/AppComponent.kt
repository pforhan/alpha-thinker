package alphainterplanetary.thinker.di

import alphainterplanetary.thinker.activitylog.ActivityLog
import alphainterplanetary.thinker.activitylog.RoomActivityLog
import alphainterplanetary.thinker.database.ActivityDatabase
import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.database.getActivityDatabase
import alphainterplanetary.thinker.database.provideActivityDatabaseBuilder
import alphainterplanetary.thinker.database.provideStorage
import alphainterplanetary.thinker.engine.DynamicPlanningBackend
import alphainterplanetary.thinker.engine.EngineMode
import alphainterplanetary.thinker.engine.HardcodedPlanningEngine
import alphainterplanetary.thinker.engine.KoogPlanningEngine
import alphainterplanetary.thinker.engine.LoggingPlanningEngine
import alphainterplanetary.thinker.engine.PlanningEngineSelector
import alphainterplanetary.thinker.engine.RemotePlanningBackend
import alphainterplanetary.thinker.engine.SlowDownPlanningEngine
import alphainterplanetary.thinker.engine.resolveSelectedEngine
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

  abstract val engineSelector: PlanningEngineSelector

  abstract val taskRunner: TaskRunner

  abstract val activityLog: ActivityLog

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
  fun providesActivityLog(
    database: ActivityDatabase,
    storage: Storage,
    scope: CoroutineScope,
  ): ActivityLog = RoomActivityLog(database, storage, scope)

  @AppScope
  @Provides
  fun providesTaskRunner(
    scope: CoroutineScope,
    activityLog: ActivityLog,
  ): TaskRunner = TaskRunner(scope, activityLog)

  @AppScope
  @Provides
  fun providesEngineSelector(
    settingsRepository: SettingsRepository,
    activityLog: ActivityLog,
  ): PlanningEngineSelector {
    val liteEngine = HardcodedPlanningEngine()

    // One Koog engine per selectable LLM mode, each bound to that mode's
    // backend. Binding at construction (rather than re-reading the live
    // engine-mode setting) is what lets [PlanningEngineSelector] freeze the
    // selection into a task: a queued task keeps the engine it was created
    // under even if the user changes modes before it runs.
    val koogBackends = mapOf(
      EngineMode.Remote to RemotePlanningBackend(settingsRepository),
    )
    val koogEngines = mapOf(
      EngineMode.Remote to KoogPlanningEngine(
        DynamicPlanningBackend(EngineMode.Remote, koogBackends),
      ),
    )

    return PlanningEngineSelector {
      SlowDownPlanningEngine(
        delegate = LoggingPlanningEngine(
          delegate = resolveSelectedEngine(
            selectedMode = settingsRepository.engineMode.value,
            llmEnabled = settingsRepository.llmEnabled.value,
            liteEngine = liteEngine,
            koogEngines = koogEngines,
          ),
          log = activityLog,
        ),
        config = settingsRepository.engineDelay,
      )
    }
  }
}

@KmpComponentCreate
expect fun createAppComponent(platformContext: PlatformContext): AppComponent
