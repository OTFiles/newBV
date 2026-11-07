package dev.otfiles.newbv.app.ui.component

import androidx.compose.animation.core.TweenSpec
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * 页面切换动画参数测试。
 *
 * 这些时长直接决定「按键到画面开始变化」的主观响应速度，
 * 固定下来可以防止后续有人无意中改回 Compose 默认的 300ms。
 */
class TransitionSpecsTest {
    @Test
    fun `transition duration stays short enough to feel instant`() {
        // 超过 200ms 在 TV 上就能被感知为「要等一下」
        assertThat(SCREEN_TRANSITION_DURATION_MS).isAtMost(200)
    }

    @Test
    fun `transition duration is not so short that motion is invisible`() {
        // 太短会让切换显得突兀，失去方向提示作用
        assertThat(SCREEN_TRANSITION_DURATION_MS).isAtLeast(80)
    }

    @Test
    fun `enter and exit specs use the shared duration`() {
        val enter = enterTransitionSpec<Float>()
        val exit = exitTransitionSpec<Float>()
        assertThat((enter as TweenSpec<Float>).durationMillis).isEqualTo(SCREEN_TRANSITION_DURATION_MS)
        assertThat((exit as TweenSpec<Float>).durationMillis).isEqualTo(SCREEN_TRANSITION_DURATION_MS)
    }

}
