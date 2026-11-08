package alphainterplanetary.thinker.ui.platform

/**
 * Whether this platform lets a remote connection use a plain `http:` endpoint.
 *
 * Android blocks cleartext traffic by default, so an `http://` remote host fails
 * there even though it works from a desktop build. The connection dialog warns
 * before the user saves a URL it knows the platform will reject.
 */
internal expect val allowsCleartextRemoteHttp: Boolean
