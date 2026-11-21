package dev.otfiles.newbv.core.focus

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 为 TV Material3 组件添加触屏点击支持。
 *
 * TV Material3 的 `tvClickable()` / `tvSelectable()` 仅处理 D-Pad Enter 键事件，
 * 不包含 `pointerInput`/`clickable`，导致触屏点击无效。
 *
 * **根因**：不能在同一 modifier 链中叠加多个 `pointerInput` 修饰符
 * （如 `clickable` + `pointerInput(detectTapGestures)`），
 * 内层的 `pointerInput` 会消费 DOWN 事件（`change.consume()`），
 * 导致外层的 `detectTapGestures` 因 `requireUnconsumed=true` 而跳过该事件。
 *
 * **解决方案**：使用单个 `pointerInput` + `detectTapGestures`，
 * 不依赖 `clickable`/`combinedClickable`，避免多 `pointerInput` 冲突。
 * TV Material3 组件的 focusable/semantics/pressed-state 由其内部
 * `tvClickable`/`tvSelectable` 已处理，此处仅补充触屏手势检测。
 *
 * 触屏点击时通过 [FocusRequester] 请求焦点，使 Tab 切换等 focus 驱动的逻辑
 * 也能在触屏模式下正常工作。
 *
 * 使用 `composed` + `rememberUpdatedState` 确保回调始终为最新值，
 * 同时 `pointerInput` 的 key 仅在 `onLongClick` 有无状态变化时重启协程。
 *
 * @param onClick 单击回调。
 * @param onLongClick 长按回调，null 时仅处理单击且响应更快（无长按等待）。
 * @param requestFocusOnTap 触屏点击时是否主动把焦点移到本元素。
 *   默认 **true**，保持既有行为（很多调用方是自身不可聚焦的自绘元素，
 *   依赖它来获得焦点）。
 *   但如果宿主组件本身已是可聚焦节点（Surface / FilterChip / Button 且传了
 *   `onClick`），必须显式传 **false**——否则同一个按钮上会出现两个焦点，
 *   方向键来回跳、点击响应不稳定。
 */
fun Modifier.touchClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    requestFocusOnTap: Boolean = true,
): Modifier =
    composed {
        val currentOnClick by rememberUpdatedState(onClick)
        val currentOnLongClick by rememberUpdatedState(onLongClick)
        val hasLongClick = onLongClick != null
        // 只有显式要求时才创建焦点请求器，避免与宿主的焦点节点冲突
        val focusRequester = if (requestFocusOnTap) remember { FocusRequester() } else null

        this
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .pointerInput(hasLongClick) {
                detectTapGestures(
                    onTap = {
                        runCatching { focusRequester?.requestFocus() }
                        currentOnClick()
                    },
                    onLongPress =
                        if (hasLongClick) {
                            {
                                runCatching { focusRequester?.requestFocus() }
                                currentOnLongClick?.invoke()
                            }
                        } else {
                            null
                        },
                )
            }
    }
