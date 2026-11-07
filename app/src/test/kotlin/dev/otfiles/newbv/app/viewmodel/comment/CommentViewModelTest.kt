package dev.otfiles.newbv.app.viewmodel.comment

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.otfiles.newbv.biliapi.entity.comment.Comment
import dev.otfiles.newbv.biliapi.entity.comment.CommentPage
import dev.otfiles.newbv.biliapi.http.entity.comment.CommentAddResult
import dev.otfiles.newbv.biliapi.repositories.CommentRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [CommentViewModel] 的排序缓存、分页、楼中楼和点赞测试。 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommentViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: CommentRepository
    private lateinit var viewModel: CommentViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk()
        // 默认让懒加载通道失败，触发 ViewModel 的自动降级到翻页通道。
        // 这样既有用例（都只 stub 了 getComments）无需逐个改写，
        // 同时每次运行都在验证降级路径本身可用。
        // 需要专门验证游标通道的用例会在用例内用 coEvery 覆盖这个 stub。
        coEvery {
            repository.getCommentsByCursor(any(), any(), any())
        } throws IllegalStateException("cursor channel disabled in tests")
        viewModel = CommentViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load requests hot comments and exposes page`() =
        runTest(dispatcher) {
            val root = comment(rpid = 1L, replyCount = 2)
            coEvery { repository.getComments(any(), any(), any(), any(), any()) } returns
                page(listOf(root), page = 1, total = 1, hasMore = false)

            viewModel.load(100L)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.comments).containsExactly(root)
            assertThat(viewModel.uiState.value.hasMore).isFalse()
            coVerify { repository.getComments(100L, 1, 1, null, any()) }
        }

    @Test
    fun `switching sort caches each comment list and does not reload`() =
        runTest(dispatcher) {
            val hot = comment(rpid = 1L)
            val latest = comment(rpid = 2L)
            coEvery { repository.getComments(100L, 1, 1, null, any()) } returns
                page(listOf(hot), page = 1, total = 1, hasMore = false)
            coEvery { repository.getComments(100L, 0, 1, null, any()) } returns
                page(listOf(latest), page = 1, total = 1, hasMore = false)

            viewModel.load(100L)
            advanceUntilIdle()

            viewModel.changeSort(CommentSort.Latest)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.comments).containsExactly(latest)

            viewModel.changeSort(CommentSort.Hot)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.comments).containsExactly(hot)

            viewModel.changeSort(CommentSort.Latest)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.comments).containsExactly(latest)

            coVerify(exactly = 1) { repository.getComments(100L, 1, 1, null, any()) }
            coVerify(exactly = 1) { repository.getComments(100L, 0, 1, null, any()) }
        }

    @Test
    fun `refresh only reloads current sort and keeps other cache`() =
        runTest(dispatcher) {
            val hot = comment(rpid = 1L)
            val latest = comment(rpid = 2L)
            coEvery { repository.getComments(100L, 1, 1, null, any()) } returns
                page(listOf(hot), page = 1, total = 1, hasMore = false)
            coEvery { repository.getComments(100L, 0, 1, null, any()) } returns
                page(listOf(latest), page = 1, total = 1, hasMore = false)

            viewModel.load(100L)
            advanceUntilIdle()
            viewModel.changeSort(CommentSort.Latest)
            advanceUntilIdle()

            viewModel.refresh()
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.getComments(100L, 1, 1, null, any()) }
            coVerify(exactly = 2) { repository.getComments(100L, 0, 1, null, any()) }

            viewModel.changeSort(CommentSort.Hot)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.comments).containsExactly(hot)
            coVerify(exactly = 1) { repository.getComments(100L, 1, 1, null, any()) }
        }

    @Test
    fun `changing aid discards stale response`() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<CommentPage>()
            coEvery { repository.getComments(1L, 1, 1, null, any()) } coAnswers { gate.await() }
            coEvery { repository.getComments(2L, 1, 1, null, any()) } returns
                page(listOf(comment(rpid = 20L)), page = 1, total = 1, hasMore = false)

            viewModel.load(1L)
            runCurrent()
            viewModel.load(2L)
            advanceUntilIdle()

            gate.complete(page(listOf(comment(rpid = 10L)), page = 1, total = 1, hasMore = false))
            runCurrent()

            assertThat(viewModel.uiState.value.aid).isEqualTo(2L)
            assertThat(
                viewModel.uiState.value.comments
                    .map { it.rpid },
            ).containsExactly(20L)
        }

    @Test
    fun `load more appends and deduplicates by rpid`() =
        runTest(dispatcher) {
            coEvery { repository.getComments(100L, 1, 1, null, any()) } returns
                page(listOf(comment(1L), comment(2L)), page = 1, total = 3, hasMore = true)
            coEvery { repository.getComments(100L, 1, 2, null, any()) } returns
                page(listOf(comment(2L), comment(3L)), page = 2, total = 3, hasMore = false)

            viewModel.load(100L)
            advanceUntilIdle()
            viewModel.loadMoreComments()
            advanceUntilIdle()

            assertThat(
                viewModel.uiState.value.comments
                    .map { it.rpid },
            ).containsExactly(1L, 2L, 3L).inOrder()
            assertThat(viewModel.uiState.value.hasMore).isFalse()
        }

    @Test
    fun `load more failure keeps list and allows retry`() =
        runTest(dispatcher) {
            var attempt = 0
            coEvery { repository.getComments(100L, 1, 1, null, any()) } returns
                page(listOf(comment(1L), comment(2L)), page = 1, total = 3, hasMore = true)
            coEvery { repository.getComments(100L, 1, 2, null, any()) } coAnswers {
                attempt++
                if (attempt == 1) throw RuntimeException("boom")
                page(listOf(comment(3L)), page = 2, total = 3, hasMore = false)
            }

            viewModel.load(100L)
            advanceUntilIdle()

            viewModel.loadMoreComments()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.loadMoreError).isTrue()
            assertThat(viewModel.uiState.value.comments).hasSize(2)

            viewModel.loadMoreComments()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.loadMoreError).isFalse()
            assertThat(
                viewModel.uiState.value.comments
                    .map { it.rpid },
            ).containsExactly(1L, 2L, 3L).inOrder()
        }

    @Test
    fun `load more empty page stops pagination`() =
        runTest(dispatcher) {
            coEvery { repository.getComments(100L, 1, 1, null, any()) } returns
                page(listOf(comment(1L)), page = 1, total = 10, hasMore = true)
            coEvery { repository.getComments(100L, 1, 2, null, any()) } returns
                page(emptyList(), page = 2, total = 10, hasMore = true)

            viewModel.load(100L)
            advanceUntilIdle()
            viewModel.loadMoreComments()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.hasMore).isFalse()
            assertThat(viewModel.uiState.value.comments).hasSize(1)
        }

    @Test
    fun `toggle replies loads and expands root comment`() =
        runTest(dispatcher) {
            val root = comment(rpid = 1L, replyCount = 1)
            val reply = comment(rpid = 2L, rootRpid = 1L)
            coEvery { repository.getComments(any(), any(), any(), any(), any()) } returns
                page(listOf(root), page = 1, total = 1, hasMore = false)
            coEvery { repository.getReplies(any(), any(), any(), any(), any()) } returns
                page(listOf(reply), page = 1, total = 1, hasMore = false)

            viewModel.load(100L)
            advanceUntilIdle()
            viewModel.toggleReplies(1L)
            advanceUntilIdle()

            val replyList =
                viewModel.uiState.value.commentList
                    .replyList(1L)
            assertThat(replyList?.expanded).isTrue()
            assertThat(replyList?.replies).containsExactly(reply)
        }

    @Test
    fun `reply first page failure can be retried with one call`() =
        runTest(dispatcher) {
            var attempt = 0
            coEvery { repository.getComments(any(), any(), any(), any(), any()) } returns
                page(listOf(comment(rpid = 1L, replyCount = 1)), page = 1, total = 1, hasMore = false)
            coEvery { repository.getReplies(any(), any(), any(), any(), any()) } coAnswers {
                attempt++
                if (attempt == 1) throw RuntimeException("boom")
                page(listOf(comment(rpid = 2L, rootRpid = 1L)), page = 1, total = 1, hasMore = false)
            }

            viewModel.load(100L)
            advanceUntilIdle()
            viewModel.toggleReplies(1L)
            advanceUntilIdle()

            assertThat(
                viewModel.uiState.value.commentList
                    .replyList(1L)
                    ?.initialError,
            ).isTrue()

            viewModel.retryReplies(1L)
            advanceUntilIdle()

            val replyList =
                viewModel.uiState.value.commentList
                    .replyList(1L)
            assertThat(replyList?.initialError).isFalse()
            assertThat(replyList?.replies).hasSize(1)
            assertThat(replyList?.expanded).isTrue()
        }

    @Test
    fun `load keeps reply previews from main page`() =
        runTest(dispatcher) {
            val root = comment(rpid = 1L, replyCount = 2)
            val preview = comment(rpid = 11L, rootRpid = 1L)
            coEvery { repository.getComments(any(), any(), any(), any(), any()) } returns
                page(
                    listOf(root),
                    page = 1,
                    total = 1,
                    hasMore = false,
                    replyPreviews = mapOf(1L to listOf(preview)),
                )

            viewModel.load(100L)
            advanceUntilIdle()

            assertThat(
                viewModel.uiState.value.commentList
                    .replyPreview(1L),
            ).containsExactly(preview)
        }

    @Test
    fun `load more merges reply previews of appended comments`() =
        runTest(dispatcher) {
            val first = comment(rpid = 1L, replyCount = 1)
            val second = comment(rpid = 2L, replyCount = 1)
            val firstPreview = comment(rpid = 11L, rootRpid = 1L)
            val secondPreview = comment(rpid = 22L, rootRpid = 2L)
            coEvery { repository.getComments(100L, 1, 1, null, any()) } returns
                page(
                    listOf(first),
                    page = 1,
                    total = 2,
                    hasMore = true,
                    replyPreviews = mapOf(1L to listOf(firstPreview)),
                )
            coEvery { repository.getComments(100L, 1, 2, null, any()) } returns
                page(
                    listOf(second),
                    page = 2,
                    total = 2,
                    hasMore = false,
                    replyPreviews = mapOf(2L to listOf(secondPreview)),
                )

            viewModel.load(100L)
            advanceUntilIdle()
            viewModel.loadMoreComments()
            advanceUntilIdle()

            val commentList = viewModel.uiState.value.commentList
            assertThat(commentList.replyPreview(1L)).containsExactly(firstPreview)
            assertThat(commentList.replyPreview(2L)).containsExactly(secondPreview)
        }

    @Test
    fun `toggle like updates preview reply only after success`() =
        runTest(dispatcher) {
            val root = comment(rpid = 1L, replyCount = 1)
            val preview = comment(rpid = 11L, rootRpid = 1L)
            coEvery { repository.getComments(any(), any(), any(), any(), any()) } returns
                page(
                    listOf(root),
                    page = 1,
                    total = 1,
                    hasMore = false,
                    replyPreviews = mapOf(1L to listOf(preview)),
                )
            coEvery { repository.toggleCommentLike(any(), any(), any(), any()) } returns Unit

            viewModel.load(100L)
            advanceUntilIdle()

            viewModel.toggleLike(11L)
            // 请求完成前不乐观更新
            assertThat(
                viewModel.uiState.value
                    .findComment(11L)
                    ?.isLiked,
            ).isFalse()

            advanceUntilIdle()
            val updated =
                viewModel.uiState.value.commentList
                    .replyPreview(1L)
                    .single()
            assertThat(updated.isLiked).isTrue()
            assertThat(updated.likeCount).isEqualTo(1L)
            coVerify { repository.toggleCommentLike(100L, 11L, true, any()) }
        }

    @Test
    fun `load more replies appends next page`() =
        runTest(dispatcher) {
            val root = comment(rpid = 1L, replyCount = 25)
            val firstPage = (1L..20L).map { comment(rpid = 100L + it, rootRpid = 1L) }
            val secondPage = (21L..25L).map { comment(rpid = 100L + it, rootRpid = 1L) }
            coEvery { repository.getComments(any(), any(), any(), any(), any()) } returns
                page(listOf(root), page = 1, total = 1, hasMore = false)
            coEvery { repository.getReplies(100L, 1L, 1, null, any()) } returns
                page(firstPage, page = 1, total = 25, hasMore = true)
            coEvery { repository.getReplies(100L, 1L, 2, null, any()) } returns
                page(secondPage, page = 2, total = 25, hasMore = false)

            viewModel.load(100L)
            advanceUntilIdle()
            viewModel.toggleReplies(1L)
            advanceUntilIdle()
            assertThat(
                viewModel.uiState.value.commentList
                    .replyList(1L)
                    ?.replies,
            ).hasSize(20)

            viewModel.loadMoreReplies(1L)
            advanceUntilIdle()

            val replyList =
                viewModel.uiState.value.commentList
                    .replyList(1L)
            assertThat(replyList?.replies).hasSize(25)
            assertThat(replyList?.hasMore).isFalse()
        }

    @Test
    fun `toggle like updates nested reply only after success`() =
        runTest(dispatcher) {
            val root = comment(rpid = 1L, replyCount = 1)
            val reply = comment(rpid = 2L, rootRpid = 1L)
            coEvery { repository.getComments(any(), any(), any(), any(), any()) } returns
                page(listOf(root), page = 1, total = 1, hasMore = false)
            coEvery { repository.getReplies(any(), any(), any(), any(), any()) } returns
                page(listOf(reply), page = 1, total = 1, hasMore = false)
            coEvery { repository.toggleCommentLike(any(), any(), any(), any()) } returns Unit

            viewModel.load(100L)
            advanceUntilIdle()
            viewModel.toggleReplies(1L)
            advanceUntilIdle()

            viewModel.toggleLike(2L)
            assertThat(
                viewModel.uiState.value
                    .findComment(2L)
                    ?.isLiked,
            ).isFalse()

            advanceUntilIdle()
            val updated = viewModel.uiState.value.findComment(2L)
            assertThat(updated?.isLiked).isTrue()
            assertThat(updated?.likeCount).isEqualTo(1L)
            coVerify { repository.toggleCommentLike(100L, 2L, true, any()) }
        }

    @Test
    fun `toggle like emits toast and keeps state on failure`() =
        runTest(dispatcher) {
            val root = comment(rpid = 1L)
            coEvery { repository.getComments(any(), any(), any(), any(), any()) } returns
                page(listOf(root), page = 1, total = 1, hasMore = false)
            coEvery { repository.toggleCommentLike(any(), any(), any(), any()) } throws
                RuntimeException("网络错误")

            viewModel.load(100L)
            advanceUntilIdle()

            viewModel.uiEffect.test {
                viewModel.toggleLike(1L)
                advanceUntilIdle()

                assertThat(
                    viewModel.uiState.value
                        .findComment(1L)
                        ?.isLiked,
                ).isFalse()
                val effect = awaitItem() as CommentUiEffect.ShowToast
                assertThat(effect.message).contains("评论点赞失败")
            }
        }

    @Test
    fun `post comment refreshes current sort and clears reply target`() =
        runTest(dispatcher) {
            val root = comment(rpid = 1L, replyCount = 0)
            coEvery { repository.getComments(any(), any(), any(), any(), any()) } returns
                page(listOf(root), page = 1, total = 1, hasMore = false)
            coEvery { repository.postComment(any(), any(), any(), any()) } returns
                CommentAddResult(code = 0, message = "0", rpid = 99L)

            viewModel.load(100L)
            advanceUntilIdle()
            val loadCallsBefore = 1

            viewModel.postComment("  新评论  ")
            advanceUntilIdle()

            // 提交时正文会被 trim，一级评论不带 root/parent
            coVerify { repository.postComment(100L, "新评论", 0L, 0L) }
            assertThat(viewModel.uiState.value.isPosting).isFalse()
            assertThat(viewModel.uiState.value.replyTarget).isNull()
            // 发表成功后重新拉取当前排序首屏
            coVerify(exactly = loadCallsBefore + 1) {
                repository.getComments(any(), any(), any(), any(), any())
            }
        }

    @Test
    fun `post comment failure keeps draft state and emits toast`() =
        runTest(dispatcher) {
            coEvery { repository.getComments(any(), any(), any(), any(), any()) } returns
                page(listOf(comment(rpid = 1L)), page = 1, total = 1, hasMore = false)
            coEvery { repository.postComment(any(), any(), any(), any()) } returns
                CommentAddResult(code = 12016, message = "啥都别说啦", rpid = 0L)

            viewModel.load(100L)
            advanceUntilIdle()

            viewModel.uiEffect.test {
                skipItems(0)
                viewModel.postComment("测试")
                advanceUntilIdle()

                // 已知错误码走 CommentAddResult.userMessage 的映射文案，
                // 而不是直接透出服务端 message
                val effect = awaitItem() as CommentUiEffect.ShowToast
                assertThat(effect.message).contains("敏感信息")
            }
            assertThat(viewModel.uiState.value.isPosting).isFalse()
        }

    @Test
    fun `post empty comment does not call repository`() =
        runTest(dispatcher) {
            coEvery { repository.getComments(any(), any(), any(), any(), any()) } returns
                page(listOf(comment(rpid = 1L)), page = 1, total = 1, hasMore = false)

            viewModel.load(100L)
            advanceUntilIdle()

            viewModel.postComment("   ")
            advanceUntilIdle()

            coVerify(exactly = 0) { repository.postComment(any(), any(), any(), any()) }
            assertThat(viewModel.uiState.value.isPosting).isFalse()
        }

    @Test
    fun `reply to root comment uses the same rpid for root and parent`() =
        runTest(dispatcher) {
            coEvery { repository.getComments(any(), any(), any(), any(), any()) } returns
                page(listOf(comment(rpid = 1L, replyCount = 0)), page = 1, total = 1, hasMore = false)
            coEvery { repository.getReplies(any(), any(), any(), any(), any()) } returns
                page(emptyList(), page = 1, total = 0, hasMore = false)
            coEvery { repository.postComment(any(), any(), any(), any()) } returns
                CommentAddResult(code = 0, message = "0", rpid = 100L)

            viewModel.load(100L)
            advanceUntilIdle()

            viewModel.replyTo(comment(rpid = 1L))
            assertThat(viewModel.uiState.value.replyTarget?.rootRpid).isEqualTo(1L)
            assertThat(viewModel.uiState.value.replyTarget?.parentRpid).isEqualTo(1L)

            viewModel.postComment("回复一下")
            advanceUntilIdle()

            coVerify { repository.postComment(100L, "回复一下", 1L, 1L) }
            assertThat(viewModel.uiState.value.replyTarget).isNull()
        }

    @Test
    fun `reply to nested reply keeps root and parent distinct`() =
        runTest(dispatcher) {
            coEvery { repository.getComments(any(), any(), any(), any(), any()) } returns
                page(listOf(comment(rpid = 1L, replyCount = 1)), page = 1, total = 1, hasMore = false)
            coEvery { repository.getReplies(any(), any(), any(), any(), any()) } returns
                page(listOf(comment(rpid = 11L, rootRpid = 1L)), page = 1, total = 1, hasMore = false)

            viewModel.load(100L)
            advanceUntilIdle()
            // 展开楼中楼后再回复某条回复
            viewModel.toggleReplies(1L)
            advanceUntilIdle()

            viewModel.replyTo(comment(rpid = 11L, rootRpid = 1L))

            assertThat(viewModel.uiState.value.replyTarget?.rootRpid).isEqualTo(1L)
            assertThat(viewModel.uiState.value.replyTarget?.parentRpid).isEqualTo(11L)
        }

    @Test
    fun `cancel reply target returns to root comment mode`() =
        runTest(dispatcher) {
            coEvery { repository.getComments(any(), any(), any(), any(), any()) } returns
                page(listOf(comment(rpid = 1L)), page = 1, total = 1, hasMore = false)

            viewModel.load(100L)
            advanceUntilIdle()

            viewModel.replyTo(comment(rpid = 1L))
            assertThat(viewModel.uiState.value.replyTarget).isNotNull()

            viewModel.setReplyTarget(null)
            assertThat(viewModel.uiState.value.replyTarget).isNull()
        }

    // ===== 懒加载通道（游标翻页）与降级 =====

    @Test
    fun `cursor channel is used on first load and feeds hots and top`() =
        runTest(dispatcher) {
            val pinned = comment(rpid = 1L)
            val hot = comment(rpid = 2L)
            val normal = comment(rpid = 3L)
            coEvery { repository.getCommentsByCursor(100L, any(), any()) } returns
                CommentPage(
                    comments = listOf(normal),
                    page = 1,
                    total = 3,
                    hasMore = true,
                    paginationOffset = """{"type":3,"direction":1,"Data":{"cursor":99}}""",
                    hots = listOf(hot),
                    topReplies = listOf(pinned),
                    supportedModes = listOf(2, 3),
                )

            viewModel.load(100L)
            advanceUntilIdle()

            val state = viewModel.uiState.value.commentList
            assertThat(state.useCursor).isTrue()
            assertThat(state.topReplies).containsExactly(pinned)
            assertThat(state.hots).containsExactly(hot)
            assertThat(state.paginationOffset).isNotNull()
            assertThat(state.supportedModes).containsExactly(2, 3)
            // 走通游标通道后不应再请求翻页通道
            coVerify(exactly = 0) { repository.getComments(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `cursor failure falls back to paging and records degraded state`() =
        runTest(dispatcher) {
            val root = comment(rpid = 1L)
            coEvery { repository.getComments(any(), any(), any(), any(), any()) } returns
                page(listOf(root), page = 1, total = 1, hasMore = false)

            viewModel.load(100L)
            advanceUntilIdle()

            val state = viewModel.uiState.value.commentList
            // 降级后必须标记 useCursor=false，避免下次加载重复打失败请求
            assertThat(state.useCursor).isFalse()
            assertThat(state.comments).containsExactly(root)
            assertThat(state.initialError).isFalse()
        }

    @Test
    fun `load more uses pagination offset on cursor channel`() =
        runTest(dispatcher) {
            val first = comment(rpid = 1L)
            coEvery { repository.getCommentsByCursor(100L, any(), any()) } returns
                CommentPage(
                    comments = listOf(first),
                    page = 1,
                    total = 2,
                    hasMore = true,
                    paginationOffset = """{"type":3,"direction":1,"Data":{"cursor":99}}""",
                )
            viewModel.load(100L)
            advanceUntilIdle()

            coEvery { repository.getCommentsByCursor(100L, any(), any()) } returns
                CommentPage(
                    comments = listOf(comment(rpid = 2L)),
                    page = 1,
                    total = 2,
                    hasMore = false,
                    paginationOffset = null,
                )
            viewModel.loadMoreComments()
            advanceUntilIdle()

            // 第二页必须把第一页返回的 offset 原样传回去
            coVerify {
                repository.getCommentsByCursor(
                    100L,
                    any(),
                    """{"type":3,"direction":1,"Data":{"cursor":99}}""",
                )
            }
            assertThat(viewModel.uiState.value.comments).hasSize(2)
            assertThat(viewModel.uiState.value.hasMore).isFalse()
        }

    @Test
    fun `top replies are excluded from both first and appended pages`() =
        runTest(dispatcher) {
            val pinned = comment(rpid = 1L)
            coEvery { repository.getCommentsByCursor(100L, any(), any()) } returns
                CommentPage(
                    comments = listOf(pinned, comment(rpid = 2L)),
                    page = 1,
                    total = 3,
                    hasMore = true,
                    paginationOffset = "off1",
                    topReplies = listOf(pinned),
                )
            viewModel.load(100L)
            advanceUntilIdle()

            // 第二页又返回了同一条置顶评论，应被过滤掉
            coEvery { repository.getCommentsByCursor(100L, any(), any()) } returns
                CommentPage(
                    comments = listOf(pinned, comment(rpid = 3L)),
                    page = 1,
                    total = 3,
                    hasMore = false,
                )
            viewModel.loadMoreComments()
            advanceUntilIdle()

            val rpids = viewModel.uiState.value.comments.map { it.rpid }
            // 置顶评论只在 topReplies 里，普通列表不应重复出现
            assertThat(rpids).doesNotContain(1L)
            assertThat(rpids).containsExactly(2L, 3L)
        }

    @Test
    fun `mixed sort is requested with cursor mode 1`() =
        runTest(dispatcher) {
            coEvery { repository.getCommentsByCursor(any(), any(), any()) } returns
                CommentPage(
                    comments = listOf(comment(rpid = 1L)),
                    page = 1,
                    total = 1,
                    hasMore = false,
                    supportedModes = listOf(1, 2, 3),
                )
            viewModel.load(100L)
            advanceUntilIdle()

            viewModel.changeSort(CommentSort.Mixed)
            advanceUntilIdle()

            coVerify { repository.getCommentsByCursor(100L, 1, any()) }
        }

    @Test
    fun `is sort supported reflects server declared modes`() =
        runTest(dispatcher) {
            coEvery { repository.getCommentsByCursor(any(), any(), any()) } returns
                CommentPage(
                    comments = listOf(comment(rpid = 1L)),
                    page = 1,
                    total = 1,
                    hasMore = false,
                    supportedModes = listOf(2, 3),
                )
            viewModel.load(100L)
            advanceUntilIdle()

            // 服务端只声明了 2/3，混合排序（1）不可用
            assertThat(viewModel.isSortSupported(CommentSort.Hot)).isTrue()
            assertThat(viewModel.isSortSupported(CommentSort.Latest)).isTrue()
            assertThat(viewModel.isSortSupported(CommentSort.Mixed)).isFalse()
        }

    private fun page(
        comments: List<Comment>,
        page: Int,
        total: Int = comments.size,
        hasMore: Boolean,
        replyPreviews: Map<Long, List<Comment>> = emptyMap(),
    ) = CommentPage(
        comments = comments,
        page = page,
        total = total,
        hasMore = hasMore,
        replyPreviews = replyPreviews,
    )

    private fun comment(
        rpid: Long,
        rootRpid: Long = 0L,
        replyCount: Int = 0,
    ) = Comment(
        rpid = rpid,
        oid = 100L,
        type = 1,
        mid = 10L,
        rootRpid = rootRpid,
        parentRpid = 0L,
        userName = "测试用户",
        avatar = "",
        level = 6,
        message = "测试评论",
        pictures = emptyList(),
        ctime = 0L,
        likeCount = 0L,
        replyCount = replyCount,
        isLiked = false,
        isUp = false,
    )
}
