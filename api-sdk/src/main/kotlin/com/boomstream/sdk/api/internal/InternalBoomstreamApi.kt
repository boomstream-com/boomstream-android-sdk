package com.boomstream.sdk.api.internal

/**
 * Marks an API as internal to the Boomstream SDK's cross-module surface.
 *
 * Declarations annotated with [InternalBoomstreamApi] are **not** part of the stable public
 * API.  They exist solely to allow sibling SDK modules (`player-sdk`, `offline-sdk`) to access
 * implementation details that cannot be expressed through Kotlin's `internal` modifier (which
 * is module-scoped and therefore invisible across Gradle subprojects).
 *
 * **Application code must never opt in to this annotation.**  Any access to an
 * [InternalBoomstreamApi]-gated declaration from outside the SDK modules is unsupported and
 * may be removed or changed in any release, including patch releases.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This is an internal Boomstream SDK API intended for cross-module use only. " +
              "It is not a stable public API and may change or be removed without notice. " +
              "Application code must not reference this declaration.",
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.ANNOTATION_CLASS,
)
annotation class InternalBoomstreamApi
