package io.github.itisnomatter.kojev.wire

import io.ktor.http.toHttpDate
import io.ktor.util.date.GMTDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ErrorParsingTest {
    @Test
    fun `message is taken from the shapes the official SDKs accept`() {
        assertEquals("plain text", ErrorBody.parse("plain text").message)
        assertEquals("quoted", ErrorBody.parse("\"quoted\"").message)
        assertEquals("m", ErrorBody.parse("""{"message":"m"}""").message)
        assertEquals("e", ErrorBody.parse("""{"error":"e"}""").message)
        assertEquals("em", ErrorBody.parse("""{"error":{"message":"em"}}""").message)
        assertEquals("d", ErrorBody.parse("""{"detail":"d"}""").message)
        assertEquals("dm", ErrorBody.parse("""{"detail":{"message":"dm"}}""").message)
        assertEquals("""{"unknown":1}""", ErrorBody.parse("""{"unknown":1}""").message)
        assertNull(ErrorBody.parse(null).message)
        assertNull(ErrorBody.parse("").message)
        assertNull(ErrorBody.parse("   ").message)
    }

    @Test
    fun `error takes precedence over message`() {
        assertEquals("e", ErrorBody.parse("""{"error":"e","message":"m"}""").message)
    }

    @Test
    fun `a 422 detail list becomes validation errors and a joined message`() {
        val parsed =
            ErrorBody.parse(
                """{"detail":[{"loc":["body","state"],"msg":"Field required","type":"missing"},""" +
                    """{"loc":["body","questions",0],"msg":"bad","type":"value_error"}]}""",
            )
        assertEquals(2, parsed.validationErrors.size)
        assertEquals(listOf("body", "questions", "0"), parsed.validationErrors[1].location)
        assertEquals("body.state: Field required (missing); body.questions.0: bad (value_error)", parsed.message)
    }

    @Test
    fun `retry-after-ms wins and is milliseconds`() {
        assertEquals(250.milliseconds, parseRetryAfter("250", "7"))
    }

    @Test
    fun `Retry-After as seconds`() {
        assertEquals(7.seconds, parseRetryAfter(null, "7"))
        assertEquals(1500.milliseconds, parseRetryAfter(null, "1.5"))
    }

    @Test
    fun `Retry-After as an HTTP date is relative to now and never negative`() {
        val now = GMTDate(1_700_000_000_000)
        val inTenSeconds = GMTDate(1_700_000_010_000)
        assertEquals(10.seconds, parseRetryAfter(null, inTenSeconds.toHttpDate()) { now })
        val inThePast = GMTDate(1_699_999_000_000)
        assertEquals(0.seconds, parseRetryAfter(null, inThePast.toHttpDate()) { now })
    }

    @Test
    fun `unparseable or negative values are ignored`() {
        assertNull(parseRetryAfter(null, null))
        assertNull(parseRetryAfter(null, "-1"))
        assertNull(parseRetryAfter(null, "soon"))
        assertNull(parseRetryAfter("-5", null))
        assertEquals(7.seconds, parseRetryAfter("garbage", "7"))
    }
}
