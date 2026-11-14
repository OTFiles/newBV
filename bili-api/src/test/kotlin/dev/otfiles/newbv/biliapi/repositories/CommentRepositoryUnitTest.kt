package dev.otfiles.newbv.biliapi.repositories

import com.google.common.truth.Truth.assertThat
import dev.otfiles.newbv.biliapi.entity.ApiType
import dev.otfiles.newbv.biliapi.entity.comment.CommentTopType
import dev.otfiles.newbv.biliapi.http.BiliHttpApi
import dev.otfiles.newbv.biliapi.http.entity.BiliResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * [CommentRepository] 的单元测试。
 *
 * 通过 MockK 模拟 [BiliHttpApi] 单例，验证 Web 路径的评论获取、楼中楼获取与
 * 评论点赞逻辑（参数传递、成功/失败路径），以及 App 路径在 channel 未初始化时
 * 直接抛异常、不自动回退到 HTTP。不依赖真实网络。
 */
class CommentRepositoryUnitTest {
    private lateinit var repository: CommentRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var channelRepository: ChannelRepository

    companion object {
        private const val AID = 993403941L
        private const val RPID = 123456L
        private const val BILI_JCT = "test-bili-jct"
        private const val ACCESS_TOKEN = "test-access-token"
        private val json = Json { ignoreUnknownKeys = true }
    }

    @BeforeEach
    fun setUp() {
        mockkObject(BiliHttpApi)
        authRepository = AuthRepository()
        authRepository.biliJct = BILI_JCT
        authRepository.accessToken = ACCESS_TOKEN
        channelRepository = ChannelRepository()
        repository = CommentRepository(authRepository, channelRepository)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(BiliHttpApi)
    }

    // ------------------------------------------------------------------
    // getComments (Web)
    // ------------------------------------------------------------------

