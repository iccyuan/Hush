package com.buzzkill.data.db

import androidx.room.TypeConverter
import com.buzzkill.data.model.Action
import com.buzzkill.data.model.Condition
import com.buzzkill.data.model.LogicMode
import com.buzzkill.data.model.Trigger
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
}
