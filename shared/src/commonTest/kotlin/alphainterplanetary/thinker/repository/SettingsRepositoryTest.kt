package alphainterplanetary.thinker.repository

import alphainterplanetary.thinker.database.SettingsKey
import alphainterplanetary.thinker.engine.EngineDelayConfig
import alphainterplanetary.thinker.engine.EngineInteraction
import alphainterplanetary.thinker.testutil.FakeStorage
import alphainterplanetary.thinker.ui.theme.PhaseTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

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
    storage.saveSetting(SettingsKey.EngineTitleRecommendationDelay, "5")
    storage.saveSetting(SettingsKey.EngineQuestionGenerationDelay, "30")

    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    val config = repo.engineDelay.value
    assertEquals(true, config.enabled)
    assertEquals(5, config.secondsByInteraction[EngineInteraction.TitleRecommendation])
    assertEquals(30, config.secondsByInteraction[EngineInteraction.QuestionGeneration])
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
    storage.saveSetting(SettingsKey.EngineTitleRecommendationDelay, "0")

    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    assertEquals(
      0,
      repo.engineDelay.value.secondsByInteraction[EngineInteraction.TitleRecommendation],
    )
  }

  @Test
  fun `setEngineDelay accepts zero to disable one interaction`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)

    repo.setEngineDelay(EngineInteraction.QuestionGeneration, 0)
    testScheduler.advanceUntilIdle()

    assertEquals(
      0,
      repo.engineDelay.value.secondsByInteraction[EngineInteraction.QuestionGeneration],
    )
    assertEquals(
      "0",
      storage.settings[SettingsKey.EngineQuestionGenerationDelay.storageKey],
    )
  }

  @Test
  fun `setEngineDelay updates state and persists the seconds`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)

    repo.setEngineDelay(EngineInteraction.QuestionGeneration, 30)
    testScheduler.advanceUntilIdle()

    assertEquals(
      30,
      repo.engineDelay.value.secondsByInteraction[EngineInteraction.QuestionGeneration],
    )
    assertEquals(
      "30",
      storage.settings[SettingsKey.EngineQuestionGenerationDelay.storageKey],
    )
  }

  @Test
  fun `setEngineDelay ignores the already-set delay`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)

    repo.setEngineDelay(EngineInteraction.TitleRecommendation, 2)

    assertEquals(
      null,
      storage.settings[SettingsKey.EngineTitleRecommendationDelay.storageKey],
    )
  }

  @Test
  fun `an unknown persisted delay falls back to the default seconds`() = runTest {
    val storage = FakeStorage()
    storage.saveSetting(SettingsKey.EngineTitleRecommendationDelay, "nope")

    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    assertEquals(
      2,
      repo.engineDelay.value.secondsByInteraction[EngineInteraction.TitleRecommendation],
    )
  }

  // ---------- announced failure ledger ----------

  @Test
  fun `no failure has been announced before the first one is raised`() = runTest {
    val repo = repository()
    testScheduler.advanceUntilIdle()

    assertNull(repo.announcedFailureActivityId.value)
  }

  @Test
  fun `markFailureAnnounced records the id and persists it`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)

    repo.markFailureAnnounced("task-1")
    testScheduler.advanceUntilIdle()

    assertEquals("task-1", repo.announcedFailureActivityId.value)
    assertEquals(
      "task-1",
      storage.settings[SettingsKey.AnnouncedFailureActivityId.storageKey],
    )
  }

  @Test
  fun `announcedFailureActivityId loads the ledger at startup`() = runTest {
    val storage = FakeStorage()
    storage.saveSetting(SettingsKey.AnnouncedFailureActivityId, "task-7")

    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    assertEquals("task-7", repo.announcedFailureActivityId.value)
  }

  /**
   * The ledger is the only memory of "already told", so a newer failure must
   * overwrite an older one rather than being ignored as a duplicate.
   */
  @Test
  fun `a newer failure supersedes the recorded one`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)

    repo.markFailureAnnounced("task-1")
    repo.markFailureAnnounced("task-2")
    testScheduler.advanceUntilIdle()

    assertEquals("task-2", repo.announcedFailureActivityId.value)
  }
}