    @Test
    fun `getComments Web parses replies and page acount`() =
        runTest {
            val data = repliesJson(responses = 2, total = 20)
            coEvery { BiliHttpApi.getVideoComments(any(), any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = data)

            val result = repository.getComments(aid = AID, sort = 1, page = 1, preferApiType = ApiType.Web)

            assertThat(result.comments).hasSize(2)
            assertThat(result.comments[0].message).isEqualTo("评论1")
            assertThat(result.comments[0].userName).isEqualTo("用户1")
            assertThat(result.total).isEqualTo(20)
        }

    @Test
    fun `getComments Web passes accessKey null`() =
        runTest {
            coEvery { BiliHttpApi.getVideoComments(any(), any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = repliesJson())

            repository.getComments(aid = AID, sort = 0, page = 2, preferApiType = ApiType.Web)

            coVerify { BiliHttpApi.getVideoComments(eq(AID), eq(0), eq(2), any(), isNull()) }
        }

    @Test
    fun `getComments Web hasMore respects page size and num`() =
        runTest {
            val data = repliesJson(responses = 2, total = 3, num = 1, size = 2)
            coEvery { BiliHttpApi.getVideoComments(any(), any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = data)

            val result = repository.getComments(aid = AID, sort = 1, page = 1, preferApiType = ApiType.Web)

            assertThat(result.hasMore).isTrue()
        }

    @Test
    fun `getComments Web stops when page covers total`() =
        runTest {
            val data = repliesJson(responses = 2, total = 2, num = 1, size = 2)
            coEvery { BiliHttpApi.getVideoComments(any(), any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = data)

            val result = repository.getComments(aid = AID, sort = 1, page = 1, preferApiType = ApiType.Web)

            assertThat(result.hasMore).isFalse()
        }

    @Test
    fun `getComments Web filters invalid and duplicate rpid`() =
        runTest {
            val data = repliesJson(rpids = listOf(0L, RPID, RPID), total = 1)
            coEvery { BiliHttpApi.getVideoComments(any(), any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = data)

            val result = repository.getComments(aid = AID, sort = 1, page = 1, preferApiType = ApiType.Web)

            assertThat(result.comments.map { it.rpid }).containsExactly(RPID)
        }

    @Test
    fun `getComments Web marks UP author by upper mid`() =
        runTest {
            // 回归：此前用 reply_control.up_like 判断「UP主」，会把 UP 点赞过的评论误标为 UP 主
            val data = repliesJson(responses = 2, total = 2, upperMid = 1000L)
            coEvery { BiliHttpApi.getVideoComments(any(), any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = data)

            val result = repository.getComments(aid = AID, sort = 1, page = 1, preferApiType = ApiType.Web)

            assertThat(result.comments[0].mid).isEqualTo(1000L)
            assertThat(result.comments[0].isUp).isTrue()
            assertThat(result.comments[1].isUp).isFalse()
        }

    @Test
    fun `getComments Web parses one level reply previews`() =
        runTest {
            val data =
                repliesJson(
                    responses = 2,
                    total = 2,
                    previews = mapOf(RPID to listOf(9001L, 9002L)),
                )
            coEvery { BiliHttpApi.getVideoComments(any(), any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = data)

            val result = repository.getComments(aid = AID, sort = 1, page = 1, preferApiType = ApiType.Web)

            // 只有带预览的根评论会出现在 map 中
            assertThat(result.replyPreviews.keys).containsExactly(RPID)
            val previews = result.replyPreviews[RPID].orEmpty()
            assertThat(previews.map { it.rpid }).containsExactly(9001L, 9002L).inOrder()
            assertThat(previews[0].message).isEqualTo("预览9001")
        }

    @Test
    fun `getReplies Web does not produce reply previews`() =
        runTest {
            // 楼中楼分页接口的条目没有可用的嵌套预览，解析结果应为空
            val data = repliesJson(responses = 1, total = 1, previews = mapOf(RPID to listOf(9001L)))
            coEvery { BiliHttpApi.getVideoCommentReplies(any(), any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = data)

            val result = repository.getReplies(aid = AID, rootRpid = RPID, page = 1, preferApiType = ApiType.Web)

            assertThat(result.replyPreviews).isEmpty()
        }

    // ------------------------------------------------------------------
    // getComments (App gRPC)
    // ------------------------------------------------------------------

    @Test
    fun `getComments App throws when channel is unavailable`() =
        runTest {
            assertThrows<IllegalStateException> {
                repository.getComments(aid = AID, sort = 1, page = 1, preferApiType = ApiType.App)
            }
        }

    // ------------------------------------------------------------------
    // getReplies (Web)
    // ------------------------------------------------------------------

    @Test
    fun `getReplies Web parses nested replies`() =
        runTest {
            val data = repliesJson(responses = 1, total = 5)
            coEvery { BiliHttpApi.getVideoCommentReplies(any(), any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = data)

            val result = repository.getReplies(aid = AID, rootRpid = RPID, page = 1, preferApiType = ApiType.Web)

            assertThat(result.comments).hasSize(1)
            assertThat(result.comments[0].message).isEqualTo("评论1")
        }

    @Test
    fun `getReplies Web passes rootRpid and accessKey null`() =
        runTest {
            coEvery { BiliHttpApi.getVideoCommentReplies(any(), any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = repliesJson())

            repository.getReplies(aid = AID, rootRpid = RPID, page = 2, preferApiType = ApiType.Web)

            coVerify { BiliHttpApi.getVideoCommentReplies(eq(AID), eq(RPID), eq(2), any(), isNull()) }
        }

    @Test
    fun `getReplies Web hasMore uses page count`() =
        runTest {
            val data = repliesJson(responses = 2, count = 25, num = 1, size = 20)
            coEvery { BiliHttpApi.getVideoCommentReplies(any(), any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = data)

            val result = repository.getReplies(aid = AID, rootRpid = RPID, page = 1, preferApiType = ApiType.Web)

            assertThat(result.total).isEqualTo(25)
            assertThat(result.hasMore).isTrue()
        }

    @Test
    fun `getReplies Web stops when page covers count`() =
        runTest {
            val data = repliesJson(responses = 2, count = 1, num = 1, size = 20)
            coEvery { BiliHttpApi.getVideoCommentReplies(any(), any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = data)

            val result = repository.getReplies(aid = AID, rootRpid = RPID, page = 1, preferApiType = ApiType.Web)

            assertThat(result.hasMore).isFalse()
        }

    // ------------------------------------------------------------------
    // getReplies (App gRPC)
    // ------------------------------------------------------------------

    @Test
    fun `getReplies App throws when channel is unavailable`() =
        runTest {
            assertThrows<IllegalStateException> {
                repository.getReplies(aid = AID, rootRpid = RPID, page = 1, preferApiType = ApiType.App)
            }
        }

    // ------------------------------------------------------------------
    // toggleCommentLike
    // ------------------------------------------------------------------

    @Test
    fun `toggleCommentLike Web passes csrf`() =
        runTest {
            coEvery { BiliHttpApi.updateCommentLiked(any(), any(), any(), any(), any()) } returns Pair(true, "")

            repository.toggleCommentLike(aid = AID, rpid = RPID, like = true, preferApiType = ApiType.Web)

            coVerify { BiliHttpApi.updateCommentLiked(eq(AID), eq(RPID), eq(true), eq(BILI_JCT), isNull()) }
        }

    @Test
    fun `toggleCommentLike Web throws on API failure`() =
        runTest {
            coEvery { BiliHttpApi.updateCommentLiked(any(), any(), any(), any(), any()) } returns Pair(false, "点赞失败")

            val exception =
                assertThrows<IllegalStateException> {
                    repository.toggleCommentLike(aid = AID, rpid = RPID, like = true, preferApiType = ApiType.Web)
                }

            assertThat(exception.message).isEqualTo("点赞失败")
        }

    @Test
    fun `toggleCommentLike Android App passes accessKey not csrf`() =
        runTest {
            coEvery { BiliHttpApi.updateCommentLiked(any(), any(), any(), any(), any()) } returns Pair(true, "")

            repository.toggleCommentLike(aid = AID, rpid = RPID, like = true, preferApiType = ApiType.App)

            coVerify { BiliHttpApi.updateCommentLiked(eq(AID), eq(RPID), eq(true), isNull(), eq(ACCESS_TOKEN)) }
        }

    @Test
    fun `toggleCommentLike App uses default message when blank`() =
        runTest {
            coEvery { BiliHttpApi.updateCommentLiked(any(), any(), any(), any(), any()) } returns Pair(false, "")

            val exception =
                assertThrows<IllegalStateException> {
                    repository.toggleCommentLike(aid = AID, rpid = RPID, like = false, preferApiType = ApiType.App)
                }

            assertThat(exception.message).isEqualTo("评论点赞失败")
        }

    // ------------------------------------------------------------------
    // getCommentsByCursor：置顶来源标注
    // ------------------------------------------------------------------

    @Test
    fun `cursor channel tags each top reply with its source`() =
        runTest {
            // data.top 下的三个 key 各代表一种置顶来源，UI 要显示不同角标。
            // 评论对象本身没有置顶字段，只能靠解析方按来源 key 打标。
            val data =
                json.parseToJsonElement(
                    buildJsonObject {
                        put(
                            "cursor",
                            buildJsonObject {
                                put("is_end", true)
                                put("all_count", 3L)
                            },
                        )
                        put("replies", buildJsonArray { })
                        put(
                            "top",
                            buildJsonObject {
                                put("admin", topCommentJson(rpid = 1L, msg = "管理员置顶"))
                                put("upper", topCommentJson(rpid = 2L, msg = "UP主置顶"))
                                put("vote", topCommentJson(rpid = 3L, msg = "投票置顶"))
                            },
                        )
                    },
                ).jsonObject
            coEvery { BiliHttpApi.getVideoCommentsByCursor(any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = data)

            val result = repository.getCommentsByCursor(aid = AID, mode = 3)

            // 顺序固定为 管理员 → UP 主 → 投票
            assertThat(result.topReplies.map { it.topType })
                .containsExactly(CommentTopType.Admin, CommentTopType.Upper, CommentTopType.Vote)
                .inOrder()
            assertThat(result.topReplies.map { it.rpid }).containsExactly(1L, 2L, 3L).inOrder()
        }

    @Test
    fun `cursor channel keeps only first source when same rpid is topped twice`() =
        runTest {
            // 同一条评论既被管理员也被 UP 主置顶时应只出现一次，且以管理员优先
            val data =
                json.parseToJsonElement(
                    buildJsonObject {
                        put(
                            "cursor",
                            buildJsonObject {
                                put("is_end", true)
                                put("all_count", 1L)
                            },
                        )
                        put("replies", buildJsonArray { })
                        put(
                            "top",
                            buildJsonObject {
                                put("admin", topCommentJson(rpid = 7L, msg = "同一条"))
                                put("upper", topCommentJson(rpid = 7L, msg = "同一条"))
                            },
                        )
                    },
                ).jsonObject
            coEvery { BiliHttpApi.getVideoCommentsByCursor(any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = data)

            val result = repository.getCommentsByCursor(aid = AID, mode = 3)

            assertThat(result.topReplies).hasSize(1)
            assertThat(result.topReplies.single().topType).isEqualTo(CommentTopType.Admin)
        }

    @Test
    fun `normal comments have no top type`() =
        runTest {
            val data =
                json.parseToJsonElement(
                    buildJsonObject {
                        put(
                            "cursor",
                            buildJsonObject {
                                put("is_end", true)
                                put("all_count", 1L)
                            },
                        )
                        put("replies", buildJsonArray { add(topCommentJson(rpid = 9L, msg = "普通")) })
                    },
                ).jsonObject
            coEvery { BiliHttpApi.getVideoCommentsByCursor(any(), any(), any(), any()) } returns
                BiliResponse(code = 0, message = "", data = data)

            val result = repository.getCommentsByCursor(aid = AID, mode = 3)

            assertThat(result.comments).hasSize(1)
            assertThat(result.comments.single().topType).isNull()
        }

    /** 构造一条可用于 data.top 的评论对象。 */
    private fun topCommentJson(
        rpid: Long,
        msg: String,
    ) = buildJsonObject {
        put("rpid", rpid)
        put("oid", AID)
        put("mid", 5000L + rpid)
        put("ctime", 1234567890L)
        put("like", 1L)
        put(
            "member",
            buildJsonObject {
                put("mid", 5000L + rpid)
                put("uname", "置顶用户$rpid")
                put("avatar", "")
            },
        )
        put("content", buildJsonObject { put("message", msg) })
    }

    // ------------------------------------------------------------------
    // 测试夹具
    // ------------------------------------------------------------------

    private fun repliesJson(
        responses: Int = 0,
        total: Int = 0,
        count: Long? = null,
        num: Int? = null,
        size: Int? = null,
        rpids: List<Long>? = null,
        upperMid: Long? = null,
        previews: Map<Long, List<Long>> = emptyMap(),
    ): JsonObject {
        val ids = rpids ?: List(responses) { RPID + it }
        return json
            .parseToJsonElement(
                buildJsonObject {
                    upperMid?.let { mid ->
                        put(
                            "upper",
                            buildJsonObject { put("mid", mid) },
                        )
                    }
                    put(
                        "replies",
                        buildJsonArray {
                            ids.forEachIndexed { i, rpid ->
                                add(
                                    buildJsonObject {
                                        put("rpid", rpid)
                                        put("oid", AID)
                                        put("ctime", 1234567890L)
                                        put("like", 10L)
                                        put("rcount", 3)
                                        put(
                                            "member",
                                            buildJsonObject {
                                                put("mid", (1000 + i).toLong())
                                                put("uname", "用户${i + 1}")
                                                put("avatar", "http://test/$i")
                                                put(
                                                    "level_info",
                                                    buildJsonObject { put("current_level", 3) },
                                                )
                                            },
                                        )
                                        put(
                                            "content",
                                            buildJsonObject { put("message", "评论${i + 1}") },
                                        )
                                        previews[rpid]?.let { nestedIds ->
                                            put(
                                                "replies",
                                                buildJsonArray {
                                                    nestedIds.forEach { nestedRpid ->
                                                        add(
                                                            buildJsonObject {
                                                                put("rpid", nestedRpid)
                                                                put("mid", 2000L + nestedRpid)
                                                                put(
                                                                    "content",
                                                                    buildJsonObject {
                                                                        put("message", "预览$nestedRpid")
                                                                    },
                                                                )
                                                            },
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                    },
                                )
                            }
                        },
                    )
                    put(
                        "page",
                        buildJsonObject {
                            put("acount", total.toLong())
                            count?.let { put("count", it) }
                            num?.let { put("num", it) }
                            size?.let { put("size", it) }
                        },
                    )
                }.toString(),
            ).jsonObject
    }
}
