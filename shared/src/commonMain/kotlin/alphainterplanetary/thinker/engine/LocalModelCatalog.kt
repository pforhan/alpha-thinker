package alphainterplanetary.thinker.engine

/**
 * The curated in-process models the Local tier offers.
 *
 * Only `.litertlm` bundles that LiteRT-LM can open directly are listed: one
 * file per model, a known byte count for the disk check and progress bar, and a
 * pinned SHA-256 so an interrupted transfer can never be mistaken for an
 * installed model. The set starts with the models that work well on a phone —
 * the 3B-4B quality tier first, then the smaller fallback models — rather than
 * exposing every Hugging Face repository.
 */
object LocalModelCatalog {
  private const val HuggingFace = "https://huggingface.co"

  val models: List<LocalModel> = listOf(
    LocalModel(
      id = "litert-community/Phi-4-mini-instruct",
      label = "Phi-4 mini (3.8B)",
      description = "Strong small model for structured planning questions.",
      repoId = "litert-community/Phi-4-mini-instruct",
      fileName = "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm",
      downloadUrl = "$HuggingFace/litert-community/Phi-4-mini-instruct/resolve/main/" +
        "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm",
      expectedBytes = 3_910_090_752L,
      sha256 = "7764d4deb53800578307be33039476b38a6c370fff71bedb3c0552563e23ab02",
      contextTokens = 4_096,
      license = "MIT",
      parameterCount = 3_800_000_000L,
      quantization = "Q8_0",
    ),
    LocalModel(
      id = "warped-community/Llama-3.2-3B-litert-lm",
      label = "Llama 3.2 3B",
      description = "Meta's 3B instruction model, converted for LiteRT-LM.",
      repoId = "warped-community/Llama-3.2-3B-litert-lm",
      fileName = "llama3_2_3b_mixed_int4_gpu.litertlm",
      downloadUrl = "$HuggingFace/warped-community/Llama-3.2-3B-litert-lm/resolve/main/" +
        "llama3_2_3b_mixed_int4_gpu.litertlm",
      expectedBytes = 2_207_940_608L,
      sha256 = "63419d689ef141cc77d6ab5739f27426d779e1165546a4904edefa9fc36fea51",
      contextTokens = 4_096,
      license = "Llama 3.2 Community License",
      parameterCount = 3_210_000_000L,
      quantization = "INT4",
    ),
    LocalModel(
      id = "litert-community/Qwen2.5-1.5B-Instruct",
      label = "Qwen 2.5 1.5B",
      description = "Fast fallback with strong JSON and structured-output behaviour.",
      repoId = "litert-community/Qwen2.5-1.5B-Instruct",
      fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
      downloadUrl = "$HuggingFace/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/" +
        "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
      expectedBytes = 1_597_922_856L,
      sha256 = "c4a9b7289365ab80fe599b844b77ad253eb3c53fb0e5d963d699146a6f01255e",
      contextTokens = 4_096,
      license = "Apache-2.0",
      parameterCount = 1_540_000_000L,
      quantization = "Q8_0",
    ),
    LocalModel(
      id = "warped-community/Llama-3.2-1B-litert-lm",
      label = "Llama 3.2 1B",
      description = "The smallest option, for devices with limited memory.",
      repoId = "warped-community/Llama-3.2-1B-litert-lm",
      fileName = "llama3_2_1b_mixed_int4_gpu.litertlm",
      downloadUrl = "$HuggingFace/warped-community/Llama-3.2-1B-litert-lm/resolve/main/" +
        "llama3_2_1b_mixed_int4_gpu.litertlm",
      expectedBytes = 963_903_488L,
      sha256 = "c6f21c5304c40f45b69e5bfb15bb45acbaad7938e941d25a0a391aef0ec6ac27",
      contextTokens = 4_096,
      license = "Llama 3.2 Community License",
      parameterCount = 1_240_000_000L,
      quantization = "INT4",
    ),
  )

  fun byId(id: String): LocalModel? = models.firstOrNull { it.id == id }

  fun byFileName(fileName: String): LocalModel? =
    models.firstOrNull { it.fileName == fileName }

  fun byRepoId(repoId: String): LocalModel? = models.firstOrNull { it.repoId == repoId }
}
