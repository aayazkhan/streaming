package com.streaming.platform.ioskit

import com.streaming.platform.network.AccessTokenProvider

/**
 * Used only for the unauthenticated auth endpoints (register/login/refresh/logout) — never
 * supplies a token. Implemented in Kotlin rather than as a Swift override of the (suspend-fun)
 * AccessTokenProvider protocol: Swift-side overrides of Kotlin suspend interface methods crash on
 * completion in classic Kotlin/Native Obj-C interop (confirmed via crash log — a trivial Swift
 * override of refreshToken() aborted inside Kotlin_ObjCExport_resumeContinuationSuccess). Calling
 * INTO Kotlin suspend functions from Swift is solid; Swift overriding one is not — so anything
 * implementing this protocol lives on the Kotlin side.
 */
object NoAccessTokenProvider : AccessTokenProvider {
    override suspend fun accessToken(): String? = null
    override suspend fun refreshToken(): Boolean = false
}
