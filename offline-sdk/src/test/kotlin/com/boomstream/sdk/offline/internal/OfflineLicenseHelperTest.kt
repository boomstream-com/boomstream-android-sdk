package com.boomstream.sdk.offline.internal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression tests asserting [OfflineLicenseHelper] uses [EncryptedSharedPreferences]
 * for keyset persistence — CSO constraint #3 (BOO-596).
 *
 * ## Strategy
 *
 * [OfflineLicenseHelper] requires a real Android [android.content.Context] for
 * construction (it opens the encrypted preference file via [EncryptedSharedPreferences]).
 * JVM-only unit tests cannot exercise that path without Robolectric, which is not
 * currently in the project.
 *
 * Instead, these tests inspect the compiled bytecode of [OfflineLicenseHelper] (and any
 * Kotlin-generated synthetic inner classes) for the constant-pool references that the
 * Kotlin compiler emits when the class is actually used in code:
 *
 * - The JVM constant pool stores class/method names as verbatim ASCII in the `.class` file.
 * - Swapping `EncryptedSharedPreferences.create(...)` to `context.getSharedPreferences(...)`
 *   removes the `EncryptedSharedPreferences` class reference from the constant pool entirely.
 * - Unused Kotlin imports are **not** emitted by the compiler, so a stale import cannot
 *   cause a false-negative.
 *
 * This matches the pattern used in `:player-sdk`'s `BoomstreamDataSourceFactoryTest` for
 * CSO constraint #2 (HttpLoggingInterceptor absence check).
 *
 * Tracks: BOO-641, BOO-596 constraint #3.
 */
class OfflineLicenseHelperTest {

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns the concatenated bytecode (ISO-8859-1 decoded) of [OfflineLicenseHelper] and
     * any synthetic inner classes the Kotlin compiler generates for lazy-property initializers.
     *
     * ISO-8859-1 gives a lossless 1:1 byte-to-char mapping, preserving every raw byte in the
     * JVM constant pool without UTF-8 decoding failures.
     */
    private fun offlineLicenseHelperBytecode(): String {
        val loader = OfflineLicenseHelper::class.java.classLoader!!
        val classPath = "com/boomstream/sdk/offline/internal/OfflineLicenseHelper.class"

        val classUrl = loader.getResource(classPath)
            ?: error("OfflineLicenseHelper.class not found on classpath — build the module first")

        // When running under the standard Gradle unit-test task the class files are on the
        // filesystem (not packaged into a JAR), so we can walk the directory to also pick up
        // any synthetic OfflineLicenseHelper$*.class files that Kotlin may generate.
        if (classUrl.protocol == "file") {
            val classDir = File(classUrl.toURI()).parentFile
            val allBytes = classDir
                .walkTopDown()
                .filter { it.isFile && it.name.startsWith("OfflineLicenseHelper") && it.name.endsWith(".class") }
                .map { it.readBytes().toString(Charsets.ISO_8859_1) }
                .joinToString(separator = "")
            if (allBytes.isNotEmpty()) return allBytes
        }

        // Fallback (e.g. test runner packaging classes into a jar): just load the main file.
        return loader.getResourceAsStream(classPath)!!.use { it.readBytes().toString(Charsets.ISO_8859_1) }
    }

    // ─── Tests ────────────────────────────────────────────────────────────────

    /**
     * CSO constraint #3 (BOO-596): keyset persistence MUST use [EncryptedSharedPreferences].
     *
     * **Regression scenarios this catches:**
     * 1. Developer swaps `EncryptedSharedPreferences.create(...)` → `context.getSharedPreferences(...)`
     *    while debugging a Keystore crash — the `EncryptedSharedPreferences` constant-pool
     *    reference disappears and this test fails.
     * 2. A library-bump that removes `androidx.security:security-crypto` causes a fallback to
     *    plain SharedPreferences — same constant-pool absence, same failure.
     */
    @Test
    fun `OfflineLicenseHelper uses EncryptedSharedPreferences — CSO constraint 3`() {
        val bytecode = offlineLicenseHelperBytecode()

        assertTrue(
            "OfflineLicenseHelper must use EncryptedSharedPreferences for keyset persistence " +
                "(CSO constraint #3, BOO-596). If this test fails, keyset storage has been " +
                "silently downgraded to plain SharedPreferences — keysets become readable " +
                "without root on a rooted device or via ADB backup.",
            bytecode.contains("EncryptedSharedPreferences"),
        )
    }

    /**
     * Guards the on-disk SharedPreferences file name used by [OfflineLicenseHelper].
     *
     * Renaming `boomstream_offline_license` would silently lose every stored keyset on
     * existing installs (the encrypted file would be abandoned; a Liquibase-style migration
     * would be required). This assertion makes any rename a visible test failure.
     */
    @Test
    fun `OfflineLicenseHelper preference file name is boomstream_offline_license`() {
        val bytecode = offlineLicenseHelperBytecode()

        assertTrue(
            "OfflineLicenseHelper must use preference file name \"boomstream_offline_license\". " +
                "Changing this name silently abandons all keysets stored on existing installs " +
                "(a migration is required before renaming).",
            bytecode.contains("boomstream_offline_license"),
        )
    }

    /**
     * Guards the AES-256 encryption schemes passed to [EncryptedSharedPreferences.create].
     *
     * [EncryptedSharedPreferences] is called with:
     * - `PrefKeyEncryptionScheme.AES256_SIV`  — encrypts preference keys
     * - `PrefValueEncryptionScheme.AES256_GCM` — encrypts preference values (the keyset bytes)
     *
     * Downgrading either scheme would weaken the encryption of the stored keyset material.
     */
    @Test
    fun `OfflineLicenseHelper uses AES-256 encryption schemes — CSO constraint 3`() {
        val bytecode = offlineLicenseHelperBytecode()

        assertTrue(
            "OfflineLicenseHelper must use AES256_SIV as the PrefKeyEncryptionScheme " +
                "(CSO constraint #3, BOO-596). Downgrading weakens key encryption.",
            bytecode.contains("AES256_SIV"),
        )
        assertTrue(
            "OfflineLicenseHelper must use AES256_GCM as the PrefValueEncryptionScheme " +
                "(CSO constraint #3, BOO-596). Downgrading weakens value encryption.",
            bytecode.contains("AES256_GCM"),
        )
    }

    /**
     * Guards against re-introduction of the deprecated `MasterKeys` API (BOO-640).
     *
     * `androidx.security.crypto.MasterKeys` is deprecated since security-crypto 1.1.0-alpha02.
     * [OfflineLicenseHelper] must use `MasterKey.Builder` (non-deprecated) instead.
     *
     * Fails if someone reverts the BOO-640 migration or copy-pastes an old snippet.
     * (This guard requires BOO-640 to be in the branch ancestry — enforced by rebase.)
     */
    @Test
    fun `OfflineLicenseHelper does not use deprecated MasterKeys — BOO-640 migration guard`() {
        val bytecode = offlineLicenseHelperBytecode()

        assertFalse(
            "OfflineLicenseHelper must not reference deprecated MasterKeys. " +
                "Migrated to MasterKey.Builder in BOO-640. " +
                "Reverting causes kotlinc deprecation warnings.",
            bytecode.contains("MasterKeys"),
        )
        assertTrue(
            "OfflineLicenseHelper must use MasterKey.Builder (BOO-640 non-deprecated API).",
            bytecode.contains("MasterKey"),
        )
    }
}
