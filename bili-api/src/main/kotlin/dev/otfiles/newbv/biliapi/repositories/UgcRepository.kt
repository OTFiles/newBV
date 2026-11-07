package dev.otfiles.newbv.biliapi.repositories

import dev.otfiles.newbv.biliapi.entity.ugc.UgcType
import dev.otfiles.newbv.biliapi.entity.ugc.UgcTypeV2
import dev.otfiles.newbv.biliapi.entity.ugc.region.UgcFeedData
import dev.otfiles.newbv.biliapi.entity.ugc.region.UgcFeedPage
import dev.otfiles.newbv.biliapi.entity.ugc.region.UgcRegionData
import dev.otfiles.newbv.biliapi.entity.ugc.region.UgcRegionListData
import dev.otfiles.newbv.biliapi.http.BiliHttpApi

class UgcRepository(
    private val authRepository: AuthRepository,
) {
    @Deprecated("User getRegionFeedRcmd instead")
    suspend fun getRegionData(ugcType: UgcType): UgcRegionData {
        val responseData =
            BiliHttpApi
                .getRegionDynamic(
                    rid = ugcType.rid,
                    accessKey = authRepository.accessToken ?: "",
                ).getResponseData()
        val data = UgcRegionData.fromRegionDynamic(responseData)
        return data
    }

    @Deprecated("User getRegionFeedRcmd instead")
    suspend fun getRegionMoreData(ugcType: UgcType): UgcRegionListData {
        val responseData =
            BiliHttpApi
                .getRegionDynamicList(
                    rid = ugcType.rid,
                    accessKey = authRepository.accessToken ?: "",
                ).getResponseData()
        val data = UgcRegionListData.fromRegionDynamicList(responseData)
        return data
    }

    suspend fun getRegionFeedRcmd(
        ugcType: UgcTypeV2,
        page: UgcFeedPage,
    ): UgcFeedData {
        val responseData =
            BiliHttpApi
                .getRegionFeedRcmd(
                    displayId = page.nextPage,
                    fromRegion = ugcType.tid,
                ).getResponseData()
        val ugcFeedData = UgcFeedData.fromRegionFeedRcmd(responseData)
        ugcFeedData.nextPage = UgcFeedPage(page.nextPage + 1)
        return ugcFeedData
    }
}
