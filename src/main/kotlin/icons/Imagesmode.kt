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
public val imagesmode: ImageVector
    get() {
        if (_imagesmode != null) {
            return _imagesmode!!
        }
        _imagesmode =
            ImageVector.Builder(
                name = "imagesmode",
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
                        moveTo(5f, 21f)
                        quadTo(4.18f, 21f, 3.59f, 20.41f)
                        reflectiveQuadTo(3f, 19f)
                        verticalLineTo(5f)
                        quadTo(3f, 4.17f, 3.59f, 3.59f)
                        reflectiveQuadTo(5f, 3f)
                        horizontalLineTo(19f)
                        quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                        reflectiveQuadTo(21f, 5f)
                        verticalLineTo(19f)
                        quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                        reflectiveQuadTo(19f, 21f)
                        horizontalLineTo(5f)
                        close()
                        moveTo(5f, 19f)
                        horizontalLineTo(19f)
                        verticalLineTo(5f)
                        horizontalLineTo(5f)
                        verticalLineTo(19f)
                        close()
                        moveTo(6f, 17f)
                        horizontalLineTo(18f)
                        lineTo(14.25f, 12f)
                        lineToRelative(-3f, 4f)
                        lineTo(9f, 13f)
                        lineTo(6f, 17f)
                        close()
                        moveTo(5f, 19f)
                        verticalLineTo(5f)
                        verticalLineTo(19f)
                        close()
                        moveTo(9.56f, 9.56f)
                        quadTo(10f, 9.13f, 10f, 8.5f)
                        reflectiveQuadTo(9.56f, 7.44f)
                        reflectiveQuadTo(8.5f, 7f)
                        reflectiveQuadTo(7.44f, 7.44f)
                        reflectiveQuadTo(7f, 8.5f)
                        reflectiveQuadTo(7.44f, 9.56f)
                        reflectiveQuadTo(8.5f, 10f)
                        reflectiveQuadTo(9.56f, 9.56f)
                        close()
                    }
                }
                .build()
        return _imagesmode!!
    }

private var _imagesmode: ImageVector? = null