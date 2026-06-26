package com.buzzkill.data

import android.content.Context
import com.buzzkill.data.db.BuzzJson
import com.buzzkill.data.model.DayType
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

/**
 * Classifies a calendar date as a Chinese statutory holiday, make-up workday,
 * weekend, or ordinary workday.
 *
 * Data sources, in priority order:
 *  1. A locally cached copy fetched from the public holiday API (authoritative for the
 *     years it covers — these are the official State-Council dates).
 *  2. The bundled `assets/holidays.json` fallback for years not yet fetched.
 *  3. Plain weekday/weekend logic for any other year.
 */
object HolidayProvider {

    // --- Bundled asset model ---
    @Serializable private data class Calendar0(val years: Map<String, YearData> = emptyMap())
    @Serializable private data class YearData(
        val holidays: List<String> = emptyList(),
        val workdays: List<String> = emptyList(),
    )

    // --- Persisted cache model (fetched data) ---
    @Serializable private data class Cache(
        val years: List<String> = emptyList(),
        val holidays: List<String> = emptyList(),
        val workdays: List<String> = emptyList(),
    )

    // --- timor.tech API model ---
    @Serializable private data class TimorResp(
        val code: Int = -1,
        val holiday: Map<String, TimorDay> = emptyMap(),
    )
    @Serializable private data class TimorDay(val holiday: Boolean = false, val date: String = "")

    @Volatile private var holidaySet: Set<String> = emptySet()
    @Volatile private var workdaySet: Set<String> = emptySet()
    @Volatile private var loaded = false

    // Manual "today is rest / today is work" override (tied to a specific date so it
    // auto-expires the next day).
    @Volatile private var overrideDate: String = ""
    @Volatile private var overrideType: String = ""

    const val OVERRIDE_REST = "rest"
    const val OVERRIDE_WORK = "work"

    private const val PREFS = "buzzkill_holiday"
    private const val KEY_UPDATED = "last_update"
    private const val KEY_OVERRIDE_DATE = "override_date"
    private const val KEY_OVERRIDE_TYPE = "override_type"

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            rebuild(context)
            loaded = true
        }
    }

    /** Reloads in-memory sets from asset + cache (cache overrides covered years). */
    private fun rebuild(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        overrideDate = prefs.getString(KEY_OVERRIDE_DATE, "").orEmpty()
        overrideType = prefs.getString(KEY_OVERRIDE_TYPE, "").orEmpty()
        val (aH, aW) = readAsset(app)
        val cache = readCache(app)
        if (cache == null) {
            holidaySet = aH
            workdaySet = aW
        } else {
            val covered = cache.years.toSet()
            fun yr(d: String) = d.take(4)
            holidaySet = aH.filterNot { yr(it) in covered }.toSet() + cache.holidays
            workdaySet = aW.filterNot { yr(it) in covered }.toSet() + cache.workdays
        }
    }

    private fun readAsset(context: Context): Pair<Set<String>, Set<String>> = runCatching {
        val json = context.assets.open("holidays.json").bufferedReader().use { it.readText() }
        val cal = BuzzJson.decodeFromString(Calendar0.serializer(), json)
        cal.years.values.flatMap { it.holidays }.toSet() to
            cal.years.values.flatMap { it.workdays }.toSet()
    }.getOrDefault(emptySet<String>() to emptySet())

    private fun cacheFile(context: Context) = File(context.filesDir, "holidays_cache.json")

    private fun readCache(context: Context): Cache? = runCatching {
        val f = cacheFile(context)
        if (!f.exists()) return null
        BuzzJson.decodeFromString(Cache.serializer(), f.readText())
    }.getOrNull()

    fun lastUpdated(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_UPDATED, 0L)

    /** Result of a [refresh]: how many years were successfully fetched. */
    data class RefreshResult(val ok: Boolean, val years: Int)

    /**
     * Fetches the current year and its neighbours from the public holiday API, caches
     * the result locally, and reloads. Network/parse failures leave the existing data
     * untouched. Call from a background thread.
     */
    fun refresh(context: Context): RefreshResult {
        val app = context.applicationContext
        val now = Calendar.getInstance().get(Calendar.YEAR)
        val years = listOf(now - 1, now, now + 1)
        val holidays = HashSet<String>()
        val workdays = HashSet<String>()
        val ok = ArrayList<String>()
        for (y in years) {
            val resp = fetchYear(y) ?: continue
            if (resp.code != 0) continue
            for (day in resp.holiday.values) {
                val d = day.date
                if (d.isBlank()) continue
                if (day.holiday) holidays.add(d) else workdays.add(d)
            }
            ok.add(y.toString())
        }
        if (ok.isEmpty()) return RefreshResult(false, 0)

        val cache = Cache(years = ok, holidays = holidays.sorted(), workdays = workdays.sorted())
        runCatching {
            cacheFile(app).writeText(BuzzJson.encodeToString(Cache.serializer(), cache))
        }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_UPDATED, System.currentTimeMillis()).apply()
        rebuild(app)
        loaded = true
        return RefreshResult(true, ok.size)
    }

    private fun fetchYear(year: Int): TimorResp? = runCatching {
        val conn = (URL("https://timor.tech/api/holiday/year/$year").openConnection() as HttpURLConnection)
        conn.apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "BuzzKill/1.0")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        BuzzJson.decodeFromString(TimorResp.serializer(), body)
    }.getOrNull()

    /**
     * @param month 1-based month, @param isoDayOfWeek 1 = Monday … 7 = Sunday.
     */
    fun dayType(year: Int, month: Int, day: Int, isoDayOfWeek: Int): DayType {
        val key = "%04d-%02d-%02d".format(year, month, day)
        // Manual override for a specific date wins over the calendar.
        if (key == overrideDate && overrideType.isNotEmpty()) {
            return if (overrideType == OVERRIDE_REST) DayType.LEGAL_HOLIDAY else DayType.WORKDAY
        }
        return when {
            holidaySet.contains(key) -> DayType.LEGAL_HOLIDAY
            workdaySet.contains(key) -> DayType.MAKEUP_WORKDAY
            isoDayOfWeek == 6 || isoDayOfWeek == 7 -> DayType.WEEKEND
            else -> DayType.WORKDAY
        }
    }

    private fun todayKey(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
    }

    /** Sets (or clears, when [type] is null) the manual day-type override for today. */
    fun setTodayOverride(context: Context, type: String?) {
        ensureLoaded(context)
        if (type == null) {
            overrideDate = ""; overrideType = ""
        } else {
            overrideDate = todayKey(); overrideType = type
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_OVERRIDE_DATE, overrideDate)
            .putString(KEY_OVERRIDE_TYPE, overrideType)
            .apply()
    }

    /** The override active for today ([OVERRIDE_REST]/[OVERRIDE_WORK]), or null. */
    fun todayOverride(context: Context): String? {
        ensureLoaded(context)
        return if (overrideDate == todayKey() && overrideType.isNotEmpty()) overrideType else null
    }
}
