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
public val battery_android_frame_question: ImageVector
    get() {
        if (_battery_android_frame_question != null) {
            return _battery_android_frame_question!!
        }
        _battery_android_frame_question =
            ImageVector.Builder(
                name = "battery_android_frame_question",
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
                        moveTo(4f, 18f)
                        quadTo(2.75f, 18f, 1.88f, 17.13f)
                        reflectiveQuadTo(1f, 15f)
                        verticalLineTo(9f)
                        quadTo(1f, 7.75f, 1.88f, 6.88f)
                        reflectiveQuadTo(4f, 6f)
                        horizontalLineTo(17.03f)
                        quadToRelative(-0.5f, 0.4f, -0.88f, 0.9f)
                        reflectiveQuadTo(15.53f, 8f)
                        horizontalLineTo(4f)
                        quadTo(3.58f, 8f, 3.29f, 8.29f)
                        reflectiveQuadTo(3f, 9f)
                        verticalLineToRelative(6f)
                        quadToRelative(0f, 0.42f, 0.29f, 0.71f)
                        reflectiveQuadTo(4f, 16f)
                        horizontalLineTo(17.05f)
                        quadToRelative(0.1f, 0.63f, 0.45f, 1.14f)
                        reflectiveQuadTo(18.35f, 18f)
                        horizontalLineTo(4f)
                        close()
                        moveTo(20.71f, 16.21f)
                        quadTo(21f, 15.93f, 21f, 15.5f)
                        reflectiveQuadTo(20.71f, 14.79f)
                        reflectiveQuadTo(20f, 14.5f)
                        reflectiveQuadToRelative(-0.71f, 0.29f)
                        reflectiveQuadTo(19f, 15.5f)
                        reflectiveQuadToRelative(0.29f, 0.71f)
                        reflectiveQuadTo(20f, 16.5f)
                        quadToRelative(0.43f, 0f, 0.71f, -0.29f)
                        close()
                        moveTo(19.3f, 13.48f)
                        horizontalLineToRelative(1.43f)
                        quadToRelative(0f, -0.28f, 0f, -0.54f)
                        reflectiveQuadToRelative(0.13f, -0.49f)
                        quadTo(21f, 12.13f, 21.24f, 11.9f)
                        quadToRelative(0.24f, -0.22f, 0.49f, -0.47f)
                        quadTo(22.15f, 11f, 22.44f, 10.5f)
                        reflectiveQuadTo(22.73f, 9.42f)
                        quadToRelative(0f, -1.05f, -0.81f, -1.74f)
                        reflectiveQuadTo(20f, 7f)
                        quadTo(19.05f, 7f, 18.3f, 7.55f)
                        reflectiveQuadTo(17.3f, 9f)
                        lineToRelative(1.27f, 0.52f)
                        quadTo(18.73f, 9.02f, 19.11f, 8.7f)
                        reflectiveQuadTo(20f, 8.38f)
                        quadToRelative(0.53f, 0f, 0.91f, 0.3f)
                        quadToRelative(0.39f, 0.3f, 0.39f, 0.8f)
                        quadToRelative(0f, 0.42f, -0.25f, 0.76f)
                        reflectiveQuadTo(20.5f, 10.85f)
                        quadToRelative(-0.27f, 0.28f, -0.56f, 0.54f)
                        reflectiveQuadTo(19.48f, 12f)
                        quadToRelative(-0.15f, 0.35f, -0.16f, 0.71f)
                        quadTo(19.3f, 13.08f, 19.3f, 13.48f)
                        close()
                        moveTo(4f, 15f)
                        verticalLineTo(9f)
                        horizontalLineTo(15.53f)
                        verticalLineToRelative(6f)
                        horizontalLineTo(4f)
                        close()
                    }
                }
                .build()
        return _battery_android_frame_question!!
    }

private var _battery_android_frame_question: ImageVector? = null