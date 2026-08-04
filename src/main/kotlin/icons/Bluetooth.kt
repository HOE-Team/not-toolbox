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
public val bluetooth: ImageVector
    get() {
        if (_bluetooth != null) {
            return _bluetooth!!
        }
        _bluetooth =
            ImageVector.Builder(
                name = "bluetooth",
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
                        pathFillType = PathFillType.NonZero,
                    ) {
                        moveTo(11f, 22f)
                        verticalLineTo(14.4f)
                        lineTo(6.4f, 19f)
                        lineTo(5f, 17.6f)
                        lineTo(10.6f, 12f)
                        lineTo(5f, 6.4f)
                        lineTo(6.4f, 5f)
                        lineTo(11f, 9.6f)
                        verticalLineTo(2f)
                        horizontalLineToRelative(1f)
                        lineToRelative(5.7f, 5.7f)
                        lineTo(13.4f, 12f)
                        lineToRelative(4.3f, 4.3f)
                        lineTo(12f, 22f)
                        horizontalLineTo(11f)
                        close()
                        moveTo(13f, 9.6f)
                        lineTo(14.9f, 7.7f)
                        lineTo(13f, 5.85f)
                        verticalLineTo(9.6f)
                        close()
                        moveToRelative(0f, 8.55f)
                        lineTo(14.9f, 16.3f)
                        lineTo(13f, 14.4f)
                        verticalLineToRelative(3.75f)
                        close()
                    }
                }
                .build()
        return _bluetooth!!
    }

private var _bluetooth: ImageVector? = null