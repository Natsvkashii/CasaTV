package it.casatv.tv.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Minimal 24dp Material-style vector icons to keep the TV APK lean and emoji-free. */
object CasaIcons {

    val Pause: ImageVector by lazy {
        icon("Pause") {
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 5f); verticalLineTo(19f); horizontalLineTo(10f); verticalLineTo(5f); close()
                moveTo(14f, 5f); verticalLineTo(19f); horizontalLineTo(18f); verticalLineTo(5f); close()
            }
        }
    }

    val Tv: ImageVector by lazy {
        icon("Tv") {
            path(fill = SolidColor(Color.White)) {
                moveTo(21f, 3f); horizontalLineTo(3f); curveTo(1.9f, 3f, 1f, 3.9f, 1f, 5f); verticalLineTo(17f)
                curveTo(1f, 18.1f, 1.9f, 19f, 3f, 19f); horizontalLineTo(8f); verticalLineTo(21f); horizontalLineTo(16f)
                verticalLineTo(19f); horizontalLineTo(21f); curveTo(22.1f, 19f, 23f, 18.1f, 23f, 17f); verticalLineTo(5f)
                curveTo(23f, 3.9f, 22.1f, 3f, 21f, 3f); close()
                moveTo(21f, 17f); horizontalLineTo(3f); verticalLineTo(5f); horizontalLineTo(21f); verticalLineTo(17f); close()
            }
        }
    }

    val VolumeUp: ImageVector by lazy {
        icon("VolumeUp") {
            path(fill = SolidColor(Color.White)) {
                moveTo(3f, 9f); verticalLineTo(15f); horizontalLineTo(7f); lineTo(12f, 20f); verticalLineTo(4f); lineTo(7f, 9f); horizontalLineTo(3f); close()
                moveTo(16.5f, 12f); curveTo(16.5f, 10.23f, 15.48f, 8.71f, 14f, 7.97f); verticalLineTo(16.02f)
                curveTo(15.48f, 15.29f, 16.5f, 13.77f, 16.5f, 12f); close()
                moveTo(14f, 3.23f); verticalLineTo(5.29f); curveTo(16.89f, 6.15f, 19f, 8.83f, 19f, 12f)
                reflectiveCurveTo(16.89f, 17.85f, 14f, 18.71f); verticalLineTo(20.77f)
                curveTo(18.01f, 19.86f, 21f, 16.28f, 21f, 12f); reflectiveCurveTo(18.01f, 4.14f, 14f, 3.23f); close()
            }
        }
    }

    val Subtitles: ImageVector by lazy {
        icon("Subtitles") {
            path(fill = SolidColor(Color.White)) {
                moveTo(20f, 4f); horizontalLineTo(4f); curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f); verticalLineTo(18f)
                curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f); horizontalLineTo(20f); curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
                verticalLineTo(6f); curveTo(22f, 4.9f, 21.1f, 4f, 20f, 4f); close()
                moveTo(20f, 18f); horizontalLineTo(4f); verticalLineTo(6f); horizontalLineTo(20f); verticalLineTo(18f); close()
                moveTo(6f, 10f); horizontalLineTo(8f); verticalLineTo(12f); horizontalLineTo(6f); close()
                moveTo(6f, 14f); horizontalLineTo(14f); verticalLineTo(16f); horizontalLineTo(6f); close()
                moveTo(16f, 14f); horizontalLineTo(18f); verticalLineTo(16f); horizontalLineTo(16f); close()
                moveTo(10f, 10f); horizontalLineTo(18f); verticalLineTo(12f); horizontalLineTo(10f); close()
            }
        }
    }

    val Tune: ImageVector by lazy {
        icon("Tune") {
            path(fill = SolidColor(Color.White)) {
                moveTo(3f, 17f); verticalLineTo(19f); horizontalLineTo(9f); verticalLineTo(17f); horizontalLineTo(3f); close()
                moveTo(3f, 5f); verticalLineTo(7f); horizontalLineTo(13f); verticalLineTo(5f); horizontalLineTo(3f); close()
                moveTo(13f, 21f); verticalLineTo(19f); horizontalLineTo(21f); verticalLineTo(17f); horizontalLineTo(13f); verticalLineTo(15f)
                horizontalLineTo(11f); verticalLineTo(21f); horizontalLineTo(13f); close()
                moveTo(7f, 9f); verticalLineTo(11f); horizontalLineTo(3f); verticalLineTo(13f); horizontalLineTo(7f); verticalLineTo(15f)
                horizontalLineTo(9f); verticalLineTo(9f); horizontalLineTo(7f); close()
                moveTo(21f, 13f); verticalLineTo(11f); horizontalLineTo(11f); verticalLineTo(13f); horizontalLineTo(21f); close()
                moveTo(15f, 9f); horizontalLineTo(17f); verticalLineTo(7f); horizontalLineTo(21f); verticalLineTo(5f)
                horizontalLineTo(17f); verticalLineTo(3f); horizontalLineTo(15f); verticalLineTo(9f); close()
            }
        }
    }

    val Public: ImageVector by lazy {
        icon("Public") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 2f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = true, isPositiveArc = false, 12f, 22f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = true, isPositiveArc = false, 12f, 2f)
                moveTo(2f, 12f); horizontalLineTo(22f)
                moveTo(12f, 2f); curveTo(14.5f, 5f, 16f, 8.4f, 16f, 12f); curveTo(16f, 15.6f, 14.5f, 19f, 12f, 22f)
                moveTo(12f, 2f); curveTo(9.5f, 5f, 8f, 8.4f, 8f, 12f); curveTo(8f, 15.6f, 9.5f, 19f, 12f, 22f)
            }
        }
    }

    val Radio: ImageVector by lazy {
        icon("Radio") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.9f, 19.1f); arcTo(10f, 10f, 0f, false, true, 4.9f, 4.9f)
                moveTo(19.1f, 4.9f); arcTo(10f, 10f, 0f, false, true, 19.1f, 19.1f)
                moveTo(7.8f, 16.2f); arcTo(6f, 6f, 0f, false, true, 7.8f, 7.8f)
                moveTo(16.2f, 7.8f); arcTo(6f, 6f, 0f, false, true, 16.2f, 16.2f)
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 10f); arcTo(2f, 2f, 0f, true, false, 12f, 14f); arcTo(2f, 2f, 0f, true, false, 12f, 10f); close()
            }
        }
    }

    val Link: ImageVector by lazy {
        icon("Link") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(10f, 13f); curveTo(11f, 14f, 12.6f, 14.2f, 13.8f, 13.4f); lineTo(18.2f, 9f)
                curveTo(19.6f, 7.6f, 19.6f, 5.4f, 18.2f, 4f); curveTo(16.8f, 2.6f, 14.6f, 2.6f, 13.2f, 4f)
                lineTo(11.4f, 5.8f)
                moveTo(14f, 11f); curveTo(13f, 10f, 11.4f, 9.8f, 10.2f, 10.6f); lineTo(5.8f, 15f)
                curveTo(4.4f, 16.4f, 4.4f, 18.6f, 5.8f, 20f); curveTo(7.2f, 21.4f, 9.4f, 21.4f, 10.8f, 20f)
                lineTo(12.6f, 18.2f)
            }
        }
    }

    val StarOutline: ImageVector by lazy {
        icon("StarOutline") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 3f); lineTo(14.7f, 8.6f); lineTo(20.9f, 9.5f); lineTo(16.4f, 13.8f)
                lineTo(17.5f, 20f); lineTo(12f, 17f); lineTo(6.5f, 20f); lineTo(7.6f, 13.8f)
                lineTo(3.1f, 9.5f); lineTo(9.3f, 8.6f); close()
            }
        }
    }

    val Layers: ImageVector by lazy {
        icon("Layers") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 3f); lineTo(2f, 8f); lineTo(12f, 13f); lineTo(22f, 8f); close()
                moveTo(2f, 12.5f); lineTo(12f, 17.5f); lineTo(22f, 12.5f)
                moveTo(2f, 17f); lineTo(12f, 22f); lineTo(22f, 17f)
            }
        }
    }

    val ArrowUpward: ImageVector by lazy {
        icon("ArrowUpward") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 19f); verticalLineTo(5f)
                moveTo(6f, 11f); lineTo(12f, 5f); lineTo(18f, 11f)
            }
        }
    }

    val KeyboardArrowDown: ImageVector by lazy {
        icon("KeyboardArrowDown") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f)
            }
        }
    }

    val Language: ImageVector by lazy { Public }

    private inline fun icon(
        name: String,
        block: ImageVector.Builder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()
}
