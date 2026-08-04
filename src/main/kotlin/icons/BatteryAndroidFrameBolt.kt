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
public val battery_android_frame_bolt: ImageVector
    get() {
        if (_battery_android_frame_bolt != null) {
            return _battery_android_frame_bolt!!
        }
        _battery_android_frame_bolt =
            ImageVector.Builder(
                name = "battery_android_frame_bolt",
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
                        horizontalLineTo(18.05f)
                        lineToRelative(-1.6f, 2f)
                        horizontalLineTo(4f)
                        quadTo(3.58f, 8f, 3.29f, 8.29f)
                        reflectiveQuadTo(3f, 9f)
                        verticalLineToRelative(6f)
                        quadToRelative(0f, 0.42f, 0.29f, 0.71f)
                        reflectiveQuadTo(4f, 16f)
                        horizontalLineTo(15.83f)
                        lineToRelative(-0.38f, 2f)
                        horizontalLineTo(4f)
                        close()
                        moveTo(17.68f, 17f)
                        lineToRelative(0.7f, -4f)
                        horizontalLineTo(15f)
                        lineTo(19.8f, 7f)
                        horizontalLineToRelative(0.53f)
                        lineToRelative(-0.7f, 4f)
                        horizontalLineTo(23f)
                        lineToRelative(-4.8f, 6f)
                        horizontalLineTo(17.68f)
                        close()
                        moveTo(4f, 15f)
                        verticalLineTo(9f)
                        horizontalLineTo(15.65f)
                        lineToRelative(-4.8f, 6f)
                        horizontalLineTo(4f)
                        close()
                    }
                }
                .build()
        return _battery_android_frame_bolt!!
    }

private var _battery_android_frame_bolt: ImageVector? = null