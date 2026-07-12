package com.iccyuan.hush.engine

import com.iccyuan.hush.data.model.Action
import com.iccyuan.hush.data.model.Condition
import com.iccyuan.hush.data.model.Trigger
import kotlinx.serialization.Serializable

/**
 * 「这条通知**为什么**被这样处理」的取证记录：命中了哪条规则、是其中哪个触发器和哪些条件
 * 促成的、最终执行了什么动作。随通知历史一并持久化。
 *
 * 只记结论所依赖的那几项，不是把规则整个抄一遍：
 *  · [triggers] 只含**实际命中**的触发器——ANY 逻辑下规则可能挂了五个触发器而只有一个说了算，
 *    把五个都列出来就等于没说。
 *  · [conditions] 是当时成立的条件，用来解释「为什么是此刻生效」（时段、充电、Wi-Fi 等）。
 *
 * 存的是结构化的规则对象而非渲染好的文案：引擎不该知道 UI 的语言，文案由展示层
 * （[com.iccyuan.hush.ui.Localize]）按当前语言渲染。规则名是用户自己起的，原样保留。
 */
@Serializable
data class MatchTrace(
    val ruleId: Long,
    val ruleName: String,
    /** 命中的触发器；为空表示该规则无触发器，即「匹配这个应用的所有通知」。 */
    val triggers: List<Trigger> = emptyList(),
    /** 当时成立的条件；为空表示该规则不设条件，任何时候都生效。 */
    val conditions: List<Condition> = emptyList(),
    /** 该规则执行的动作。 */
    val actions: List<Action> = emptyList(),
)

/**
 * 「差一点就命中」的规则：应用对上了，却卡在了后面某一关。
 *
 * 通知**没被处理**时，用户最想知道的恰恰是为什么——而这个问题此前只能靠翻 logcat 回答。
 * 记下卡在哪一关、已经过了哪些关，历史里就能直接给出答案：
 *
 * ```
 * 最接近的规则  拦截促销
 *   ✓ 触发器   标题 包含「促销」
 *   ✗ 条件     屏幕点亮时          ← 差在这里
 * ```
 *
 * 只记「应用匹配上」的规则：连应用都不沾边的规则与这条通知毫无关系，列出来只是噪音。
 */
@Serializable
data class NearMiss(
    val ruleId: Long,
    val ruleName: String,
    /** 卡在了哪一关。 */
    val blockedAt: Stage,
    /**
     * 已经命中的触发器。卡在触发器关时它可能非空——「全部满足」的规则挂了三个触发器、
     * 命中两个，也照样不算数；把命中的那两个列出来，用户才知道差的是哪一个。
     */
    val passedTriggers: List<Trigger> = emptyList(),
    /** 当时**不**成立的条件（卡在条件关时才有值）。 */
    val failedConditions: List<Condition> = emptyList(),
) {
    /** 卡在哪一关。越靠后说明这条规则差得越少——历史里优先展示走得最远的那条。 */
    @Serializable
    enum class Stage { TRIGGER, CONDITION }
}
