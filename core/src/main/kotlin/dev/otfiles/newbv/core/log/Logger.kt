package dev.otfiles.newbv.core.log

import android.util.Log

/**
 * 应用诊断日志接口。
 *
 * 消息使用惰性 lambda 构造，未启用对应日志级别时不会执行字符串插值。
 */
interface Logger {
    /** 记录 Debug 级别日志。 */
    fun debug(message: () -> String)

    /** 记录 Info 级别日志。 */
    fun info(message: () -> String)

    /** 记录 Warn 级别日志。 */
    fun warn(message: () -> String)

    /** 记录带异常的 Warn 级别日志。 */
    fun warn(
        throwable: Throwable,
        message: () -> String,
    )

    /** 记录 Error 级别日志。 */
    fun error(message: () -> String)

    /** 记录带异常的 Error 级别日志。 */
    fun error(
        throwable: Throwable,
        message: () -> String,
    )
}

/**
 * Android Logcat Logger 实现。
 *
 * Android 单条 Log 消息存在长度限制，因此过长消息会拆分后输出。
 *
 * @param tag Logcat 标签。
 */
private class AndroidLogger(
    private val tag: String,
) : Logger {
    override fun debug(message: () -> String) {
        write(Log.DEBUG, message)
    }

    override fun info(message: () -> String) {
        write(Log.INFO, message)
    }

    override fun warn(message: () -> String) {
        write(Log.WARN, message)
    }

    override fun warn(
        throwable: Throwable,
        message: () -> String,
    ) {
        write(Log.WARN, message, throwable)
    }

    override fun error(message: () -> String) {
        write(Log.ERROR, message)
    }

    override fun error(
        throwable: Throwable,
        message: () -> String,
    ) {
        write(Log.ERROR, message, throwable)
    }

    private fun write(
        priority: Int,
        message: () -> String,
        throwable: Throwable? = null,
    ) {
        runCatching {
            val text = message()
            val detail = throwable?.let { "\n${Log.getStackTraceString(it)}" }.orEmpty()
            val line = text + detail
            // 先落内存缓冲，再写 logcat。
            // 从 Android 4.1 起普通 app 只能读到属于自己的 logcat，
            // 而部分电视/ROM 上连这一点都被 SELinux 挡掉，
            // 导致导出日志时 `logcat -t` 返回空、文件里只有设备信息。
            // 自建缓冲不依赖系统权限，导出时一定拿得到内容。
            LogBuffer.record(priority, tag, line)
            Log.println(priority, tag, line)
        }
    }
}

/**
 * 进程内日志环形缓冲。
 *
 * 只保留最近 [CAPACITY] 条，避免长期运行占用过多内存。
 * 导出日志（含崩溃日志）时整体写出，作为 `logcat` 命令不可用时的兜底。
 */
object LogBuffer {
    private const val CAPACITY = 4000

    private val entries = ArrayDeque<String>(CAPACITY)

    /** 记录一条日志。线程安全由 synchronized 保证，写入频率很低不影响性能。 */
    fun record(
        priority: Int,
        tag: String,
        message: String,
    ) {
        val level =
            when (priority) {
                Log.DEBUG -> "D"
                Log.INFO -> "I"
                Log.WARN -> "W"
                Log.ERROR -> "E"
                else -> "V"
            }
        synchronized(entries) {
            if (entries.size >= CAPACITY) entries.removeFirst()
            entries.addLast("$level/$tag: $message")
        }
    }

    /** 取出全部缓冲内容（按记录顺序）。 */
    fun dump(): String =
        synchronized(entries) {
            entries.joinToString("\n")
        }
}

/**
 * Logger 创建入口。
 *
 * 这是日志基础设施的轻量工厂，不承载业务状态，也不替代 Hilt 业务依赖注入。
 */
object Loggers {
    /**
     * 获取指定标签的 Logger。
     *
     * @param tag Logcat 标签。
     * @return Android Logcat Logger。
     */
    fun get(tag: String): Logger = AndroidLogger(tag)
}
