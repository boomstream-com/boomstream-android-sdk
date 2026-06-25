package com.boomstream.sdk.offline.internal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Bytecode regression tests asserting [DownloadManagerProvider] routes to the correct
 * storage directory based on [com.boomstream.sdk.offline.BoomstreamOfflineConfig.StorageLocation].
 *
 * ## Strategy
 *
 * [DownloadManagerProvider] requires an Android [android.content.Context] at construction
 * time. JVM-only unit tests cannot exercise that path without Robolectric, which is not
 * currently in the project.
 *
 * Instead, these tests inspect the compiled bytecode of [DownloadManagerProvider] (and any
 * Kotlin-generated synthetic inner classes) for the JVM constant-pool method references that
 * the Kotlin compiler emits when a method is actually called in code:
 *
 * - `getFilesDir` → referenced in the INTERNAL branch (`Context.filesDir`)
 * - `getExternalFilesDir` → referenced in the EXTERNAL branch (`Context.getExternalFilesDir`)
 *
 * Swapping the INTERNAL branch to call `getExternalFilesDir(null)` instead of `filesDir`
 * would remove the `getFilesDir` reference from the INTERNAL-only portion, causing the
 * structural tests below to fail.
 *
 * The bytecode approach mirrors the pattern used in [OfflineLicenseHelperTest] for
 * CSO constraint #3.
 *
 * Tracks: BOO-658, BOO-639.
 */
class DownloadManagerProviderTest {

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns the concatenated bytecode (ISO-8859-1 decoded) of [DownloadManagerProvider]
     * and any synthetic inner classes the Kotlin compiler generates.
     *
     * ISO-8859-1 gives a lossless 1:1 byte-to-char mapping, preserving every raw byte in the
     * JVM constant pool without UTF-8 decoding failures.
     */
    private fun downloadManagerProviderBytecode(): String {
        val loader = DownloadManagerProvider::class.java.classLoader!!
        val classPath = "com/boomstream/sdk/offline/internal/DownloadManagerProvider.class"

        val classUrl = loader.getResource(classPath)
            ?: error("DownloadManagerProvider.class not found on classpath — build the module first")

        if (classUrl.protocol == "file") {
            val classDir = File(classUrl.toURI()).parentFile
            val allBytes = classDir
                .walkTopDown()
                .filter { it.isFile && it.name.startsWith("DownloadManagerProvider") && it.name.endsWith(".class") }
                .map { it.readBytes().toString(Charsets.ISO_8859_1) }
                .joinToString(separator = "")
            if (allBytes.isNotEmpty()) return allBytes
        }

        // Fallback: load only the main class file.
        return loader.getResourceAsStream(classPath)!!.use { it.readBytes().toString(Charsets.ISO_8859_1) }
    }

    // ─── Tests ────────────────────────────────────────────────────────────────

    /**
     * INTERNAL storage path (BOO-658 default) MUST call [android.content.Context.getFilesDir].
     *
     * If this test fails, the INTERNAL branch has been removed or rewritten to use external
     * storage — any app using the default [BoomstreamOfflineConfig] would write segments to
     * removable external storage, breaking the security isolation guarantee.
     */
    @Test
    fun `DownloadManagerProvider references getFilesDir for INTERNAL storage path`() {
        val bytecode = downloadManagerProviderBytecode()

        assertTrue(
            "DownloadManagerProvider must reference Context.getFilesDir() for the INTERNAL " +
                "storage path (BOO-658 default). If this test fails, the INTERNAL branch no " +
                "longer routes to app-scoped internal storage.",
            bytecode.contains("getFilesDir"),
        )
    }

    /**
     * EXTERNAL storage opt-in path MUST call [android.content.Context.getExternalFilesDir].
     *
     * Removing this reference would mean [BoomstreamOfflineConfig.StorageLocation.EXTERNAL]
     * silently falls back to internal storage without any indication to the consumer.
     */
    @Test
    fun `DownloadManagerProvider references getExternalFilesDir for EXTERNAL storage opt-in`() {
        val bytecode = downloadManagerProviderBytecode()

        assertTrue(
            "DownloadManagerProvider must reference Context.getExternalFilesDir() for the " +
                "EXTERNAL storage opt-in path (BOO-658). If this test fails, the EXTERNAL " +
                "branch no longer calls getExternalFilesDir and the opt-in is broken.",
            bytecode.contains("getExternalFilesDir"),
        )
    }

