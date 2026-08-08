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
public val search_off: ImageVector
    get() {
        if (_search_off != null) {
            return _search_off!!
        }
        _search_off =
            ImageVector.Builder(
                name = "search_off",
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
                        moveTo(3.46f, 20.54f)
                        quadTo(2f, 19.08f, 2f, 17f)
                        reflectiveQuadTo(3.46f, 13.46f)
                        reflectiveQuadTo(7f, 12f)
                        quadToRelative(2.08f, 0f, 3.54f, 1.46f)
                        quadTo(12f, 14.93f, 12f, 17f)
                        reflectiveQuadToRelative(-1.46f, 3.54f)
                        reflectiveQuadTo(7f, 22f)
                        quadTo(4.93f, 22f, 3.46f, 20.54f)
                        close()
                        moveTo(20.6f, 21f)
                        lineTo(14.2f, 14.6f)
                        quadTo(13.9f, 14.27f, 13.56f, 13.94f)
                        reflectiveQuadTo(12.9f, 13.3f)
                        quadToRelative(0.95f, -0.6f, 1.53f, -1.6f)
                        reflectiveQuadTo(15f, 9.5f)
                        quadTo(15f, 7.63f, 13.69f, 6.31f)
                        reflectiveQuadTo(10.5f, 5f)
                        reflectiveQuadTo(7.31f, 6.31f)
                        reflectiveQuadTo(6f, 9.5f)
                        quadTo(6f, 9.65f, 6.01f, 9.79f)
                        quadToRelative(0.01f, 0.14f, 0.04f, 0.29f)
                        quadTo(5.6f, 10.13f, 5.06f, 10.27f)
                        quadTo(4.53f, 10.43f, 4.1f, 10.63f)
                        quadTo(4.05f, 10.35f, 4.03f, 10.07f)
                        reflectiveQuadTo(4f, 9.5f)
                        quadTo(4f, 6.77f, 5.89f, 4.89f)
                        reflectiveQuadTo(10.5f, 3f)
                        reflectiveQuadToRelative(4.61f, 1.89f)
                        reflectiveQuadTo(17f, 9.5f)
                        quadToRelative(0f, 1.07f, -0.34f, 2.04f)
                        reflectiveQuadTo(15.73f, 13.3f)
                        lineTo(22f, 19.6f)
                        lineTo(20.6f, 21f)
                        close()
                        moveTo(5.23f, 19.48f)
                        lineTo(7f, 17.7f)
                        lineToRelative(1.75f, 1.78f)
                        lineToRelative(0.72f, -0.7f)
                        lineTo(7.7f, 17f)
                        lineTo(9.48f, 15.23f)
                        lineToRelative(-0.7f, -0.7f)
                        lineTo(7f, 16.3f)
                        lineTo(5.23f, 14.53f)
                        lineToRelative(-0.7f, 0.7f)
                        lineTo(6.3f, 17f)
                        lineTo(4.53f, 18.77f)
                        lineToRelative(0.7f, 0.7f)
                        close()
                    }
                }
                .build()
        return _search_off!!
    }

private var _search_off: ImageVector? = null