package com.buzzkill.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** 遍历 context-wrapper 链以查找宿主 Activity（用于 recreate()）。 */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
