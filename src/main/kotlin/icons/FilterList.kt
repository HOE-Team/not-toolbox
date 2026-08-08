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
public val filter_list: ImageVector
    get() {
        if (_filter_list != null) {
            return _filter_list!!
        }
        _filter_list =
            ImageVector.Builder(
                name = "filter_list",
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
                        moveTo(10f, 18f)
                        horizontalLineTo(14f)
                        verticalLineTo(16f)
                        horizontalLineTo(10f)
                        verticalLineTo(18f)
                        close()
                        moveTo(3f, 6f)
                        verticalLineTo(8f)
                        horizontalLineTo(21f)
                        verticalLineTo(6f)
                        horizontalLineTo(3f)
                        close()
                        moveTo(6f, 13f)
                        horizontalLineTo(18f)
                        verticalLineTo(11f)
                        horizontalLineTo(6f)
                        verticalLineTo(13f)
                        close()
                    }
                }
                .build()
        return _filter_list!!
    }

private var _filter_list: ImageVector? = null