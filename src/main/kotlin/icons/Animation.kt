package icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val animation: ImageVector
    get() {
        if (_animation != null) {
            return _animation!!
        }
        _animation =
            ImageVector.Builder(
                name = "animation",
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
                        moveTo(9f, 22f)
                        quadTo(7.55f, 22f, 6.28f, 21.45f)
                        reflectiveQuadTo(4.05f, 19.95f)
                        reflectiveQuadTo(2.55f, 17.73f)
                        reflectiveQuadTo(2f, 15f)
                        quadTo(2f, 12.98f, 3.05f, 11.3f)
                        quadTo(4.1f, 9.63f, 5.8f, 8.75f)
                        quadTo(6.3f, 7.77f, 7.04f, 7.04f)
                        reflectiveQuadTo(8.75f, 5.8f)
                        quadTo(9.58f, 4.1f, 11.28f, 3.05f)
                        reflectiveQuadTo(15f, 2f)
                        quadToRelative(1.45f, 0f, 2.73f, 0.55f)
                        reflectiveQuadToRelative(2.23f, 1.5f)
                        reflectiveQuadToRelative(1.5f, 2.23f)
                        reflectiveQuadTo(22f, 9f)
                        quadToRelative(0f, 2.13f, -1.05f, 3.75f)
                        reflectiveQuadToRelative(-2.75f, 2.5f)
                        quadToRelative(-0.5f, 0.98f, -1.24f, 1.71f)
                        reflectiveQuadTo(15.25f, 18.2f)
                        quadToRelative(-0.88f, 1.7f, -2.55f, 2.75f)
                        reflectiveQuadTo(9f, 22f)
                        close()
                        moveTo(9f, 20f)
                        quadToRelative(0.83f, 0f, 1.59f, -0.25f)
                        reflectiveQuadTo(12f, 19f)
                        quadTo(10.55f, 19f, 9.28f, 18.45f)
                        reflectiveQuadTo(7.05f, 16.95f)
                        reflectiveQuadTo(5.55f, 14.73f)
                        reflectiveQuadTo(5f, 12f)
                        quadTo(4.5f, 12.65f, 4.25f, 13.41f)
                        reflectiveQuadTo(4f, 15f)
                        quadToRelative(0f, 1.05f, 0.4f, 1.95f)
                        reflectiveQuadToRelative(1.08f, 1.57f)
                        reflectiveQuadTo(7.05f, 19.6f)
                        reflectiveQuadTo(9f, 20f)
                        close()
                        moveToRelative(3f, -3f)
                        quadToRelative(0.83f, 0f, 1.61f, -0.25f)
                        reflectiveQuadTo(15.05f, 16f)
                        quadTo(13.58f, 16f, 12.3f, 15.44f)
                        reflectiveQuadTo(10.08f, 13.93f)
                        quadTo(9.13f, 12.98f, 8.56f, 11.7f)
                        reflectiveQuadTo(8f, 8.95f)
                        quadTo(7.5f, 9.6f, 7.25f, 10.39f)
                        reflectiveQuadTo(7f, 12f)
                        quadToRelative(0f, 1.05f, 0.39f, 1.95f)
                        reflectiveQuadToRelative(1.09f, 1.58f)
                        quadToRelative(0.68f, 0.7f, 1.58f, 1.09f)
                        reflectiveQuadTo(12f, 17f)
                        close()
                        moveToRelative(3f, -3f)
                        quadToRelative(0.45f, 0f, 0.86f, -0.08f)
                        quadTo(16.28f, 13.85f, 16.7f, 13.7f)
                        quadToRelative(0.55f, -1.5f, 0.16f, -2.89f)
                        quadTo(16.48f, 9.42f, 15.53f, 8.48f)
                        quadTo(14.58f, 7.52f, 13.19f, 7.14f)
                        reflectiveQuadTo(10.3f, 7.3f)
                        quadTo(10.15f, 7.72f, 10.08f, 8.14f)
                        reflectiveQuadTo(10f, 9f)
                        quadToRelative(0f, 1.05f, 0.39f, 1.95f)
                        reflectiveQuadToRelative(1.09f, 1.57f)
                        quadToRelative(0.67f, 0.7f, 1.57f, 1.09f)
                        reflectiveQuadTo(15f, 14f)
                        close()
                        moveToRelative(4f, -1.95f)
                        quadToRelative(0.5f, -0.65f, 0.75f, -1.44f)
                        reflectiveQuadTo(20f, 9f)
                        quadTo(20f, 7.95f, 19.61f, 7.05f)
                        reflectiveQuadTo(18.53f, 5.47f)
                        quadTo(17.85f, 4.77f, 16.95f, 4.39f)
                        reflectiveQuadTo(15f, 4f)
                        quadTo(14.13f, 4f, 13.36f, 4.25f)
                        reflectiveQuadTo(11.95f, 5f)
                        quadToRelative(1.47f, 0f, 2.75f, 0.56f)
                        reflectiveQuadToRelative(2.22f, 1.51f)
                        reflectiveQuadTo(18.44f, 9.3f)
                        reflectiveQuadTo(19f, 12.05f)
                        close()
                        moveTo(15f, 9f)
                        close()
                    }
                }
                .build()
        return _animation!!
    }

private var _animation: ImageVector? = null