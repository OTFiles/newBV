package dev.otfiles.newbv.app.ui.component

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * 页面切换动画的时长与缓动配置。
 *
 * TV 端的核心体验指标是「按键到画面响应的延迟」。Compose 的
 * `fadeIn()` / `slideInHorizontally()` 在不指定 `animationSpec` 时使用
 * 默认的 300ms tween，一次左右切换光动画就占掉三分之一秒，叠加新旧两页
 * 同时组合（composition）的开销，主观感受就是「按了要等了才动」。
 *
 * 这里统一收敛到 150ms 并以「快进慢出」的缓动为主：
 * - 前段加速快 → 按下按键的瞬间画面立刻开始变化，视觉上有即时反馈
 * - 后段减速 → 收尾柔和，不会显得生硬
 */

/** 页面切换动画时长（毫秒）。默认 300ms 在 TV 上偏拖沓。 */
const val SCREEN_TRANSITION_DURATION_MS = 150

/**
 * 浮点类动画（透明度、缩放）的进入 spec。
 *
 * 用 [LinearOutSlowInEasing]：一开始速度就很快，随后减速收尾，
 * 按键后的第一帧就有明显变化。
 */
fun <T> enterTransitionSpec(): FiniteAnimationSpec<T> =
    tween(
        durationMillis = SCREEN_TRANSITION_DURATION_MS,
        easing = LinearOutSlowInEasing,
    )

/**
 * 浮点类动画的退出 spec。
 *
 * 用 [FastOutLinearInEasing]：旧内容快速让位，尽快结束组合并释放资源。
 */
fun <T> exitTransitionSpec(): FiniteAnimationSpec<T> =
    tween(
        durationMillis = SCREEN_TRANSITION_DURATION_MS,
        easing = FastOutLinearInEasing,
    )

/**
 * 位移动画（`slideInHorizontally` / `slideInVertically`）的进入 spec。
 *
 * `slide*` 系列要求 `FiniteAnimationSpec<IntOffset>`，与透明度用的
 * `FiniteAnimationSpec<Float>` 不是同一类型参数，必须单独给出，
 * 否则在调用点会因类型推断不出 `T` 而编译失败。
 */
fun slideTransitionSpec(): FiniteAnimationSpec<IntOffset> =
    tween(
        durationMillis = SCREEN_TRANSITION_DURATION_MS,
        easing = LinearOutSlowInEasing,
    )
