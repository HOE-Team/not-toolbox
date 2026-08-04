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
public val login: ImageVector
    get() {
        if (_login != null) {
            return _login!!
        }
        _login =
            ImageVector.Builder(
                name = "login",
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
                        moveTo(11f, 17.15f)
                        lineTo(12.6f, 15.55f)
                        lineTo(9.75f, 12.7f)
                        horizontalLineTo(20f)
                        verticalLineTo(10.6f)
                        horizontalLineTo(9.75f)
                        lineTo(12.6f, 7.75f)
                        lineTo(11f, 6.15f)
                        lineTo(5f, 12f)
                        lineTo(11f, 17.15f)
                        close()
                        moveTo(4f, 20f)
                        quadTo(3.18f, 20f, 2.59f, 19.41f)
                        reflectiveQuadTo(2f, 18f)
                        verticalLineTo(6f)
                        quadTo(2f, 5.18f, 2.59f, 4.59f)
                        reflectiveQuadTo(4f, 4f)
                        horizontalLineTo(10f)
                        verticalLineTo(6f)
                        horizontalLineTo(4f)
                        verticalLineTo(18f)
                        horizontalLineTo(10f)
                        verticalLineTo(20f)
                        horizontalLineTo(4f)
                        close()
                    }
                }
                .build()
        return _login!!
    }

private var _login: ImageVector? = null