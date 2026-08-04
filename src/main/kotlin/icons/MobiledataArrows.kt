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
public val mobiledata_arrows: ImageVector
    get() {
        if (_mobiledata_arrows != null) {
            return _mobiledata_arrows!!
        }
        _mobiledata_arrows =
            ImageVector.Builder(
                name = "mobiledata_arrows",
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
                        moveTo(8f, 9.98f)
                        horizontalLineToRelative(2f)
                        verticalLineToRelative(7.17f)
                        lineTo(11.55f, 15.6f)
                        lineTo(13f, 17f)
                        lineTo(9f, 21f)
                        lineTo(5f, 17f)
                        lineTo(6.45f, 15.6f)
                        lineTo(8f, 17.15f)
                        verticalLineTo(9.98f)
                        close()
                        moveToRelative(8f, 4f)
                        horizontalLineTo(14f)
                        verticalLineTo(6.8f)
                        lineTo(12.4f, 8.4f)
                        lineTo(11f, 7f)
                        lineTo(15f, 3f)
                        lineToRelative(4f, 4f)
                        lineTo(17.6f, 8.4f)
                        lineTo(16f, 6.8f)
                        verticalLineToRelative(7.17f)
                        close()
                    }
                }
                .build()
        return _mobiledata_arrows!!
    }

private var _mobiledata_arrows: ImageVector? = null