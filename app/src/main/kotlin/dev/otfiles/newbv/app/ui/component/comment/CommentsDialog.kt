package dev.otfiles.newbv.app.ui.component.comment

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.otfiles.newbv.app.ui.component.ListFooterTip
import dev.otfiles.newbv.app.util.ToastUtils
import dev.otfiles.newbv.app.util.formatCommentTime
import dev.otfiles.newbv.app.viewmodel.comment.CommentListState
import dev.otfiles.newbv.app.viewmodel.comment.CommentSort
import dev.otfiles.newbv.app.viewmodel.comment.CommentUiEffect
import dev.otfiles.newbv.app.viewmodel.comment.CommentUiState
import dev.otfiles.newbv.app.viewmodel.comment.CommentViewModel
import dev.otfiles.newbv.app.viewmodel.comment.ReplyListState
import dev.otfiles.newbv.app.viewmodel.comment.ReplyTarget
import dev.otfiles.newbv.biliapi.entity.comment.Comment
import dev.otfiles.newbv.data.datastore.CommentPanelPosition
import dev.otfiles.newbv.data.datastore.Prefs
import dev.otfiles.newbv.core.focus.isDpadDown
import dev.otfiles.newbv.core.focus.isDpadLeft
import dev.otfiles.newbv.core.focus.isDpadRight
import dev.otfiles.newbv.core.focus.isDpadUp
import dev.otfiles.newbv.core.focus.touchClickable
import dev.otfiles.newbv.core.theme.BVTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/** 楼中楼回复相对根评论的左侧缩进。 */
private val REPLY_INDENT = 40.dp

/**
 * 播放器评论面板的外观。
 *
 * 把「位置 / 占屏比例 / 透明度」这组设置预先算成 Compose 能直接用的值，
 * 避免在布局里散落 when 分支。形状也跟着停靠方向变：
 * 贴右/左时圆角朝内，贴下/上时圆角朝内，视觉上才像「从屏幕边缘滑出的抽屉」。
 *
 * @property alignment 面板在容器内的对齐方式
 * @property sizeModifier 尺寸约束（宽或高按占屏比例）
 * @property shape 面板圆角形状
 * @property panelOpacity 面板背景不透明度（0..1）
 * @property scrimOpacity 面板外遮罩不透明度（0..1）
 */
private data class CommentPanelAppearance(
    val alignment: Alignment,
    val sizeModifier: Modifier,
    val shape: Shape,
    val panelOpacity: Float,
    val scrimOpacity: Float,
) {
    companion object {
        /** 详情页用的默认外观：右侧居中、固定宽度、较深遮罩。 */
        val DetailDefault =
            CommentPanelAppearance(
                alignment = Alignment.CenterEnd,
                sizeModifier = Modifier.fillMaxHeight().width(680.dp),
                shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                panelOpacity = 1f,
                scrimOpacity = 0.62f,
            )
    }
}

/**
 * 读取并组装播放器评论面板的外观设置。
 *
 * 数值直接读 [Prefs]（与播放器其它设置项一致，不做 Compose 状态订阅）：
 * 这些值只在弹窗打开时使用一次，弹窗关闭后修改设置、再次打开即生效。
 */
@Composable
private fun rememberPlayerCommentAppearance(): CommentPanelAppearance {
    val position = Prefs.playerCommentPanelPosition
    // 用权威的 Float 比例，而不是预设档位，这样滑块微调也能生效
    val fraction = Prefs.playerCommentPanelFraction
    val panelOpacity = Prefs.playerCommentPanelOpacity.coerceIn(0f, 1f)
    val scrimOpacity = Prefs.playerCommentPanelScrimOpacity.coerceIn(0f, 1f)

    return remember(position, fraction, panelOpacity, scrimOpacity) {
        when (position) {
            CommentPanelPosition.Right ->
                CommentPanelAppearance(
                    alignment = Alignment.CenterEnd,
                    // fillMaxHeight + fraction 宽度：高度占满，宽度按比例
                    sizeModifier = Modifier.fillMaxHeight().fillMaxWidth(fraction),
                    shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                    panelOpacity = panelOpacity,
                    scrimOpacity = scrimOpacity,
                )
            CommentPanelPosition.Left ->
                CommentPanelAppearance(
                    alignment = Alignment.CenterStart,
                    sizeModifier = Modifier.fillMaxHeight().fillMaxWidth(fraction),
                    shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                    panelOpacity = panelOpacity,
                    scrimOpacity = scrimOpacity,
                )
            CommentPanelPosition.Bottom ->
                CommentPanelAppearance(
                    alignment = Alignment.BottomCenter,
                    sizeModifier = Modifier.fillMaxWidth().fillMaxHeight(fraction),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    panelOpacity = panelOpacity,
                    scrimOpacity = scrimOpacity,
                )
            CommentPanelPosition.Top ->
                CommentPanelAppearance(
                    alignment = Alignment.TopCenter,
                    sizeModifier = Modifier.fillMaxWidth().fillMaxHeight(fraction),
                    shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                    panelOpacity = panelOpacity,
                    scrimOpacity = scrimOpacity,
                )
        }
    }
}

/** 评论弹窗展示模式。 */
enum class CommentDialogMode {
    /** 详情页展示，使用更宽的弹窗。 */
    Detail,

