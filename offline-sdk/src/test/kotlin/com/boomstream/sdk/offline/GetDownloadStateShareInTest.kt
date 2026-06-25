package com.boomstream.sdk.offline

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Bytecode regression tests asserting that [BoomstreamOfflineManager.getDownloadState]
 * uses `shareIn` to deduplicate 500 ms poll loops across multiple concurrent collectors.
 *
 * ## Strategy
 *
 * [BoomstreamOfflineManager] requires an Android [android.content.Context] at construction
 * time, which is unavailable in JVM unit tests without Robolectric (not in project).
 * Bytecode inspection (ISO-8859-1 constant-pool scan) is used instead — the same approach
 * as [com.boomstream.sdk.offline.internal.DownloadManagerProviderTest] and
 * [com.boomstream.sdk.offline.internal.OfflineLicenseHelperTest].
 *
 * Key invariants verified:
 * - `shareIn` is called (deduplication mechanism).
 * - `WhileSubscribed` is used as [kotlinx.coroutines.flow.SharingStarted] (upstream stops
 *   when all subscribers leave, restarts on next subscriber — correct lifecycle).
 * - `computeIfAbsent` is called (atomic single-creation per mediaCode — no double-upstream
 *   under concurrent first-subscribe).
 * - `ConcurrentHashMap` is referenced (thread-safe per-mediaCode cache).
 *
 * ## What this guards
 *
 * Before BOO-661, each `getDownloadState(mediaCode)` call created an independent
 * `callbackFlow` with its own 500 ms poll coroutine. Five parallel collectors produced
 * five DB reads every 500 ms. After BOO-661 the shared flow routes all five collectors
 * to one upstream.
 *
 * Tracks: BOO-661.
 */
class GetDownloadStateShareInTest {

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns the concatenated ISO-8859-1 bytecode of [BoomstreamOfflineManager] and all
     * Kotlin-generated synthetic inner/companion classes.
     *
     * ISO-8859-1 provides lossless 1-to-1 byte mapping, preserving JVM constant-pool entries
     * without UTF-8 decoding failures on arbitrary byte sequences.
     */
    private fun offlineManagerBytecode(): String {
        val loader = BoomstreamOfflineManager::class.java.classLoader!!
        val classPath = "com/boomstream/sdk/offline/BoomstreamOfflineManager.class"

        val classUrl = loader.getResource(classPath)
            ?: error(
                "BoomstreamOfflineManager.class not found on classpath — " +
                    "build the :offline-sdk module first (./gradlew :offline-sdk:compileDebugUnitTestKotlin)",
            )

        if (classUrl.protocol == "file") {
            val classDir = File(classUrl.toURI()).parentFile
            val allBytes = classDir
                .walkTopDown()
                .filter { it.isFile && it.name.startsWith("BoomstreamOfflineManager") && it.name.endsWith(".class") }
                .map { it.readBytes().toString(Charsets.ISO_8859_1) }
                .joinToString(separator = "")
            if (allBytes.isNotEmpty()) return allBytes
        }

        return loader.getResourceAsStream(classPath)!!.use { it.readBytes().toString(Charsets.ISO_8859_1) }
    }

    // ─── shareIn deduplication tests ─────────────────────────────────────────

    /**
     * `getDownloadState` MUST reference `shareIn` from kotlinx-coroutines.
     *
     * Without `shareIn`, each collector would start an independent upstream callbackFlow
     * with its own 500 ms poll loop — N collectors → N DB reads every 500 ms.
     * Removing `shareIn` causes this test to fail, immediately surfacing the regression.
     */
    @Test
    fun `BoomstreamOfflineManager references shareIn for poll deduplication`() {
        val bytecode = offlineManagerBytecode()

        assertTrue(
            "BoomstreamOfflineManager must call shareIn() (BOO-661). " +
                "Without it each getDownloadState() collector starts its own 500 ms poll loop.",
            bytecode.contains("shareIn"),
        )
    }

    /**
     * The sharing strategy MUST be `WhileSubscribed`.
     *
     * `WhileSubscribed` ensures the upstream poll loop is running only while there is at
     * least one subscriber and is stopped (with a 5-second grace period) when the last
     * subscriber leaves. Using `Eagerly` or `Lazily` would leak a poll loop for the
     * lifetime of the manager scope even when no UI is active.
     */
    @Test
    fun `BoomstreamOfflineManager uses WhileSubscribed sharing strategy`() {
        val bytecode = offlineManagerBytecode()

        assertTrue(
            "BoomstreamOfflineManager must use SharingStarted.WhileSubscribed (BOO-661). " +
                "Eagerly/Lazily would keep the poll loop alive with no active subscribers.",
            bytecode.contains("WhileSubscribed"),
        )
    }

    /**
     * The per-mediaCode flow cache MUST use `computeIfAbsent` for atomic single-creation.
     *
     * `ConcurrentHashMap.computeIfAbsent` guarantees the factory lambda is called at most
     * once per key even under concurrent first-subscribe calls for the same mediaCode.
     * A non-atomic `getOrPut` (Kotlin stdlib extension) is NOT safe: two threads can both
     * see a missing entry and create two upstream flows.
     */
    @Test
    fun `BoomstreamOfflineManager uses computeIfAbsent for atomic per-mediaCode flow creation`() {
        val bytecode = offlineManagerBytecode()

        assertTrue(
            "BoomstreamOfflineManager must call ConcurrentHashMap.computeIfAbsent (BOO-661). " +
                "Non-atomic getOrPut can create two upstream flows under concurrent first-subscribe.",
            bytecode.contains("computeIfAbsent"),
        )
    }

    /**
     * The flow cache field MUST be backed by `ConcurrentHashMap`.
     *
     * The constant-pool entry for `java/util/concurrent/ConcurrentHashMap` appears in the
     * bytecode whenever the class is instantiated or its methods are called. Replacing it
     * with `HashMap` (not thread-safe) or `mutableMapOf()` (also `HashMap` under the hood)
     * would remove this constant and cause this test to fail.
     */
    @Test
    fun `BoomstreamOfflineManager uses ConcurrentHashMap for thread-safe flow cache`() {
        val bytecode = offlineManagerBytecode()

        assertTrue(
            "BoomstreamOfflineManager must reference ConcurrentHashMap (BOO-661). " +
                "A plain HashMap is not thread-safe under concurrent getDownloadState() calls.",
            bytecode.contains("ConcurrentHashMap"),
        )
    }
}
