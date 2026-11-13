package alphainterplanetary.thinker.database

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Append-only storage row for the app-wide activity log. Timestamps persist as
 * epoch millis like every other entity in this app's schema; [topic], [source],
 * and [step] persist as enum names, decoded in `Mappers.kt`.
 */
@Entity(
  tableName = "log_events",
  indices = [
    Index("activityId"),
    Index("projectId"),
  ],
)
data class LogEntryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val projectId: String? = null,
  val activityId: String? = null,
  val topic: String,
  val source: String? = null,
  /** Typed step, stored as the [alphainterplanetary.thinker.activitylog.LogStep] name. */
  val step: String,
  val log: String,
  val timestampMillis: Long,
  /** The verbatim payload behind [log] (see `LogEntry.raw`); rarely null. */
  val raw: String? = null,
)
