package com.hstc.quicklogin.data

import org.json.JSONObject

object JsonpParser {
    fun extractObject(raw: String): JSONObject = JSONObject(extractPayload(raw))

    fun extractPayload(raw: String): String {
        val trimmed = raw.trim()
        val firstParen = trimmed.indexOf('(')
        val lastParen = trimmed.lastIndexOf(')')
        return if (firstParen >= 0 && lastParen > firstParen) {
            trimmed.substring(firstParen + 1, lastParen)
        } else {
            trimmed
        }
    }
}
