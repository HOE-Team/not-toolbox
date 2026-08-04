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
public val pan_zoom: ImageVector
    get() {
        if (_pan_zoom != null) {
            return _pan_zoom!!
        }
        _pan_zoom =
            ImageVector.Builder(
                name = "pan_zoom",
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
                        moveTo(3f, 21f)
                        verticalLineTo(15f)
                        horizontalLineTo(5f)
                        verticalLineToRelative(2.6f)
                        lineTo(8.1f, 14.5f)
                        lineToRelative(1.4f, 1.4f)
                        lineTo(6.4f, 19f)
                        horizontalLineTo(9f)
                        verticalLineToRelative(2f)
                        horizontalLineTo(3f)
                        close()
                        moveTo(15.9f, 9.5f)
                        lineTo(14.5f, 8.1f)
                        lineTo(17.6f, 5f)
                        horizontalLineTo(15f)
                        verticalLineTo(3f)
                        horizontalLineToRelative(6f)
                        verticalLineTo(9f)
                        horizontalLineTo(19f)
                        verticalLineTo(6.4f)
                        lineTo(15.9f, 9.5f)
                        close()
                    }
                }
                .build()
        return _pan_zoom!!
    }

private var _pan_zoom: ImageVector? = null