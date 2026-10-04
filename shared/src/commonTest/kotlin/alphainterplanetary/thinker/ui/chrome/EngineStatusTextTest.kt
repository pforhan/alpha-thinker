package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.engine.EngineCapability
import alphainterplanetary.thinker.engine.EngineMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The chrome's copy for a status: one vocabulary, so the sentence a screen reader
 * announces and the value printed in the row beside it cannot call the same state
 * two different things.
 */
class EngineStatusTextTest {
  @Test
  fun aValuelessSlotIsDescribedInTheSameWordsAsItsSentence() {
    val status = engineStatus(mode = EngineMode.Remote, remoteBaseUrl = "   ")

    val slot = status.slot(EngineCapability.Network)
    assertEquals("Needs setup", slot.displayDetail())
    assertTrue(slot.sentence().contains("needs setup"))
  }

  @Test
  fun aValuelessSlotStatesTheModeVocabularyForEveryState() {
    val lite = engineStatus(mode = EngineMode.Lite)

    // Unused across the board, so "not used" is the only word the column can show.
    assertEquals(
      listOf("Not used", "Not used", "Not used"),
      lite.slots.map { it.displayDetail() },
    )
    lite.slots.forEach { slot ->
      assertTrue(
        slot.sentence().contains(slot.displayDetail().lowercase()),
        "sentence '${slot.sentence()}' should carry '${slot.displayDetail()}'",
      )
    }
  }

  @Test
  fun aConfiguredSlotShowsItsValueNotItsState() {
    val status = engineStatus(
      mode = EngineMode.Remote,
      remoteBaseUrl = "http://localhost:11434",
      remoteModel = "llama3",
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
}