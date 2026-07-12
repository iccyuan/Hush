package com.iccyuan.notifytest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * adb 自动化入口（备用）：
 *   adb shell am broadcast -n com.iccyuan.notifytest/.Post --es tag t1 --es title Hi --es text Msg
 * 注意部分 OEM（如 ColorOS）会代理/延迟发往冻结应用的广播，自动化优先用
 * `am start -n com.iccyuan.notifytest/.MainActivity` 携带同名 extras。
 */
class Post : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Notifier.post(
            ctx,
            tag = intent.getStringExtra("tag") ?: "t1",
            title = intent.getStringExtra("title") ?: "NotifyTest",
            text = intent.getStringExtra("text") ?: "测试通知",
            alertOnce = intent.getBooleanExtra("once", false),
        )
    }
}
