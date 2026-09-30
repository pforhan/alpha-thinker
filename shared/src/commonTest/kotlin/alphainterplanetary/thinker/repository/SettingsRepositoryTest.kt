package alphainterplanetary.thinker.repository

import alphainterplanetary.thinker.database.SettingsKey
import alphainterplanetary.thinker.engine.EngineDelayConfig
import alphainterplanetary.thinker.engine.EngineInteraction
import alphainterplanetary.thinker.engine.EngineMode
import alphainterplanetary.thinker.engine.PlanningContext
import alphainterplanetary.thinker.testutil.FakeStorage
import alphainterplanetary.thinker.ui.theme.PhaseTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

  // ---------- planning engine mode + LLM toggle ----------

  @Test
  fun `engineMode starts at Lite before the saved value loads`() = runTest {
    val repo = repository()

    assertEquals(EngineMode.Default, repo.engineMode.value)
  }

  @Test
  fun `engineMode loads the persisted mode at startup`() = runTest {
    val storage = FakeStorage()
    storage.saveSetting(SettingsKey.EngineMode, "remote")

    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    assertEquals(EngineMode.Remote, repo.engineMode.value)
  }

  @Test
  fun `setEngineMode updates state and persists the choice`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)

    repo.setEngineMode(EngineMode.OnDevice)
    testScheduler.advanceUntilIdle()

    assertEquals(EngineMode.OnDevice, repo.engineMode.value)
    assertEquals("on-device", storage.settings[SettingsKey.EngineMode.storageKey])
  }

  @Test
  fun `setEngineMode ignores the already-selected mode`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)

    repo.setEngineMode(EngineMode.Lite)

    assertEquals(EngineMode.Lite, repo.engineMode.value)
    assertEquals(null, storage.settings[SettingsKey.EngineMode.storageKey])
  }

  @Test
  fun `an unknown persisted mode falls back to Lite`() = runTest {
    val storage = FakeStorage()
    storage.saveSetting(SettingsKey.EngineMode, "nope")

    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    assertEquals(EngineMode.Default, repo.engineMode.value)
  }

  // ---------- remote LLM connection ----------

  @Test
  fun `remote settings start at the Ollama defaults before load`() = runTest {
    val repo = repository()

    assertEquals(SettingsRepository.DefaultRemoteLlmBaseUrl, repo.remoteLlmBaseUrl.value)
    assertEquals(SettingsRepository.DefaultRemoteLlmApiKey, repo.remoteLlmApiKey.value)
    assertEquals(SettingsRepository.DefaultRemoteLlmModel, repo.remoteLlmModel.value)
  }

  @Test
  fun `remote settings load the persisted values at startup`() = runTest {
    val storage = FakeStorage()
    storage.saveSetting(SettingsKey.RemoteLlmBaseUrl, "http://example.com/v1")
    storage.saveSetting(SettingsKey.RemoteLlmApiKey, "secret")
    storage.saveSetting(SettingsKey.RemoteLlmModel, "gpt-4o-mini")

    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    assertEquals("http://example.com/v1", repo.remoteLlmBaseUrl.value)
    assertEquals("secret", repo.remoteLlmApiKey.value)
    assertEquals("gpt-4o-mini", repo.remoteLlmModel.value)
  }

  @Test
  fun `setRemoteLlmBaseUrl updates state and persists the choice`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)

    repo.setRemoteLlmBaseUrl("http://example.com/v1")
    testScheduler.advanceUntilIdle()

    assertEquals("http://example.com/v1", repo.remoteLlmBaseUrl.value)
    assertEquals("http://example.com/v1", storage.settings[SettingsKey.RemoteLlmBaseUrl.storageKey])
  }

  @Test
  fun `setRemoteLlmApiKey updates state and persists the choice`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)

    repo.setRemoteLlmApiKey("secret")
    testScheduler.advanceUntilIdle()

    assertEquals("secret", repo.remoteLlmApiKey.value)
    assertEquals("secret", storage.settings[SettingsKey.RemoteLlmApiKey.storageKey])
  }

  @Test
  fun `setRemoteLlmModel updates state and persists the choice`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)

    repo.setRemoteLlmModel("gpt-4o-mini")
    testScheduler.advanceUntilIdle()

    assertEquals("gpt-4o-mini", repo.remoteLlmModel.value)
    assertEquals("gpt-4o-mini", storage.settings[SettingsKey.RemoteLlmModel.storageKey])
  }

  @Test
  fun `remote setters ignore the already-set value`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)

    repo.setRemoteLlmBaseUrl(SettingsRepository.DefaultRemoteLlmBaseUrl)
    repo.setRemoteLlmApiKey(SettingsRepository.DefaultRemoteLlmApiKey)
    repo.setRemoteLlmModel(SettingsRepository.DefaultRemoteLlmModel)

    assertEquals(0, storage.settings.size)
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
    storage.saveSetting(SettingsKey.EngineQuestionGenerationDelay, "30")

    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    val config = repo.engineDelay.value
    assertEquals(true, config.enabled)
    assertEquals(5, config.secondsByInteraction[EngineInteraction.RecommendTitle])
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

  // ---------- the remote model's declared window ----------

  @Test
  fun `the remote context window starts conservative`() = runTest {
    val repo = repository()
    testScheduler.advanceUntilIdle()

    assertEquals(
      SettingsRepository.DefaultRemoteLlmContextTokens,
      repo.remoteLlmContextTokens.value,
    )
  }

  @Test
  fun `setRemoteLlmContextTokens persists the declared window`() = runTest {
    val storage = FakeStorage()
    val repo = repository(storage)

    repo.setRemoteLlmContextTokens(128_000)
    testScheduler.advanceUntilIdle()

    assertEquals(128_000, repo.remoteLlmContextTokens.value)
    assertEquals("128000", storage.settings[SettingsKey.RemoteLlmContextTokens.storageKey])
  }

  /**
   * A window of zero would resolve every budget to zero and compact every
   * answer in every round, which is a configuration nobody means — and a
   * negative one is not a window at all.
   */
  @Test
  fun `a non-positive remote context window is rejected`() = runTest {
    val repo = repository()

    assertFailsWith<IllegalArgumentException> { repo.setRemoteLlmContextTokens(0) }
    assertFailsWith<IllegalArgumentException> { repo.setRemoteLlmContextTokens(-1) }
  }

  @Test
  fun `a persisted non-positive remote window falls back to the default`() = runTest {
    val storage = FakeStorage()
    storage.saveSetting(SettingsKey.RemoteLlmContextTokens, "0")

    val repo = repository(storage)
    testScheduler.advanceUntilIdle()

    assertEquals(
      SettingsRepository.DefaultRemoteLlmContextTokens,
      repo.remoteLlmContextTokens.value,
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