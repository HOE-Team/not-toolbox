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
public val room_service: ImageVector
    get() {
        if (_room_service != null) {
            return _room_service!!
        }
        _room_service =
            ImageVector.Builder(
                name = "room_service",
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
                        moveTo(2f, 19f)
                        verticalLineTo(17f)
                        horizontalLineTo(22f)
                        verticalLineToRelative(2f)
                        horizontalLineTo(2f)
                        close()
                        moveTo(3f, 16f)
                        verticalLineTo(15f)
                        quadTo(3f, 11.8f, 4.96f, 9.35f)
                        reflectiveQuadTo(10f, 6.25f)
                        verticalLineTo(6f)
                        quadTo(10f, 5.18f, 10.59f, 4.59f)
                        reflectiveQuadTo(12f, 4f)
                        reflectiveQuadToRelative(1.41f, 0.59f)
                        quadTo(14f, 5.18f, 14f, 6f)
                        verticalLineTo(6.25f)
                        quadToRelative(3.1f, 0.65f, 5.05f, 3.1f)
                        reflectiveQuadTo(21f, 15f)
                        verticalLineToRelative(1f)
                        horizontalLineTo(3f)
                        close()
                        moveTo(5.05f, 14f)
                        horizontalLineToRelative(13.9f)
                        quadTo(18.6f, 11.4f, 16.63f, 9.7f)
                        reflectiveQuadTo(12f, 8f)
                        reflectiveQuadTo(7.39f, 9.7f)
                        reflectiveQuadTo(5.05f, 14f)
                        close()
                        moveTo(12f, 14f)
                        close()
                    }
                }
                .build()
        return _room_service!!
    }

private var _room_service: ImageVector? = null