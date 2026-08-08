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
public val terminal_2: ImageVector
    get() {
        if (_terminal_2 != null) {
            return _terminal_2!!
        }
        _terminal_2 =
            ImageVector.Builder(
                name = "terminal_2",
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
                        moveTo(12f, 20f)
                        verticalLineTo(18f)
                        horizontalLineToRelative(8f)
                        verticalLineToRelative(2f)
                        horizontalLineTo(12f)
                        close()
                        moveTo(5.5f, 16f)
                        lineTo(4.1f, 14.6f)
                        lineTo(8.68f, 10f)
                        lineTo(4.1f, 5.4f)
                        lineTo(5.5f, 4f)
                        lineToRelative(6f, 6f)
                        lineToRelative(-6f, 6f)
                        close()
                    }
                }
                .build()
        return _terminal_2!!
    }

private var _terminal_2: ImageVector? = null