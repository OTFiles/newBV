package dev.otfiles.newbv.app.ui.component.comment

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.otfiles.newbv.biliapi.entity.comment.Comment
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.tv.material3.MaterialTheme as TvMaterialTheme

/**
 * 评论卡片按钮的焦点可见性插桩测试。
 *
 * 回归背景：`DialogActionButton` 曾只配置了非聚焦态的颜色，
 * `ClickableSurfaceDefaults.colors(...)` 没有给 `focusedContainerColor`，
 * 导致方向键移动焦点时**画面上完全看不出焦点在哪个按钮上**。
 * 用户会误以为焦点丢失，或以为按键没生效（要按确认键才“突然”出现）。
 *
 * 这里验证的是「按钮可获得焦点」这一事实。颜色对比由
 * [CommentFocusColorsTest] 之外的代码审查保证：
 * 组件必须显式传入 focusedContainerColor 与 containerColor 不同的值。
 */
@RunWith(AndroidJUnit4::class)
class CommentActionButtonFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun fakeComment() =
        Comment(
            rpid = 1L,
            oid = 1L,
            type = 1,
            mid = 100L,
            rootRpid = 0L,
            parentRpid = 0L,
            userName = "测试用户",
            avatar = "",
            level = 5,
            message = "评论内容",
            pictures = emptyList(),
            ctime = 0L,
            likeCount = 3L,
            replyCount = 2,
            isLiked = false,
            isUp = false,
        )

    @Test
    fun commentCardButtonsCanReceiveFocus() {
        composeRule.setContent {
            val likeFocus = remember { FocusRequester() }
            TvMaterialTheme {
                Box(modifier = Modifier.fillMaxSize().width(320.dp)) {
                    CommentCard(
                        comment = fakeComment(),
                        compact = false,
                        isLiking = false,
                        showReplyButton = true,
                        isExpanded = false,
                        isLoadingReplies = false,
                        isTextExpanded = false,
                        onToggleTextExpand = {},
                        onToggleReplies = {},
                        onToggleLike = {},
                        onReply = {},
                        onImageClick = { _, _ -> },
                        previewReplyCount = 0,
                        firstActionFocusRequester = likeFocus,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // 把焦点显式移到卡片第一个操作按钮上；可聚焦即说明该节点进入了焦点链。
        composeRule.onNodeWithText("赞 3").requestFocus()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("赞 3").assertIsFocused()
    }
}
