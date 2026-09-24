package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.activitylog.LogCategory
import alphainterplanetary.thinker.activitylog.LogSource
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class LogDaoTest {

  private fun inMemory(): ActivityDatabase =
    getActivityDatabase(Room.inMemoryDatabaseBuilder<ActivityDatabase>().setDriver(BundledSQLiteDriver()))

  private fun entry(
    activityId: String?,
    timestamp: Instant,
    projectId: String? = null,
    source: LogSource? = LogSource.TaskRunner,
  ): LogEntryEntity = LogEntryEntity(
    projectId = projectId,
    activityId = activityId,
    category = LogCategory.TaskRun.name,
    source = source?.name,
    log = "started: InitialQuestions",
    timestampMillis = timestamp.toEpochMilliseconds(),
  )

  @Test
  fun `append is append-only and integrates autoincrement ids`() = runTest {
    val db = inMemory()
    val dao = db.logDao()
    val timestamp = Instant.fromEpochMilliseconds(1_000)

    val first = dao.append(entry("a1", timestamp))
    val second = dao.append(entry("a1", timestamp + 1.days))

    assertTrue(second > first, "entry ids must increase strictly")
    val rows = dao.all()
    assertEquals(2, rows.size)
    assertEquals(listOf("a1", "a1"), rows.map { it.activityId })
    assertTrue(rows.map { it.id } == listOf(first, second))
    assertEquals(LogCategory.TaskRun.name, rows[0].category)
    assertEquals(LogSource.TaskRunner.name, rows[0].source)
  }

  @Test
  fun `all and project scoping preserve append order`() = runTest {
    val db = inMemory()
    val dao = db.logDao()
    val timestamp = Instant.fromEpochMilliseconds(1_000)

    dao.append(entry("a1", timestamp, projectId = "p1"))
    dao.append(entry("a2", timestamp, projectId = "p2"))
    dao.append(entry("a1", timestamp + 1.days, projectId = "p1"))
    dao.append(entry("a1", timestamp + 2.days, projectId = "p1"))

    assertEquals(listOf("a1", "a2", "a1", "a1"), dao.all().map { it.activityId })
    assertEquals(listOf("a1", "a1", "a1"), dao.allForProject("p1").map { it.activityId })
    assertEquals(listOf("p2"), dao.allForProject("p2").map { it.projectId })
    assertEquals(4, dao.observeAll().first().size)
  }

  @Test
  fun `prune deletes only rows older than the cutoff`() = runTest {
    val db = inMemory()
    val dao = db.logDao()
    val now = Instant.fromEpochMilliseconds(10_000_000_000)

    dao.append(entry("old", now - 30.days))
    dao.append(entry("fresh", now - 1.days))
    dao.append(entry("old-too", now - 10.days))

    val deleted = dao.pruneOlderThan((now - 7.days).toEpochMilliseconds())

    assertEquals(2, deleted, "only rows older than the retention window are deleted")
    assertEquals(listOf("fresh"), dao.all().map { it.activityId })
  }

  @Test
  fun `clearAll wipes every row`() = runTest {
    val db = inMemory()
    val dao = db.logDao()
    val now = Instant.fromEpochMilliseconds(1_000)

    dao.append(entry("a1", now))
    dao.append(entry("a1", now + 1.days))

    dao.clearAll()

    assertTrue(dao.all().isEmpty())
    assertTrue(dao.observeAll().first().isEmpty())
  }
}