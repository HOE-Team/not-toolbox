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
public val link_off: ImageVector
    get() {
        if (_link_off != null) {
            return _link_off!!
        }
        _link_off =
            ImageVector.Builder(
                name = "link_off",
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
                        moveTo(19.25f, 16.45f)
                        lineTo(17.75f, 14.9f)
                        quadToRelative(1f, -0.28f, 1.63f, -1.06f)
                        reflectiveQuadTo(20f, 12f)
                        quadTo(20f, 10.75f, 19.13f, 9.88f)
                        reflectiveQuadTo(17f, 9f)
                        horizontalLineTo(13f)
                        verticalLineTo(7f)
                        horizontalLineToRelative(4f)
                        quadToRelative(2.07f, 0f, 3.54f, 1.46f)
                        reflectiveQuadTo(22f, 12f)
                        quadToRelative(0f, 1.42f, -0.74f, 2.63f)
                        reflectiveQuadToRelative(-2.01f, 1.82f)
                        close()
                        moveTo(15.85f, 13f)
                        lineToRelative(-2f, -2f)
                        horizontalLineTo(16f)
                        verticalLineToRelative(2f)
                        horizontalLineTo(15.85f)
                        close()
                        moveToRelative(3.95f, 9.6f)
                        lineTo(1.4f, 4.2f)
                        lineTo(2.8f, 2.8f)
                        lineTo(21.2f, 21.2f)
                        lineToRelative(-1.4f, 1.4f)
                        close()
                        moveTo(11f, 17f)
                        horizontalLineTo(7f)
                        quadTo(4.93f, 17f, 3.46f, 15.54f)
                        reflectiveQuadTo(2f, 12f)
                        quadTo(2f, 10.27f, 3.05f, 8.92f)
                        reflectiveQuadTo(5.75f, 7.15f)
                        lineTo(7.6f, 9f)
                        horizontalLineTo(7f)
                        quadTo(5.75f, 9f, 4.88f, 9.88f)
                        reflectiveQuadTo(4f, 12f)
                        reflectiveQuadToRelative(0.88f, 2.13f)
                        reflectiveQuadTo(7f, 15f)
                        horizontalLineToRelative(4f)
                        verticalLineToRelative(2f)
                        close()
                        moveTo(8f, 13f)
                        verticalLineTo(11f)
                        horizontalLineTo(9.63f)
                        lineToRelative(1.98f, 2f)
                        horizontalLineTo(8f)
                        close()
                    }
                }
                .build()
        return _link_off!!
    }

private var _link_off: ImageVector? = null