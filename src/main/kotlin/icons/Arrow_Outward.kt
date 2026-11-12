package icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val arrow_outward: ImageVector
    get() {
        if (_arrow_outward != null) {
            return _arrow_outward!!
        }
        _arrow_outward =
            ImageVector.Builder(
                name = "arrow_outward",
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
                        lineTo(5f, 16.6f)
                        lineTo(14.6f, 7f)
                        horizontalLineTo(6f)
                        verticalLineTo(5f)
                        horizontalLineTo(18f)
                        verticalLineTo(17f)
                        horizontalLineTo(16f)
                        verticalLineTo(8.4f)
                        lineTo(6.4f, 18f)
                        close()
                    }
                }
                .build()
        return _arrow_outward!!
    }

private var _arrow_outward: ImageVector? = null