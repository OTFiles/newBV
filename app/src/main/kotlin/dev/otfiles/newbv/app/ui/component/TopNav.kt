package dev.otfiles.newbv.app.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowScope
import androidx.tv.material3.Text
import dev.otfiles.newbv.core.focus.touchClickable

/**
 * 顶部导航 Tab 栏。
 *
 * TV Material3 [TabRow] 封装，支持 D-Pad 焦点导航。
 * Tab 切换时触发 [onSelectedChanged]，点击同一 Tab 触发 [onClick]（用于刷新）。
 *
 * @param items Tab 项列表（已按首选项排序）。
 * @param selectedIndex 当前选中的 Tab 索引（由外部控制，用于导航返回后恢复）。
 * @param isLargePadding 内容区未获焦点时使用较大内边距。
 * @param onSelectedChanged Tab 焦点切换回调。
 * @param onClick Tab 点击回调。
 */
@Composable
fun TopNav(
    modifier: Modifier = Modifier,
    items: List<TopNavItem>,
    selectedIndex: Int = 0,
    isLargePadding: Boolean,
    onSelectedChanged: (TopNavItem) -> Unit = {},
    onClick: (TopNavItem) -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }

    var selectedTabIndex by remember { mutableIntStateOf(selectedIndex) }
    // 每个 Tab 一个 FocusRequester，供按键预切时主动移动焦点
    val tabFocusRequesters =
        remember(items.size) {
            List(items.size) { FocusRequester() }
        }

    LaunchedEffect(selectedIndex) {
        selectedTabIndex = selectedIndex
    }
    val verticalPadding by animateDpAsState(
        targetValue = if (isLargePadding) 12.dp else 6.dp,
        label = "top-nav-padding",
    )

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(12.dp, verticalPadding)
                // 在 KeyDown 阶段就把内容切过去，而不是等焦点移动完成后的 onFocus。
                // onFocus 要等焦点系统完成一次完整遍历才触发，在低端电视上这一帧
                // 就是肉眼可见的「按了没反应」。这里提前一个事件相位切换，
                // 让按键与画面变化发生在同一帧。
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val delta =
                        when (event.key) {
                            Key.DirectionLeft -> -1
                            Key.DirectionRight -> 1
                            else -> return@onPreviewKeyEvent false
                        }
                    val target = selectedTabIndex + delta
                    // 越界时放行，交给焦点系统去做跨区域跳转（如跳到左侧栏）
                    if (target !in items.indices) return@onPreviewKeyEvent false

                    // 同步三件事：内容状态、选中索引、真实焦点。
                    // 只改前三者不改焦点的话，视觉高亮会和实际焦点位置不一致，
                    // 再按方向键就会从错误的位置继续跳。
                    selectedTabIndex = target
                    onSelectedChanged(items[target])
                    tabFocusRequesters[target]?.let { runCatching { it.requestFocus() } }
                    // 消费事件：焦点已由我们手动移动，避免同一次按键
                    // 又被 TabRow 的默认焦点搜索再移动一次，导致跳两格
                    true
                },
        horizontalArrangement = Arrangement.Center,
    ) {
        TabRow(
            modifier =
                Modifier
                    .focusRestorer(focusRequester),
            selectedTabIndex = selectedTabIndex,
            separator = { Spacer(modifier = Modifier.width(12.dp)) },
        ) {
            items.forEachIndexed { index, tab ->
                NavItemTab(
                    modifier =
                        Modifier
                            .then(
                                if (index == 0) {
                                    Modifier.focusRequester(focusRequester)
                                } else {
                                    Modifier
                                },
                            ).focusRequester(tabFocusRequesters[index]),
                    topNavItem = tab,
                    selected = index == selectedTabIndex,
                    onFocus = {
                        // 焦点被其它方式移动（如鼠标、初始聚焦）时仍然同步
                        if (selectedTabIndex != index) {
                            selectedTabIndex = index
                            onSelectedChanged(tab)
                        }
                    },
                    onClick = { onClick(tab) },
                )
            }
        }
    }
}

@Composable
private fun TabRowScope.NavItemTab(
    modifier: Modifier = Modifier,
    topNavItem: TopNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit,
) {
    // 统一使用品牌青绿作为 TopNav 强调色，避免各页面颜色不一致
    val accentColor = MaterialTheme.colorScheme.secondary
    var hasFocus by remember { mutableStateOf(false) }
    val containerColor =
        if (selected) {
            accentColor.copy(alpha = if (hasFocus) 0.24f else 0.14f)
        } else {
            Color.Transparent
        }
    val borderColor = if (hasFocus) accentColor else Color.Transparent
    Tab(
        modifier =
            modifier
                .onFocusChanged { hasFocus = it.hasFocus }
                .clip(RoundedCornerShape(50))
                .background(containerColor)
                .border(2.dp, borderColor, RoundedCornerShape(50))
                .touchClickable(onClick = onClick),
        selected = selected,
        onFocus = onFocus,
        onClick = onClick,
    ) {
        // 用固定高度的 Box 居中文字：直接对 Text 设 height 会把字形顶对齐，
        // 字体行高与剩余空间不等时文字就偏上，不同字体/字号下表现不一致。
        Box(
            modifier =
                Modifier
                    .height(32.dp)
                    .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = topNavItem.displayName,
                color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * 顶部导航项接口。
 */
interface TopNavItem {
    val displayName: String
}

private data class DummyTopNavItem(
    override val displayName: String,
) : TopNavItem

@Preview(showBackground = true)
@Composable
private fun TopNavPreview() {
    dev.otfiles.newbv.core.theme.BVTheme {
        TopNav(
            items =
                listOf(
                    DummyTopNavItem("推荐"),
                    DummyTopNavItem("热门"),
                    DummyTopNavItem("动态"),
                ),
            isLargePadding = true,
        )
    }
}
