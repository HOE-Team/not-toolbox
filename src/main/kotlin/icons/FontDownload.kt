package main.kotlin.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val font_download: ImageVector
    get() {
        if (_font_download != null) {
            return _font_download!!
        }
        _font_download =
            ImageVector.Builder(
                name = "font_download",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            )
                .apply {
                    path(
                        fill = SolidColor(Color.Black),
                        fillAlpha = 1f,
                        stroke = null,
                        strokeAlpha = 1f,
                        strokeLineWidth = 1f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Bevel,
                        strokeLineMiter = 1f,
                        pathFillType = PathFillType.Companion.NonZero,
                    ) {
                        moveTo(6.4f, 18f)
                        horizontalLineTo(8.5f)
                        lineTo(9.6f, 14.95f)
                        horizontalLineToRelative(4.8f)
                        lineTo(15.5f, 18f)
                        horizontalLineToRelative(2.1f)
                        lineTo(13.05f, 6f)
                        horizontalLineToRelative(-2.1f)
                        lineTo(6.4f, 18f)
                        close()
                        moveToRelative(3.8f, -4.8f)
                        lineTo(11.95f, 8.25f)
                        horizontalLineToRelative(0.1f)
                        lineTo(13.8f, 13.2f)
                        horizontalLineTo(10.2f)
                        close()
                        moveTo(4f, 22f)
                        quadTo(3.18f, 22f, 2.59f, 21.41f)
                        reflectiveQuadTo(2f, 20f)
                        verticalLineTo(4f)
                        quadTo(2f, 3.17f, 2.59f, 2.59f)
                        reflectiveQuadTo(4f, 2f)
                        horizontalLineTo(20f)
                        quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                        reflectiveQuadTo(22f, 4f)
                        verticalLineTo(20f)
                        quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                        reflectiveQuadTo(20f, 22f)
                        horizontalLineTo(4f)
                        close()
                        moveTo(4f, 20f)
                        horizontalLineTo(20f)
                        verticalLineTo(4f)
                        horizontalLineTo(4f)
                        verticalLineTo(20f)
                        close()
                        moveTo(4f, 4f)
                        verticalLineTo(20f)
                        verticalLineTo(4f)
                        close()
                    }
                }
                .build()
        return _font_download!!
    }

private var _font_download: ImageVector? = null