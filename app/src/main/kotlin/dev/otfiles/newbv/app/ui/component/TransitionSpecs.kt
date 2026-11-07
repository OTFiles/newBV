package dev.otfiles.newbv.app.ui.component

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween

/**
 * 页面切换动画的时长与缓动配置。
 *
 * TV 端的核心体验指标是「按键到画面响应的延迟」。Compose 的
 * `fadeIn()` / `slideInHorizontally()` 在不指定 `animationSpec` 时使用
 * 默认的 300ms tween，一次左右切换光动画就占掉三分之一秒，叠加新旧两页
 * 同时组合（composition）的开销，主观感受就是「按了要等一下才动」。
 *
 * 这里统一收敛到 150ms 并以「快进慢出」的缓动为主：
 * - 前段加速快 → 按下按键的瞬间画面立刻开始变化，视觉上有即时反馈
 * - 后段减速 → 收尾柔和，不会显得生硬
 *
 * 播放器以外的页面切换（首页 Tab、左侧抽屉导航）全部复用这套参数。
 */

/** 页面切换动画时长（毫秒）。默认 300ms 在 TV 上偏拖沓。 */
const val SCREEN_TRANSITION_DURATION_MS = 150

/**
 * 进入动画的 `animationSpec`。
 *
 * 用 [LinearOutSlowInEasing]：一开始速度就很快，随后减速收尾。
 * 这样按键后的第一帧就有明显位移，主观响应最快。
 */
fun <T> enterTransitionSpec() = tween<T>(
    durationMillis = SCREEN_TRANSITION_DURATION_MS,
    easing = LinearOutSlowInEasing,
)

/**
 * 退出动画的 `animationSpec`。
 *
 * 用 [FastOutLinearInEasing]：旧页面快速让位，不与新页面争夺注意力，
 * 也让它更快结束组合、释放资源。
 */
fun <T> exitTransitionSpec() = tween<T>(
    durationMillis = SCREEN_TRANSITION_DURATION_MS,
    easing = FastOutLinearInEasing,
)
