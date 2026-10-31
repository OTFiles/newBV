package dev.frost819.newbv.biliapi.http.entity.comment

/**
 * 发表评论的结果。
 *
 * 与 [dev.frost819.newbv.biliapi.http.entity.BiliResponse.getResponseData] 不同，
 * 这里保留原始 `code`：发表评论失败时服务端会给出「评论过于频繁」「评论被关闭」
 * 「UP 主已拉黑你」等不同原因，UI 需要把这些提示原样反馈给用户，
 * 而不是统一抛异常后只显示一句「发表失败」。
 *
 * @property code B 站业务返回码，0 表示成功
 * @property message 服务端提示信息
 * @property rpid 新评论 ID，成功但服务端未返回时为 0
 */
data class CommentAddResult(
    val code: Int,
    val message: String,
    val rpid: Long,
) {
    /** 是否发表成功。 */
    val isSuccess: Boolean get() = code == 0
}
