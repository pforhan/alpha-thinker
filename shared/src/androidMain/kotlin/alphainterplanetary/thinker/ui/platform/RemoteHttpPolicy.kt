package alphainterplanetary.thinker.ui.platform

/** Android blocks cleartext traffic unless the manifest opts in, which it does not. */
internal actual val allowsCleartextRemoteHttp: Boolean = false
