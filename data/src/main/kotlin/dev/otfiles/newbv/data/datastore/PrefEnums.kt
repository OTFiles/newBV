package dev.otfiles.newbv.data.datastore

/*
 * 偏好设置相关枚举集合。
 *
 * 这些枚举用于 DataStore 持久化，仅保留持久化所需的 code/ordinal 与反序列化方法。
 * 显示名称（getDisplayName）由 app 层扩展，避免 data 模块依赖 Android 资源。
 *
 * 当 bili-api / player / danmaku 模块迁移完成后，部分枚举（ApiType / Resolution /
 * VideoCodec / Audio）可迁移至对应模块，data 层改为引用。
 */

/**
 * 接口类型。
 *
 * 控制使用 Web HTTP API 还是 App gRPC API。
 * 通过 ordinal 持久化，反序列化时越界回退到 [Web]。
 */
enum class ApiType {
    /** Web HTTP API（需 WBI 签名）。 */
    Web,

    /** App gRPC API（需 App 签名）。 */
    App,

    ;

    companion object {
        /** 从序号安全解析，越界返回 [Web]。 */
        fun fromOrdinal(ordinal: Int): ApiType = entries.getOrElse(ordinal) { Web }
    }
}

// ===== 播放器 - 视频 =====

/**
 * 视频画质。
 *
 * code 对应 B 站 `qn` 参数。通过 [code] 持久化，反序列化时未知 code 回退到 [R1080P]。
 *
 * @property code B 站画质标识（qn）。
 */
enum class Resolution(
    val code: Int,
) {
    R240P(6),
    R360P(16),
    R480P(32),
    R720P(64),
    R720P60(74),
    R1080P(80),
    R1080PPlus(112),
    R1080P60(116),
    R4K(120),
    RHdr(125),
    RDolby(126),
    R8K(127),
    ;

    companion object {
        /** 从 code 安全解析，未知 code 返回 [R1080P]。 */
        fun fromCode(code: Int): Resolution = entries.find { it.code == code } ?: R1080P
    }
}

/**
 * 视频编码。
 *
 * 通过 ordinal 持久化，反序列化时越界回退到 [AVC]。
 *
 * @property prefix 主要编码前缀，用于显示和 API 参数。
 * @property codecId B 站编码 ID。
 * @property prefixes 所有匹配前缀，用于从 codec string 匹配（如 HEVC 匹配 `hev1.*` 和 `hvc1.*`）。
 */
enum class VideoCodec(
    val prefix: String,
    val codecId: Int,
    val prefixes: List<String> = listOf(prefix),
) {
    AVC("avc1", 7),
    HEVC("hev1", 12, listOf("hev1", "hvc1")),
    AV1("av01", 13),
    DVH1("dvh1", 0),
    ;

    companion object {
        /** 从 ordinal 安全解析，越界返回 [AVC]。 */
        fun fromCode(ordinal: Int): VideoCodec = entries.find { it.ordinal == ordinal } ?: AVC

        /** 从 codec string（如 `avc1.640028`、`hvc1.1.6.L153.90`）匹配编码，无匹配返回 null。 */
        fun fromCodecString(codec: String): VideoCodec? =
            runCatching {
                entries.forEach { if (it.prefixes.any { p -> codec.startsWith(p) }) return it }
                null
            }.getOrNull()

        /** 从 B 站 codecId 匹配，无匹配返回 [AVC]。 */
        fun fromCodecId(codecId: Int): VideoCodec = entries.find { it.codecId == codecId } ?: AVC
    }
}

/**
 * 播放结束动作。
 *
 * 通过 [code] 持久化，反序列化时未知 code 回退到 [PlayNext]。
 *
 * @property code 动作标识。
 */
enum class ActionAfterPlay(
    val code: Int,
) {
    Pause(0),
    PlayNext(1),
    Exit(2),
    PlayRelated(3),
    ;

    companion object {
        /** 从 code 安全解析，未知 code 返回 [PlayNext]。 */
        fun fromCode(code: Int): ActionAfterPlay = entries.find { it.code == code } ?: PlayNext
    }
}

// ===== 播放器 - 音频 =====

/**
 * 音频编码。
 *
 * code 对应 B 站音频 ID。通过 [code] 持久化，反序列化时未知 code 回退到 [A192K]。
 *
 * @property code B 站音频标识。
 */
enum class Audio(
    val code: Int,
) {
    A64K(30216),
    A132K(30232),
    A192K(30280),
    ADolbyAtoms(30250),
    AHiRes(30251),
    ;

    companion object {
        /** 从 code 安全解析，未知 code 返回 [A192K]。 */
        fun fromCode(code: Int): Audio = entries.find { it.code == code } ?: A192K
    }
}

// ===== 播放器 - 弹幕 =====

/**
 * 弹幕类型。
 *
 * 通过 ordinal 持久化，用于多选过滤。
 */
enum class DanmakuType {
    /** 全部（元类型，选中时表示显示所有）。 */
    All,

    /** 顶部弹幕。 */
    Top,

    /** 滚动弹幕。 */
    Rolling,

    /** 底部弹幕。 */
    Bottom,
}

// ===== 播放器 - 界面 =====

