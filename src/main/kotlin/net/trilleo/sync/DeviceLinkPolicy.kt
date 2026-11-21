package net.trilleo.sync

/** What the link flow does after one poll of `POST /v1/auth/device/token` that did not return a token. */
sealed interface PollStep {
    /** Poll again after [seconds], which become the new interval. */
    data class Wait(val seconds: Long) : PollStep

    /** The player declined, or the account has no room for another token. */
    data object Denied : PollStep

    /** The code expired or was already used; the player has to start over. */
    data object Expired : PollStep

    /** Anything else: stop and report [code]. */
    data class Failed(val code: String) : PollStep
}

/**
 * The polling rules from the API's personal-token guide, kept pure so they are tested without a network.
 */
object DeviceLinkPolicy {

    /** The guide's default interval, used when the start answer leaves it out. */
    const val DEFAULT_INTERVAL: Long = 5

    /** What a `slow_down` adds when it does not say the new interval itself. */
    private const val SLOW_DOWN_STEP: Long = 5

    /** The step after an error answer to a poll made at [interval] seconds. */
    fun afterError(error: ApiError, interval: Long): PollStep = when (error.code) {
        "authorization_pending" -> PollStep.Wait(interval)
        "slow_down" -> PollStep.Wait(maxOf(error.interval ?: (interval + SLOW_DOWN_STEP), interval + 1))
        "access_denied" -> PollStep.Denied
        "expired_token" -> PollStep.Expired
        "rate_limited" -> PollStep.Wait(maxOf(error.retryAfterSeconds ?: interval, interval))
        else -> if (error.status >= 500) PollStep.Wait(interval) else PollStep.Failed(error.code)
    }

    /** After a poll that got no answer at all, keep trying at the same pace until the code expires. */
    fun afterNetworkFailure(interval: Long): PollStep = PollStep.Wait(interval)
}
