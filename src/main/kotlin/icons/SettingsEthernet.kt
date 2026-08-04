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
public val settings_ethernet: ImageVector
    get() {
        if (_settings_ethernet != null) {
            return _settings_ethernet!!
        }
        _settings_ethernet =
            ImageVector.Builder(
                name = "settings_ethernet",
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
                        moveTo(17f, 18f)
                        lineTo(15.6f, 16.6f)
                        lineTo(20.15f, 12f)
                        lineTo(15.6f, 7.4f)
                        lineTo(17f, 6f)
                        lineToRelative(6f, 6f)
                        lineToRelative(-6f, 6f)
                        close()
                        moveTo(7f, 18f)
                        lineTo(1f, 12f)
                        lineTo(7f, 6f)
                        lineTo(8.4f, 7.4f)
                        lineTo(3.85f, 12f)
                        lineTo(8.4f, 16.6f)
                        lineTo(7f, 18f)
                        close()
                        moveTo(7.29f, 12.71f)
                        quadTo(7f, 12.43f, 7f, 12f)
                        reflectiveQuadTo(7.29f, 11.29f)
                        reflectiveQuadTo(8f, 11f)
                        reflectiveQuadToRelative(0.71f, 0.29f)
                        reflectiveQuadTo(9f, 12f)
                        reflectiveQuadTo(8.71f, 12.71f)
                        reflectiveQuadTo(8f, 13f)
                        quadTo(7.58f, 13f, 7.29f, 12.71f)
                        close()
                        moveToRelative(4f, 0f)
                        quadTo(11f, 12.43f, 11f, 12f)
                        reflectiveQuadToRelative(0.29f, -0.71f)
                        reflectiveQuadTo(12f, 11f)
                        reflectiveQuadToRelative(0.71f, 0.29f)
                        reflectiveQuadTo(13f, 12f)
                        reflectiveQuadToRelative(-0.29f, 0.71f)
                        reflectiveQuadTo(12f, 13f)
                        reflectiveQuadTo(11.29f, 12.71f)
                        close()
                        moveToRelative(4f, 0f)
                        quadTo(15f, 12.43f, 15f, 12f)
                        reflectiveQuadToRelative(0.29f, -0.71f)
                        reflectiveQuadTo(16f, 11f)
                        quadToRelative(0.43f, 0f, 0.71f, 0.29f)
                        reflectiveQuadTo(17f, 12f)
                        reflectiveQuadToRelative(-0.29f, 0.71f)
                        reflectiveQuadTo(16f, 13f)
                        reflectiveQuadTo(15.29f, 12.71f)
                        close()
                    }
                }
                .build()
        return _settings_ethernet!!
    }

private var _settings_ethernet: ImageVector? = null