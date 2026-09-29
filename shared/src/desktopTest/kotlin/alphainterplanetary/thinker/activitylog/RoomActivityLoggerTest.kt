package alphainterplanetary.thinker.activitylog

import alphainterplanetary.thinker.database.ActivityDatabase
import alphainterplanetary.thinker.database.LogEntryEntity
import alphainterplanetary.thinker.database.SettingsKey
import alphainterplanetary.thinker.database.getActivityDatabase
import alphainterplanetary.thinker.database.toEntity
import alphainterplanetary.thinker.testutil.FakeStorage
import alphainterplanetary.thinker.util.now
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class RoomActivityLoggerTest {

  private fun inMemory(): ActivityDatabase =
    getActivityDatabase(Room.inMemoryDatabaseBuilder<ActivityDatabase>().setDriver(BundledSQLiteDriver()))

  private fun Entry(
    activityId: String?,
    log: String,
    category: LogCategory = LogCategory.QuestionGeneration,
    source: LogSource? = LogSource.RemoteLLM,
    timestamp: Instant,
  ): LogEntryEntity = LogEntry(
    projectId = "p1",
    activityId = activityId,
    category = category,
    source = source,
    log = log,
    timestamp = timestamp,
  ).toEntity()

  private fun log(
    db: ActivityDatabase,
    storage: FakeStorage = FakeStorage(),
    scope: CoroutineScope,
  ): RoomActivityLogger = RoomActivityLogger(
    database = db,
    storage = storage,
    scope = scope,
    runStartupSweep = false,
  )

  @Test
  fun `startup sweep prunes the whole log at the persisted retention window`() = runTest {
    val storage = FakeStorage().apply {
      settings[SettingsKey.ActivityLoggerRetentionDays.storageKey] = "30"
    }
    val db = inMemory()
    val dao = db.logDao()
    val now = now()

    dao.append(Entry("old-1", "response: x", timestamp = now - 40.days))
    dao.append(Entry("old-2", "response: y", timestamp = now - 20.days))
    dao.append(Entry("fresh", "started: QuestionGeneration", LogCategory.TaskRun, LogSource.TaskRunner, timestamp = now - 1.days))

    log(db, storage = storage, scope = CoroutineScope(coroutineContext)).runStartupSweep()

    assertEquals(
      listOf("old-2", "fresh"),
      dao.all().map { it.activityId },
      "rows older than the 30-day window are pruned by row age; rows within it survive",
    )
  }

  @Test
  fun `prune with an explicit window deletes rows older than it`() = runTest {
    val db = inMemory()
    val dao = db.logDao()
    val now = Instant.fromEpochMilliseconds(10_000_000_000)

    dao.append(Entry("stale", "response: x", timestamp = now - 400.days))
    dao.append(Entry("recent", "response: y", timestamp = now - 1.days))

    val deleted = log(db, scope = CoroutineScope(coroutineContext)).prune(retentionDays = 7, now = now)

    assertEquals(1, deleted)
    assertEquals(listOf("recent"), dao.all().map { it.activityId })
  }

  @Test
  fun `entries returns the whole log and derived activities group by activity id`() = runTest {
    val db = inMemory()
    val dao = db.logDao()
    val now = Instant.fromEpochMilliseconds(10_000_000_000)
    dao.append(Entry("a1", "started: QuestionGeneration", LogCategory.TaskRun, LogSource.TaskRunner, timestamp = now))
    dao.append(Entry("a1", "response: done", timestamp = now + 1.days))
    dao.append(Entry("a2", "response: title", LogCategory.TitleRecommendation, timestamp = now + 2.days))

    val roomLog = log(db, scope = CoroutineScope(coroutineContext))
    val grouped = ActivityRecord.groupByActivity(roomLog.entries().first())

    assertEquals(listOf("a2", "a1"), grouped.map { it.activityId })
    assertEquals("Recommended title: title", grouped.first().summary)
  }

  @Test
  fun `latestActivity returns the newest activity with its error flag`() = runTest {
    val db = inMemory()
    val dao = db.logDao()
    val now = Instant.fromEpochMilliseconds(10_000_000_000)
    dao.append(Entry("a1", "response: done", timestamp = now))
    dao.append(Entry("a2", "started: QuestionGeneration", LogCategory.TaskRun, LogSource.TaskRunner, timestamp = now + 1.days))
    dao.append(Entry("a2", "failed: model exploded", timestamp = now + 1.days))

    val latest = log(db, scope = CoroutineScope(coroutineContext)).latestActivity().first()

    assertNotNull(latest)
    assertEquals("a2", latest.activityId)
    assertEquals(latest.hasError, true)
    assertEquals("Question generation failed: model exploded", latest.summary)
  }

  @Test
  fun `latestActivity folds only the most recent rows`() = runTest {
    val db = inMemory()
    val dao = db.logDao()
    val now = Instant.fromEpochMilliseconds(10_000_000_000)
    // An older activity buried under the row limit, then a newer one.
    repeat(ActivityLogger.RecentActivityRowLimit) {
      dao.append(Entry("old", "response: buried", timestamp = now))
    }
    dao.append(Entry("new", "response: fresh", LogCategory.TitleRecommendation, timestamp = now + 1.days))

    val latest = log(db, scope = CoroutineScope(coroutineContext)).latestActivity().first()

    assertEquals("new", latest?.activityId)
    assertEquals("Recommended title: fresh", latest?.summary)
  }

  @Test
  fun `latestActivity is null on an empty log`() = runTest {
    val db = inMemory()

    val latest = log(db, scope = CoroutineScope(coroutineContext)).latestActivity().first()

    assertEquals(null, latest)
  }

  @Test
  fun `clear wipes the whole log`() = runTest {
    val db = inMemory()
    val dao = db.logDao()
    val now = Instant.fromEpochMilliseconds(10_000_000_000)

    dao.append(Entry("a1", "response: x", timestamp = now))
    dao.append(Entry("a1", "response: y", timestamp = now + 1.days))

    log(db, scope = CoroutineScope(coroutineContext)).clear()

    assertTrue(dao.all().isEmpty())
  }
}