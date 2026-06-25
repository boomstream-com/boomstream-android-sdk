// SPDX-License-Identifier: Apache-2.0
// Self-test fixture for the .gitlab-ci.yml `on-disk-cache-guard` job.
//
// This file intentionally contains every pattern the guard MUST detect. It lives
// outside every module's src/main, so it is never compiled and never shipped in any
// published AAR. The CI job `on-disk-cache-guard-self-test` greps THIS directory
// with the same $ON_DISK_CACHE_GUARD_PATTERNS regex and expects matches; if the
// regex drifts out of sync with what's listed here, that job fails (negative-test
// regression catcher for the guard itself).
//
// To extend coverage:
//   1. Add the new pattern to $ON_DISK_CACHE_GUARD_PATTERNS in .gitlab-ci.yml.
//   2. Add a corresponding line below, annotated `// pattern: <regex-fragment>`.
//   3. Re-run CI; on-disk-cache-guard-self-test verifies every pattern has at
//      least one match here.
//
// DO NOT compile this file. DO NOT import from any production module.

package ci.fixtures.ondiskcache

import androidx.security.crypto.EncryptedSharedPreferences      // pattern: EncryptedSharedPreferences
import androidx.room.Database                                    // pattern: androidx.room.
import androidx.datastore.preferences.preferencesDataStore       // pattern: androidx.datastore.
import okhttp3.Cache                                             // pattern: okhttp3.Cache
import io.realm.kotlin.Realm                                     // pattern: io.realm
import io.objectbox.BoxStore                                     // pattern: io.objectbox
import app.cash.sqldelight.db.SqlDriver                          // pattern: app.cash.sqldelight

class FixtureNeverCompiled {
    fun a() = getSharedPreferences("x", 0)                       // pattern: getSharedPreferences
    fun b() = openFileOutput("f", 0)                             // pattern: openFileOutput
    fun c() = getFilesDir()                                      // pattern: getFilesDir
    fun d() = getCacheDir()                                      // pattern: getCacheDir
    fun e() = getExternalFilesDir(null)                          // pattern: getExternalFilesDir
    fun f() = SQLiteOpenHelper                                   // pattern: SQLiteOpenHelper
    fun g() = SQLiteDatabase                                     // pattern: SQLiteDatabase
}
