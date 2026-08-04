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
public val upload: ImageVector
    get() {
        if (_upload != null) {
            return _upload!!
        }
        _upload =
            ImageVector.Builder(
                name = "upload",
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
                        moveTo(11f, 16f)
                        verticalLineTo(7.85f)
                        lineToRelative(-2.6f, 2.6f)
                        lineTo(7f, 9f)
                        lineTo(12f, 4f)
                        lineToRelative(5f, 5f)
                        lineToRelative(-1.4f, 1.45f)
                        lineTo(13f, 7.85f)
                        verticalLineTo(16f)
                        horizontalLineTo(11f)
                        close()
                        moveTo(6f, 20f)
                        quadTo(5.18f, 20f, 4.59f, 19.41f)
                        reflectiveQuadTo(4f, 18f)
                        verticalLineTo(15f)
                        horizontalLineTo(6f)
                        verticalLineToRelative(3f)
                        horizontalLineTo(18f)
                        verticalLineTo(15f)
                        horizontalLineToRelative(2f)
                        verticalLineToRelative(3f)
                        quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                        reflectiveQuadTo(18f, 20f)
                        horizontalLineTo(6f)
                        close()
                    }
                }
                .build()
        return _upload!!
    }

private var _upload: ImageVector? = null