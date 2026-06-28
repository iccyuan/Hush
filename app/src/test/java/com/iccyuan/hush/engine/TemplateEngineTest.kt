package com.iccyuan.hush.engine

import com.iccyuan.hush.data.model.DayType
import com.iccyuan.hush.data.model.NotificationField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TemplateEngineTest {

    private fun ctx(
        pkg: String = "com.example",
        app: String = "Example",
        fields: Map<NotificationField, String> = emptyMap(),
        nowMillis: Long = 0L,
    ): MatchContext {
        val device = DeviceContext(
            charging = false,
            screenOn = true,
            batteryPercent = 50,
            minuteOfDay = 0,
            isoDayOfWeek = 1,
            dayType = DayType.WORKDAY,
            nowMillis = nowMillis,
        )
        return MatchContext(pkg, app, fields.toMutableMap(), false, false, device)
    }

    @Before fun reset() = VariableStore.clear()

    @Test fun resolvesBuiltinPlaceholders() {
        val c = ctx(
            fields = mapOf(
                NotificationField.TITLE to "Hi",
                NotificationField.TEXT to "There",
            ),
        )
        assertEquals("Hi/There @ Example (com.example)",
            TemplateEngine.render("{title}/{text} @ {app} ({package})", c))
    }

    @Test fun resolvesRegexCaptures() {
        val c = ctx()
        c.captures["1"] = "42"
        assertEquals("got 42", TemplateEngine.render("got {1}", c))
    }

    @Test fun resolvesUserVariable() {
        VariableStore.setVariable("count", "7")
        assertEquals("n=7", TemplateEngine.render("n={var:count}", ctx()))
    }

    @Test fun unknownPlaceholderIsPreserved() {
        assertEquals("{nope} kept", TemplateEngine.render("{nope} kept", ctx()))
    }

    @Test fun timeAndDateArePopulated_notLeftLiteral() {
        // nowMillis = 0 触发回退到当前挂钟时间；无论如何都不应保留字面占位符。
        val rendered = TemplateEngine.render("{date} {time}", ctx(nowMillis = 0L))
        assertTrue("date 应被填充: $rendered", Regex("""\d{4}-\d{2}-\d{2}""").containsMatchIn(rendered))
        assertTrue("time 应被填充: $rendered", Regex("""\d{2}:\d{2}""").containsMatchIn(rendered))
    }
}
