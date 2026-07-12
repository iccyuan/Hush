/**
 * 构建期守卫：禁止绕过项目的 Logger 直接调用 `android.util.Log`。
 *
 * 统一日志出口的意义在于 TAG 唯一、可过滤、能统一加开关（如正式版不输出热路径日志）——
 * 只要有一处绕过去，这些就都不成立了。而这种事光靠约定是拦不住的：项目里已经出现过一次
 * 直接 `android.util.Log.i(...)` 的代码。所以把它变成构建失败，而不是一条口头规矩。
 *
 * 日志实现自身（Logger.kt）自然要用它，故排除。
 */
val checkNoRawLog = tasks.register("checkNoRawLog") {
    group = "verification"
    description = "禁止直接使用 android.util.Log，必须走统一的 Logger"

    val sources = fileTree("src/main/java") {
        include("**/*.kt")
        exclude("**/Logger.kt")
    }
    // 声明为输入，内容没变时这个任务可以跳过，不拖慢增量构建。
    inputs.files(sources).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.upToDateWhen { true }

    doLast {
        val offenders = sources.files.filter { file ->
            file.readText().contains("android.util.Log")
        }
        if (offenders.isNotEmpty()) {
            val list = offenders.joinToString("\n") { "  - ${it.relativeTo(projectDir)}" }
            throw GradleException(
                "以下文件直接使用了 android.util.Log：\n$list\n" +
                    "请改用统一的 Logger（Logger.i/d/w/e，或 Logger.scoped(\"子系统\")）。"
            )
        }
    }
}

tasks.named("preBuild") { dependsOn(checkNoRawLog) }
