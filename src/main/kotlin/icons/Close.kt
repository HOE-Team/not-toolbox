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
public val close: ImageVector
    get() {
        if (_close != null) {
            return _close!!
        }
        _close =
            ImageVector.Builder(
                name = "close",
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
                        moveTo(18.3f, 5.71f)
                        lineTo(12f, 12f)
                        lineTo(5.7f, 5.71f)
                        quadTo(5.32f, 5.32f, 4.82f, 5.32f)
                        reflectiveQuadTo(4.32f, 5.71f)
                        quadTo(3.93f, 6.1f, 3.93f, 6.6f)
                        reflectiveQuadTo(4.32f, 7.1f)
                        lineTo(10.59f, 13.37f)
                        lineTo(4.32f, 19.63f)
                        quadTo(3.93f, 20.02f, 3.93f, 20.52f)
                        reflectiveQuadTo(4.32f, 21.02f)
                        quadTo(4.71f, 21.41f, 5.21f, 21.41f)
                        reflectiveQuadTo(5.71f, 21.02f)
                        lineTo(11.97f, 14.75f)
                        lineTo(18.24f, 21.02f)
                        quadTo(18.63f, 21.41f, 19.13f, 21.41f)
                        reflectiveQuadTo(19.63f, 21.02f)
                        quadTo(20.02f, 20.63f, 20.02f, 20.13f)
                        reflectiveQuadTo(19.63f, 19.63f)
                        lineTo(13.36f, 13.36f)
                        lineTo(19.62f, 7.1f)
                        quadTo(20.02f, 6.7f, 20.02f, 6.2f)
                        reflectiveQuadTo(19.62f, 5.7f)
                        quadTo(19.23f, 5.32f, 18.73f, 5.32f)
                        reflectiveQuadTo(18.23f, 5.71f)
                        close()
                    }
                }
                .build()
        return _close!!
    }

private var _close: ImageVector? = null