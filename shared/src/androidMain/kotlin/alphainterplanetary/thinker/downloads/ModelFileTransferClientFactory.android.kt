package alphainterplanetary.thinker.downloads

import alphainterplanetary.thinker.di.PlatformContext

actual object ModelFileTransferClientFactory {
  actual fun create(platformContext: PlatformContext): ModelFileTransferClient =
    AndroidModelFileTransferClient(platformContext)
}
