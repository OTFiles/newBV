package dev.otfiles.newbv.biliapi.entity.ugc.region

import dev.otfiles.newbv.biliapi.entity.ugc.UgcItem

@Deprecated("User region v2 instead")
data class UgcRegionListData(
    val items: List<UgcItem>,
    val next: UgcRegionPage,
) {
    companion object {
        fun fromRegionDynamicList(
            data: dev.otfiles.newbv.biliapi.http.entity.region.RegionDynamicList,
        ): UgcRegionListData =
            UgcRegionListData(
                items = data.new.map { UgcItem.fromRegionDynamicListItem(it) },
                next = UgcRegionPage(data.cBottom),
            )
    }
}
