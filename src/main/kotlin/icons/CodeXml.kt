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
public val code_xml: ImageVector
    get() {
        if (_code_xml != null) {
            return _code_xml!!
        }
        _code_xml =
            ImageVector.Builder(
                name = "code_xml",
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
                        moveTo(6f, 17f)
                        lineTo(1f, 12f)
                        lineTo(6f, 7f)
                        lineTo(7.4f, 8.4f)
                        lineTo(3.83f, 12f)
                        lineTo(7.4f, 15.6f)
                        lineTo(6f, 17f)
                        close()
                        moveToRelative(4.45f, 3.3f)
                        lineTo(8.55f, 19.7f)
                        lineToRelative(5f, -16f)
                        lineToRelative(1.9f, 0.6f)
                        lineToRelative(-5f, 16f)
                        close()
                        moveTo(18f, 17f)
                        lineTo(16.6f, 15.6f)
                        lineTo(20.18f, 12f)
                        lineTo(16.6f, 8.4f)
                        lineTo(18f, 7f)
                        lineToRelative(5f, 5f)
                        lineToRelative(-5f, 5f)
                        close()
                    }
                }
                .build()
        return _code_xml!!
    }

private var _code_xml: ImageVector? = null