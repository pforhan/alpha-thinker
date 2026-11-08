package alphainterplanetary.thinker.ui.platform

/** iOS has no app-wide cleartext ban; ATS rules are per-request and not set here. */
internal actual val allowsCleartextRemoteHttp: Boolean = true
