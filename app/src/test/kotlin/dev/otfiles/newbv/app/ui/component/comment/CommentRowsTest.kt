package dev.otfiles.newbv.app.ui.component.comment

import com.google.common.truth.Truth.assertThat
import dev.otfiles.newbv.app.viewmodel.comment.CommentListState
import dev.otfiles.newbv.app.viewmodel.comment.ReplyListState
import dev.otfiles.newbv.biliapi.entity.comment.Comment
import org.junit.jupiter.api.Test

/**
 * [buildCommentRows] 的行构建测试。
 *
 * 重点回归三个真实出现过的缺陷：
 * 1. 置顶评论的楼中楼展开后，回复行从未被加入列表（点了没反应）
 * 2. 热评的楼中楼同样无法展开
 * 3. 置顶/热评与普通列表之间的 rpid 去重
 */
class CommentRowsTest {
    private fun comment(
        rpid: Long,
        replyCount: Int = 0,
    ) = Comment(
        rpid = rpid,
        oid = 100L,
        type = 1,
        mid = 10L,
        rootRpid = 0L,
        parentRpid = 0L,
        userName = "用户$rpid",
        avatar = "",
        level = 3,
        message = "内容$rpid",
        pictures = emptyList(),
        ctime = 0L,
        likeCount = 0L,
        replyCount = replyCount,
        isLiked = false,
        isUp = false,
    )

    private fun reply(
        rpid: Long,
        rootRpid: Long,
    ) = comment(rpid = rpid).copy(rootRpid = rootRpid, parentRpid = rootRpid)

    private fun rows(state: CommentListState) = buildCommentRows(state)

    @Test
    fun `top comment replies are rendered when expanded`() {
        val top = comment(rpid = 1L, replyCount = 2)
        val replies = listOf(reply(11L, 1L), reply(12L, 1L))
        val state =
            CommentListState(
                topReplies = listOf(top),
                replyLists =
                    mapOf(
                        1L to
                            ReplyListState(
                                rootRpid = 1L,
                                replies = replies,
                                page = 1,
                                loaded = true,
                                expanded = true,
                                hasMore = false,
                            ),
                    ),
            )

        val result = rows(state)

        // 这是本次修复的核心：置顶评论展开后，两条回复必须出现在行列表里
        assertThat(result.filterIsInstance<CommentRow.Reply>().map { it.comment.rpid })
            .containsExactly(11L, 12L)
            .inOrder()
        // 回复行必须挂在正确的根评论下
        assertThat(result.filterIsInstance<CommentRow.Reply>().map { it.rootRpid })
            .containsExactly(1L, 1L)
    }

    @Test
    fun `hot comment replies are rendered when expanded`() {
        val hot = comment(rpid = 2L, replyCount = 1)
        val state =
            CommentListState(
                hots = listOf(hot),
                replyLists =
                    mapOf(
                        2L to
                            ReplyListState(
                                rootRpid = 2L,
                                replies = listOf(reply(21L, 2L)),
                                page = 1,
                                loaded = true,
                                expanded = true,
                                hasMore = false,
                            ),
                    ),
            )

        val result = rows(state)

        assertThat(result.filterIsInstance<CommentRow.Reply>().map { it.comment.rpid })
            .containsExactly(21L)
    }

    @Test
    fun `expanded top comment shows reply footer when more pages exist`() {
        val top = comment(rpid = 1L, replyCount = 30)
        val state =
            CommentListState(
                topReplies = listOf(top),
                replyLists =
                    mapOf(
                        1L to
                            ReplyListState(
                                rootRpid = 1L,
                                replies = listOf(reply(11L, 1L)),
                                page = 1,
                                loaded = true,
                                expanded = true,
                                hasMore = true,
                            ),
                    ),
            )

        // 有下一页时必须给出「更多回复」入口，否则用户无法继续翻页
        assertThat(rows(state).filterIsInstance<CommentRow.ReplyFooter>().map { it.rootRpid })
            .containsExactly(1L)
    }

    @Test
    fun `top comment does not duplicate in normal list`() {
        val top = comment(rpid = 1L, replyCount = 0)
        val other = comment(rpid = 2L, replyCount = 0)
        val state =
            CommentListState(
                topReplies = listOf(top),
                // 服务端有时会把置顶评论也放进普通列表
                comments = listOf(top, other),
            )

        val mainRpids =
            rows(state)
                .filterIsInstance<CommentRow.MainComment>()
                .map { it.comment.rpid }

        // 置顶区已经展示过一次，普通列表里不能再出现
        assertThat(mainRpids).containsExactly(2L)
        assertThat(rows(state).filterIsInstance<CommentRow.TopComment>().map { it.comment.rpid })
            .containsExactly(1L)
    }

    @Test
    fun `hot comment does not duplicate in normal list`() {
        val hot = comment(rpid = 3L, replyCount = 0)
        val other = comment(rpid = 4L, replyCount = 0)
        val state =
            CommentListState(
                hots = listOf(hot),
                comments = listOf(hot, other),
            )

        val mainRpids =
            rows(state)
                .filterIsInstance<CommentRow.MainComment>()
                .map { it.comment.rpid }

        assertThat(mainRpids).containsExactly(4L)
    }

    @Test
    fun `collapsed normal comment shows preview replies`() {
        val root = comment(rpid = 5L, replyCount = 3)
        val state =
            CommentListState(
                comments = listOf(root),
                replyPreviews = mapOf(5L to listOf(reply(51L, 5L), reply(52L, 5L))),
            )

        // 收起态用接口预览，避免为预览额外请求一页
        assertThat(rows(state).filterIsInstance<CommentRow.Reply>().map { it.comment.rpid })
            .containsExactly(51L, 52L)
    }

    @Test
    fun `row keys are unique across all sections`() {
        val top = comment(rpid = 1L, replyCount = 1)
        val hot = comment(rpid = 2L, replyCount = 0)
        val state =
            CommentListState(
                topReplies = listOf(top),
                hots = listOf(hot),
                comments = listOf(comment(rpid = 3L, replyCount = 1)),
                replyLists =
                    mapOf(
                        1L to
                            ReplyListState(
                                rootRpid = 1L,
                                replies = listOf(reply(11L, 1L)),
                                loaded = true,
                                expanded = true,
                            ),
                    ),
            )

        val keys = rows(state).map { it.key }
        // LazyColumn 的 key 重复会直接崩溃，且置顶/热评/普通用了不同前缀，
        // 这条测试锁住「同一条评论跨区出现时 key 不会撞」
        assertThat(keys).containsNoDuplicates()
    }

    @Test
    fun `main footer is always present`() {
        // 空评论区也要有 footer，否则「加载更多/暂无数据」提示无处可显示
        assertThat(rows(CommentListState())).contains(CommentRow.MainFooter)
    }

    @Test
    fun `no reply rows when top comment is collapsed`() {
        val top = comment(rpid = 1L, replyCount = 5)
        val state =
            CommentListState(
                topReplies = listOf(top),
                replyLists = mapOf(1L to ReplyListState(rootRpid = 1L, loaded = false)),
                // 即使有预览数据，置顶区收起态也不展示预览（列表顶部空间宝贵）
                replyPreviews = mapOf(1L to listOf(reply(11L, 1L))),
            )

        assertThat(rows(state).filterIsInstance<CommentRow.Reply>()).isEmpty()
    }
}
