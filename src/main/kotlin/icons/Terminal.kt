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
public val terminal: ImageVector
    get() {
        if (_terminal != null) {
            return _terminal!!
        }
        _terminal =
            ImageVector.Builder(
                name = "terminal",
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
                        moveTo(4f, 20f)
                        quadTo(3.18f, 20f, 2.59f, 19.41f)
                        reflectiveQuadTo(2f, 18f)
                        verticalLineTo(6f)
                        quadTo(2f, 5.18f, 2.59f, 4.59f)
                        reflectiveQuadTo(4f, 4f)
                        horizontalLineTo(20f)
                        quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                        quadTo(22f, 5.18f, 22f, 6f)
                        verticalLineTo(18f)
                        quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                        reflectiveQuadTo(20f, 20f)
                        horizontalLineTo(4f)
                        close()
                        moveTo(4f, 18f)
                        horizontalLineTo(20f)
                        verticalLineTo(8f)
                        horizontalLineTo(4f)
                        verticalLineTo(18f)
                        close()
                        moveTo(7.5f, 17f)
                        lineTo(6.1f, 15.6f)
                        lineTo(8.68f, 13f)
                        lineTo(6.08f, 10.4f)
                        lineTo(7.5f, 9f)
                        lineToRelative(4f, 4f)
                        lineToRelative(-4f, 4f)
                        close()
                        moveTo(12f, 17f)
                        verticalLineTo(15f)
                        horizontalLineToRelative(6f)
                        verticalLineToRelative(2f)
                        horizontalLineTo(12f)
                        close()
                    }
                }
                .build()
        return _terminal!!
    }

private var _terminal: ImageVector? = null