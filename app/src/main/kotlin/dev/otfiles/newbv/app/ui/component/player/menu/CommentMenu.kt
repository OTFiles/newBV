package dev.otfiles.newbv.app.ui.component.player.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.otfiles.newbv.app.ui.component.player.menu.component.PlayerThreeLevelMenu
import dev.otfiles.newbv.app.ui.component.player.menu.component.RadioMenuList
import dev.otfiles.newbv.app.ui.component.player.menu.component.StepLessMenuItem
import dev.otfiles.newbv.app.viewmodel.player.MenuFocusState
import dev.otfiles.newbv.app.viewmodel.player.VideoPlayerCommentMenuItem
import dev.otfiles.newbv.data.datastore.CommentPanelPosition
import dev.otfiles.newbv.data.datastore.CommentPanelSize
import dev.otfiles.newbv.data.datastore.Prefs
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * 播放器评论面板设置。
 *
 * 双列布局与其它设置面板一致：左侧为取值面板，右侧为子项列表。
 * 子项：位置、占屏大小、不透明度、背景遮罩。
 *
 * 这些设置只影响**播放器内**的评论弹窗；详情页的评论窗保持居中大窗。
 * 修改后需重新打开评论弹窗才生效（弹窗打开期间读取一次外观参数）。
 *
 * 数值直接读写 [Prefs]，与播放器其它设置项的持久化方式保持一致，
 * 因此不经过 ViewModel，避免为此新增一整套 action/state 链路。
 *
 * @param modifier 修饰符
 * @param onFocusStateChange 焦点状态变化回调
 */
@Composable
fun CommentMenuList(
    modifier: Modifier = Modifier,
    onFocusStateChange: (MenuFocusState) -> Unit,
) {
    var position by remember { mutableStateOf(Prefs.playerCommentPanelPosition) }
    var fraction by remember { mutableStateOf(Prefs.playerCommentPanelFraction) }
    var opacity by remember { mutableStateOf(Prefs.playerCommentPanelOpacity) }
    var scrimOpacity by remember { mutableStateOf(Prefs.playerCommentPanelScrimOpacity) }

    PlayerThreeLevelMenu(
        modifier = modifier,
        categories = VideoPlayerCommentMenuItem.entries,
        categoryLabel = { it.displayName },
        onFocusStateChange = onFocusStateChange,
    ) { selectedItem, itemModifier, backToMenu ->
        when (selectedItem) {
            VideoPlayerCommentMenuItem.Position ->
                RadioMenuList(
                    modifier = itemModifier,
                    items = CommentPanelPosition.entries.map { it.displayName },
                    selected = position.ordinal,
                    onSelectedChanged = { index ->
                        CommentPanelPosition.entries.getOrNull(index)?.let {
                            position = it
                            Prefs.playerCommentPanelPosition = it
                        }
                    },
                    // 位置变更会重建整个弹窗布局，这里把焦点交还菜单，
                    // 避免停留在一个已经消失的面板上
                    onFocusBackToParent = backToMenu,
                )

            VideoPlayerCommentMenuItem.Size -> {
                // 先给一个可连续调节的滑块（任意比例），
                // 再提供四档预设做快速定位——点预设等价于把滑块拖到该值，
                // 两者写的是同一个 Float，不会互相覆盖。
                StepLessMenuItem(
                    modifier = itemModifier,
                    value = fraction,
                    step = 0.01f,
                    range = CommentPanelSize.MIN_FRACTION..CommentPanelSize.MAX_FRACTION,
                    text = "${(fraction * 100).roundToInt()}%",
                    onValueChange = {
                        fraction = CommentPanelSize.coerceFraction(it)
                        Prefs.playerCommentPanelFraction = fraction
                        // 命中预设时同步高亮状态，纯展示用途
                        CommentPanelSize.closestTo(fraction)?.let { Prefs.playerCommentPanelSize = it }
                    },
                    onFocusBackToParent = backToMenu,
                )
            }

            VideoPlayerCommentMenuItem.SizePreset ->
                RadioMenuList(
                    modifier = itemModifier,
                    items = CommentPanelSize.entries.map { it.displayName },
                    // 未命中任何预设时高亮「最接近」的一档，而不是 -1：
                    // RadioMenuList 依赖 selected 索引设置初始焦点，
                    // 传 -1 会让首次进入该面板时没有焦点落点。
                    selected =
                        (
                            CommentPanelSize.closestTo(fraction) ?: CommentPanelSize.entries.minByOrNull {
                                kotlin.math.abs(it.fraction - fraction)
                            }
                        )?.ordinal ?: 0,
                    onSelectedChanged = { index ->
                        CommentPanelSize.entries.getOrNull(index)?.let {
                            fraction = it.fraction
                            Prefs.playerCommentPanelFraction = it.fraction
                            Prefs.playerCommentPanelSize = it
                        }
                    },
                    onFocusBackToParent = backToMenu,
                )

            VideoPlayerCommentMenuItem.Opacity ->
                StepLessMenuItem(
                    modifier = itemModifier,
                    value = opacity,
                    step = 0.05f,
                    range = 0.2f..1f,
                    text = percent(opacity),
                    onValueChange = {
                        opacity = it
                        Prefs.playerCommentPanelOpacity = it
                    },
                    onFocusBackToParent = backToMenu,
                )

            VideoPlayerCommentMenuItem.ScrimOpacity ->
                StepLessMenuItem(
                    modifier = itemModifier,
                    value = scrimOpacity,
                    // 允许一路调到 0：完全不要背景压暗，画面零遮挡
                    step = 0.05f,
                    range = 0f..1f,
                    text = if (scrimOpacity <= 0.001f) "关闭" else percent(scrimOpacity),
                    onValueChange = {
                        scrimOpacity = it
                        Prefs.playerCommentPanelScrimOpacity = it
                    },
                    onFocusBackToParent = backToMenu,
                )
        }
    }
}

/** 把 0..1 的比例格式化成百分比文本。 */
private fun percent(value: Float): String =
    NumberFormat
        .getPercentInstance()
        .apply { maximumFractionDigits = 0 }
        .format(value)
