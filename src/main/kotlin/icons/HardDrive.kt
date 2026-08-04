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
public val hard_drive: ImageVector
    get() {
        if (_hard_drive != null) {
            return _hard_drive!!
        }
        _hard_drive =
            ImageVector.Builder(
                name = "hard_drive",
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
                        moveTo(4f, 17f)
                        horizontalLineTo(20f)
                        verticalLineTo(11f)
                        horizontalLineTo(4f)
                        verticalLineToRelative(6f)
                        close()
                        moveTo(18.06f, 15.06f)
                        quadTo(18.5f, 14.63f, 18.5f, 14f)
                        reflectiveQuadTo(18.06f, 12.94f)
                        reflectiveQuadTo(17f, 12.5f)
                        reflectiveQuadToRelative(-1.06f, 0.44f)
                        reflectiveQuadTo(15.5f, 14f)
                        reflectiveQuadToRelative(0.44f, 1.06f)
                        reflectiveQuadTo(17f, 15.5f)
                        reflectiveQuadToRelative(1.06f, -0.44f)
                        close()
                        moveTo(22f, 9f)
                        horizontalLineTo(19.18f)
                        lineToRelative(-2f, -2f)
                        horizontalLineTo(6.83f)
                        lineToRelative(-2f, 2f)
                        horizontalLineTo(2f)
                        lineTo(5.43f, 5.57f)
                        quadTo(5.7f, 5.3f, 6.06f, 5.15f)
                        reflectiveQuadTo(6.83f, 5f)
                        horizontalLineTo(17.18f)
                        quadToRelative(0.4f, 0f, 0.76f, 0.15f)
                        reflectiveQuadToRelative(0.64f, 0.43f)
                        lineTo(22f, 9f)
                        close()
                        moveTo(4f, 19f)
                        quadTo(3.18f, 19f, 2.59f, 18.41f)
                        reflectiveQuadTo(2f, 17f)
                        verticalLineTo(9f)
                        horizontalLineTo(22f)
                        verticalLineToRelative(8f)
                        quadToRelative(0f, 0.82f, -0.59f, 1.41f)
                        reflectiveQuadTo(20f, 19f)
                        horizontalLineTo(4f)
                        close()
                    }
                }
                .build()
        return _hard_drive!!
    }

private var _hard_drive: ImageVector? = null