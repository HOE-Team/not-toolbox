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
public val manufacturing: ImageVector
    get() {
        if (_manufacturing != null) {
            return _manufacturing!!
        }
        _manufacturing =
            ImageVector.Builder(
                name = "manufacturing",
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
                        moveTo(5.85f, 12f)
                        lineTo(5.55f, 10.5f)
                        quadTo(5.25f, 10.38f, 4.99f, 10.24f)
                        reflectiveQuadTo(4.45f, 9.9f)
                        lineTo(3f, 10.35f)
                        lineTo(2f, 8.65f)
                        lineToRelative(1.15f, -1f)
                        quadTo(3.1f, 7.32f, 3.1f, 7f)
                        reflectiveQuadTo(3.15f, 6.35f)
                        lineTo(2f, 5.35f)
                        lineTo(3f, 3.65f)
                        lineTo(4.45f, 4.1f)
                        quadTo(4.73f, 3.9f, 4.99f, 3.76f)
                        reflectiveQuadTo(5.55f, 3.5f)
                        lineTo(5.85f, 2f)
                        horizontalLineToRelative(2f)
                        lineToRelative(0.3f, 1.5f)
                        quadToRelative(0.3f, 0.13f, 0.56f, 0.26f)
                        reflectiveQuadTo(9.25f, 4.1f)
                        lineTo(10.7f, 3.65f)
                        lineToRelative(1f, 1.7f)
                        lineToRelative(-1.15f, 1f)
                        quadTo(10.6f, 6.68f, 10.6f, 7f)
                        reflectiveQuadTo(10.55f, 7.65f)
                        lineToRelative(1.15f, 1f)
                        lineToRelative(-1f, 1.7f)
                        lineTo(9.25f, 9.9f)
                        quadTo(8.98f, 10.1f, 8.71f, 10.24f)
                        quadTo(8.45f, 10.38f, 8.15f, 10.5f)
                        lineTo(7.85f, 12f)
                        horizontalLineToRelative(-2f)
                        close()
                        moveTo(8.26f, 8.41f)
                        quadTo(8.85f, 7.82f, 8.85f, 7f)
                        reflectiveQuadTo(8.26f, 5.59f)
                        reflectiveQuadTo(6.85f, 5f)
                        reflectiveQuadTo(5.44f, 5.59f)
                        quadTo(4.85f, 6.18f, 4.85f, 7f)
                        reflectiveQuadTo(5.44f, 8.41f)
                        reflectiveQuadTo(6.85f, 9f)
                        reflectiveQuadTo(8.26f, 8.41f)
                        close()
                        moveTo(14.8f, 23f)
                        lineTo(14.35f, 20.9f)
                        quadTo(13.93f, 20.75f, 13.56f, 20.54f)
                        reflectiveQuadTo(12.85f, 20.05f)
                        lineToRelative(-2f, 0.65f)
                        lineTo(9.45f, 18.3f)
                        lineToRelative(1.6f, -1.4f)
                        quadTo(11f, 16.45f, 11f, 16f)
                        reflectiveQuadToRelative(0.05f, -0.9f)
                        lineTo(9.45f, 13.7f)
                        lineToRelative(1.4f, -2.4f)
                        lineToRelative(2f, 0.65f)
                        quadToRelative(0.35f, -0.28f, 0.71f, -0.49f)
                        reflectiveQuadTo(14.35f, 11.1f)
                        lineTo(14.8f, 9f)
                        horizontalLineToRelative(2.8f)
                        lineToRelative(0.45f, 2.1f)
                        quadToRelative(0.43f, 0.15f, 0.79f, 0.36f)
                        quadToRelative(0.36f, 0.21f, 0.71f, 0.49f)
                        lineToRelative(2f, -0.65f)
                        lineToRelative(1.4f, 2.4f)
                        lineToRelative(-1.6f, 1.4f)
                        quadTo(21.4f, 15.55f, 21.4f, 16f)
                        reflectiveQuadToRelative(-0.05f, 0.9f)
                        lineToRelative(1.6f, 1.4f)
                        lineToRelative(-1.4f, 2.4f)
                        lineToRelative(-2f, -0.65f)
                        quadToRelative(-0.35f, 0.27f, -0.71f, 0.49f)
                        reflectiveQuadTo(18.05f, 20.9f)
                        lineTo(17.6f, 23f)
                        horizontalLineTo(14.8f)
                        close()
                        moveToRelative(1.4f, -4f)
                        quadToRelative(1.25f, 0f, 2.13f, -0.88f)
                        reflectiveQuadTo(19.2f, 16f)
                        reflectiveQuadTo(18.33f, 13.88f)
                        reflectiveQuadTo(16.2f, 13f)
                        reflectiveQuadToRelative(-2.12f, 0.88f)
                        reflectiveQuadTo(13.2f, 16f)
                        reflectiveQuadToRelative(0.88f, 2.13f)
                        reflectiveQuadTo(16.2f, 19f)
                        close()
                    }
                }
                .build()
        return _manufacturing!!
    }

private var _manufacturing: ImageVector? = null