    /** 播放器展示，使用右侧窄弹窗。 */
    Player,
}

/**
 * 评论弹窗 LazyColumn 的扁平化行模型。
 *
 * 主评论、楼中楼回复与各自分页 footer 使用稳定 key，便于分页追加时维持焦点与滚动位置。
 */
internal sealed interface CommentRow {
    /** 稳定且全局唯一的 LazyColumn key。 */
    val key: String

    /** 主评论行。 */
    data class MainComment(
        val comment: Comment,
    ) : CommentRow {
        override val key: String get() = "comment:${comment.rpid}"
    }

    /**
     * 置顶评论行。
     *
     * 与 [MainComment] 使用不同 key，因为同一条评论可能同时出现在置顶区与普通列表，
     * 但 UI 上只展示置顶那次（普通列表在构建行时已按 rpid 去重）。
     */
    data class TopComment(
        val comment: Comment,
    ) : CommentRow {
        override val key: String get() = "top:${comment.rpid}"
    }

    /** 热评区标题行。 */
    data object HotHeader : CommentRow {
        override val key: String get() = "hots-header"
    }

    /** 热评条目行。 */
    data class HotComment(
        val comment: Comment,
    ) : CommentRow {
        override val key: String get() = "hot:${comment.rpid}"
    }

    /** 置顶区标题行。 */
    data object TopHeader : CommentRow {
        override val key: String get() = "top-header"
    }

    /** 楼中楼回复行（收起态预览与展开后的分页回复共用同一套渲染）。 */
    data class Reply(
        val rootRpid: Long,
        val comment: Comment,
    ) : CommentRow {
        override val key: String get() = "reply:$rootRpid:${comment.rpid}"
    }

    /** 某个根评论的楼中楼分页 footer。 */
    data class ReplyFooter(
        val rootRpid: Long,
    ) : CommentRow {
        override val key: String get() = "reply-footer:$rootRpid"
    }

    /** 主评论分页 footer。 */
    data object MainFooter : CommentRow {
        override val key: String get() = "comment-footer"
    }
}

/**
 * 将当前排序的评论分页状态扁平化为 LazyColumn 行。
 *
 * 楼中楼收起时展示接口自带的预览（条数已由数据层限制）；展开后替换为真实分页回复
 * 与 footer，footer 内的「更多」按钮每次触发一页加载（不随滚动自动加载）。
 *
 * @param commentList 当前排序的评论分页状态
 */
internal fun buildCommentRows(commentList: CommentListState): List<CommentRow> =
    buildList {
        // 置顶区：懒加载通道返回的 top（管理员 → UP 主 → 投票），始终排在最前
        if (commentList.topReplies.isNotEmpty()) {
            add(CommentRow.TopHeader)
            commentList.topReplies.forEach { root ->
                add(CommentRow.TopComment(root))
                addRootReplies(commentList, root)
            }
        }

        // 热评区：独立于翻页列表的一组高热评论，同样排在最前
        val topRpids = commentList.topRpidSet
        val hots = commentList.hots.filter { it.rpid !in topRpids }
        if (hots.isNotEmpty()) {
            add(CommentRow.HotHeader)
            hots.forEach { root ->
                add(CommentRow.HotComment(root))
                addRootReplies(commentList, root)
            }
        }

        // 普通列表需同时排除置顶与热评，否则同一评论会出现两三次。
        // 热评条目本身带 rpid，去重后不丢失任何信息。
        val deduped =
            commentList.comments
                .filter { it.rpid !in topRpids }
                .filter { comment -> hots.none { it.rpid == comment.rpid } }
        deduped.forEach { root ->
            add(CommentRow.MainComment(root))
            addRootReplies(commentList, root, includePreview = true)
        }
        add(CommentRow.MainFooter)
    }

/**
 * 把某个根评论的楼中楼行追加到行列表。
 *
 * 置顶评论与热评同样可以展开楼中楼，但它们的展开状态存在同一个
 * `replyLists[rootRpid]` 里。早期实现只在普通列表里展开，导致置顶/热评的
 * 「N 条回复」按钮点了没有任何反应——行列表里根本没有对应的子行。
 *
 * @param commentList 当前分页状态
 * @param root 根评论
 * @param includePreview 是否在收起态展示接口自带的预览。
 *   置顶/热评不展示预览：它们在列表顶部，展开后内容已经足够，再塞预览会让首屏过长。
 */
/**
 * 找到第一个「内容行」（评论卡片）的下标。
 *
 * 置顶/热评/普通区都有各自的标题行，标题不可聚焦。
 * 收起输入区后需要把焦点还给列表，若指向标题会直接失败（不可聚焦），
 * 因此必须定位到第一张真正的评论卡片。
 *
 * @return 第一个内容行下标；全为空时返回 -1
 */
internal fun List<CommentRow>.indexOfFirstNonHeader(): Int =
    indexOfFirst { row ->
        row is CommentRow.TopComment ||
            row is CommentRow.HotComment ||
            row is CommentRow.MainComment
    }

