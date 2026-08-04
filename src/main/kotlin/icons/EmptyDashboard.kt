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
public val empty_dashboard: ImageVector
    get() {
        if (_empty_dashboard != null) {
            return _empty_dashboard!!
        }
        _empty_dashboard =
            ImageVector.Builder(
                name = "empty_dashboard",
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
                        moveTo(6f, 18f)
                        horizontalLineToRelative(5.5f)
                        verticalLineTo(14f)
                        horizontalLineTo(6f)
                        verticalLineToRelative(4f)
                        close()
                        moveTo(6f, 13f)
                        horizontalLineToRelative(5.5f)
                        verticalLineTo(6f)
                        horizontalLineTo(6f)
                        verticalLineToRelative(7f)
                        close()
                        moveToRelative(6.5f, 5f)
                        horizontalLineTo(18f)
                        verticalLineTo(11f)
                        horizontalLineTo(12.5f)
                        verticalLineToRelative(7f)
                        close()
                        moveToRelative(0f, -8f)
                        horizontalLineTo(18f)
                        verticalLineTo(6f)
                        horizontalLineTo(12.5f)
                        verticalLineToRelative(4f)
                        close()
                        moveTo(5f, 21f)
                        quadTo(4.18f, 21f, 3.59f, 20.41f)
                        reflectiveQuadTo(3f, 19f)
                        verticalLineTo(5f)
                        quadTo(3f, 4.17f, 3.59f, 3.59f)
                        reflectiveQuadTo(5f, 3f)
                        horizontalLineTo(19f)
                        quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                        reflectiveQuadTo(21f, 5f)
                        verticalLineTo(7f)
                        horizontalLineToRelative(2f)
                        verticalLineTo(9f)
                        horizontalLineTo(21f)
                        verticalLineToRelative(2f)
                        horizontalLineToRelative(2f)
                        verticalLineToRelative(2f)
                        horizontalLineTo(21f)
                        verticalLineToRelative(2f)
                        horizontalLineToRelative(2f)
                        verticalLineToRelative(2f)
                        horizontalLineTo(21f)
                        verticalLineToRelative(2f)
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
                        moveTo(5f, 5f)
                        verticalLineTo(19f)
                        verticalLineTo(5f)
                        close()
                    }
                }
                .build()
        return _empty_dashboard!!
    }

private var _empty_dashboard: ImageVector? = null