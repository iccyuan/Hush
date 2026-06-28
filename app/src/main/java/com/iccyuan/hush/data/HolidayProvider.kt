package com.iccyuan.hush.data

import android.content.Context
import com.iccyuan.hush.data.db.BuzzJson
import com.iccyuan.hush.data.model.DayType
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

/**
 * 将日历日期归类为中国法定节假日、调休补班工作日、周末或普通工作日。
 *
 * 数据来源（按优先级排列）：
 *  1. 从公共节假日 API 获取并本地缓存的副本（对其覆盖的年份具有权威性——这些是国务院官方公布的日期）。
 *  2. 内置的 `assets/holidays.json`，作为尚未获取年份的回退数据。
 *  3. 对于其他任何年份，采用普通的工作日/周末判断逻辑。
 */
object HolidayProvider {

    // --- 内置资源数据模型 ---
    @Serializable private data class Calendar0(val years: Map<String, YearData> = emptyMap())
    @Serializable private data class YearData(
        val holidays: List<String> = emptyList(),
        val workdays: List<String> = emptyList(),
    )

    // --- 持久化缓存数据模型（已获取的数据） ---
    @Serializable private data class Cache(
        val years: List<String> = emptyList(),
        val holidays: List<String> = emptyList(),
        val workdays: List<String> = emptyList(),
    )

    // --- timor.tech API 数据模型 ---
    @Serializable private data class TimorResp(
        val code: Int = -1,
        val holiday: Map<String, TimorDay> = emptyMap(),
    )
    @Serializable private data class TimorDay(val holiday: Boolean = false, val date: String = "")

    @Volatile private var holidaySet: Set<String> = emptySet()
    @Volatile private var workdaySet: Set<String> = emptySet()
    @Volatile private var loaded = false

    // 手动的“今天休息 / 今天上班”覆盖设置（与具体日期绑定，因此第二天会自动失效）。
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

    /** 从内置资源和缓存重新加载内存中的集合（缓存会覆盖其所涵盖的年份）。 */
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

    /** 当前公历年份。 */
    fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

    /**
     * 当前年份的节假日数据是否来自联网获取（即官方权威数据），而非内置的暂定数据。
     * 只有「当年」的数据对判断今天是否放假/调休才有意义。
     */
    fun isCurrentYearVerified(context: Context): Boolean {
        val cache = readCache(context.applicationContext) ?: return false
        return cache.years.contains(currentYear().toString())
    }

    /** [refresh] 的结果：成功获取了多少个年份。 */
    data class RefreshResult(val ok: Boolean, val years: Int)

    /**
     * 从公共节假日 API 获取当前年份及其相邻年份的数据，将结果缓存到本地并重新加载。
     * 网络或解析失败时会保持现有数据不变。请在后台线程中调用。
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
     * @param month 从 1 开始计的月份，@param isoDayOfWeek 1 = 周一 … 7 = 周日。
     */
    fun dayType(year: Int, month: Int, day: Int, isoDayOfWeek: Int): DayType {
        val key = "%04d-%02d-%02d".format(year, month, day)
        // 针对特定日期的手动覆盖设置优先于日历判断。
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

    /** 设置（当 [type] 为 null 时则清除）今天的手动日期类型覆盖设置。 */
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

    /** 今天生效的覆盖设置（[OVERRIDE_REST]/[OVERRIDE_WORK]），若无则为 null。 */
    fun todayOverride(context: Context): String? {
        ensureLoaded(context)
        return if (overrideDate == todayKey() && overrideType.isNotEmpty()) overrideType else null
    }
}
