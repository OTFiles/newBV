package dev.otfiles.newbv.biliapi.repositories

import dev.otfiles.newbv.biliapi.entity.ApiType
import dev.otfiles.newbv.biliapi.http.BiliHttpApi

class LikeRepository(
    private val authRepository: AuthRepository,
) {
    suspend fun checkVideoLiked(
        aid: Long,
        preferApiType: ApiType,
        bvid: String? = null,
    ): Boolean =
        when (preferApiType) {
            ApiType.Web ->
                BiliHttpApi.checkVideoLiked(
                    avid = aid,
                    bvid = bvid,
                )

            ApiType.App ->
                BiliHttpApi.checkVideoLiked(
                    avid = aid,
                    bvid = bvid,
                    accessKey = authRepository.accessToken,
                )
        }

    suspend fun updateVideoLiked(
        aid: Long,
        like: Boolean,
        preferApiType: ApiType,
        bvid: String? = null,
    ) {
        val (success, message) =
            when (preferApiType) {
                ApiType.Web ->
                    BiliHttpApi.sendVideoLike(
                        avid = aid,
                        bvid = bvid,
                        like = like,
                        csrf = authRepository.biliJct ?: "",
                    )

                ApiType.App ->
                    BiliHttpApi.sendVideoLikeApp(
                        avid = aid,
                        like = like,
                        accessKey = authRepository.accessToken ?: "",
                    )
            }
        if (!success) {
            throw Exception(message)
        }
    }
}
