package alphainterplanetary.thinker.model

import alphainterplanetary.thinker.database.toDomainModel
import alphainterplanetary.thinker.database.toEntity
import alphainterplanetary.thinker.phases.Phase
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoundTest {

  private fun round(
    roundNumber: Int = 1,
    origin: RoundOrigin = RoundOrigin.Initial,
    completedAt: Instant? = null,
  ) = Round(
    id = "r",
    projectId = "p",
    phase = Phase.ScopeGoals,
    roundNumber = roundNumber,
    origin = origin,
    startedAt = Instant.fromEpochMilliseconds(0),
    completedAt = completedAt,
  )

  // ---------- invariant rails ----------

  @Test
  fun `round number must be positive`() {
    assertFailsWith<IllegalArgumentException> {
      round(roundNumber = 0)
    }
  }

  @Test
  fun `round number must be positive even for completed rounds`() {
    assertFailsWith<IllegalArgumentException> {
      round(roundNumber = -1, completedAt = Instant.fromEpochMilliseconds(1))
    }
  }

  // ---------- completion ----------

  @Test
  fun `round starts incomplete`() {
    assertFalse(round().isCompleted)
  }

  @Test
  fun `complete stamps the completedAt and marks the round complete`() {
    val done = round().complete(Instant.fromEpochMilliseconds(100))
    assertTrue(done.isCompleted)
    assertEquals(Instant.fromEpochMilliseconds(100), done.completedAt)
  }

  @Test
  fun `complete is idempotent on completion state`() {
    val done = round(completedAt = Instant.fromEpochMilliseconds(50))
    assertTrue(done.isCompleted)
  }

  // ---------- entity round-trip ----------

  @Test
  fun `toEntity and back preserves the round`() {
    val original = round(
      roundNumber = 2,
      origin = RoundOrigin.UserRequested,
      completedAt = Instant.fromEpochMilliseconds(500),
    )
    assertEquals(original, original.toEntity().toDomainModel())
  }

  @Test
  fun `toEntity and back preserves an in-progress round`() {
    val original = round()
    assertEquals(original, original.toEntity().toDomainModel())
  }

  @Test
  fun `an unknown phase key on entity load is rejected`() {
    val entity = round().toEntity().copy(phase = "not-a-phase")
    assertFailsWith<IllegalArgumentException> {
      entity.toDomainModel()
    }
  }
}