package alphainterplanetary.thinker.repository

import alphainterplanetary.thinker.database.SettingsKey
import alphainterplanetary.thinker.engine.EngineDelayConfig
import alphainterplanetary.thinker.engine.EngineInteraction
import alphainterplanetary.thinker.testutil.FakeStorage
import alphainterplanetary.thinker.ui.theme.PhaseTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsRepositoryTest {

  private fun TestScope.repository(storage: FakeStorage = FakeStorage()): SettingsRepository =
    SettingsRepository(storage, CoroutineScope(coroutineContext))

  @Test
  fun `phaseTheme starts at the default before the saved value loads`() = runTest {
    val repo = repository()

    assertEquals(PhaseTheme.Default, repo.phaseTheme.value)
  }

  @Test
  fun `phaseTheme loads the persisted theme at startup`() = runTest {
    val storage = FakeStorage()
    storage.saveSetting(SettingsKey.PhaseTheme, PhaseTheme.Earth.key)

    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    assertEquals(PhaseTheme.Earth, repo.phaseTheme.value)
  }

  @Test
  fun `setPhaseTheme updates state and persists the choice`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)

    repo.setPhaseTheme(PhaseTheme.Ocean)
    testScheduler.advanceUntilIdle()

    assertEquals(PhaseTheme.Ocean, repo.phaseTheme.value)
    assertEquals(PhaseTheme.Ocean.key, storage.settings[SettingsKey.PhaseTheme.storageKey])
  }

  @Test
  fun `setPhaseTheme ignores the already-selected theme`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)

    repo.setPhaseTheme(PhaseTheme.Vibrant)

    assertEquals(PhaseTheme.Vibrant, repo.phaseTheme.value)
    assertEquals(null, storage.settings[SettingsKey.PhaseTheme.storageKey])
  }

  @Test
  fun `an unknown persisted key falls back to the default theme`() = runTest {
    val storage = FakeStorage()
    storage.saveSetting(SettingsKey.PhaseTheme, "nope")

    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    assertEquals(PhaseTheme.Default, repo.phaseTheme.value)
  }

  // ---------- engine slow-down delays ----------

  @Test
  fun `engineDelay starts disabled with default delays`() = runTest {
    val repo = repository()

    assertEquals(EngineDelayConfig.Default, repo.engineDelay.value)
  }

  @Test
  fun `engineDelay loads the persisted enable flag and delays at startup`() = runTest {
    val storage = FakeStorage()
    storage.saveSetting(SettingsKey.SlowDownPlanningEngine, "true")
    storage.saveSetting(SettingsKey.EngineRecommendTitleDelay, "5")
    storage.saveSetting(SettingsKey.EngineInitialQuestionsDelay, "30")

    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    val config = repo.engineDelay.value
    assertEquals(true, config.enabled)
    assertEquals(5, config.secondsByInteraction[EngineInteraction.RecommendTitle])
    assertEquals(30, config.secondsByInteraction[EngineInteraction.InitialQuestions])
    assertEquals(2, config.secondsByInteraction[EngineInteraction.FollowUpQuestions])
    assertEquals(2, config.secondsByInteraction[EngineInteraction.RemainingInPhase])
  }

  @Test
  fun `setEngineDelayEnabled updates state and persists the choice`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)

    repo.setEngineDelayEnabled(true)
    testScheduler.advanceUntilIdle()

    assertEquals(true, repo.engineDelay.value.enabled)
    assertEquals("true", storage.settings[SettingsKey.SlowDownPlanningEngine.storageKey])
  }

  @Test
  fun `setEngineDelayEnabled ignores the already-set value`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)

    repo.setEngineDelayEnabled(false)

    assertEquals(false, repo.engineDelay.value.enabled)
    assertEquals(null, storage.settings[SettingsKey.SlowDownPlanningEngine.storageKey])
  }

  @Test
  fun `engineDelay loads a persisted zero delay at startup`() = runTest {
    val storage = FakeStorage()
    storage.saveSetting(SettingsKey.EngineRecommendTitleDelay, "0")

    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    assertEquals(
      0,
      repo.engineDelay.value.secondsByInteraction[EngineInteraction.RecommendTitle],
    )
  }

  @Test
  fun `setEngineDelay accepts zero to disable one interaction`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)

    repo.setEngineDelay(EngineInteraction.FollowUpQuestions, 0)
    testScheduler.advanceUntilIdle()

    assertEquals(
      0,
      repo.engineDelay.value.secondsByInteraction[EngineInteraction.FollowUpQuestions],
    )
    assertEquals(
      "0",
      storage.settings[SettingsKey.EngineFollowUpQuestionsDelay.storageKey],
    )
  }

  @Test
  fun `setEngineDelay updates state and persists the seconds`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)

    repo.setEngineDelay(EngineInteraction.FollowUpQuestions, 30)
    testScheduler.advanceUntilIdle()

    assertEquals(
      30,
      repo.engineDelay.value.secondsByInteraction[EngineInteraction.FollowUpQuestions],
    )
    assertEquals(
      "30",
      storage.settings[SettingsKey.EngineFollowUpQuestionsDelay.storageKey],
    )
  }

  @Test
  fun `setEngineDelay ignores the already-set delay`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)

    repo.setEngineDelay(EngineInteraction.RecommendTitle, 2)

    assertEquals(
      null,
      storage.settings[SettingsKey.EngineRecommendTitleDelay.storageKey],
    )
  }

  @Test
  fun `an unknown persisted delay falls back to the default seconds`() = runTest {
    val storage = FakeStorage()
    storage.saveSetting(SettingsKey.EngineRecommendTitleDelay, "nope")

    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    assertEquals(
      2,
      repo.engineDelay.value.secondsByInteraction[EngineInteraction.RecommendTitle],
    )
  }
}