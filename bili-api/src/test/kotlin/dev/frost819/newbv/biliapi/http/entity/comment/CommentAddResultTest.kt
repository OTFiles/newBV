package dev.frost819.newbv.biliapi.http.entity.comment

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [CommentAddResult] 的错误码映射测试。
 *
 * 错误码含义参考 `docs/bilibili-API-collect-master/docs/comment/action.md`。
 * 这里锁定几个会直接影响用户行为的分支：关闭/拉黑应该放弃重试，频率限制应该提示稍后，
 * 验证码应该引导去官方 App，未知码不能返回空字符串。
 */
class CommentAddResultTest {
    private fun result(
        code: Int,
        message: String = "",
    ) = CommentAddResult(code = code, message = message, rpid = 0L)

    @Test
    fun `success has empty user message`() {
        assertTrue(result(0).isSuccess)
        assertEquals("", result(0).userMessage)
    }

    @Test
    fun `not logged in tells user to log in`() {
        assertTrue(result(-101).userMessage.contains("登录"))
    }

    @Test
    fun `comment area closed is reported as closed`() {
        // 12002 与 12052 文档中都是「评论区已关闭」
        assertTrue(result(12002).userMessage.contains("关闭"))
        assertTrue(result(12052).userMessage.contains("关闭"))
    }

    @Test
    fun `blacklisted by uploader is explained clearly`() {
        assertTrue(result(12035).userMessage.contains("黑名单"))
    }

    @Test
    fun `rate limited asks user to retry later`() {
        assertTrue(result(-509).userMessage.contains("太快"))
    }

    @Test
    fun `captcha required guides user to official app`() {
        // 这是唯一需要用户去别的地方操作的错误码，提示必须可执行
        val message = result(12015).userMessage
        assertTrue(message.contains("验证码"))
        assertTrue(message.contains("App"))
    }

    @Test
    fun `deleted comment is reported as gone`() {
        assertTrue(result(12006).userMessage.contains("不存在"))
        assertTrue(result(-404).userMessage.contains("不存在"))
    }

    @Test
    fun `unknown code falls back to server message`() {
        assertEquals("服务端自定义提示", result(99999, "服务端自定义提示").userMessage)
    }

    @Test
    fun `unknown code without message still yields non empty hint`() {
        // 不能给用户弹一个空 toast
        val message = result(99999, "").userMessage
        assertTrue(message.isNotEmpty())
        assertTrue(message.contains("99999"))
    }

    @Test
    fun `blank server message does not leak whitespace`() {
        assertTrue(result(99999, "   ").userMessage.trim().isNotEmpty())
    }
}
