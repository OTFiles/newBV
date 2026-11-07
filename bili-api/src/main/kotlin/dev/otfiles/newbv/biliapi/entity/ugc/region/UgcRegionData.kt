package dev.otfiles.newbv.biliapi.entity.ugc.region

import dev.otfiles.newbv.biliapi.entity.CarouselData
import dev.otfiles.newbv.biliapi.entity.ugc.UgcItem

@Deprecated("User region v2 instead")
data class UgcRegionData(
    val carouselData: CarouselData?,
    val items: List<UgcItem>,
    val next: UgcRegionPage,
) {
    companion object {
        fun fromRegionDynamic(data: dev.otfiles.newbv.biliapi.http.entity.region.RegionDynamic): UgcRegionData =
            UgcRegionData(
                carouselData = data.banner?.let { CarouselData.fromUgcRegionDynamicBanner(it) },
                items = data.new.map { UgcItem.fromRegionDynamicListItem(it) },
                next = UgcRegionPage(data.cBottom),
            )
    }
}
