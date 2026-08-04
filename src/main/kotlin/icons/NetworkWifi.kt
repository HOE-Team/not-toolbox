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
public val network_wifi: ImageVector
    get() {
        if (_network_wifi != null) {
            return _network_wifi!!
        }
        _network_wifi =
            ImageVector.Builder(
                name = "network_wifi",
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
                        moveTo(12f, 21f)
                        lineTo(0f, 9f)
                        quadTo(2.38f, 6.57f, 5.49f, 5.29f)
                        reflectiveQuadTo(12f, 4f)
                        quadToRelative(3.43f, 0f, 6.53f, 1.27f)
                        reflectiveQuadTo(24f, 9f)
                        lineTo(12f, 21f)
                        close()
                        moveTo(4.35f, 10.5f)
                        quadTo(6.03f, 9.3f, 7.98f, 8.65f)
                        reflectiveQuadTo(12f, 8f)
                        reflectiveQuadToRelative(4.03f, 0.65f)
                        reflectiveQuadToRelative(3.63f, 1.85f)
                        lineTo(21.1f, 9.05f)
                        quadTo(19.13f, 7.55f, 16.8f, 6.77f)
                        reflectiveQuadTo(12f, 6f)
                        quadTo(9.53f, 6f, 7.2f, 6.77f)
                        reflectiveQuadTo(2.9f, 9.05f)
                        lineTo(4.35f, 10.5f)
                        close()
                    }
                }
                .build()
        return _network_wifi!!
    }

private var _network_wifi: ImageVector? = null