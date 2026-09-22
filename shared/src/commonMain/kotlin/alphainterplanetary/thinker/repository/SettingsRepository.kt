package alphainterplanetary.thinker.repository

import alphainterplanetary.thinker.database.SettingsKey
import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.di.AppScope
import alphainterplanetary.thinker.engine.EngineDelayConfig
import alphainterplanetary.thinker.engine.EngineInteraction
import alphainterplanetary.thinker.engine.EngineMode
import alphainterplanetary.thinker.ui.theme.PhaseTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

/**
 * Owns the app-wide settings. The selected [PhaseTheme] is loaded once at
 * startup and applied from the app root, so a change here re-colors phase
 * badges and pills immediately and persists across launches. The testing
 * [engineDelay] controls the artificial slow-down applied to PlanningEngine
 * interactions so the Task Manager stays exercisable.
 */
@AppScope
class SettingsRepository @Inject constructor(
  private val storage: Storage,
  private val scope: CoroutineScope,
) {
  private val _phaseTheme = MutableStateFlow(PhaseTheme.Default)
  val phaseTheme: StateFlow<PhaseTheme> = _phaseTheme.asStateFlow()

  /**
   * The selected planning backend ([EngineMode]). Only [EngineMode.Lite] is
   * selectable today — the LLM backends are gated off by [EngineMode.available]
   * until they land (IMPLEMENTATION-PLAN.md Phase 3).
   */
  private val _engineMode = MutableStateFlow(EngineMode.Default)
  val engineMode: StateFlow<EngineMode> = _engineMode.asStateFlow()

  /** Whether the planning LLM may run at all; when off, Lite is used. */
  private val _llmEnabled = MutableStateFlow(true)
  val llmEnabled: StateFlow<Boolean> = _llmEnabled.asStateFlow()

  private val _engineDelay = MutableStateFlow(EngineDelayConfig.Default)
  val engineDelay: StateFlow<EngineDelayConfig> = _engineDelay.asStateFlow()

  init {
    scope.launch {
      val loaded = storage.getSetting(SettingsKey.PhaseTheme, PhaseTheme.Default.key)
        .let(PhaseTheme::fromKey)
        ?: PhaseTheme.Default
      // Only apply the persisted value while the user hasn't already picked a
      // different theme, so a startup load never clobbers their selection.
      if (_phaseTheme.value == PhaseTheme.Default) {
        _phaseTheme.value = loaded
      }
    }
    scope.launch {
      val loaded = storage.getSetting(SettingsKey.EngineMode, EngineMode.Default.key)
        .let(EngineMode::fromKey)
        ?: EngineMode.Default
      // Only apply the persisted value while the user hasn't already picked a
      // different mode, so a startup load never clobbers their selection.
      if (_engineMode.value == EngineMode.Default) {
        _engineMode.value = loaded
      }
    }
    scope.launch {
      val enabled = storage.getSetting(SettingsKey.LlmEnabled, "true").toBoolean()
      // Only apply the persisted value while the user hasn't already flipped the
      // toggle off this session, so a startup load never clobbers their choice.
      if (_llmEnabled.value && !enabled) {
        _llmEnabled.value = enabled
      }
    }
    scope.launch {
      val enabled = storage.getSetting(SettingsKey.SlowDownPlanningEngine, "false").toBoolean()
      val loadedSeconds = EngineInteraction.entries.associateWith { interaction ->
        loadDelaySeconds(interaction)
      }
      // Only apply the persisted values while the user hasn't already changed a
      // field, so a startup load never clobbers their choices.
      _engineDelay.update { current ->
        EngineDelayConfig(
          enabled = current.enabled || enabled,
          secondsByInteraction = current.secondsByInteraction.mapValues { (interaction, already) ->
            // A delay still on the first option hasn't been customized this session.
            if (already == EngineDelayConfig.DelayOptionsSeconds.first()) {
              loadedSeconds.getValue(interaction)
            } else {
              already
            }
          },
        )
      }
    }
  }

  fun setPhaseTheme(theme: PhaseTheme) {
    if (theme == _phaseTheme.value) return
    _phaseTheme.value = theme
    scope.launch {
      storage.saveSetting(SettingsKey.PhaseTheme, theme.key)
    }
  }

  /** Selects the planning backend; unavailable modes are rejected by the picker UI. */
  fun setEngineMode(mode: EngineMode) {
    if (mode == _engineMode.value) return
    _engineMode.value = mode
    scope.launch {
      storage.saveSetting(SettingsKey.EngineMode, mode.key)
    }
  }

  /** Turns the planning LLM on or off; when off, planning falls back to Lite. */
  fun setLlmEnabled(enabled: Boolean) {
    if (enabled == _llmEnabled.value) return
    _llmEnabled.value = enabled
    scope.launch {
      storage.saveSetting(SettingsKey.LlmEnabled, enabled.toString())
    }
  }

  /** Turns the artificial PlanningEngine slow-down on or off for Task Manager testing. */
  fun setEngineDelayEnabled(enabled: Boolean) {
    if (enabled == _engineDelay.value.enabled) return
    _engineDelay.update { it.copy(enabled = enabled) }
    scope.launch {
      storage.saveSetting(SettingsKey.SlowDownPlanningEngine, enabled.toString())
    }
  }

  /** Sets the artificial slow-down hold time for one [EngineInteraction]. */
  fun setEngineDelay(interaction: EngineInteraction, seconds: Int) {
    require(seconds in EngineDelayConfig.DelayOptionsSeconds) {
      "unsupported slow-down delay: $seconds"
    }
    if (_engineDelay.value.secondsByInteraction[interaction] == seconds) return
    _engineDelay.update { config ->
      config.copy(secondsByInteraction = config.secondsByInteraction + (interaction to seconds))
    }
    scope.launch {
      storage.saveSetting(interaction.settingsKey, seconds.toString())
    }
  }

  /** Reads a persisted delay, falling back to the default when absent or unknown. */
  private suspend fun loadDelaySeconds(interaction: EngineInteraction): Int {
    val stored = storage.getSetting(interaction.settingsKey, "").toIntOrNull()
    return stored.takeIf { it in EngineDelayConfig.DelayOptionsSeconds }
      ?: EngineDelayConfig.DelayOptionsSeconds.first()
  }

  private val EngineInteraction.settingsKey: SettingsKey
    get() = when (this) {
      EngineInteraction.RecommendTitle -> SettingsKey.EngineRecommendTitleDelay
      EngineInteraction.InitialQuestions -> SettingsKey.EngineInitialQuestionsDelay
      EngineInteraction.FollowUpQuestions -> SettingsKey.EngineFollowUpQuestionsDelay
      EngineInteraction.RemainingInPhase -> SettingsKey.EngineRemainingInPhaseDelay
    }
}