private fun MutableList<CommentRow>.addRootReplies(
    commentList: CommentListState,
    root: Comment,
    includePreview: Boolean = false,
) {
    val replyList = commentList.replyList(root.rpid)
    if (replyList?.expanded == true) {
        replyList.replies.forEach { reply ->
            add(CommentRow.Reply(rootRpid = root.rpid, comment = reply))
        }
        val showFooter =
            replyList.initialLoading ||
                replyList.initialError ||
                replyList.loadingMore ||
                replyList.loadMoreError ||
                replyList.hasMore
        if (showFooter) add(CommentRow.ReplyFooter(root.rpid))
    } else if (includePreview && root.replyCount > 0) {
        // 收起态直接展示接口预览，避免为预览额外请求第一页
        commentList.replyPreview(root.rpid).forEach { preview ->
            add(CommentRow.Reply(rootRpid = root.rpid, comment = preview))
        }
    }
}

/**
 * 视频评论弹窗。
 *
 * 详情页和播放器共用该组件，不创建独立评论路由。主评论热门/最新各自缓存，楼中楼
 * 默认收起；展开时加载第一页，后续分页由 footer 的「更多」按钮手动触发，不随滚动
 * 自动加载。
 *
 * @param aid 视频 AV 号
 * @param mode 弹窗展示模式
 * @param viewModel 评论状态 ViewModel
 * @param onDismiss 关闭回调
 */
@Composable
fun CommentsDialog(
    aid: Long,
    mode: CommentDialogMode,
    viewModel: CommentViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var imageViewerPictures by remember { mutableStateOf<List<String>?>(null) }
    var imageViewerIndex by remember { mutableIntStateOf(0) }
    // 草稿上提到 Dialog 层：发表成功后需要清空，而输入栏自身在成功后不应被重建
    var draft by remember(aid) { mutableStateOf("") }
    var draftResetToken by remember(aid) { mutableIntStateOf(0) }

    LaunchedEffect(aid) {
        viewModel.load(aid)
    }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is CommentUiEffect.ShowToast -> ToastUtils.show(context, effect.message)
                CommentUiEffect.Posted -> {
                    // 清空草稿：递增 token 让输入框内部重建，避免在外部重置时丢失焦点
                    draft = ""
                    draftResetToken++
                    ToastUtils.show(context, "发表成功")
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = onDismiss)

        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(50)
            runCatching { focusRequester.requestFocus() }
        }

        // 播放器模式读取用户外观设置；详情页保持居中大窗不变。
        // 在 Dialog 内部读取而不是由调用方传入：设置改动后重开弹窗即可生效，
        // 无需把状态提升到播放页。
        val appearance =
            if (mode == CommentDialogMode.Player) {
                rememberPlayerCommentAppearance()
            } else {
                CommentPanelAppearance.DetailDefault
            }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    // 遮罩透明度可调；设为 0 时完全不压暗画面
                    .background(Color.Black.copy(alpha = appearance.scrimOpacity)),
        ) {
            Surface(
                modifier =
                    Modifier
                        .align(appearance.alignment)
                        .then(appearance.sizeModifier)
                        .focusRequester(focusRequester),
                shape = appearance.shape,
                colors =
                    SurfaceDefaults.colors(
                        // 背景透明度作用于容器色，文字颜色保持不透明以免可读性下降
                        containerColor =
                            MaterialTheme.colorScheme.surface.copy(alpha = appearance.panelOpacity),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            ) {
                CommentsContent(
                    state = state,
                    mode = mode,
                    onRefresh = viewModel::refresh,
                    onSortChange = viewModel::changeSort,
                    onLoadMoreComments = viewModel::loadMoreComments,
                    onToggleReplies = viewModel::toggleReplies,
                    onRetryReplies = viewModel::retryReplies,
                    onLoadMoreReplies = viewModel::loadMoreReplies,
                    onToggleLike = viewModel::toggleLike,
                    onPostComment = viewModel::postComment,
                    onReply = viewModel::replyTo,
                    onCancelReply = { viewModel.setReplyTarget(null) },
                    draft = draft,
                    onDraftChange = { draft = it },
                    draftResetToken = draftResetToken,
                    onImageClick = { pictures, index ->
                        imageViewerPictures = pictures
                        imageViewerIndex = index
                    },
                )
            }

            imageViewerPictures?.let { pictures ->
                CommentImageOverlay(
                    pictures = pictures,
                    currentIndex = imageViewerIndex,
                    onDismiss = { imageViewerPictures = null },
                    onIndexChange = { imageViewerIndex = it },
                )
            }
        }
    }
}

