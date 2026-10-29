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

object ModelBrowserMerge {
  /**
   * Pure merge logic for the model browser: combines the models an Ollama server
   * already has with the strong-quality curated models that are missing, ranks the
   * combined set, and records which entries are pulls.
   */
  fun merge(
    installed: List<RemoteModelInfo>,
    provider: RemoteProvider = RemoteProvider.Ollama,
    curatedModelIds: Collection<String> = ModelProfiles.strongModelIds,
  ): List<ModelBrowserEntry> {
    val installedKeys = installed.flatMap { model ->
      listOf(model.id, model.displayName)
    }.map { it.lowercase() }.toSet()
    val pullModels = if (provider == RemoteProvider.Ollama) {
      curatedModelIds
        .filter { id ->
          val key = id.lowercase()
          key !in installedKeys && id.lowercase() !in installedKeys
        }
        .map { id -> RemoteModelInfo(id = id, source = ModelSource.Ollama) }
    } else {
      emptyList()
    }
    val pullIds = pullModels.map { it.id }.toSet()
    val ranked = ModelRanker.rank(installed + pullModels)
    return ranked.map { rankedModel ->
      ModelBrowserEntry(rank = rankedModel, isPull = rankedModel.model.id in pullIds)
    }
  }
}
