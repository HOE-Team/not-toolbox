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
public val developer_board: ImageVector
    get() {
        if (_developer_board != null) {
            return _developer_board!!
        }
        _developer_board =
            ImageVector.Builder(
                name = "developer_board",
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
                        moveTo(4f, 21f)
                        quadTo(3.18f, 21f, 2.59f, 20.41f)
                        reflectiveQuadTo(2f, 19f)
                        verticalLineTo(5f)
                        quadTo(2f, 4.17f, 2.59f, 3.59f)
                        reflectiveQuadTo(4f, 3f)
                        horizontalLineTo(18f)
                        quadToRelative(0.82f, 0f, 1.41f, 0.59f)
                        reflectiveQuadTo(20f, 5f)
                        verticalLineTo(7f)
                        horizontalLineToRelative(2f)
                        verticalLineTo(9f)
                        horizontalLineTo(20f)
                        verticalLineToRelative(2f)
                        horizontalLineToRelative(2f)
                        verticalLineToRelative(2f)
                        horizontalLineTo(20f)
                        verticalLineToRelative(2f)
                        horizontalLineToRelative(2f)
                        verticalLineToRelative(2f)
                        horizontalLineTo(20f)
                        verticalLineToRelative(2f)
                        quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                        reflectiveQuadTo(18f, 21f)
                        horizontalLineTo(4f)
                        close()
                        moveTo(4f, 19f)
                        horizontalLineTo(18f)
                        verticalLineTo(5f)
                        horizontalLineTo(4f)
                        verticalLineTo(19f)
                        close()
                        moveTo(6f, 17f)
                        horizontalLineToRelative(5f)
                        verticalLineTo(13f)
                        horizontalLineTo(6f)
                        verticalLineToRelative(4f)
                        close()
                        moveToRelative(6f, -7f)
                        horizontalLineToRelative(4f)
                        verticalLineTo(7f)
                        horizontalLineTo(12f)
                        verticalLineToRelative(3f)
                        close()
                        moveTo(6f, 12f)
                        horizontalLineToRelative(5f)
                        verticalLineTo(7f)
                        horizontalLineTo(6f)
                        verticalLineToRelative(5f)
                        close()
                        moveToRelative(6f, 5f)
                        horizontalLineToRelative(4f)
                        verticalLineTo(11f)
                        horizontalLineTo(12f)
                        verticalLineToRelative(6f)
                        close()
                        moveTo(4f, 5f)
                        verticalLineTo(19f)
                        verticalLineTo(5f)
                        close()
                    }
                }
                .build()
        return _developer_board!!
    }

private var _developer_board: ImageVector? = null