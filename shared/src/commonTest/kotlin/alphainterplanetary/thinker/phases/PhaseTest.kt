package alphainterplanetary.thinker.phases

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhaseTest {

  @Test
  fun `library is the settled six phases in order`() {
    assertEquals(
      listOf(
        "scope-goals",
        "research",
        "design",
        "execution-plan",
        "validation-plan",
        "definition-of-done",
      ),
      BuiltInPhase.entries.map { it.key },
    )
  }

  @Test
  fun `phase keys are unique`() {
    val keys = BuiltInPhase.entries.map { it.key }
    assertEquals(keys.size, keys.toSet().size)
  }

  @Test
  fun `order is the contiguous 1-based display index`() {
    BuiltInPhase.entries.sortedBy { it.order }.forEachIndexed { i, phase ->
      assertEquals(i + 1, phase.order)
    }
  }

  @Test
  fun `labels match the settled names`() {
    assertEquals(
      listOf(
        "Scope & Goals",
        "Research",
        "Design",
        "Execution Plan",
        "Validation Plan",
        "Definition of Done",
      ),
      BuiltInPhase.entries.sortedBy { it.order }.map { it.label },
    )
  }

  @Test
  fun `first is a fresh project's starting phase`() {
    assertEquals("scope-goals", Phase.first.key)
  }

  @Test
  fun `fromKey resolves a stored round key`() {
    assertEquals("Research", Phase.fromKey("research")?.label)
    assertEquals("Definition of Done", Phase.fromKey("definition-of-done")?.label)
  }

  @Test
  fun `fromKey returns null for unknown keys`() {
    assertNull(Phase.fromKey("not-a-phase"))
  }

  @Test
  fun `indexOf is the phase number for display`() {
    assertEquals(1, Phase.indexOf("scope-goals"))
    assertEquals(6, Phase.indexOf("definition-of-done"))
  }

  @Test
  fun `indexOf is 0 for unknown keys`() {
    assertEquals(0, Phase.indexOf("nope"))
  }

  @Test
  fun `indexOf matches order across the library`() {
    BuiltInPhase.entries.forEach { phase ->
      assertEquals(phase.order, Phase.indexOf(phase.key))
    }
  }
}