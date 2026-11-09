package alphainterplanetary.thinker.downloads

data class OllamaPullEvent(
  val status: String? = null,
  val digest: String? = null,
  val total: Long? = null,
  val completed: Long? = null,
  val error: String? = null,
)
