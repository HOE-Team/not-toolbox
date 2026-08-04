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
public val lan: ImageVector
    get() {
        if (_lan != null) {
            return _lan!!
        }
        _lan =
            ImageVector.Builder(
                name = "lan",
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
                        moveTo(3f, 22f)
                        verticalLineTo(15f)
                        horizontalLineTo(6f)
                        verticalLineTo(11f)
                        horizontalLineToRelative(5f)
                        verticalLineTo(9f)
                        horizontalLineTo(8f)
                        verticalLineTo(2f)
                        horizontalLineToRelative(8f)
                        verticalLineTo(9f)
                        horizontalLineTo(13f)
                        verticalLineToRelative(2f)
                        horizontalLineToRelative(5f)
                        verticalLineToRelative(4f)
                        horizontalLineToRelative(3f)
                        verticalLineToRelative(7f)
                        horizontalLineTo(13f)
                        verticalLineTo(15f)
                        horizontalLineToRelative(3f)
                        verticalLineTo(13f)
                        horizontalLineTo(8f)
                        verticalLineToRelative(2f)
                        horizontalLineToRelative(3f)
                        verticalLineToRelative(7f)
                        horizontalLineTo(3f)
                        close()
                        moveTo(10f, 7f)
                        horizontalLineToRelative(4f)
                        verticalLineTo(4f)
                        horizontalLineTo(10f)
                        verticalLineTo(7f)
                        close()
                        moveTo(5f, 20f)
                        horizontalLineTo(9f)
                        verticalLineTo(17f)
                        horizontalLineTo(5f)
                        verticalLineToRelative(3f)
                        close()
                        moveToRelative(10f, 0f)
                        horizontalLineToRelative(4f)
                        verticalLineTo(17f)
                        horizontalLineTo(15f)
                        verticalLineToRelative(3f)
                        close()
                        moveTo(12f, 7f)
                        close()
                        moveTo(9f, 17f)
                        close()
                        moveToRelative(6f, 0f)
                        close()
                    }
                }
                .build()
        return _lan!!
    }

private var _lan: ImageVector? = null