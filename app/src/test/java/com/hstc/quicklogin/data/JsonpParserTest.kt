package com.hstc.quicklogin.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URLEncoder
import java.util.Base64

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

    @Test
    fun `parse portal url with alternate parameter names`() {
        val context = parsePortalRedirectUrl(
            "https://rz.hstc.edu.cn/eportal/index.jsp?wlan_user_ip=10.1.2.3&wlan_user_mac=a8-3b-76-28-c8-e1&wlan_ac_ip=10.9.8.7&wlan_ac_name=AC-S3"
        )

        assertEquals("10.1.2.3", context?.ip)
        assertEquals("a83b7628c8e1", context?.mac)
        assertEquals("10.9.8.7", context?.wlanAcIp)
        assertEquals("AC-S3", context?.wlanAcName)
    }

    @Test
    fun `parse cas service state with url safe base64`() {
        val state = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("x|y|10.1.2.3||a8-3b-76-28-c8-e1|0|10.9.8.7|AC-S3".toByteArray())
        val service = URLEncoder.encode("https://rz.hstc.edu.cn:802/eportal/portal/cas?state=$state", "UTF-8")
        val context = parsePortalRedirectUrl("https://hscas.hstc.edu.cn/cas/login?service=$service")

        assertEquals("10.1.2.3", context?.ip)
        assertEquals("a83b7628c8e1", context?.mac)
        assertEquals("10.9.8.7", context?.wlanAcIp)
        assertEquals("AC-S3", context?.wlanAcName)
    }

    @Test
    fun `parse ip hosted portal parameter url`() {
        val context = parsePortalRedirectUrl(
            "http://192.168.2.34/a79.htm?wlanuserip=192.168.115.140&usermac=8e-11-b5-dd-e3-6b&uservid=3302&wlanacip=192.168.2.33&wlanacname=CORE-RG-N18012&nasport=153"
        )

        assertEquals("192.168.115.140", context?.ip)
        assertEquals("8e11b5dde36b", context?.mac)
        assertEquals("3302", context?.vlan)
        assertEquals("192.168.2.33", context?.wlanAcIp)
        assertEquals("CORE-RG-N18012", context?.wlanAcName)
    }

    @Test
    fun `parse direct cas callback state`() {
        val state = Base64.getEncoder()
            .encodeToString("1|1|192.168.115.140|fe80::8c11:b5ff:fedd:e36b|8e11b5dde36b||192.168.2.33|CORE-RG-N18012|".toByteArray())
        val context = parsePortalRedirectUrl(
            "http://192.168.2.34:801/eportal/portal/cas/login?state=${URLEncoder.encode(state, "UTF-8")}&ticket=ST-1"
        )

        assertEquals("192.168.115.140", context?.ip)
        assertEquals("8e11b5dde36b", context?.mac)
        assertEquals("192.168.2.33", context?.wlanAcIp)
        assertEquals("CORE-RG-N18012", context?.wlanAcName)
    }

    @Test
    fun `extract portal context from html`() {
        val html = """
            <script>
              window.location.href='/eportal/index.jsp?wlanuserip=10.1.2.3&amp;usermac=a8:3b:76:28:c8:e1&amp;wlanacip=10.9.8.7';
            </script>
        """.trimIndent()

        val context = extractPortalContextFromText(html, "https://rz.hstc.edu.cn/")

        assertEquals("10.1.2.3", context?.ip)
        assertEquals("a83b7628c8e1", context?.mac)
        assertEquals("10.9.8.7", context?.wlanAcIp)
    }
}
