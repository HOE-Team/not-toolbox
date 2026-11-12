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
public val editor_choice: ImageVector
    get() {
        if (_editor_choice != null) {
            return _editor_choice!!
        }
        _editor_choice =
            ImageVector.Builder(
                name = "editor_choice",
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
                        moveTo(6f, 23f)
                        verticalLineTo(14.78f)
                        lineTo(2.75f, 9.5f)
                        lineTo(7.38f, 2f)
                        horizontalLineToRelative(9.25f)
                        lineToRelative(4.63f, 7.5f)
                        lineTo(18f, 14.78f)
                        verticalLineTo(23f)
                        lineTo(12f, 21f)
                        lineTo(6f, 23f)
                        close()
                        moveTo(8f, 20.23f)
                        lineTo(12f, 18.9f)
                        lineToRelative(4f, 1.33f)
                        verticalLineTo(17f)
                        horizontalLineTo(8f)
                        verticalLineToRelative(3.23f)
                        close()
                        moveTo(8.5f, 4f)
                        lineTo(5.1f, 9.5f)
                        lineTo(8.5f, 15f)
                        horizontalLineToRelative(7f)
                        lineTo(18.9f, 9.5f)
                        lineTo(15.5f, 4f)
                        horizontalLineToRelative(-7f)
                        close()
                        moveToRelative(2.45f, 9.57f)
                        lineTo(7.4f, 10.05f)
                        lineTo(8.83f, 8.63f)
                        lineToRelative(2.13f, 2.13f)
                        lineTo(15.18f, 6.5f)
                        lineTo(16.6f, 7.9f)
                        lineToRelative(-5.65f, 5.68f)
                        close()
                        moveTo(8f, 17f)
                        horizontalLineToRelative(8f)
                        horizontalLineTo(12f)
                        horizontalLineTo(8f)
                        close()
                    }
                }
                .build()
        return _editor_choice!!
    }

private var _editor_choice: ImageVector? = null