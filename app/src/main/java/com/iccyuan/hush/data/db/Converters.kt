package com.iccyuan.hush.data.db

import androidx.room.TypeConverter
import com.iccyuan.hush.data.model.Action
import com.iccyuan.hush.data.model.Condition
import com.iccyuan.hush.data.model.ConditionLogic
import com.iccyuan.hush.data.model.LogicMode
import com.iccyuan.hush.data.model.Trigger
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** 用于持久化与导入/导出的共享宽松 JSON 实例。 */
val BuzzJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
    prettyPrint = false
}

/** 将规则组件列表以 JSON 文本列形式存储的 Room 转换器。 */
class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String =
        BuzzJson.encodeToString(ListSerializer(String.serializer()), value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        BuzzJson.decodeFromString(ListSerializer(String.serializer()), value)

    @TypeConverter
    fun fromTriggers(value: List<Trigger>): String =
        BuzzJson.encodeToString(ListSerializer(Trigger.serializer()), value)

    @TypeConverter
    fun toTriggers(value: String): List<Trigger> =
        BuzzJson.decodeFromString(ListSerializer(Trigger.serializer()), value)

    @TypeConverter
    fun fromConditions(value: List<Condition>): String =
        BuzzJson.encodeToString(ListSerializer(Condition.serializer()), value)

    @TypeConverter
    fun toConditions(value: String): List<Condition> =
        BuzzJson.decodeFromString(ListSerializer(Condition.serializer()), value)

    @TypeConverter
    fun fromActions(value: List<Action>): String =
        BuzzJson.encodeToString(ListSerializer(Action.serializer()), value)

    @TypeConverter
    fun toActions(value: String): List<Action> =
        BuzzJson.decodeFromString(ListSerializer(Action.serializer()), value)

    @TypeConverter
    fun fromLogicMode(value: LogicMode): String = value.name

    @TypeConverter
    fun toLogicMode(value: String): LogicMode = LogicMode.valueOf(value)

    @TypeConverter
    fun fromConditionLogic(value: ConditionLogic): String = value.name

    // 兼容旧值：早期该列存的是 LogicMode（ALL/ANY），但当时引擎并未真正使用它，
    // 一律按智能分组处理；因此把任何非 ConditionLogic 的旧值都归一为 SMART。
    @TypeConverter
    fun toConditionLogic(value: String): ConditionLogic =
        runCatching { ConditionLogic.valueOf(value) }.getOrDefault(ConditionLogic.SMART)
}
