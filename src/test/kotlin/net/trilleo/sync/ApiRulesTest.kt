package net.trilleo.sync

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Reading the API's error envelope, and the device-link polling rules built on it. */
class ApiRulesTest {

    private fun error(status: Int, code: String, details: String = "{}", retryAfter: String? = null) =
        ApiError.parse(
            status,
            """{"error":{"code":"$code","message":"m","details":$details,"requestId":"req_1"}}""",
            retryAfter,
        )

    @Test
    fun `the envelope is read`() {
        val e = error(429, "rate_limited", retryAfter = "12")
        assertEquals("rate_limited", e.code)
        assertEquals("req_1", e.requestId)
        assertEquals(12L, e.retryAfterSeconds)
        assertTrue(e.retryable)
    }

    @Test
    fun `retry-after falls back to the details`() {
        assertEquals(7L, error(429, "rate_limited", """{"retryAfter":7}""").retryAfterSeconds)
    }

    @Test
    fun `a body that is not the envelope is coded by status`() {
        val e = ApiError.parse(502, "<html>Bad gateway</html>", null)
        assertEquals("http_502", e.code)
        assertTrue(e.retryable)
    }

    @Test
    fun `the token errors forget the token and nothing else does`() {
        listOf("token_required", "invalid_key", "key_revoked", "key_expired").forEach {
            assertTrue(error(401, it).forgetsToken, it)
        }
        assertTrue(error(403, "account_blocked").forgetsToken)
        assertTrue(error(401, "some_future_code").forgetsToken)
        assertFalse(error(409, "profile_limit").forgetsToken)
        assertFalse(error(429, "rate_limited").forgetsToken)
        assertFalse(error(400, "invalid_input").forgetsToken)
    }

    @Test
    fun `polling follows the guide`() {
        assertEquals(PollStep.Wait(5), DeviceLinkPolicy.afterError(error(400, "authorization_pending"), 5))
        assertEquals(PollStep.Wait(10), DeviceLinkPolicy.afterError(error(400, "slow_down"), 5))
        assertEquals(PollStep.Wait(15), DeviceLinkPolicy.afterError(error(400, "slow_down", """{"interval":15}"""), 5))
        assertEquals(PollStep.Denied, DeviceLinkPolicy.afterError(error(403, "access_denied"), 5))
        assertEquals(PollStep.Expired, DeviceLinkPolicy.afterError(error(400, "expired_token"), 5))
        assertEquals(PollStep.Wait(30), DeviceLinkPolicy.afterError(error(429, "rate_limited", retryAfter = "30"), 5))
        assertEquals(PollStep.Wait(5), DeviceLinkPolicy.afterError(error(500, "internal_error"), 5))
        assertEquals(PollStep.Failed("invalid_input"), DeviceLinkPolicy.afterError(error(400, "invalid_input"), 5))
    }
}
