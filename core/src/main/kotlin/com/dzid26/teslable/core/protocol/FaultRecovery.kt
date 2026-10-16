// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.core.protocol

import com.tesla.generated.universalmessage.MessageStatus

/** What the link does about a message the car rejected with a fault. */
enum class FaultRecovery {
    /** Our session drifted (counter, epoch, clock, signature): take the car's session info, else handshake again. */
    RESYNC_SESSION,

    /** The car does not know this phone's key: no session recovers, the car must be paired again. */
    PAIR_AGAIN,

    /**
     * No session recovery applies: the session is intact, or the message was not rejected.
     * Whether to ask again is up to the caller; the next poll does.
     */
    NONE,
}

/** The recovery for a rejected message; [FaultRecovery.NONE] for a message that was not rejected. */
val MessageStatus.recovery: FaultRecovery
    get() =
        when {
            isUnknownKey -> FaultRecovery.PAIR_AGAIN
            isStaleSession -> FaultRecovery.RESYNC_SESSION
            else -> FaultRecovery.NONE
        }