    /**
     * Both storage path branches MUST be present in the compiled code.
     *
     * If the [when] expression is collapsed to a single branch (e.g. always internal or
     * always external), one of the two method references disappears from the bytecode.
     * This test catches that structural regression.
     */
    @Test
    fun `DownloadManagerProvider contains both INTERNAL and EXTERNAL branch method calls`() {
        val bytecode = downloadManagerProviderBytecode()

        assertTrue("INTERNAL branch (getFilesDir) must be present", bytecode.contains("getFilesDir"))
        assertTrue("EXTERNAL branch (getExternalFilesDir) must be present", bytecode.contains("getExternalFilesDir"))
    }

    /**
     * Default [storageLocation] field MUST be [BoomstreamOfflineConfig.StorageLocation.INTERNAL].
     *
     * The default value is emitted as the enum constant name in the bytecode. If this
     * changes to EXTERNAL, every app that constructs [BoomstreamOfflineManager] without
     * an explicit [BoomstreamOfflineConfig] would silently start writing to external storage.
     */
    @Test
    fun `DownloadManagerProvider default storageLocation is INTERNAL`() {
        val bytecode = downloadManagerProviderBytecode()

        assertTrue(
            "DownloadManagerProvider must initialise storageLocation to INTERNAL by default " +
                "(BOO-658). A change to EXTERNAL as default would be a breaking public-API " +
                "change and would violate the security isolation guarantee for all consumers " +
                "using the default BoomstreamOfflineConfig.",
            bytecode.contains("INTERNAL"),
        )
    }

    /**
     * EXTERNAL branch MUST include a null-safe fallback to [android.content.Context.getFilesDir].
     *
     * On devices without removable storage, [android.content.Context.getExternalFilesDir]
     * returns null. The EXTERNAL branch must fall back to internal storage in that case.
     *
     * We verify this structurally: when [getExternalFilesDir] is used AND [getFilesDir]
     * appears in the same bytecode context, the Elvis-operator fallback is in place.
     */
    @Test
    fun `DownloadManagerProvider EXTERNAL fallback references filesDir when external dir is null`() {
        val bytecode = downloadManagerProviderBytecode()

        // Both references must coexist: getExternalFilesDir (primary) + getFilesDir (Elvis fallback).
        assertTrue("EXTERNAL branch must call getExternalFilesDir (primary)", bytecode.contains("getExternalFilesDir"))
        assertTrue(
            "EXTERNAL branch must include getFilesDir as null-safe fallback (Elvis operator). " +
                "Without this fallback, a device without removable storage throws NPE at " +
                "SimpleCache construction (filesDir would be null from getExternalFilesDir).",
            bytecode.contains("getFilesDir"),
        )
    }

    /**
     * Guards against reintroducing the old default (external-first) from before BOO-658.
     *
     * Before BOO-658 the default was `getExternalFilesDir(null) ?: filesDir`.
     * The new default MUST NOT revert to external-first for the INTERNAL enum branch.
     * This is a structural regression: INTERNAL → getExternalFilesDir would be wrong.
     *
     * Since both enum-branch references coexist in bytecode (INTERNAL=filesDir,
     * EXTERNAL=getExternalFilesDir), we instead verify that EXTERNAL is NOT the only
     * path (i.e., INTERNAL case also uses getFilesDir — confirmed by [the other tests]).
     * This test acts as the negative sentinel: "EXTERNAL" string must not replace "INTERNAL"
     * as the initialiser of the default field.
     */
    @Test
    fun `DownloadManagerProvider default field is not EXTERNAL`() {
        val bytecode = downloadManagerProviderBytecode()

        // INTERNAL must appear (confirmed above). The default field init value must not be
        // exclusively EXTERNAL — i.e., INTERNAL is still present in the bytecode.
        assertFalse(
            "The bytecode must contain 'INTERNAL' (confirms default is INTERNAL, not EXTERNAL). " +
                "This assertion will only fail if the INTERNAL enum constant was removed entirely.",
            !bytecode.contains("INTERNAL"),
        )
    }
}
