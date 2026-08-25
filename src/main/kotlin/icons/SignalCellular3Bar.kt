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
public val signal_cellular_3_bar: ImageVector
    get() {
        if (_signal_cellular_3_bar != null) {
            return _signal_cellular_3_bar!!
        }
        _signal_cellular_3_bar =
            ImageVector.Builder(
                name = "signal_cellular_3_bar",
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
                        moveTo(2f, 22f)
                        lineTo(22f, 2f)
                        verticalLineTo(22f)
                        horizontalLineTo(2f)
                        close()
                        moveTo(15f, 20f)
                        horizontalLineToRelative(5f)
                        verticalLineTo(6.85f)
                        lineToRelative(-5f, 5f)
                        verticalLineTo(20f)
                        close()
                    }
                }
                .build()
        return _signal_cellular_3_bar!!
    }

private var _signal_cellular_3_bar: ImageVector? = null