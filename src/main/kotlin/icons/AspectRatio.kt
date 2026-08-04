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
public val aspect_ratio: ImageVector
    get() {
        if (_aspect_ratio != null) {
            return _aspect_ratio!!
        }
        _aspect_ratio =
            ImageVector.Builder(
                name = "aspect_ratio",
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
                        moveTo(14f, 17f)
                        horizontalLineToRelative(5f)
                        verticalLineTo(12f)
                        horizontalLineTo(17f)
                        verticalLineToRelative(3f)
                        horizontalLineTo(14f)
                        verticalLineToRelative(2f)
                        close()
                        moveTo(5f, 12f)
                        horizontalLineTo(7f)
                        verticalLineTo(9f)
                        horizontalLineToRelative(3f)
                        verticalLineTo(7f)
                        horizontalLineTo(5f)
                        verticalLineToRelative(5f)
                        close()
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
                        verticalLineTo(6f)
                        horizontalLineTo(4f)
                        verticalLineTo(18f)
                        close()
                        moveToRelative(0f, 0f)
                        verticalLineTo(6f)
                        verticalLineTo(18f)
                        close()
                    }
                }
                .build()
        return _aspect_ratio!!
    }

private var _aspect_ratio: ImageVector? = null