package com.iccyuan.hush.engine

import com.iccyuan.hush.data.model.MatchMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMatcherTest {

    private fun matched(mode: MatchMode, query: String, value: String, cs: Boolean = false) =
        TextMatcher.evaluate(mode, query, value, cs).matched

    @Test fun contains_isCaseInsensitiveByDefault() {
        assertTrue(matched(MatchMode.CONTAINS, "WORLD", "hello world"))
        assertFalse(matched(MatchMode.CONTAINS, "WORLD", "hello world", cs = true))
    }

    @Test fun contains_emptyQueryNeverMatches() {
        assertFalse(matched(MatchMode.CONTAINS, "", "anything"))
    }

    @Test fun equals_startsWith_endsWith() {
        assertTrue(matched(MatchMode.EQUALS, "abc", "abc"))
        assertFalse(matched(MatchMode.EQUALS, "abc", "abcd"))
        assertTrue(matched(MatchMode.STARTS_WITH, "ab", "abcd"))
        assertTrue(matched(MatchMode.ENDS_WITH, "cd", "abcd"))
    }

    @Test fun regex_capturesGroups() {
        val res = TextMatcher.evaluate(MatchMode.REGEX, "code (\\d+)", "your code 1234 now", false)
        assertTrue(res.matched)
        assertEquals("1234", res.groups["1"])
    }

    @Test fun wildcard_matchesWithAnchors() {
        assertTrue(matched(MatchMode.WILDCARD, "hel*o", "hello"))
        assertFalse(matched(MatchMode.WILDCARD, "hel*o", "xhello"))
        assertTrue(matched(MatchMode.WILDCARD, "h?llo", "hallo"))
    }

    @Test fun invalidRegex_neverMatches_butDoesNotThrow() {
        assertFalse(matched(MatchMode.REGEX, "(unclosed", "anything"))
    }

    @Test fun regexError_reportsInvalidAndAcceptsValid() {
        assertNull(TextMatcher.regexError("a(b)c"))
        assertNotNull(TextMatcher.regexError("(unclosed"))
    }
}
