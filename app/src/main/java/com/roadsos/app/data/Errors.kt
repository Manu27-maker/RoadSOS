package com.roadsos.app.data

import java.io.IOException

/**
 * Turns any failure into something a reporter can act on.
 *
 * Everything here runs on the phone, so there is no "server unreachable" case:
 * the only realistic failures are unreadable photos, missing permissions and
 * the local rate limiter.
 */
fun errorMessage(error: Throwable): String = when (error) {
    is IllegalArgumentException,
    is IllegalStateException,
    -> error.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please retry."

    is SecurityException ->
        "Permission denied. Allow access in Setup and try again."

    is IOException ->
        "Could not read that photo from storage. Try another image."

    else -> error.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please retry."
}
