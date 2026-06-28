package com.iccyuan.hush.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.iccyuan.hush.data.model.NotificationLog
import com.iccyuan.hush.data.model.Rule

@Database(entities = [Rule::class, NotificationLog::class], version = 4, exportSchema = true)
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
        private val MIGRATIONS: Array<Migration> = emptyArray()

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "buzzkill.db"
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
