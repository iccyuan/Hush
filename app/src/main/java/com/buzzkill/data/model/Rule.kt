package com.buzzkill.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A complete automation: which apps it watches, the triggers that select a
 * notification, the conditions that gate it, and the actions it performs.
 *
 * The component lists are persisted as JSON columns via [com.buzzkill.data.db.Converters].
 */
@Entity(tableName = "rules")
@Serializable
data class Rule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String = "Untitled rule",
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    /** Empty = applies to every app. */
    val appPackages: List<String> = emptyList(),
    val triggerLogic: LogicMode = LogicMode.ALL,
    val triggers: List<Trigger> = emptyList(),
    val conditions: List<Condition> = emptyList(),
    val actions: List<Action> = emptyList(),
    /** If true, no later rules are evaluated once this one fires. */
    val stopProcessing: Boolean = false,
    /**
     * If true, a matching notification is shown as a scrolling "danmaku" overlay bullet
     * instead of the native notification (requires draw-over-other-apps permission).
     */
    val showDanmaku: Boolean = false,
    /** Lifetime count of how many notifications this rule has acted on. */
    val fireCount: Long = 0,
    val notes: String = "",
) {
    /** A rule with no triggers matches every notification from its apps. */
    val matchesEverything: Boolean get() = triggers.isEmpty()
}
