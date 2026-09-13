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
public val robot_2: ImageVector
    get() {
        if (_robot_2 != null) {
            return _robot_2!!
        }
        _robot_2 =
            ImageVector.Builder(
                name = "robot_2",
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
                        verticalLineTo(16f)
                        quadTo(4f, 15.18f, 4.59f, 14.59f)
                        reflectiveQuadTo(6f, 14f)
                        horizontalLineTo(18f)
                        quadToRelative(0.82f, 0f, 1.41f, 0.59f)
                        reflectiveQuadTo(20f, 16f)
                        verticalLineToRelative(5f)
                        horizontalLineTo(4f)
                        close()
                        moveTo(9f, 13f)
                        quadTo(6.93f, 13f, 5.46f, 11.54f)
                        reflectiveQuadTo(4f, 8f)
                        reflectiveQuadTo(5.46f, 4.46f)
                        reflectiveQuadTo(9f, 3f)
                        horizontalLineToRelative(6f)
                        quadToRelative(2.07f, 0f, 3.54f, 1.46f)
                        quadTo(20f, 5.93f, 20f, 8f)
                        reflectiveQuadToRelative(-1.46f, 3.54f)
                        reflectiveQuadTo(15f, 13f)
                        horizontalLineTo(9f)
                        close()
                        moveTo(6f, 19f)
                        horizontalLineTo(18f)
                        verticalLineTo(16f)
                        horizontalLineTo(6f)
                        verticalLineToRelative(3f)
                        close()
                        moveTo(9f, 11f)
                        horizontalLineToRelative(6f)
                        quadToRelative(1.25f, 0f, 2.13f, -0.88f)
                        reflectiveQuadTo(18f, 8f)
                        reflectiveQuadTo(17.13f, 5.88f)
                        reflectiveQuadTo(15f, 5f)
                        horizontalLineTo(9f)
                        quadTo(7.75f, 5f, 6.88f, 5.88f)
                        reflectiveQuadTo(6f, 8f)
                        reflectiveQuadToRelative(0.88f, 2.13f)
                        reflectiveQuadTo(9f, 11f)
                        close()
                        moveTo(9.71f, 8.71f)
                        quadTo(10f, 8.42f, 10f, 8f)
                        quadTo(10f, 7.57f, 9.71f, 7.29f)
                        reflectiveQuadTo(9f, 7f)
                        quadTo(8.58f, 7f, 8.29f, 7.29f)
                        reflectiveQuadTo(8f, 8f)
                        quadTo(8f, 8.42f, 8.29f, 8.71f)
                        quadTo(8.58f, 9f, 9f, 9f)
                        quadTo(9.43f, 9f, 9.71f, 8.71f)
                        close()
                        moveToRelative(6f, 0f)
                        quadTo(16f, 8.42f, 16f, 8f)
                        quadTo(16f, 7.57f, 15.71f, 7.29f)
                        reflectiveQuadTo(15f, 7f)
                        reflectiveQuadTo(14.29f, 7.29f)
                        reflectiveQuadTo(14f, 8f)
                        quadToRelative(0f, 0.42f, 0.29f, 0.71f)
                        reflectiveQuadTo(15f, 9f)
                        reflectiveQuadTo(15.71f, 8.71f)
                        close()
                        moveTo(12f, 19f)
                        close()
                        moveTo(12f, 8f)
                        close()
                    }
                }
                .build()
        return _robot_2!!
    }

private var _robot_2: ImageVector? = null