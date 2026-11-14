package dev.otfiles.newbv.biliapi.repositories

import bilibili.main.community.reply.v1.ReplyGrpcKt
import bilibili.main.community.reply.v1.ReplyInfo
import bilibili.main.community.reply.v1.cursorReq
import bilibili.main.community.reply.v1.detailListReq
import bilibili.main.community.reply.v1.mainListReq
import dev.otfiles.newbv.biliapi.entity.ApiType
import dev.otfiles.newbv.biliapi.entity.comment.Comment
import dev.otfiles.newbv.biliapi.entity.comment.CommentPage
import dev.otfiles.newbv.biliapi.entity.comment.CommentTopType
import dev.otfiles.newbv.biliapi.http.BiliHttpApi
import dev.otfiles.newbv.biliapi.http.entity.comment.CommentAddResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 视频评论仓库。
 *
 * 统一封装主评论、楼中楼和评论点赞操作。Web 请求使用 Cookie，App 请求使用
 * access_key；UI 层不直接依赖 B 站 JSON 结构。
 *
 * @param authRepository 当前登录凭证仓库
 */
class CommentRepository(
    private val authRepository: AuthRepository,
    private val channelRepository: ChannelRepository,
) {
    private val replyStub
        get() =
            runCatching {
                ReplyGrpcKt.ReplyCoroutineStub(channelRepository.requireDefaultChannel())
            }.getOrNull()

    /**
     * 获取主评论分页。
     *
     * 对应文档：docs/bilibili-API-collect-master/docs/comment/list.md（翻页加载 / 懒加载两节）
     *
     * @param aid 视频 AV 号
     * @param sort 排序方式，0 为时间，1 为热度，2 为回复数
     * @param page 页码，从 1 开始（Web 通道使用）
     * @param nextCursor App gRPC 通道的下一页游标，首次请求传 null
     * @param preferApiType 首选接口类型
     */
    suspend fun getComments(
        aid: Long,
        sort: Int = 1,
        page: Int = 1,
        nextCursor: Long? = null,
        preferApiType: ApiType,
    ): CommentPage =
        when (preferApiType) {
            ApiType.Web -> {
                val data =
                    BiliHttpApi.getVideoComments(aid, sort, page, accessKey = null).getResponseData()
                parsePage(data, aid = aid, page = page, defaultType = 1, isThread = false)
            }

            ApiType.App -> {
                val reply =
                    replyStub?.mainList(
                        mainListReq {
                            oid = aid
                            type = 1
                            cursor =
                                cursorReq {
                                    // gRPC 使用服务端返回的 next cursor 翻页，不能用本地页码代替
                                    next = nextCursor ?: 0L
                                    mode =
                                        if (sort == 0) {
                                            bilibili.main.community.reply.v1.Mode.MAIN_LIST_TIME
                                        } else {
                                            bilibili.main.community.reply.v1.Mode.MAIN_LIST_HOT
                                        }
                                }
                        },
                    ) ?: throw IllegalStateException("App gRPC reply stub is not initialized")
                CommentPage(
                    comments =
                        reply.repliesList
                            .map { Comment.fromGrpc(it, aid, upperMid = reply.subjectControl.upMid) }
                            .filter { it.rpid > 0L }
                            .distinctBy { it.rpid },
                    page = page,
                    total = 0,
                    hasMore = !reply.cursor.isEnd,
                    nextCursor = reply.cursor.next.takeIf { !reply.cursor.isEnd },
                    replyPreviews = parseGrpcReplyPreviews(reply.repliesList, aid, reply.subjectControl.upMid),
                )
            }
        }

    /**
     * 获取主评论分页（懒加载通道，游标翻页）。
     *
     * 对应文档：docs/bilibili-API-collect-master/docs/comment/list.md（「获取评论区明细_懒加载」一节）
     *
     * 相比 [getComments] 的翻页通道，本方法额外解析热评、置顶评论与可用排序方式。
     * 调用方（ViewModel）在本方法抛异常时应自行降级到 [getComments]，本方法不做回退，
     * 以便调用方能区分「通道失败」与「确实没有评论」。
     *
     * @param aid 视频 AV 号
     * @param mode 排序方式：0|3 仅热度 / 1 热度+时间 / 2 仅时间
     * @param paginationOffset 上一页的 `next_offset` 原样回传，第一页传 null
     * @throws IllegalStateException 当 B 站返回失败状态时抛出
     */
    suspend fun getCommentsByCursor(
        aid: Long,
        mode: Int = 3,
        paginationOffset: String? = null,
    ): CommentPage {
        val data =
            BiliHttpApi
                .getVideoCommentsByCursor(aid = aid, mode = mode, paginationOffset = paginationOffset)
                .getResponseData()
        val cursor = data["cursor"] as? JsonObject
        val upperMid = (data["upper"] as? JsonObject)?.longValue("mid") ?: 0L
        val repliesJson = data["replies"] as? JsonArray
        val comments =
            repliesJson
                ?.mapNotNull { it as? JsonObject }
                ?.map { Comment.fromJson(it, oid = aid, defaultType = 1, upperMid = upperMid) }
                .orEmpty()
                .filter { it.rpid > 0L }
                .distinctBy { it.rpid }

        // 热评与置顶都是独立数组，条目结构与 replies 相同
        fun parseList(key: String): List<Comment> =
            (data[key] as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                ?.map { Comment.fromJson(it, oid = aid, defaultType = 1, upperMid = upperMid) }
                .orEmpty()
                .filter { it.rpid > 0L }
                .distinctBy { it.rpid }

        // 置顶：data.top.{admin,upper,vote}，按「管理员 → UP 主 → 投票」顺序拼。
        // 每个来源要打上对应的 topType：评论对象本身没有置顶字段，
        // 只能由解析方按它来自哪个 key 来标注，UI 才能显示正确的角标。
        val topObject = data["top"] as? JsonObject
        val topSources =
            listOf(
                "admin" to CommentTopType.Admin,
                "upper" to CommentTopType.Upper,
                "vote" to CommentTopType.Vote,
            )
        val topReplies =
            topSources
                .mapNotNull { (key, type) ->
                    (topObject?.get(key) as? JsonObject)?.let { obj ->
                        // 同一个 rpid 可能同时被多个来源置顶，取先出现的（管理员优先）
                        Comment
                            .fromJson(obj, oid = aid, defaultType = 1, upperMid = upperMid)
                            .copy(topType = type)
                    }
                }.filter { it.rpid > 0L }
                .distinctBy { it.rpid }

        val supportedModes =
            (cursor?.get("support_mode") as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.intOrNull }
                .orEmpty()

        val total = cursor?.longValue("all_count")?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0
        val isEnd = cursor?.booleanValue("is_end") ?: true
        val nextOffset =
            ((cursor?.get("pagination_reply") as? JsonObject)?.get("next_offset"))
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf { it.isNotBlank() && it != "null" }

        return CommentPage(
            comments = comments,
            // 游标通道没有页码概念，固定为 1，避免 UI 依赖页码时出现 0
            page = 1,
            total = total,
            // 有下一页偏移且服务端未标记结束才继续翻页
            hasMore = !isEnd && nextOffset != null,
            paginationOffset = nextOffset,
            hots = parseList("hots"),
            topReplies = topReplies,
            supportedModes = supportedModes,
        )
    }

    /**
     * 获取根评论下的楼中楼分页。
     *
     * 对应文档：docs/bilibili-API-collect-master/docs/comment/list.md（「获取指定评论的回复」一节）
     *
     * @param aid 视频 AV 号
     * @param rootRpid 根评论 ID
     * @param page 页码，从 1 开始（Web 通道使用）
     * @param nextCursor App gRPC 通道的下一页游标，首次请求传 null
     * @param preferApiType 首选接口类型
     */
    suspend fun getReplies(
        aid: Long,
        rootRpid: Long,
        page: Int = 1,
        nextCursor: Long? = null,
        preferApiType: ApiType,
    ): CommentPage =
        when (preferApiType) {
            ApiType.Web -> {
                val data =
                    BiliHttpApi
                        .getVideoCommentReplies(
                            aid = aid,
                            rootRpid = rootRpid,
                            page = page,
                            accessKey = null,
                        ).getResponseData()
                parsePage(data, aid = aid, page = page, defaultType = 1, isThread = true)
            }

            ApiType.App -> {
                val reply =
                    replyStub?.detailList(
                        detailListReq {
                            oid = aid
                            type = 1
                            root = rootRpid
                            rpid = rootRpid
                            // gRPC 使用服务端返回的 next cursor 翻页，不能用本地页码代替
                            cursor = cursorReq { next = nextCursor ?: 0L }
                        },
                    ) ?: throw IllegalStateException("App gRPC reply stub is not initialized")
                val root = reply.root
                CommentPage(
                    comments =
                        root.repliesList
                            .map { Comment.fromGrpc(it, aid, upperMid = reply.subjectControl.upMid) }
                            .filter { it.rpid > 0L && it.rpid != rootRpid }
                            .distinctBy { it.rpid },
                    page = page,
                    total = 0,
                    hasMore = !reply.cursor.isEnd,
                    nextCursor = reply.cursor.next.takeIf { !reply.cursor.isEnd },
                )
            }
        }

    /**
     * 点赞或取消点赞评论。
     *
     * @param aid 视频 AV 号
     * @param rpid 评论 ID
     * @param like 是否点赞
     * @param preferApiType 首选接口类型
     * @throws IllegalStateException 当 B 站返回失败状态时抛出
     */
    suspend fun toggleCommentLike(
        aid: Long,
        rpid: Long,
        like: Boolean,
        preferApiType: ApiType,
    ) {
        val successMessage =
            BiliHttpApi.updateCommentLiked(
                aid = aid,
                rpid = rpid,
                like = like,
                csrf = authRepository.biliJct.takeIf { preferApiType == ApiType.Web },
                accessKey = authRepository.accessToken.takeIf { preferApiType == ApiType.App },
            )
        check(successMessage.first) { successMessage.second.ifBlank { "评论点赞失败" } }
    }

    /**
     * 发表评论或回复评论。
     *
     * 对应文档：docs/bilibili-API-collect-master/docs/comment/action.md（「发表评论」一节）
     *
     * 只允许 Web 通道：发表评论需要 `bili_jct`（CSRF），而 App access_key 通道
     * 在部分账号上会被风控拦截。另外，发表前先在本地做一次参数校验，
     * 让空内容、超长、未登录这类问题在发请求前就得到明确的提示。
     *
     * @param aid 视频 AV 号
     * @param message 评论正文（会自动去除首尾空白）
     * @param rootRpid 根评论 ID，回复楼中楼时必填
     * @param parentRpid 直接父评论 ID，回复楼中楼时必填
     * @return 发表结果，包含 B 站原始返回码与提示
     * @throws IllegalArgumentException 内容为空、超长或未登录时抛出
     */
    suspend fun postComment(
        aid: Long,
        message: String,
        rootRpid: Long = 0L,
        parentRpid: Long = 0L,
    ): CommentAddResult {
        val content = message.trim()
        require(aid > 0L) { "视频 ID 无效" }
        require(content.isNotEmpty()) { "评论内容不能为空" }
        require(content.length <= MAX_COMMENT_LENGTH) { "评论最多 $MAX_COMMENT_LENGTH 个字" }

        val csrf = authRepository.biliJct?.takeIf { it.isNotBlank() }
        require(csrf != null) { "请先登录后再发表评论" }

        return BiliHttpApi.addVideoComment(
            aid = aid,
            message = content,
            rootRpid = rootRpid,
            parentRpid = parentRpid,
            csrf = csrf,
        )
    }

    /**
     * 解析 Web 通道的评论 JSON。
     *
     * 主评论与楼中楼共用 `replies` 数组，但总数与页码字段不同：
     * - 主评论：`page.acount` 为总数，`page.count` 为兜底
     * - 楼中楼：`page.count` 为该根评论的二级评论总数
     *
     * @param isThread 是否为楼中楼分页
     */
    private fun parsePage(
        data: JsonObject,
        aid: Long,
        page: Int,
        defaultType: Int,
        isThread: Boolean,
    ): CommentPage {
        val pageObject = data["page"] as? JsonObject
        val repliesJson = data["replies"] as? JsonArray
        val rawCount = repliesJson?.count { it is JsonObject } ?: 0
        // UP 主 mid 用于标记「UP主」评论；主评论与楼中楼响应均携带 data.upper.mid
        val upperMid = (data["upper"] as? JsonObject)?.longValue("mid") ?: 0L
        val rawComments =
            repliesJson
                ?.mapNotNull { it as? JsonObject }
                ?.map { obj ->
                    obj to
                        Comment.fromJson(
                            obj,
                            oid = aid,
                            defaultType = defaultType,
                            upperMid = upperMid,
                        )
                }.orEmpty()
        val comments =
            rawComments
                .map { it.second }
                .filter { it.rpid > 0L }
                .distinctBy { it.rpid }
        // 主评论接口自带一层楼中楼预览；楼中楼分页接口的条目没有可用的嵌套预览
        val replyPreviews = if (isThread) emptyMap() else parseReplyPreviews(rawComments, aid, defaultType, upperMid)
        val total =
            (
                if (isThread) {
                    pageObject?.longValue("count")
                } else {
                    pageObject?.longValue("acount") ?: pageObject?.longValue("count")
                }
            ) ?: 0L
        val pageNum = pageObject?.longValue("num")?.toInt()?.takeIf { it > 0 } ?: page
        val pageSize = pageObject?.longValue("size")?.toInt()?.takeIf { it > 0 } ?: DEFAULT_PAGE_SIZE
        return CommentPage(
            comments = comments,
            page = pageNum,
            total = total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            hasMore = rawCount > 0 && (total <= 0L || pageNum.toLong() * pageSize < total),
            replyPreviews = replyPreviews,
        )
    }

    /**
     * 解析主评论条目内嵌的楼中楼预览（接口保证只嵌套一层）。
     *
     * 每个根评论最多取前 3 条（与 B 站 Web 端一致），过滤非法 rpid 与根评论自身，
     * 避免收起态展示重复或错误数据。
     */
    private fun parseReplyPreviews(
        rawComments: List<Pair<JsonObject, Comment>>,
        aid: Long,
        defaultType: Int,
        upperMid: Long,
    ): Map<Long, List<Comment>> =
        buildMap {
            rawComments.forEach { (obj, root) ->
                if (root.rpid <= 0L) return@forEach
                val previews =
                    (obj["replies"] as? JsonArray)
                        ?.mapNotNull { it as? JsonObject }
                        ?.map { preview ->
                            Comment.fromJson(
                                preview,
                                oid = aid,
                                defaultType = defaultType,
                                upperMid = upperMid,
                            )
                        }.orEmpty()
                        .filter { it.rpid > 0L && it.rpid != root.rpid }
                        .distinctBy { it.rpid }
                        .take(MAX_REPLY_PREVIEW_COUNT)
                if (previews.isNotEmpty()) put(root.rpid, previews)
            }
        }

    /** 解析 gRPC 主评论列表内嵌的楼中楼预览（`ReplyInfo.replies`，仅一层）。 */
    private fun parseGrpcReplyPreviews(
        roots: List<ReplyInfo>,
        aid: Long,
        upperMid: Long,
    ): Map<Long, List<Comment>> =
        buildMap {
            roots.forEach { info ->
                val root = Comment.fromGrpc(info, aid, upperMid = upperMid)
                if (root.rpid <= 0L) return@forEach
                val previews =
                    info.repliesList
                        .map { Comment.fromGrpc(it, aid, upperMid = upperMid) }
                        .filter { it.rpid > 0L && it.rpid != root.rpid }
                        .distinctBy { it.rpid }
                        .take(MAX_REPLY_PREVIEW_COUNT)
                if (previews.isNotEmpty()) put(root.rpid, previews)
            }
        }

    private companion object {
        /** 服务端未返回 `page.size` 时的默认每页数量。 */
        const val DEFAULT_PAGE_SIZE = 20

        /**
         * 楼中楼预览条数上限（与 B 站 Web 端一致）。
         *
         * Web 与 gRPC 两条通道都在解析时截断，UI 直接展示即可，无需再次限制。
         */
        const val MAX_REPLY_PREVIEW_COUNT = 3

        /** B 站评论正文的最大长度（服务端限制为 1000 字）。 */
        const val MAX_COMMENT_LENGTH = 1000
    }
}

private fun JsonObject.longValue(name: String): Long? = this[name]?.toString()?.trim('"')?.toLongOrNull()

private fun JsonObject.booleanValue(name: String): Boolean? =
    when (this[name]?.jsonPrimitive?.contentOrNull) {
        "true" -> true
        "false" -> false
        else -> null
    }
