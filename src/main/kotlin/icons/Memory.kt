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
public val memory: ImageVector
    get() {
        if (_memory != null) {
            return _memory!!
        }
        _memory =
            ImageVector.Builder(
                name = "memory",
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
                        moveTo(9f, 15f)
                        verticalLineTo(9f)
                        horizontalLineToRelative(6f)
                        verticalLineToRelative(6f)
                        horizontalLineTo(9f)
                        close()
                        moveToRelative(2f, -2f)
                        horizontalLineToRelative(2f)
                        verticalLineTo(11f)
                        horizontalLineTo(11f)
                        verticalLineToRelative(2f)
                        close()
                        moveTo(9f, 21f)
                        verticalLineTo(19f)
                        horizontalLineTo(7f)
                        quadTo(6.18f, 19f, 5.59f, 18.41f)
                        reflectiveQuadTo(5f, 17f)
                        verticalLineTo(15f)
                        horizontalLineTo(3f)
                        verticalLineTo(13f)
                        horizontalLineTo(5f)
                        verticalLineTo(11f)
                        horizontalLineTo(3f)
                        verticalLineTo(9f)
                        horizontalLineTo(5f)
                        verticalLineTo(7f)
                        quadTo(5f, 6.18f, 5.59f, 5.59f)
                        reflectiveQuadTo(7f, 5f)
                        horizontalLineTo(9f)
                        verticalLineTo(3f)
                        horizontalLineToRelative(2f)
                        verticalLineTo(5f)
                        horizontalLineToRelative(2f)
                        verticalLineTo(3f)
                        horizontalLineToRelative(2f)
                        verticalLineTo(5f)
                        horizontalLineToRelative(2f)
                        quadToRelative(0.82f, 0f, 1.41f, 0.59f)
                        quadTo(19f, 6.18f, 19f, 7f)
                        verticalLineTo(9f)
                        horizontalLineToRelative(2f)
                        verticalLineToRelative(2f)
                        horizontalLineTo(19f)
                        verticalLineToRelative(2f)
                        horizontalLineToRelative(2f)
                        verticalLineToRelative(2f)
                        horizontalLineTo(19f)
                        verticalLineToRelative(2f)
                        quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                        reflectiveQuadTo(17f, 19f)
                        horizontalLineTo(15f)
                        verticalLineToRelative(2f)
                        horizontalLineTo(13f)
                        verticalLineTo(19f)
                        horizontalLineTo(11f)
                        verticalLineToRelative(2f)
                        horizontalLineTo(9f)
                        close()
                        moveToRelative(8f, -4f)
                        verticalLineTo(7f)
                        horizontalLineTo(7f)
                        verticalLineTo(17f)
                        horizontalLineTo(17f)
                        close()
                        moveTo(12f, 12f)
                        close()
                    }
                }
                .build()
        return _memory!!
    }

private var _memory: ImageVector? = null