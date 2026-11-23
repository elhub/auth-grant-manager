package no.elhub.auth.v1.common.logging

internal val Throwable.errorName: String
    get() = this::class.simpleName ?: "Unknown"
