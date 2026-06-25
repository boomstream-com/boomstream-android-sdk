package com.boomstream.sdk.player.internal

/**
 * Returns `true` when [throwable] (or any cause in its chain) represents a network-level
 * connectivity failure — i.e. the device could not reach the remote host.
 *
 * Used to distinguish "no network" from other failure modes when deciding whether to show the
 * `no_network_offline` system message instead of a raw error string.
 */
internal fun isNetworkError(throwable: Throwable): Boolean {
    var ex: Throwable? = throwable
    while (ex != null) {
        if (ex is java.net.UnknownHostException ||
            ex is java.net.ConnectException ||
            ex is java.net.SocketException ||
            ex is java.net.SocketTimeoutException) return true
        ex = ex.cause
    }
    return false
}
