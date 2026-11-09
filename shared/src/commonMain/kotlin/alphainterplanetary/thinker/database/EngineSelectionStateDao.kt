package alphainterplanetary.thinker.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert

/**
 * Persistence for per-engine selection and usage. [setSelected] keeps the
 * "at most one selected" invariant, and preserves a row's remembered
 * `lastUsedAt` when it is re-selected, all in one transaction.
 */
@Dao
interface EngineSelectionStateDao {
  @Query("SELECT * FROM engine_selection_state")
  suspend fun getAll(): List<EngineSelectionStateEntity>

  @Query("SELECT * FROM engine_selection_state WHERE refKey = :refKey")
  suspend fun getByKey(refKey: String): EngineSelectionStateEntity?

  @Upsert
  suspend fun upsert(state: EngineSelectionStateEntity)

  @Query("DELETE FROM engine_selection_state WHERE refKey = :refKey")
  suspend fun deleteByKey(refKey: String)

  /**
   * Marks [refKey] as the sole selection: every other row is cleared first so
   * the app can never resolve two selected engines. The target row is inserted
   * if absent, carrying over its remembered `lastUsedAt` when it exists.
   */
  @Transaction
  suspend fun setSelected(refKey: String) {
    clearSelected()
    val existing = getByKey(refKey)
    upsert(
      EngineSelectionStateEntity(
        refKey = refKey,
        selected = true,
        lastUsedAt = existing?.lastUsedAt
      )
    )
  }

  @Query("UPDATE engine_selection_state SET selected = 0")
  suspend fun clearSelected()
}
