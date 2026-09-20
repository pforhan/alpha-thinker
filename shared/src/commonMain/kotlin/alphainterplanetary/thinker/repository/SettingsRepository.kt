package alphainterplanetary.thinker.repository

import alphainterplanetary.thinker.database.SettingsKey
import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.di.AppScope
import alphainterplanetary.thinker.llm.GeneratorDelayConfig
import alphainterplanetary.thinker.llm.GeneratorInteraction
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
 * [generatorDelay] controls the artificial slow-down applied to QuestionGenerator
 * interactions so the Task Manager stays exercisable.
 */
@AppScope
class SettingsRepository @Inject constructor(
  private val storage: Storage,
  private val scope: CoroutineScope,
) {
  private val _phaseTheme = MutableStateFlow(PhaseTheme.Default)
  val phaseTheme: StateFlow<PhaseTheme> = _phaseTheme.asStateFlow()

  private val _generatorDelay = MutableStateFlow(GeneratorDelayConfig.Default)
  val generatorDelay: StateFlow<GeneratorDelayConfig> = _generatorDelay.asStateFlow()

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
      val enabled = storage.getSetting(SettingsKey.SlowDownQuestionGenerator, "false").toBoolean()
      val loadedSeconds = GeneratorInteraction.entries.associateWith { interaction ->
        loadDelaySeconds(interaction)
      }
      // Only apply the persisted values while the user hasn't already changed a
      // field, so a startup load never clobbers their choices.
      _generatorDelay.update { current ->
        GeneratorDelayConfig(
          enabled = current.enabled || enabled,
          secondsByInteraction = current.secondsByInteraction.mapValues { (interaction, already) ->
            // A delay still on the first option hasn't been customized this session.
            if (already == GeneratorDelayConfig.DelayOptionsSeconds.first()) {
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

  /** Turns the artificial QuestionGenerator slow-down on or off for Task Manager testing. */
  fun setGeneratorDelayEnabled(enabled: Boolean) {
    if (enabled == _generatorDelay.value.enabled) return
    _generatorDelay.update { it.copy(enabled = enabled) }
    scope.launch {
      storage.saveSetting(SettingsKey.SlowDownQuestionGenerator, enabled.toString())
    }
  }

  /** Sets the artificial slow-down hold time for one [GeneratorInteraction]. */
  fun setGeneratorDelay(interaction: GeneratorInteraction, seconds: Int) {
    require(seconds in GeneratorDelayConfig.DelayOptionsSeconds) {
      "unsupported slow-down delay: $seconds"
    }
    if (_generatorDelay.value.secondsByInteraction[interaction] == seconds) return
    _generatorDelay.update { config ->
      config.copy(secondsByInteraction = config.secondsByInteraction + (interaction to seconds))
    }
    scope.launch {
      storage.saveSetting(interaction.settingsKey, seconds.toString())
    }
  }

  /** Reads a persisted delay, falling back to the default when absent or unknown. */
  private suspend fun loadDelaySeconds(interaction: GeneratorInteraction): Int {
    val stored = storage.getSetting(interaction.settingsKey, "").toIntOrNull()
    return stored.takeIf { it in GeneratorDelayConfig.DelayOptionsSeconds }
      ?: GeneratorDelayConfig.DelayOptionsSeconds.first()
  }

  private val GeneratorInteraction.settingsKey: SettingsKey
    get() = when (this) {
      GeneratorInteraction.RecommendTitle -> SettingsKey.GeneratorRecommendTitleDelay
      GeneratorInteraction.InitialQuestions -> SettingsKey.GeneratorInitialQuestionsDelay
      GeneratorInteraction.FollowUpQuestions -> SettingsKey.GeneratorFollowUpQuestionsDelay
      GeneratorInteraction.RemainingInPhase -> SettingsKey.GeneratorRemainingInPhaseDelay
    }
}