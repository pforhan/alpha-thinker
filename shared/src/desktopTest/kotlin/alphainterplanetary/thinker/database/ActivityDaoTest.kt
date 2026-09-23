package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.activitylog.EngineActivityEvent
import alphainterplanetary.thinker.activitylog.EngineActivityEventType
import alphainterplanetary.thinker.activitylog.LogCategory
import alphainterplanetary.thinker.tasks.TaskKind
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class ActivityDaoTest {

  private fun inMemory(): ActivityDatabase =
    getActivityDatabase(Room.inMemoryDatabaseBuilder<ActivityDatabase>().setDriver(BundledSQLiteDriver()))

  private fun event(
    activityId: String,
    eventType: EngineActivityEventType,
    timestamp: Instant,
    projectId: String? = null,
  ): ActivityEventEntity = EngineActivityEvent(
    activityId = activityId,
    projectId = projectId,
    kind = TaskKind.InitialQuestions,
    logCategory = LogCategory.Hardcoded,
    eventType = eventType,
    timestamp = timestamp,
  ).toEntity()

  @Test
  fun `append is append-only and integrates autoincrement ids`() = runTest {
    val db = inMemory()
    val dao = db.activityDao()
    val timestamp = Instant.fromEpochMilliseconds(1_000)

    val first = dao.append(event("a1", EngineActivityEventType.Created, timestamp))
    val second = dao.append(event("a1", EngineActivityEventType.Succeeded, timestamp + 1.days))

    assertTrue(second > first, "event ids must increase strictly")
    val rows = dao.all()
    assertEquals(2, rows.size)
    assertEquals(listOf("a1", "a1"), rows.map { it.activityId })
    assertTrue(rows.map { it.eventId } == listOf(first, second))
    assertEquals(EngineActivityEventType.Created.name, rows[0].eventType)
    assertEquals(EngineActivityEventType.Succeeded.name, rows[1].eventType)
  }

  @Test
  fun `history and project scoping preserve append order`() = runTest {
    val db = inMemory()
    val dao = db.activityDao()
    val timestamp = Instant.fromEpochMilliseconds(1_000)

    dao.append(event("a1", EngineActivityEventType.Created, timestamp, projectId = "p1"))
    dao.append(event("a2", EngineActivityEventType.Created, timestamp, projectId = "p2"))
    dao.append(event("a1", EngineActivityEventType.Progress, timestamp + 1.days, projectId = "p1"))
    dao.append(event("a1", EngineActivityEventType.Succeeded, timestamp + 2.days, projectId = "p1"))

    assertEquals(
      listOf("a1", "a1", "a1"),
      dao.historyForActivity("a1").map { it.activityId },
      "history returns every event in append order",
    )
    assertEquals(listOf("a1", "a1", "a1"), dao.allForProject("p1").map { it.activityId })
    assertEquals(listOf("p2"), dao.allForProject("p2").map { it.projectId })
    assertEquals(4, dao.observeAll().first().size)
  }

  @Test
  fun `latestPerActivity folds to the newest event per activity`() = runTest {
    val db = inMemory()
    val dao = db.activityDao()
    val timestamp = Instant.fromEpochMilliseconds(1_000)

    dao.append(event("a1", EngineActivityEventType.Created, timestamp))
    dao.append(event("a2", EngineActivityEventType.Created, timestamp))
    dao.append(event("a1", EngineActivityEventType.Progress, timestamp + 1.days))

    val latest = dao.latestPerActivity()
    assertEquals(setOf("a1", "a2"), latest.map { it.activityId }.toSet())
    val a1 = latest.first { it.activityId == "a1" }
    assertEquals(EngineActivityEventType.Progress.name, a1.eventType)
    assertEquals(3L, a1.eventId, "latest row carries the highest event id per activity")
  }

  @Test
  fun `prune deletes only terminal activities older than the window`() = runTest {
    val db = inMemory()
    val dao = db.activityDao()
    val now = Instant.fromEpochMilliseconds(10_000_000_000)

    dao.append(event("old-terminal", EngineActivityEventType.Succeeded, now - 30.days))
    dao.append(event("fresh-terminal", EngineActivityEventType.Succeeded, now - 1.days))
    dao.append(event("old-live", EngineActivityEventType.Created, now - 30.days))
    dao.append(event("old-live", EngineActivityEventType.Progress, now - 29.days))

    val deleted = dao.pruneTerminalActivities((now - 7.days).toEpochMilliseconds())

    assertEquals(1, deleted, "only the old terminal activity's rows are deleted")
    val remaining = dao.all().map { it.activityId }
    assertEquals(setOf("fresh-terminal", "old-live"), remaining.toSet())
  }

  @Test
  fun `clearAll wipes every row`() = runTest {
    val db = inMemory()
    val dao = db.activityDao()
    val now = Instant.fromEpochMilliseconds(1_000)

    dao.append(event("a1", EngineActivityEventType.Created, now))
    dao.append(event("a1", EngineActivityEventType.Succeeded, now + 1.days))

    dao.clearAll()

    assertTrue(dao.all().isEmpty())
    assertTrue(dao.observeAll().first().isEmpty())
    assertNotNull(dao)
  }
}