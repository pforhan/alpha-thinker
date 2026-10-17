package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.engine.EngineAvailability.Available
import alphainterplanetary.thinker.engine.EngineAvailability.NotImplemented
import alphainterplanetary.thinker.engine.EngineAvailability.Unavailable
import alphainterplanetary.thinker.engine.EngineCapability
import alphainterplanetary.thinker.engine.EngineInstance
import alphainterplanetary.thinker.engine.EngineKind
import alphainterplanetary.thinker.engine.EngineRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The chrome's copy for a status: one vocabulary, so the sentence a screen reader
 * announces and the value printed in the row beside it cannot call the same state
 * two different things.
 */
class EngineStatusCopyTest {
  @Test
  fun aValuelessSlotIsDescribedInTheSameWordsAsItsSentence() {
    val status = engineStatus(
      ref = EngineRef.Builtin(EngineKind.Remote),
      availability = Available
    )

    val slot = status.slot(EngineCapability.Network)
    assertEquals("Needs setup", slot.displayDetail())
    assertTrue(slot.sentence().contains("needs setup"))
  }

  @Test
  fun anUnavailableEngineIsDescribedAsNeedingSetup() {
    val status = engineStatus(
      ref = EngineRef.Builtin(EngineKind.OnDevice),
      availability = Unavailable("some fancy reason why not avail"),
    )

    val slot = status.slot(EngineCapability.Llm)
    assertEquals("Needs setup", slot.displayDetail())
    assertTrue(slot.sentence().contains("needs setup"))
  }

  @Test
  fun aValuelessSlotStatesTheModeVocabularyForEveryState() {
    val offline = engineStatus(
      ref = EngineRef.Builtin(EngineKind.Offline),
      availability = Available
    )

    // Unused across the board, so "not used" is the only word the column can show.
    assertEquals(
      listOf("Not used", "Not used", "Not used"),
      offline.slots.map { it.displayDetail() },
    )
    offline.slots.forEach { slot ->
      assertTrue(
        slot.sentence().contains(slot.displayDetail().lowercase()),
        "sentence '${slot.sentence()}' should carry '${slot.displayDetail()}'",
      )
    }
  }

  @Test
  fun aConfiguredSlotShowsItsValueNotItsState() {
    val status = engineStatus(
      ref = EngineRef.Builtin(EngineKind.Remote),
      instance = EngineInstance(
        id = "test",
        kind = EngineKind.Remote,
        label = "test",
        provider = alphainterplanetary.thinker.engine.RemoteProvider.Ollama,
        baseUrl = "http://localhost:11434",
        model = "llama3",
      ),
      availability = Available,
    )

    assertEquals("http://localhost:11434", status.slot(EngineCapability.Network).displayDetail())
    assertEquals("llama3", status.slot(EngineCapability.Llm).displayDetail())
  }

  @Test
  fun anActiveSlotWithNoValueLeavesTheColumnToItsPill() {
    // No mode produces this pair today (a slot is Active only when it has a value),
    // but the fill and check already say "in use", so a word here would only repeat
    // them — and the next mode that produces the pair must not change that.
    val slot = CapabilityStatus(
      capability = EngineCapability.Tools,
      state = CapabilityState.Active,
      detail = "",
    )

    assertEquals("", slot.displayDetail())
  }

  @Test
  fun aRemoteInstanceThatNeedsSetupIsDescribedAsNeedingSetup() {
    val status = engineStatus(
      ref = EngineRef.Instance("test"),
      instance = EngineInstance(
        id = "test",
        kind = EngineKind.Remote,
        label = "test",
        provider = alphainterplanetary.thinker.engine.RemoteProvider.Ollama,
        baseUrl = "http://localhost:11434",
        model = "",
      ),
      availability = Available,
    )

    val slot = status.slot(EngineCapability.Llm)
    assertEquals("Needs setup", slot.displayDetail())
    assertTrue(slot.sentence().contains("needs setup"))
  }

  @Test
  fun aNotImplementedEngineIsDescribedAsNeedingSetup() {
    val status = engineStatus(
      ref = EngineRef.Builtin(EngineKind.OnDevice),
      availability = NotImplemented,
    )

    val slot = status.slot(EngineCapability.Llm)
    assertEquals("Needs setup", slot.displayDetail())
    assertTrue(slot.sentence().contains("needs setup"))
  }
}
