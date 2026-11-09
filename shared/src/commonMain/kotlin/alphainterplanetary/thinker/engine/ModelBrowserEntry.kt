package alphainterplanetary.thinker.engine

/**
 * One entry in the model browser after merging the models a server already
 * serves with the curated models that can be pulled onto it.
 *
 * [isPull] marks a curated entry that is not installed yet, so the UI can
 * render it as a pull chip instead of a select chip.
 */
data class ModelBrowserEntry(
  val rank: ModelRank,
  val isPull: Boolean,
)
