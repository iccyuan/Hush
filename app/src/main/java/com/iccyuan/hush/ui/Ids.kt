package com.iccyuan.hush.ui

import java.util.UUID

/** 为编辑器中创建的规则组件生成简短而稳定的 id。 */
object Ids {
    fun next(): String = UUID.randomUUID().toString().take(8)
}
