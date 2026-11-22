package dev.otfiles.newbv.app.ui.component.comment

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [resolveSubmitText] 的单元测试。
 *
 * 覆盖「点发送按钮导致输入法回退文本」这一具体场景：
 * 用户输入完整内容 → 焦点移到按钮 → 输入法把内容截短 → 提交时应恢复完整内容。
 */
class ResolveSubmitTextTest {
    @Test
    fun `truncated by unfocus restores frozen text`() {
        // 用户输入「一二三四五六」，失焦后被回退成「一二三」
        val result = resolveSubmitText(current = "一二三", frozen = "一二三四五六")
        assertThat(result).isEqualTo("一二三四五六")
    }

    @Test
    fun `truncated to empty restores frozen text`() {
        // 极端情况：组合文本全部被丢弃，输入框变空
        val result = resolveSubmitText(current = "", frozen = "一二三四五六")
        assertThat(result).isEqualTo("一二三四五六")
    }

    @Test
    fun `user deleting text keeps current`() {
        // 用户主动删字：current 仍是 frozen 的前缀且更短，
        // 但此时 frozen 已被同步成删除后的内容，两者应当相等
        val result = resolveSubmitText(current = "一二三", frozen = "一二三")
        assertThat(result).isEqualTo("一二三")
    }

    @Test
    fun `edited content keeps current`() {
        // 用户把内容改成了不同文字，不是前缀关系
        val result = resolveSubmitText(current = "四五六", frozen = "一二三四五六")
        assertThat(result).isEqualTo("四五六")
    }

    @Test
    fun `longer current wins`() {
        // current 比 frozen 长：正常继续输入，用 current
        val result = resolveSubmitText(current = "一二三四五六七", frozen = "一二三四五六")
        assertThat(result).isEqualTo("一二三四五六七")
    }

    @Test
    fun `both empty yields empty`() {
        val result = resolveSubmitText(current = "", frozen = "")
        assertThat(result).isEmpty()
    }
}
