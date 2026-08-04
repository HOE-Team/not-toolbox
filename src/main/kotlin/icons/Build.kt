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
public val build: ImageVector
    get() {
        if (_build != null) {
            return _build!!
        }
        _build =
            ImageVector.Builder(
                name = "build",
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
                        moveTo(17.15f, 20.7f)
                        lineTo(11.1f, 14.6f)
                        quadToRelative(-0.5f, 0.2f, -1.01f, 0.3f)
                        quadTo(9.58f, 15f, 9f, 15f)
                        quadTo(6.5f, 15f, 4.75f, 13.25f)
                        reflectiveQuadTo(3f, 9f)
                        quadTo(3f, 8.1f, 3.25f, 7.29f)
                        reflectiveQuadTo(3.95f, 5.75f)
                        lineTo(7.6f, 9.4f)
                        lineTo(9.4f, 7.6f)
                        lineTo(5.75f, 3.95f)
                        quadTo(6.48f, 3.5f, 7.29f, 3.25f)
                        reflectiveQuadTo(9f, 3f)
                        quadToRelative(2.5f, 0f, 4.25f, 1.75f)
                        reflectiveQuadTo(15f, 9f)
                        quadToRelative(0f, 0.57f, -0.1f, 1.09f)
                        reflectiveQuadTo(14.6f, 11.1f)
                        lineToRelative(6.1f, 6.05f)
                        quadToRelative(0.3f, 0.3f, 0.3f, 0.73f)
                        reflectiveQuadTo(20.7f, 18.6f)
                        lineToRelative(-2.1f, 2.1f)
                        quadTo(18.3f, 21f, 17.88f, 21f)
                        reflectiveQuadTo(17.15f, 20.7f)
                        close()
                        moveToRelative(0.72f, -2.13f)
                        lineTo(18.55f, 17.9f)
                        lineToRelative(-6.4f, -6.4f)
                        quadTo(12.6f, 11f, 12.8f, 10.34f)
                        reflectiveQuadTo(13f, 9f)
                        quadTo(13f, 7.5f, 12.04f, 6.39f)
                        reflectiveQuadTo(9.65f, 5.05f)
                        lineTo(11.5f, 6.9f)
                        quadToRelative(0.3f, 0.3f, 0.3f, 0.7f)
                        reflectiveQuadTo(11.5f, 8.3f)
                        lineTo(8.3f, 11.5f)
                        quadTo(8f, 11.8f, 7.6f, 11.8f)
                        reflectiveQuadTo(6.9f, 11.5f)
                        lineTo(5.05f, 9.65f)
                        quadToRelative(0.22f, 1.43f, 1.34f, 2.39f)
                        reflectiveQuadTo(9f, 13f)
                        quadToRelative(0.65f, 0f, 1.3f, -0.2f)
                        reflectiveQuadToRelative(1.18f, -0.63f)
                        lineToRelative(6.4f, 6.4f)
                        close()
                        moveTo(11.8f, 11.8f)
                        close()
                    }
                }
                .build()
        return _build!!
    }

private var _build: ImageVector? = null