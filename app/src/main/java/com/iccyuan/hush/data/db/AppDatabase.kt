package com.iccyuan.hush.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.iccyuan.hush.data.model.NotificationLog
import com.iccyuan.hush.data.model.Rule

@Database(entities = [Rule::class, NotificationLog::class], version = 7, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun notificationLogDao(): NotificationLogDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * 版本之间的真实迁移。每次升高 @Database 的 version 时，都必须在此追加一条
         * [Migration]，以避免丢失用户的规则与历史。当前 schema 已是 version 4，
         * 自该版本起的所有变更都应以迁移而非破坏性回退来处理。
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 新增「条件组合方式」列（与/或），默认 ALL，与既有规则的旧行为一致。
                db.execSQL("ALTER TABLE rules ADD COLUMN conditionLogic TEXT NOT NULL DEFAULT 'ALL'")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // conditionLogic 改用 ConditionLogic（SMART/ALL/ANY）。早期该列存的 ALL/ANY 并未被引擎
                // 实际使用（一律按智能分组），故把所有旧值归一为 SMART，保持既有行为不变。
                db.execSQL("UPDATE rules SET conditionLogic = 'SMART'")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 改用「逐间隔连接符」表达条件逻辑（conditionJoins）。新增列，默认空列表；
                // 引擎对缺失的间隔回退为「且」，老规则保持「全部满足」的稳妥行为。
                db.execSQL("ALTER TABLE rules ADD COLUMN conditionJoins TEXT NOT NULL DEFAULT '[]'")
            }
        }

        private val MIGRATIONS: Array<Migration> =
            arrayOf(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hush.db"
                )
                    .addMigrations(*MIGRATIONS)
                    // 仅允许从发布前的历史版本（1–3，无 schema 存档、无法编写可靠迁移）做破坏性回退。
                    // 自 version 4 起若缺少对应迁移，Room 会抛错而不是悄无声息地清库——
                    // 这会强制后续每次 schema 变更都补上真正的迁移。
                    .fallbackToDestructiveMigrationFrom(1, 2, 3)
                    .build()
                    .also { instance = it }
            }
    }
}
