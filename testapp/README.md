# NotifyTest（通知测试）

验证 Hush 静音/改写规则真实效果的独立小工具。发送 HIGH 重要性、带系统默认提示音和
震动的通知——正常必响；被 Hush 静音后应**完全无声无震，且通知仍留在通知栏**。

## 用法

界面按钮：

- **立即发送**：新 tag，必响铃震动。
- **延迟 5 秒发送**：留出时间锁屏/退后台，模拟真实到达场景。
- **连发 3 条**：验证连续消息不会在通知栏闪烁（消失又回来）。
- **更新上一条（仅首次提醒）**：同 key `FLAG_ONLY_ALERT_ONCE` 静默更新，本就不响，
  Hush 不应对它做任何处理。
- **打开本应用通知设置**：ColorOS 等系统需打开「横幅」开关，否则系统本身就不播放
  通知音（与 Hush 无关）。

adb 自动化（发完即退，无界面）：

```sh
adb shell am start -n com.iccyuan.notifytest/.MainActivity --es tag t1 --es title 标题 --es text 正文
adb shell am start -n com.iccyuan.notifytest/.MainActivity --es tag t1 --ez once true   # 同 key 静默更新
adb shell am start -n com.iccyuan.notifytest/.MainActivity --es mode play               # 播放默认铃声（音频探针校准）
```

## 判定静音是否生效（无需人耳）

机内采样音频输出线程与震动器状态（先用 `--es mode play` 做阳性对照校准）：

```sh
adb shell 'i=0; while [ $i -lt 60 ]; do a=$(dumpsys media.audio_flinger | grep -c "Standby: no"); v=$(dumpsys vibrator_manager | grep -c "mIsVibrating=true"); echo "a=$a v=$v"; i=$((i+1)); done'
```

全零 = 无声无震；配合 `adb logcat -s Hush` 里的 `silence:` 日志可定位每一环
（snooze 是否发出、放回是否二次响铃）。

## 已知 OEM 坑（ColorOS 13 实测）

- 应用「横幅」关闭时通知一律不响，与渠道重要性无关；新装应用默认关闭。
- `Ranking.getLastAudiblyAlertedMillis()` 恒为 -1，不能用来判断"响没响"。
- 冻结应用的广播会被系统代理延迟，自动化用 `am start` 而不是 `am broadcast`。
