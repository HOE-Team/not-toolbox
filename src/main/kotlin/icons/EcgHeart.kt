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
public val ecg_heart: ImageVector
    get() {
        if (_ecg_heart != null) {
            return _ecg_heart!!
        }
        _ecg_heart =
            ImageVector.Builder(
                name = "ecg_heart",
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
                        moveTo(12f, 12f)
                        close()
                        moveToRelative(0f, 9f)
                        quadToRelative(-0.45f, 0f, -0.86f, -0.16f)
                        quadTo(10.73f, 20.68f, 10.4f, 20.35f)
                        lineTo(3.7f, 13.63f)
                        quadToRelative(-0.88f, -0.88f, -1.29f, -2f)
                        reflectiveQuadTo(2f, 9.27f)
                        quadTo(2f, 6.7f, 3.68f, 4.85f)
                        reflectiveQuadTo(7.85f, 3f)
                        quadToRelative(1.2f, 0f, 2.26f, 0.47f)
                        reflectiveQuadTo(12f, 4.8f)
                        quadTo(12.8f, 3.95f, 13.86f, 3.47f)
                        reflectiveQuadTo(16.13f, 3f)
                        quadToRelative(2.5f, 0f, 4.19f, 1.85f)
                        reflectiveQuadTo(22f, 9.25f)
                        quadToRelative(0f, 1.22f, -0.42f, 2.35f)
                        reflectiveQuadToRelative(-1.28f, 2f)
                        lineToRelative(-6.73f, 6.75f)
                        quadToRelative(-0.33f, 0.32f, -0.72f, 0.49f)
                        reflectiveQuadTo(12f, 21f)
                        close()
                        moveTo(13f, 8f)
                        quadToRelative(0.25f, 0f, 0.48f, 0.13f)
                        reflectiveQuadToRelative(0.35f, 0.32f)
                        lineTo(15.53f, 11f)
                        horizontalLineToRelative(4.15f)
                        quadToRelative(0.18f, -0.43f, 0.26f, -0.86f)
                        quadTo(20.03f, 9.7f, 20.03f, 9.25f)
                        quadTo(19.98f, 7.52f, 18.88f, 6.29f)
                        quadTo(17.78f, 5.05f, 16.13f, 5.05f)
                        quadToRelative(-0.77f, 0f, -1.49f, 0.3f)
                        reflectiveQuadTo(13.4f, 6.22f)
                        lineTo(12.73f, 6.95f)
                        quadTo(12.6f, 7.1f, 12.4f, 7.19f)
                        quadTo(12.2f, 7.27f, 12f, 7.27f)
                        reflectiveQuadTo(11.6f, 7.19f)
                        reflectiveQuadTo(11.25f, 6.95f)
                        lineTo(10.58f, 6.22f)
                        quadTo(10.05f, 5.65f, 9.35f, 5.32f)
                        reflectiveQuadTo(7.85f, 5f)
                        quadTo(6.2f, 5f, 5.1f, 6.26f)
                        reflectiveQuadTo(4f, 9.25f)
                        quadTo(4f, 9.7f, 4.08f, 10.14f)
                        reflectiveQuadTo(4.33f, 11f)
                        horizontalLineTo(9f)
                        quadToRelative(0.25f, 0f, 0.47f, 0.13f)
                        reflectiveQuadToRelative(0.35f, 0.32f)
                        lineToRelative(0.88f, 1.3f)
                        lineTo(12.05f, 8.7f)
                        quadToRelative(0.1f, -0.3f, 0.36f, -0.5f)
                        reflectiveQuadTo(13f, 8f)
                        close()
                        moveToRelative(0.3f, 3.25f)
                        lineTo(11.95f, 15.3f)
                        quadToRelative(-0.1f, 0.3f, -0.38f, 0.5f)
                        reflectiveQuadTo(10.98f, 16f)
                        quadTo(10.73f, 16f, 10.5f, 15.88f)
                        quadTo(10.28f, 15.75f, 10.15f, 15.55f)
                        lineTo(8.45f, 13f)
                        horizontalLineTo(5.9f)
                        lineToRelative(5.93f, 5.93f)
                        quadToRelative(0.05f, 0.05f, 0.09f, 0.06f)
                        reflectiveQuadTo(12f, 19f)
                        reflectiveQuadToRelative(0.09f, -0.01f)
                        reflectiveQuadToRelative(0.09f, -0.06f)
                        lineTo(18.08f, 13f)
                        horizontalLineTo(15f)
                        quadToRelative(-0.25f, 0f, -0.47f, -0.13f)
                        reflectiveQuadTo(14.15f, 12.55f)
                        lineTo(13.3f, 11.25f)
                        close()
                    }
                }
                .build()
        return _ecg_heart!!
    }

private var _ecg_heart: ImageVector? = null