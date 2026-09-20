package alphainterplanetary.thinker.repository

import alphainterplanetary.thinker.database.SettingsKey
import alphainterplanetary.thinker.llm.GeneratorDelayConfig
import alphainterplanetary.thinker.llm.GeneratorInteraction
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

  // ---------- generator slow-down delays ----------

  @Test
  fun `generatorDelay starts disabled with default delays`() = runTest {
    val repo = repository()

    assertEquals(GeneratorDelayConfig.Default, repo.generatorDelay.value)
  }

  @Test
  fun `generatorDelay loads the persisted enable flag and delays at startup`() = runTest {
    val storage = FakeStorage()
    storage.saveSetting(SettingsKey.SlowDownQuestionGenerator, "true")
    storage.saveSetting(SettingsKey.GeneratorRecommendTitleDelay, "5")
    storage.saveSetting(SettingsKey.GeneratorInitialQuestionsDelay, "30")

    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    val config = repo.generatorDelay.value
    assertEquals(true, config.enabled)
    assertEquals(5, config.secondsByInteraction[GeneratorInteraction.RecommendTitle])
    assertEquals(30, config.secondsByInteraction[GeneratorInteraction.InitialQuestions])
    assertEquals(2, config.secondsByInteraction[GeneratorInteraction.FollowUpQuestions])
    assertEquals(2, config.secondsByInteraction[GeneratorInteraction.RemainingInPhase])
  }

  @Test
  fun `setGeneratorDelayEnabled updates state and persists the choice`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)

    repo.setGeneratorDelayEnabled(true)
    testScheduler.advanceUntilIdle()

    assertEquals(true, repo.generatorDelay.value.enabled)
    assertEquals("true", storage.settings[SettingsKey.SlowDownQuestionGenerator.storageKey])
  }

  @Test
  fun `setGeneratorDelayEnabled ignores the already-set value`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)

    repo.setGeneratorDelayEnabled(false)

    assertEquals(false, repo.generatorDelay.value.enabled)
    assertEquals(null, storage.settings[SettingsKey.SlowDownQuestionGenerator.storageKey])
  }

  @Test
  fun `setGeneratorDelay updates state and persists the seconds`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)

    repo.setGeneratorDelay(GeneratorInteraction.FollowUpQuestions, 30)
    testScheduler.advanceUntilIdle()

    assertEquals(
      30,
      repo.generatorDelay.value.secondsByInteraction[GeneratorInteraction.FollowUpQuestions],
    )
    assertEquals(
      "30",
      storage.settings[SettingsKey.GeneratorFollowUpQuestionsDelay.storageKey],
    )
  }

  @Test
  fun `setGeneratorDelay ignores the already-set delay`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)

    repo.setGeneratorDelay(GeneratorInteraction.RecommendTitle, 2)

    assertEquals(
      null,
      storage.settings[SettingsKey.GeneratorRecommendTitleDelay.storageKey],
    )
  }

  @Test
  fun `an unknown persisted delay falls back to the default seconds`() = runTest {
    val storage = FakeStorage()
    storage.saveSetting(SettingsKey.GeneratorRecommendTitleDelay, "nope")

    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    assertEquals(
      2,
      repo.generatorDelay.value.secondsByInteraction[GeneratorInteraction.RecommendTitle],
    )
  }
}