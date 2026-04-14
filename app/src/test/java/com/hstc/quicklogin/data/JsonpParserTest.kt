package com.hstc.quicklogin.data

import org.junit.Assert.assertEquals
import org.junit.Test

class JsonpParserTest {
    @Test
    fun `extract payload from jsonp`() {
        val payload = JsonpParser.extractPayload("""androidCb({"result":1,"msg":"ok"})""")
        assertEquals("""{"result":1,"msg":"ok"}""", payload)
    }

    @Test
    fun `sanitize mac removes separators`() {
        assertEquals("a83b7628c8e1", sanitizeMac("a8-3b-76-28-c8-e1"))
    }

    @Test
    fun `redact sensitive fields`() {
        val redacted = redactSensitive("DDDDD=user&upass=secret&password=raw")
        assertEquals("DDDDD=***&upass=***&password=***", redacted)
    }
}