/**
 * 播放速度。
 *
 * 通过 [code] 持久化，反序列化时未知 code 回退到 [X1]。
 *
 * @property code 速度标识。
 * @property speed 实际倍速值。
 */
enum class PlaySpeed(
    val code: Int,
    val speed: Float,
) {
    X0_5(0, 0.5f),
    X1(1, 1f),
    X1_25(2, 1.25f),
    X1_5(3, 1.5f),
    X2(4, 2f),
    ;

    companion object {
        /** 从 code 安全解析，未知 code 返回 [X1]。 */
        fun fromCode(code: Int): PlaySpeed = entries.find { it.code == code } ?: X1

        /** 从倍速值匹配，无精确匹配返回 [X1]。 */
        fun fromSpeed(speed: Float): PlaySpeed = entries.find { it.speed == speed } ?: X1
    }
}

// ===== 应用界面 =====

/**
 * 左侧导航项（启动页）。
 *
 * 通过 ordinal 持久化，反序列化时越界回退到 [Home]。
 * 相对原版新增 [Live] 项（PRD 7.2）。
 */
enum class LeftNaviItem : java.io.Serializable {
    Search,
    Personal,
    Home,
    UGC,
    PGC,
    Live,
    ;

    companion object {
        /** 从序号安全解析，越界返回 [Home]。 */
        fun fromOrdinal(ordinal: Int): LeftNaviItem = entries.getOrElse(ordinal) { Home }
    }
}

/**
 * 首页顶部 Tab。
 *
 * 通过 [code] 持久化，反序列化时未知 code 回退到 [Dynamics]。
 *
 * @property code Tab 标识。
 */
enum class HomeTopNavItem(
    val code: Int,
) {
    Dynamics(0),
    Recommend(1),
    Popular(2),
    ;

    companion object {
        /** 从 code 安全解析，未知 code 返回 [Dynamics]。 */
        fun fromCode(code: Int): HomeTopNavItem = entries.find { it.code == code } ?: Dynamics
    }
}

/**
 * 个人页顶部 Tab。
 *
 * 通过 ordinal 持久化，反序列化时越界回退到 [ToView]。
 */
enum class PersonalTopNavItem {
    ToView,
    History,
    Favorite,
    FollowingSeason,
    ;

    companion object {
        /** 从序号安全解析，越界返回 [ToView]。 */
        fun fromOrdinal(ordinal: Int): PersonalTopNavItem = entries.getOrElse(ordinal) { ToView }
    }
}

/**
 * 主题模式。
 *
 * 通过 ordinal 持久化，反序列化时越界回退到 [FollowSystem]。
 *
 * 注意：core 模块已有同名 `ThemeMode` 枚举，此处在 data 模块独立定义以避免循环依赖。
 * app 层负责两者之间的映射。
 */
enum class ThemeMode {
    /** 跟随系统暗色模式。 */
    FollowSystem,

    /** 强制深色。 */
    Dark,

    /** 强制浅色。 */
    Light,

    ;

    companion object {
        /** 从序号安全解析，越界返回 [FollowSystem]。 */
        fun fromOrdinal(ordinal: Int): ThemeMode = entries.getOrElse(ordinal) { FollowSystem }
    }
}

// ===== 播放器 - 评论面板 =====

/**
 * 播放器评论面板的停靠位置。
 *
 * 通过 ordinal 持久化，反序列化时越界回退到 [Right]。
 *
 * 播放器画面是 16:9 的，左右两侧空间较窄、上下两侧空间更矮：
 * - [Right] / [Left]：竖向面板，适合看长评论，是默认值（与上游行为一致）
 * - [Top] / [Bottom]：横向面板，适合宽屏边缘阅读，但每屏能显示的评论行数少
 *
 * @property displayName 设置页显示名
 */
enum class CommentPanelPosition(
    val displayName: String,
) {
    Right("右侧"),
    Left("左侧"),
    Bottom("底部"),
    Top("顶部"),
    ;

    /** 是否为竖向停靠（左右），影响面板宽高比的计算方向。 */
    val isVertical: Boolean
        get() = this == Right || this == Left

    companion object {
        /** 从序号安全解析，越界返回 [Right]。 */
        fun fromOrdinal(ordinal: Int): CommentPanelPosition = entries.getOrElse(ordinal) { Right }
    }
}

/**
 * 播放器评论面板的占屏比例。
 *
 * 通过 ordinal 持久化，反序列化时越界回退到 [Medium]。
 *
 * 面板不会再压满整个屏幕：无论哪一档，都会保留至少一部分画面可见，
 * 这样「边看边刷评论」时视频不会被完全挡住。
 *
 * @property fraction 面板占据可用空间的比例（仅针对停靠方向上的尺寸）
 * @property displayName 设置页显示名
 */
enum class CommentPanelSize(
    val fraction: Float,
    val displayName: String,
) {
    Small(0.30f, "小（30%）"),
    Medium(0.42f, "中（42%）"),
    Large(0.55f, "大（55%）"),
    XLarge(0.68f, "特大（68%）"),
    ;

    companion object {
        /** 从序号安全解析，越界返回 [Medium]。 */
        fun fromOrdinal(ordinal: Int): CommentPanelSize = entries.getOrElse(ordinal) { Medium }
    }
}
