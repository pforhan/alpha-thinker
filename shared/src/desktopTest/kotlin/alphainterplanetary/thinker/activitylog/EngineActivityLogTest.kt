package alphainterplanetary.thinker.activitylog

import alphainterplanetary.thinker.database.ActivityDatabase
import alphainterplanetary.thinker.database.ActivityEventEntity
import alphainterplanetary.thinker.database.SettingsKey
import alphainterplanetary.thinker.database.getActivityDatabase
import alphainterplanetary.thinker.database.toEntity
import alphainterplanetary.thinker.testutil.FakeStorage
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class EngineActivityLogTest {

  private fun inMemory(): ActivityDatabase =
    getActivityDatabase(Room.inMemoryDatabaseBuilder<ActivityDatabase>().setDriver(BundledSQLiteDriver()))

  private fun Event(
    activityId: String,
    eventType: EngineActivityEventType,
    timestamp: Instant,
  ): ActivityEventEntity = EngineActivityEvent(
    activityId = activityId,
    eventType = eventType,
    timestamp = timestamp,
  ).toEntity()

  @Test
  fun `recoverInterrupted closes only live activities, idempotently`() = runTest {
    val db = inMemory()
    val dao = db.activityDao()
    val now = Instant.fromEpochMilliseconds(10_000_000_000)

    dao.append(Event("killed-created", EngineActivityEventType.Created, now - 1.days))
    dao.append(Event("killed-progress", EngineActivityEventType.Progress, now - 1.days))
    dao.append(Event("finished", EngineActivityEventType.Succeeded, now - 1.days))

    val log = RoomEngineActivityLog(
      database = db,
      storage = FakeStorage(),
      scope = CoroutineScope(coroutineContext),
      runStartupSweep = false,
    )
    log.recoverInterrupted(now)
    log.recoverInterrupted(now)

    val latest = log.latestPerActivity()
    val killedCreated = latest.first { it.activityId == "killed-created" }
    val killedProgress = latest.first { it.activityId == "killed-progress" }
    val finished = latest.first { it.activityId == "finished" }

    assertEquals(EngineActivityEventType.Failed, killedCreated.eventType)
    assertEquals("interrupted", killedCreated.error)
    assertEquals(EngineActivityEventType.Failed, killedProgress.eventType)
    assertEquals("interrupted", killedProgress.error)
    assertEquals(EngineActivityEventType.Succeeded, finished.eventType)
    assertNull(finished.error)

    val killedHistory = db.activityDao().historyForActivity("killed-created")
    assertEquals(2, killedHistory.size, "recovery appends exactly one marker per activity, even when run twice")
  }

  @Test
  fun `startup sweep prunes whole terminal activities at the persisted retention window`() = runTest {
    val storage = FakeStorage().apply {
      settings[SettingsKey.ActivityLogRetentionDays.storageKey] = "30"
    }
    val db = inMemory()
    val dao = db.activityDao()
    val now = Instant.fromEpochMilliseconds(10_000_000_000)

    dao.append(Event("old-terminal", EngineActivityEventType.Succeeded, now - 40.days))
    dao.append(Event("old-live", EngineActivityEventType.Created, now - 20.days))

    RoomEngineActivityLog(
      database = db,
      storage = storage,
      scope = CoroutineScope(coroutineContext),
      runStartupSweep = false,
    ).runStartupSweep()

    assertEquals(
      listOf("old-live", "old-live"),
      dao.all().map { it.activityId },
      "only the terminal activity older than the 30-day window is pruned; the live one is closed by recovery",
    )
  }

  @Test
  fun `prune with an explicit window ignores live activities`() = runTest {
    val db = inMemory()
    val dao = db.activityDao()
    val now = Instant.fromEpochMilliseconds(10_000_000_000)

    dao.append(Event("live", EngineActivityEventType.Created, now - 400.days))

    val log = RoomEngineActivityLog(
      database = db,
      storage = FakeStorage(),
      scope = CoroutineScope(coroutineContext),
      runStartupSweep = false,
    )
    val deleted = log.prune(retentionDays = 7, now = now)

    assertEquals(0, deleted)
    assertEquals(listOf("live"), dao.all().map { it.activityId })
  }

  @Test
  fun `clear wipes the whole log`() = runTest {
    val db = inMemory()
    val dao = db.activityDao()
    val now = Instant.fromEpochMilliseconds(10_000_000_000)

    dao.append(Event("a1", EngineActivityEventType.Created, now))
    dao.append(Event("a1", EngineActivityEventType.Succeeded, now + 1.days))

    val log = RoomEngineActivityLog(
      database = db,
      storage = FakeStorage(),
      scope = CoroutineScope(coroutineContext),
      runStartupSweep = false,
    )
    log.clear()

    assertTrue(dao.all().isEmpty())
  }
}