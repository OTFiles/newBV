package dev.otfiles.newbv.biliapi.entity.ugc.region

import dev.otfiles.newbv.biliapi.entity.ugc.UgcItem

data class UgcFeedData(
    var hasNext: Boolean,
    var nextPage: UgcFeedPage,
    var items: List<UgcItem> = emptyList(),
) {
    companion object {
        fun fromRegionFeedRcmd(data: dev.otfiles.newbv.biliapi.http.entity.region.RegionFeedRcmd): UgcFeedData =
            UgcFeedData(
                hasNext = data.archives.isNotEmpty(),
                nextPage = UgcFeedPage(),
                items = data.archives.map { UgcItem.fromRegionRcmdArchive(it) },
            )
    }
}
