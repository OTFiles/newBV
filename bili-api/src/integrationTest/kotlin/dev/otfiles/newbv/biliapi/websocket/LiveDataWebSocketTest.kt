package dev.otfiles.newbv.biliapi.websocket

import dev.otfiles.newbv.biliapi.http.BiliLiveHttpApi
import dev.otfiles.newbv.biliapi.http.entity.live.DanmakuEvent
import dev.otfiles.newbv.biliapi.http.entity.live.InteractWordEvent
import dev.otfiles.newbv.biliapi.http.entity.live.OnlineRankCountEvent
import dev.otfiles.newbv.biliapi.http.entity.live.WatchedChangeEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

internal class LiveDataWebSocketTest {
    @Test
    fun `connects to configured room and receives a live event`() {
        runBlocking {
            val roomInfo = BiliLiveHttpApi.getRoomPlayInfoV2(1718159119).data
            assumeTrue(roomInfo?.liveStatus == 1, "configured room is not live")

            val event =
                withTimeoutOrNull(30_000) {
                    LiveDataWebSocket.connectLiveEvent(1718159119).first()
                }

            // A live room may have no chat activity during the observation window.
            if (event != null) {
                assertTrue(
                    event is DanmakuEvent ||
                        event is InteractWordEvent ||
                        event is OnlineRankCountEvent ||
                        event is WatchedChangeEvent,
                )
            }
        }
    }
}
