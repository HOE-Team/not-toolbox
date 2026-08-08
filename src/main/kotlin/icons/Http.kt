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
public val http: ImageVector
    get() {
        if (_http != null) {
            return _http!!
        }
        _http =
            ImageVector.Builder(
                name = "http",
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
                        moveTo(1f, 15f)
                        verticalLineTo(9f)
                        horizontalLineTo(2.5f)
                        verticalLineToRelative(2f)
                        horizontalLineToRelative(2f)
                        verticalLineTo(9f)
                        horizontalLineTo(6f)
                        verticalLineToRelative(6f)
                        horizontalLineTo(4.5f)
                        verticalLineTo(12.5f)
                        horizontalLineToRelative(-2f)
                        verticalLineTo(15f)
                        horizontalLineTo(1f)
                        close()
                        moveToRelative(7.5f, 0f)
                        verticalLineTo(10.5f)
                        horizontalLineTo(7f)
                        verticalLineTo(9f)
                        horizontalLineToRelative(4.5f)
                        verticalLineToRelative(1.5f)
                        horizontalLineTo(10f)
                        verticalLineTo(15f)
                        horizontalLineTo(8.5f)
                        close()
                        moveTo(14f, 15f)
                        verticalLineTo(10.5f)
                        horizontalLineTo(12.5f)
                        verticalLineTo(9f)
                        horizontalLineTo(17f)
                        verticalLineToRelative(1.5f)
                        horizontalLineTo(15.5f)
                        verticalLineTo(15f)
                        horizontalLineTo(14f)
                        close()
                        moveToRelative(4f, 0f)
                        verticalLineTo(9f)
                        horizontalLineToRelative(3.5f)
                        quadToRelative(0.6f, 0f, 1.05f, 0.45f)
                        reflectiveQuadTo(23f, 10.5f)
                        verticalLineToRelative(1f)
                        quadToRelative(0f, 0.6f, -0.45f, 1.05f)
                        reflectiveQuadTo(21.5f, 13f)
                        horizontalLineToRelative(-2f)
                        verticalLineToRelative(2f)
                        horizontalLineTo(18f)
                        close()
                        moveToRelative(1.5f, -3.5f)
                        horizontalLineToRelative(2f)
                        verticalLineToRelative(-1f)
                        horizontalLineToRelative(-2f)
                        verticalLineToRelative(1f)
                        close()
                    }
                }
                .build()
        return _http!!
    }

private var _http: ImageVector? = null