@Composable
private fun CommentsContent(
    state: CommentUiState,
    mode: CommentDialogMode,
    onRefresh: () -> Unit,
    onSortChange: (CommentSort) -> Unit,
    onLoadMoreComments: () -> Unit,
    onToggleReplies: (Long) -> Unit,
    onRetryReplies: (Long) -> Unit,
    onLoadMoreReplies: (Long) -> Unit,
    onToggleLike: (Long) -> Unit,
    onPostComment: (String) -> Unit,
    onReply: (Comment) -> Unit,
    onCancelReply: () -> Unit,
    draft: String,
    onDraftChange: (String) -> Unit,
    draftResetToken: Int,
    onImageClick: (List<String>, Int) -> Unit,
) {
    // 热门与最新各自维护滚动状态：共用一个 LazyListState 时，Compose 会按首个可见 item 的
    // key 在新列表中重新定位，导致切换排序后上一条排序的评论被锚定在顶部
    // 每种排序独立维护滚动位置，切换排序后不会把上一种排序的锚点带过来。
    // 必须在 composition 中逐个调用 rememberLazyListState（Composable 不能放在 remember 的 lambda 里）
    val hotListState = rememberLazyListState()
    val mixedListState = rememberLazyListState()
    val latestListState = rememberLazyListState()
    val listState =
        when (state.sort) {
            CommentSort.Hot -> hotListState
            CommentSort.Mixed -> mixedListState
            CommentSort.Latest -> latestListState
        }
    val rows = remember(state.commentList) { buildCommentRows(state.commentList) }
    // 收起输入区后把焦点还给列表：绑定到列表第一个可聚焦条目上。
    // 这个下标只与行构成有关，提到 LazyColumn 外部算一次即可——
    // 放在 item 的 lambda 里会被每一行重复调用，长列表下是 O(n²)。
    val firstContentRowIndex = remember(rows) { rows.indexOfFirstNonHeader() }
    val listFocusRequester = remember { FocusRequester() }

    // 评论文本展开状态：纯 UI 态，按 rpid 记录，切换排序/分页重排时保持
    val expandedCommentIds = remember { mutableStateMapOf<Long, Boolean>() }

    /** 切换单条评论正文的展开/收起状态。 */
    fun toggleCommentText(rpid: Long) {
        expandedCommentIds[rpid] = expandedCommentIds[rpid] != true
    }

    // 主评论滚动接近末尾时自动加载下一页
    LaunchedEffect(listState, state.hasMore, state.loadingMore, state.loadMoreError, rows.size) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index ?: 0
        }.distinctUntilChanged()
            .collectLatest { lastVisibleIndex ->
                if (state.hasMore &&
                    !state.loadingMore &&
                    !state.loadMoreError &&
                    lastVisibleIndex >= rows.size - 3
                ) {
                    onLoadMoreComments()
                }
            }
    }

    // 楼中楼分页不随滚动自动加载：由 footer 的「更多」按钮手动触发，避免展开后
    // 一次性拉取全部回复（也便于用户按需控制请求）

    // 横向面板（顶/底）高度有限，收紧内边距与标题字号，避免把列表挤得只剩一两行。
    // 这是纯展示细节，从 Prefs 直接读即可，不污染 CommentUiState。
    val horizontalPanel =
        mode == CommentDialogMode.Player && !Prefs.playerCommentPanelPosition.isVertical
    val contentPadding = if (horizontalPanel) 12.dp else 20.dp

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        Text(
            text = "评论${state.total.takeIf { it > 0 }?.let { " ($it)" } ?: ""}",
            style =
                if (horizontalPanel) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.headlineSmall
                },
        )

        // 输入区放在列表上方：底部方案要求焦点穿过整屏评论卡片才能到达输入框，
        // 评论多的时候基本无法触达。折叠态只占一行，不影响观感。
        CommentComposer(
            state = state,
            mode = mode,
            draft = draft,
            onDraftChange = onDraftChange,
            draftResetToken = draftResetToken,
            onSubmit = onPostComment,
            onCancelReply = onCancelReply,
            onCollapseFocusTo = {
                // 收起后把焦点交回评论列表的第一张卡片。
                // 这里不指向折叠按钮：该按钮此刻刚进入组合，尚未完成布局，
                // requestFocus 会静默失败且不报错，焦点就会「凭空消失」。
                runCatching { listFocusRequester.requestFocus() }
            },
        )

        Row(
            modifier = Modifier.padding(vertical = if (horizontalPanel) 6.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 服务端通过 cursor.support_mode 声明可用排序；未声明时（空）全部展示
            val supportedModes = state.commentList.supportedModes
            CommentSort.entries
                .filter { supportedModes.isEmpty() || it.cursorMode in supportedModes }
                .forEach { sort ->
                    SortButton(sort, state.sort, onSortChange)
                }
        }

        when {
            state.loading -> {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("评论加载中…")
                }
            }
            state.error && state.comments.isEmpty() -> {
                val retryFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    runCatching { retryFocusRequester.requestFocus() }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("评论加载失败")
                        Spacer(Modifier.height(12.dp))
                        DialogActionButton(
                            text = "重试",
                            icon = Icons.Outlined.Refresh,
                            onClick = onRefresh,
                            focusRequester = retryFocusRequester,
                        )
                    }
                }
            }
            state.comments.isEmpty() -> {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("暂无评论")
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                        // 只有第一个可聚焦的评论卡片接收「收起后回到列表」的焦点
                        val isFirstContentRow = index == firstContentRowIndex
                        when (row) {
                            CommentRow.TopHeader -> SectionHeader("置顶")
                            CommentRow.HotHeader -> SectionHeader("热门评论")
                            is CommentRow.TopComment -> {
                                val comment = row.comment
                                val rpid = comment.rpid
                                val replyList = state.commentList.replyList(rpid)
                                CommentCard(
                                    comment = comment,
                                    compact = mode == CommentDialogMode.Player,
                                    isLiking = rpid in state.likingIds,
                                    showReplyButton = comment.replyCount > 0,
                                    isExpanded = replyList?.expanded == true,
                                    isLoadingReplies = replyList?.initialLoading == true,
                                    previewReplyCount = state.commentList.replyPreview(rpid).size,
                                    isTextExpanded = expandedCommentIds[rpid] == true,
                                    onToggleTextExpand = { toggleCommentText(rpid) },
                                    onToggleReplies = { onToggleReplies(rpid) },
                                    onToggleLike = { onToggleLike(rpid) },
                                    onReply = { onReply(comment) },
                                    onImageClick = onImageClick,
                                    modifier =
                                        if (isFirstContentRow) {
                                            Modifier
                                                .focusRequester(listFocusRequester)
                                                .focusable()
                                        } else {
                                            Modifier
                                        },
                                )
                            }
                            is CommentRow.HotComment -> {
                                val comment = row.comment
                                val rpid = comment.rpid
                                val replyList = state.commentList.replyList(rpid)
                                CommentCard(
                                    comment = comment,
                                    compact = mode == CommentDialogMode.Player,
                                    isLiking = rpid in state.likingIds,
                                    showReplyButton = comment.replyCount > 0,
                                    isExpanded = replyList?.expanded == true,
                                    isLoadingReplies = replyList?.initialLoading == true,
                                    previewReplyCount = state.commentList.replyPreview(rpid).size,
                                    isTextExpanded = expandedCommentIds[rpid] == true,
                                    onToggleTextExpand = { toggleCommentText(rpid) },
                                    onToggleReplies = { onToggleReplies(rpid) },
                                    onToggleLike = { onToggleLike(rpid) },
                                    onReply = { onReply(comment) },
                                    onImageClick = onImageClick,
                                    modifier =
                                        if (isFirstContentRow) {
                                            Modifier
                                                .focusRequester(listFocusRequester)
                                                .focusable()
                                        } else {
                                            Modifier
                                        },
                                )
                            }
                            is CommentRow.MainComment -> {
                                val comment = row.comment
                                val rpid = comment.rpid
                                val replyList = state.commentList.replyList(rpid)
                                CommentCard(
                                    comment = comment,
                                    compact = mode == CommentDialogMode.Player,
                                    isLiking = rpid in state.likingIds,
                                    showReplyButton = comment.replyCount > 0,
                                    isExpanded = replyList?.expanded == true,
                                    isLoadingReplies = replyList?.initialLoading == true,
                                    previewReplyCount = state.commentList.replyPreview(rpid).size,
                                    isTextExpanded = expandedCommentIds[rpid] == true,
                                    onToggleTextExpand = { toggleCommentText(rpid) },
                                    onToggleReplies = { onToggleReplies(rpid) },
                                    onToggleLike = { onToggleLike(rpid) },
                                    onReply = { onReply(comment) },
                                    onImageClick = onImageClick,
                                    modifier =
                                        if (isFirstContentRow) {
                                            Modifier
                                                .focusRequester(listFocusRequester)
                                                .focusable()
                                        } else {
                                            Modifier
                                        },
                                )
                            }
                            is CommentRow.Reply -> {
                                val comment = row.comment
                                val rpid = comment.rpid
                                ReplyCommentCard(
                                    comment = comment,
                                    isLiking = rpid in state.likingIds,
                                    isTextExpanded = expandedCommentIds[rpid] == true,
                                    onToggleTextExpand = { toggleCommentText(rpid) },
                                    onToggleLike = { onToggleLike(rpid) },
                                    onReply = { onReply(comment) },
                                    onImageClick = onImageClick,
                                )
                            }
                            is CommentRow.ReplyFooter -> {
                                state.commentList.replyList(row.rootRpid)?.let { replyList ->
                                    ReplyFooterRow(
                                        replyList = replyList,
                                        onRetry = { onRetryReplies(row.rootRpid) },
                                        onLoadMore = { onLoadMoreReplies(row.rootRpid) },
                                    )
                                }
                            }
                            CommentRow.MainFooter -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    ListFooterTip(
                                        isLoading = state.loadingMore,
                                        isError = state.loadMoreError,
                                        hasMore = state.hasMore,
                                        itemsIsEmpty = state.comments.isEmpty(),
                                    )
                                    if (state.loadMoreError) {
                                        Spacer(Modifier.height(8.dp))
                                        DialogActionButton(
                                            text = "重试",
                                            icon = Icons.Outlined.Refresh,
                                            onClick = onLoadMoreComments,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}

/**
 * 楼中楼回复卡片（已加载回复与收起态预览共用）。
 *
 * 回复行统一紧凑样式、带左缩进，且不展示「回复」展开按钮。
 */
@Composable
private fun ReplyCommentCard(
    comment: Comment,
    isLiking: Boolean,
    isTextExpanded: Boolean,
    onToggleTextExpand: () -> Unit,
    onToggleLike: () -> Unit,
    onReply: () -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
) {
    CommentCard(
        comment = comment,
        compact = true,
        isLiking = isLiking,
        showReplyButton = false,
        isExpanded = false,
        isLoadingReplies = false,
        isTextExpanded = isTextExpanded,
        onToggleTextExpand = onToggleTextExpand,
        onToggleReplies = {},
        onToggleLike = onToggleLike,
        onReply = onReply,
        onImageClick = onImageClick,
        indent = REPLY_INDENT,
    )
}

/**
 * 单条评论卡片。
 *
 * 主评论与楼中楼回复共用；[showReplyButton] 为 false 时（回复行）不展示回复展开按钮。
 * 评论文本超出最大行数时可展开全文，仅在实际溢出时展示"展开/收起"按钮。
 *
 * @param previewReplyCount 收起态已经展示的楼中楼预览条数；大于 0 时回复按钮文案
 *   变为「共 N 条回复」，提示点击后进入完整分页列表
 * @param onReply 点击本条卡片的「回复」按钮，把该评论设为回复目标
 */
@Composable
internal fun CommentCard(
    comment: Comment,
    compact: Boolean,
    isLiking: Boolean,
    showReplyButton: Boolean,
    isExpanded: Boolean,
    isLoadingReplies: Boolean,
    isTextExpanded: Boolean,
    onToggleTextExpand: () -> Unit,
    onToggleReplies: () -> Unit,
    onToggleLike: () -> Unit,
    onReply: () -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
    indent: Dp = 0.dp,
    previewReplyCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    // 记录文本在折叠状态下是否溢出；溢出过一次后按钮常驻，避免展开后回落到 false 导致按钮消失
    var isTextOverflowing by remember(comment.rpid) { mutableStateOf(false) }
    val textMaxLines =
        when {
            isTextExpanded -> Int.MAX_VALUE
            compact -> 4
            else -> 8
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = indent)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AsyncImage(
                model = comment.avatar,
                contentDescription = "${comment.userName}头像",
                modifier = Modifier.size(if (compact) 32.dp else 40.dp).clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text =
                            buildString {
                                append(comment.userName)
                                if (comment.level > 0) append("  Lv.${comment.level}")
                                if (comment.isUp) append("  UP主")
                            },
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    val timeText = remember(comment.ctime) { comment.ctime.formatCommentTime() }
                    if (timeText.isNotEmpty()) {
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = comment.message,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = textMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result -> if (result.hasVisualOverflow) isTextOverflowing = true },
                    modifier = Modifier.padding(top = 4.dp).animateContentSize(),
                )
                if (comment.pictures.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        comment.pictures.forEachIndexed { index, url ->
                            val picFocusRequester = remember { FocusRequester() }
                            Surface(
                                modifier =
                                    Modifier
                                        .size(if (compact) 60.dp else 80.dp)
                                        .focusRequester(picFocusRequester)
                                        .touchClickable(onClick = { onImageClick(comment.pictures, index) }),
                                onClick = { onImageClick(comment.pictures, index) },
                                shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
                                border =
                                    ClickableSurfaceDefaults.border(
                                        focusedBorder =
                                            Border(
                                                border =
                                                    androidx.compose.foundation.BorderStroke(
                                                        2.dp,
                                                        MaterialTheme.colorScheme.border,
                                                    ),
                                                shape = MaterialTheme.shapes.small,
                                            ),
                                    ),
                            ) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = "评论图片 ${index + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DialogActionButton(
                        text = "赞 ${comment.likeCount}",
                        icon = if (comment.isLiked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                        onClick = onToggleLike,
                        enabled = !isLiking,
                    )
                    if (showReplyButton) {
                        val replyActionText =
                            when {
                                isLoadingReplies -> "加载中"
                                isExpanded -> "收起"
                                // 收起态已有接口预览时，点击按钮进入完整分页列表
                                previewReplyCount > 0 -> "共 ${comment.replyCount} 条回复"
                                else -> "回复 ${comment.replyCount}"
                            }
                        DialogActionButton(
                            text = replyActionText,
                            icon = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            onClick = onToggleReplies,
                            enabled = !isLoadingReplies,
                        )
                    }
                    if (isTextOverflowing) {
                        DialogActionButton(
                            text = if (isTextExpanded) "收起" else "展开",
                            icon = if (isTextExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            onClick = onToggleTextExpand,
                        )
                    }
                    DialogActionButton(
                        text = "回复",
                        icon = Icons.Outlined.Reply,
                        onClick = onReply,
                    )
                }
            }
        }
    }
}

/**
 * 楼中楼分页 footer。
 *
 * 第一页由展开自动加载，后续页仅在用户点击「更多」时加载一页，
 * 加载中/失败分别展示状态与重试入口。
 */
@Composable
internal fun ReplyFooterRow(
    replyList: ReplyListState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = REPLY_INDENT + 8.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            replyList.initialError -> {
                Text(
                    text = "回复加载失败",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
                DialogActionButton(text = "重试", icon = Icons.Outlined.Refresh, onClick = onRetry)
            }
            replyList.loadMoreError -> {
                Text(
                    text = "加载失败",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
                DialogActionButton(text = "重试", icon = Icons.Outlined.Refresh, onClick = onLoadMore)
            }
            replyList.initialLoading || replyList.loadingMore -> {
                Text(text = "回复加载中…", style = MaterialTheme.typography.labelMedium)
            }
            else -> {
                DialogActionButton(text = "更多回复", icon = Icons.Outlined.ExpandMore, onClick = onLoadMore)
            }
        }
    }
}

@Composable
private fun SortButton(
    sort: CommentSort,
    selected: CommentSort,
    onClick: (CommentSort) -> Unit,
) {
    Surface(
        modifier = Modifier.touchClickable(onClick = { onClick(sort) }),
        onClick = { onClick(sort) },
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor =
                    if (sort ==
                        selected
                    ) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                contentColor =
                    if (sort ==
                        selected
                    ) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder =
                    Border(
                        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.border),
                        shape = MaterialTheme.shapes.small,
                    ),
            ),
    ) {
        Text(sort.displayName, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

/**
 * 评论区小节标题（「置顶」「热门评论」）。
 *
 * @param text 标题文字
 */
@Composable
private fun SectionHeader(text: String) {
    Text(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun DialogActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    Surface(
        modifier =
            Modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .touchClickable(onClick = onClick),
        onClick = onClick,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = text, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * 评论输入栏。
 *
 * 底部常驻，包含：回复目标提示条（仅回复时出现）、多行输入框与发送按钮。
 *
 * TV 端没有物理键盘，输入依赖系统输入法（遥控器数字键盘按键会触发输入法弹窗）；
 * 有硬键盘/鼠标时也可直接输入。因此这里不重建一套自制键盘，只在输入框上做好
 * D-Pad 焦点样式，并在发送成功后清空草稿。
 *
 * @param state 当前评论状态（提供回复目标与发送中标志）
 * @param mode 弹窗模式，影响输入框行数
 * @param draft 当前草稿文本，由 Dialog 层持有以便发表成功后清空
 * @param onDraftChange 草稿变更回调
 * @param draftResetToken 草稿重置令牌，变化时输入框会重新获取焦点并清空内部状态
 * @param onSubmit 提交回调，传入去掉首尾空白的正文
 * @param onCancelReply 取消回复，回到发表一级评论
 */
@Composable
private fun CommentComposer(
    state: CommentUiState,
    mode: CommentDialogMode,
    draft: String,
    onDraftChange: (String) -> Unit,
    draftResetToken: Int,
    onSubmit: (String) -> Unit,
    onCancelReply: () -> Unit,
    onCollapseFocusTo: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val collapsedButtonFocusRequester = remember { FocusRequester() }

    val target = state.replyTarget

    // 有任何「要写点什么」的信号就自动展开并聚焦输入框：
    // - 点了某条评论的「回复」
    // - 草稿非空（例如上一次没发出去的残留）
    // 这样按「回复」后可以直接打字，不需要再用方向键去找输入框。
    LaunchedEffect(target, draft.isNotEmpty()) {
        if (target != null || draft.isNotEmpty()) {
            if (!expanded) expanded = true
            // 等一帧让输入框完成布局再请求焦点
            kotlinx.coroutines.delay(16)
            runCatching { focusRequester.requestFocus() }
        }
    }

    // 草稿重置令牌变化：发表成功后清空并重新聚焦，方便连续发言
    LaunchedEffect(draftResetToken) {
        if (draftResetToken > 0) {
            expanded = true
            kotlinx.coroutines.delay(16)
            runCatching { focusRequester.requestFocus() }
        }
    }

    /** 收起输入区并把焦点交给调用方安排的落点（通常回到评论列表）。 */
    fun collapse() {
        expanded = false
        // 收起后焦点要去处明确。requestFocus() 在节点尚未完成布局时是静默失败的
        // （不抛异常），无法靠返回值判断，因此改由调用方统一处理：
        // 等一帧让折叠按钮完成布局，再请求焦点；失败则由调用方退回列表首项。
        onCollapseFocusTo()
    }

    val canSubmit = draft.isNotBlank() && !state.isPosting

    fun submit() {
        if (!canSubmit) return
        onSubmit(draft)
    }

    if (!expanded) {
        // 折叠态：一行按钮。焦点只需从上方的排序区按一次下键即可到达，
        // 不再需要穿过整个评论列表。
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(collapsedButtonFocusRequester),
                // Surface 的 onClick 已经处理点击，不再叠 touchClickable：
                // 两者同时存在会导致触屏点一次触发两次
                onClick = { expanded = true },
                shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                border =
                    ClickableSurfaceDefaults.border(
                        focusedBorder =
                            Border(
                                border =
                                    androidx.compose.foundation.BorderStroke(
                                        2.dp,
                                        MaterialTheme.colorScheme.border,
                                    ),
                                shape = MaterialTheme.shapes.small,
                            ),
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Reply,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "写评论…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        target?.let { replyTarget ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = replyTarget.hint,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                DialogActionButton(
                    text = "取消",
                    icon = Icons.Outlined.Close,
                    onClick = onCancelReply,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Surface(
                modifier =
                    Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                onClick = { runCatching { focusRequester.requestFocus() } },
                shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                border =
                    ClickableSurfaceDefaults.border(
                        focusedBorder =
                            Border(
                                border =
                                    androidx.compose.foundation.BorderStroke(
                                        2.dp,
                                        MaterialTheme.colorScheme.border,
                                    ),
                                shape = MaterialTheme.shapes.small,
                            ),
                    ),
            ) {
                Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    if (draft.isEmpty()) {
                        Text(
                            text = target?.let { "回复 @${it.targetUserName}…" } ?: "发一条友善的评论…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { if (it.length <= COMMENT_INPUT_MAX_LENGTH) onDraftChange(it) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isFocused = it.isFocused },
                        textStyle =
                            TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                            ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = if (mode == CommentDialogMode.Player) 3 else 5,
                        keyboardOptions =
                            KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                imeAction = ImeAction.Send,
                            ),
                        keyboardActions = KeyboardActions(onSend = { submit() }),
                    )
                }
            }

            DialogActionButton(
                text =
                    when {
                        state.isPosting -> "发送中"
                        else -> "发送"
                    },
                icon = Icons.AutoMirrored.Outlined.Send,
                onClick = ::submit,
                enabled = canSubmit,
            )

            // 收起入口：草稿为空时提供，避免误触丢失已输入内容
            if (draft.isEmpty() && !state.isPosting) {
                DialogActionButton(
                    text = "收起",
                    icon = Icons.Outlined.ExpandLess,
                    onClick = ::collapse,
                )
            }
        }

        // 有内容时常显字数；聚焦但为空时给出输入提示
        when {
            draft.isNotEmpty() -> {
                Text(
                    text = "${draft.length} / $COMMENT_INPUT_MAX_LENGTH",
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (draft.length >= COMMENT_INPUT_MAX_LENGTH) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            isFocused -> {
                Text(
                    text = "遥控器按确认键唤出输入法，也可直接用鼠标/键盘输入",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 输入框本地字符限制，与服务端 1000 字上限一致。 */
private const val COMMENT_INPUT_MAX_LENGTH = 1000

/**
 * 图片查看覆盖层。
 *
 * 使用独立 Dialog 创建独立焦点窗口，D-Pad 左右翻页，上下键被消费以避免焦点逃逸，
 * 关闭后回到评论列表的缩略图焦点。
 *
 * @param pictures 图片 URL 列表
 * @param currentIndex 当前显示的图片索引
 * @param onDismiss 关闭回调
 * @param onIndexChange 图片切换回调
 */
@Composable
private fun CommentImageOverlay(
    pictures: List<String>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onIndexChange: (Int) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = onDismiss)

        LaunchedEffect(Unit) {
            runCatching { focusRequester.requestFocus() }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                        when {
                            event.isDpadLeft() && currentIndex > 0 -> {
                                onIndexChange(currentIndex - 1)
                                true
                            }
                            event.isDpadRight() && currentIndex < pictures.lastIndex -> {
                                onIndexChange(currentIndex + 1)
                                true
                            }
                            event.isDpadUp() || event.isDpadDown() -> true
                            else -> false
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = pictures[currentIndex],
                contentDescription = "评论图片 ${currentIndex + 1}/${pictures.size}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            if (pictures.size > 1) {
                Text(
                    text = "${currentIndex + 1} / ${pictures.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

// region Previews

private fun fakeComment(
    rpid: Long = 1L,
    userName: String = "用户名",
    message: String = "这是一条评论内容",
    likeCount: Long = 42,
    replyCount: Int = 3,
    isLiked: Boolean = false,
    isUp: Boolean = false,
    level: Int = 5,
): Comment =
    Comment(
        rpid = rpid,
        oid = 1L,
        type = 1,
        mid = 100L,
        rootRpid = 0L,
        parentRpid = 0L,
        userName = userName,
        avatar = "",
        level = level,
        message = message,
        pictures = emptyList(),
        ctime = System.currentTimeMillis() / 1000,
        likeCount = likeCount,
        replyCount = replyCount,
        isLiked = isLiked,
        isUp = isUp,
    )

@Preview(device = "id:tv_1080p", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun CommentsContentPreview() {
    BVTheme {
        val comments =
            listOf(
                fakeComment(
                    rpid = 1,
                    userName = "测试用户A",
                    message = "这个视频做得太好了，学到了很多！",
                    likeCount = 128,
                    replyCount = 5,
                    level = 6,
                ),
                fakeComment(
                    rpid = 2,
                    userName = "UP主本人",
                    message = "感谢大家的支持！下期视频已经在做了。",
                    likeCount = 56,
                    replyCount = 12,
                    isUp = true,
                    level = 6,
                ),
                fakeComment(
                    rpid = 3,
                    userName = "路人乙",
                    message = "沙发沙发，第一次这么靠前",
                    likeCount = 3,
                    replyCount = 0,
                    level = 2,
                ),
            )
        CommentsContent(
            state =
                CommentUiState(
                    aid = 1L,
                    commentLists =
                        mapOf(
                            CommentSort.Hot to
                                dev.otfiles.newbv.app.viewmodel.comment.CommentListState(
                                    comments = comments,
                                    page = 1,
                                    total = 328,
                                    hasMore = false,
                                    loaded = true,
                                ),
                        ),
                    sort = CommentSort.Hot,
                ),
            mode = CommentDialogMode.Player,
            onRefresh = {},
            onSortChange = {},
            onLoadMoreComments = {},
            onToggleReplies = {},
            onRetryReplies = {},
            onLoadMoreReplies = {},
            onToggleLike = {},
            onPostComment = {},
            onReply = {},
            onCancelReply = {},
            draft = "",
            onDraftChange = {},
            draftResetToken = 0,
            onImageClick = { _, _ -> },
        )
    }
}

// endregion
