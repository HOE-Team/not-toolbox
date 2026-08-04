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
public val battery_android_alert: ImageVector
    get() {
        if (_battery_android_alert != null) {
            return _battery_android_alert!!
        }
        _battery_android_alert =
            ImageVector.Builder(
                name = "battery_android_alert",
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
                        moveTo(20.29f, 16.21f)
                        quadTo(20f, 15.93f, 20f, 15.5f)
                        reflectiveQuadToRelative(0.29f, -0.71f)
                        reflectiveQuadTo(21f, 14.5f)
                        quadToRelative(0.43f, 0f, 0.71f, 0.29f)
                        reflectiveQuadTo(22f, 15.5f)
                        reflectiveQuadToRelative(-0.29f, 0.71f)
                        reflectiveQuadTo(21f, 16.5f)
                        reflectiveQuadTo(20.29f, 16.21f)
                        close()
                        moveTo(20f, 13f)
                        verticalLineTo(7f)
                        horizontalLineToRelative(2f)
                        verticalLineToRelative(6f)
                        horizontalLineTo(20f)
                        close()
                        moveToRelative(-1.22f, 5f)
                        horizontalLineTo(4f)
                        quadTo(2.75f, 18f, 1.88f, 17.13f)
                        reflectiveQuadTo(1f, 15f)
                        verticalLineTo(9f)
                        quadTo(1f, 7.75f, 1.88f, 6.88f)
                        reflectiveQuadTo(4f, 6f)
                        horizontalLineTo(18f)
                        verticalLineTo(8f)
                        horizontalLineTo(4f)
                        quadTo(3.58f, 8f, 3.29f, 8.29f)
                        reflectiveQuadTo(3f, 9f)
                        verticalLineToRelative(6f)
                        quadToRelative(0f, 0.42f, 0.29f, 0.71f)
                        reflectiveQuadTo(4f, 16f)
                        horizontalLineTo(18f)
                        quadToRelative(0f, 0.57f, 0.21f, 1.09f)
                        reflectiveQuadTo(18.78f, 18f)
                        close()
                        moveTo(3f, 16f)
                        quadToRelative(0f, 0f, 0f, -0.29f)
                        reflectiveQuadTo(3f, 15f)
                        verticalLineTo(9f)
                        quadTo(3f, 8.57f, 3f, 8.29f)
                        reflectiveQuadTo(3f, 8f)
                        verticalLineToRelative(8f)
                        close()
                    }
                }
                .build()
        return _battery_android_alert!!
    }

private var _battery_android_alert: ImageVector? = null