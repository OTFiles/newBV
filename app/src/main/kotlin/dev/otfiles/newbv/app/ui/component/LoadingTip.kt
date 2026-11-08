package dev.otfiles.newbv.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.otfiles.newbv.R

/** 列表底部提示区的固定高度，见 [ListFooterTip] 中的说明。 */
private val FOOTER_HEIGHT = 56.dp

/**
 * 加载中提示组件。
 *
 * 圆形进度指示器 + "加载中…" 文本，用于列表底部加载更多。
 */
@Composable
fun LoadingTip(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = stringResource(id = R.string.loading),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 加载失败提示组件。
 *
 * 显示 "加载失败" 文本，用于网络超时或请求报错场景。
 */
@Composable
fun ErrorTip(modifier: Modifier = Modifier) {
    Text(
        modifier = modifier,
        text = stringResource(id = R.string.load_failed),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * 列表底部统一提示组件。
 *
 * 封装加载/错误/无更多三种状态的判断逻辑，供所有分页列表页复用。
 *
 * 状态优先级：loading > error > noMore。
 * - [isLoading] 为 true 时显示 [LoadingTip]。
 * - [isError] 为 true 时显示 [ErrorTip]。
 * - [hasMore] 为 false 且列表非空时显示 "没有更多了捏"。
 *
 * @param isLoading 是否正在加载。
 * @param isError 是否加载失败（超时或报错）。
 * @param hasMore 是否还有更多数据。
 * @param itemsIsEmpty 列表是否为空（空列表时不显示"没有更多"）。
 */
@Composable
fun ListFooterTip(
    isLoading: Boolean,
    isError: Boolean,
    hasMore: Boolean,
    itemsIsEmpty: Boolean,
    modifier: Modifier = Modifier,
) {
    // 用固定高度 + fillMaxWidth，而不是 fillMaxSize。
    //
    // 这个组件放在 LazyGrid 的最后一个跨列 item 里。fillMaxSize 会让它尝试撑满
    // 剩余空间，item 高度随内容变化 → 每次 isLoading 切换都要重算整个网格尺寸，
    // 而 CircularProgressIndicator 是无限循环动画，与尺寸重算叠加会在低端设备上
    // 表现为「动画跑着跑着卡住」。固定高度后布局稳定，不再触发这轮重算。
    when {
        isLoading -> {
            Box(
                modifier = modifier.fillMaxWidth().height(FOOTER_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                LoadingTip()
            }
        }

        isError -> {
            Box(
                modifier = modifier.fillMaxWidth().height(FOOTER_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                ErrorTip()
            }
        }

        !hasMore && !itemsIsEmpty -> {
            Text(
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                text = "没有更多了捏",
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun LoadingTipPreview() {
    dev.otfiles.newbv.core.theme.BVTheme {
        LoadingTip()
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorTipPreview() {
    dev.otfiles.newbv.core.theme.BVTheme {
        ErrorTip()
    }
}

@Preview(showBackground = true)
@Composable
private fun ListFooterTipLoadingPreview() {
    dev.otfiles.newbv.core.theme.BVTheme {
        ListFooterTip(isLoading = true, isError = false, hasMore = true, itemsIsEmpty = true)
    }
}

@Preview(showBackground = true)
@Composable
private fun ListFooterTipErrorPreview() {
    dev.otfiles.newbv.core.theme.BVTheme {
        ListFooterTip(isLoading = false, isError = true, hasMore = true, itemsIsEmpty = true)
    }
}

@Preview(showBackground = true)
@Composable
private fun ListFooterTipNoMorePreview() {
    dev.otfiles.newbv.core.theme.BVTheme {
        ListFooterTip(isLoading = false, isError = false, hasMore = false, itemsIsEmpty = false)
    }
}

// endregion
