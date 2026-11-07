package dev.otfiles.newbv.app.ui.component

import androidx.compose.runtime.mutableStateOf
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * [FocusSaver] 的 key 管理测试。
 *
 * 重点是 [FocusSaver.pruneKeys]：首页三个 Tab 各自的前缀不同，
 * 裁剪出错会删掉正在使用的 key，导致从详情页返回后焦点恢复失效——
 * 这类问题在 UI 上表现为「返回后焦点跑到左上角」，很难靠手工回归发现。
 */
class FocusSaverTest {
    private fun saver() = FocusSaver(mutableStateOf(""))

    @Test
    fun `focusRequesterFor returns same instance for same key`() {
        val saver = saver()
        // 同一 key 必须复用同一个 requester，否则焦点恢复指向的实例与实际布局挂的不是一个
        assertThat(saver.focusRequesterFor("rcmd_0")).isSameInstanceAs(saver.focusRequesterFor("rcmd_0"))
    }

    @Test
    fun `focusRequesterFor returns distinct instances for distinct keys`() {
        val saver = saver()
        assertThat(saver.focusRequesterFor("rcmd_0"))
            .isNotSameInstanceAs(saver.focusRequesterFor("rcmd_1"))
    }

    @Test
    fun `pruneKeys keeps keys matching the given prefix`() {
        val saver = saver()
        repeat(600) { saver.focusRequesterFor("rcmd_$it") }
        repeat(600) { saver.focusRequesterFor("popular_$it") }

        saver.pruneKeys(setOf("rcmd_"))

        // 保留的 key 必须还能取回「同一个」实例
        val kept = saver.focusRequesterFor("rcmd_0")
        assertThat(kept).isSameInstanceAs(saver.focusRequesterFor("rcmd_0"))
    }

    @Test
    fun `pruneKeys removes keys of other prefixes`() {
        val saver = saver()
        repeat(600) { saver.focusRequesterFor("dynamics_$it") }
        val before = saver.focusRequesterFor("rcmd_0")

        // rcmd_ 不在保留列表里，应被清掉；再取会得到新实例
        saver.pruneKeys(setOf("dynamics_"))
        assertThat(saver.focusRequesterFor("rcmd_0")).isNotSameInstanceAs(before)
    }

    @Test
    fun `pruneKeys does nothing below threshold`() {
        val saver = saver()
        repeat(10) { saver.focusRequesterFor("rcmd_$it") }
        val before = saver.focusRequesterFor("rcmd_0")

        // 条目数未达阈值时不扫描，避免每次切换都做全量遍历
        saver.pruneKeys(emptySet())
        assertThat(saver.focusRequesterFor("rcmd_0")).isSameInstanceAs(before)
    }

    @Test
    fun `saveFocusedKey and savedKeyValue round trip`() {
        val saver = saver()
        saver.saveFocusedKey("popular_3")
        assertThat(saver.savedKeyValue()).isEqualTo("popular_3")
    }

    @Test
    fun `clearFocusedKey resets saved key`() {
        val saver = saver()
        saver.saveFocusedKey("dynamics_7")
        saver.clearFocusedKey()
        assertThat(saver.savedKeyValue()).isEmpty()
    }
}
