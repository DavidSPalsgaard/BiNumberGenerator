package dk.binumre.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Orienteringens farver: postflagets orange og hvide, kortets brune højdekurver, grønne skov og blå vand. */
object OColors {
    val Orange = Color(0xFFEE6A1B)
    val OrangeDark = Color(0xFFB84A08)
    val Contour = Color(0xFFA0602E)
    val Forest = Color(0xFF3B8C3A)
    val Water = Color(0xFF2E8BC9)
    val Paper = Color(0xFFFBF8F3)
    val Ink = Color(0xFF231F1C)
}

private val colors = lightColorScheme(
    primary = OColors.Orange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE2CF),
    onPrimaryContainer = Color(0xFF3A1600),
    secondary = OColors.Contour,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4E6D9),
    onSecondaryContainer = Color(0xFF3B2412),
    tertiary = OColors.Forest,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCEFD6),
    onTertiaryContainer = Color(0xFF0E2E0D),
    background = OColors.Paper,
    onBackground = OColors.Ink,
    surface = Color.White,
    onSurface = OColors.Ink,
    surfaceVariant = Color(0xFFF2ECE5),
    onSurfaceVariant = Color(0xFF5C534C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCF9F5),
    surfaceContainer = Color(0xFFF6F1EB),
    surfaceContainerHigh = Color(0xFFF1EBE4),
    surfaceContainerHighest = Color(0xFFEBE4DC),
    outline = Color(0xFFCFC3B7),
    outlineVariant = Color(0xFFE6DDD3),
    error = Color(0xFFC62828),
    errorContainer = Color(0xFFFDE3E1),
)

// Lidt mindre end Materials standard, så der er plads til tabeller og paneler på en bærbar
private val base = Typography()
private fun TextStyle.sized(size: Int, line: Int, weight: FontWeight? = fontWeight) = copy(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)
private val typography = base.copy(
    headlineLarge = base.headlineLarge.sized(28, 34, FontWeight.Bold),
    headlineSmall = base.headlineSmall.sized(20, 26, FontWeight.SemiBold),
    titleLarge = base.titleLarge.sized(18, 24, FontWeight.SemiBold),
    titleMedium = base.titleMedium.sized(14, 20, FontWeight.SemiBold),
    titleSmall = base.titleSmall.sized(13, 18),
    bodyLarge = base.bodyLarge.sized(14, 20),
    bodyMedium = base.bodyMedium.sized(13, 18),
    bodySmall = base.bodySmall.sized(11, 15),
    labelLarge = base.labelLarge.sized(13, 18, FontWeight.SemiBold),
    labelMedium = base.labelMedium.sized(11, 15),
    labelSmall = base.labelSmall.sized(10, 14),
)

const val UI_SCALE = 1f

@Composable
fun OTheme(content: @Composable () -> Unit) {
    // Skalering af hele brugerfladen (1 = standard)
    val d = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density * UI_SCALE, d.fontScale),
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = typography,
            shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(18.dp)),
            content = content,
        )
    }
}

/** Ikoner der ikke findes i Compose' grundsæt (Material Design-ikoner, Apache 2.0). */
object AppIcons {
    private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(path), fill = SolidColor(Color.Black)).build()

    val Print by lazy { icon("Print", "M19,8L5,8c-1.66,0 -3,1.34 -3,3v6h4v4h12v-4h4v-6c0,-1.66 -1.34,-3 -3,-3zM16,19L8,19v-5h8v5zM19,12c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1 1,0.45 1,1 -0.45,1 -1,1zM18,3L6,3v4h12L18,3z") }
    val Pdf by lazy { icon("Pdf", "M20,2L8,2c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2L22,4c0,-1.1 -0.9,-2 -2,-2zM11.5,9.5c0,0.83 -0.67,1.5 -1.5,1.5L9,11v2L7.5,13L7.5,7L10,7c0.83,0 1.5,0.67 1.5,1.5v1zM16.5,11.5c0,0.83 -0.67,1.5 -1.5,1.5h-2.5L12.5,7L15,7c0.83,0 1.5,0.67 1.5,1.5v3zM20.5,8.5L19,8.5v1h1.5L20.5,11L19,11v2h-1.5L17.5,7h3v1.5zM9,9.5h1v-1L9,8.5v1zM4,6L2,6v14c0,1.1 0.9,2 2,2h14v-2L4,20L4,6zM14,11.5h1v-3h-1v3z") }
    val Folder by lazy { icon("Folder", "M20,6h-8l-2,-2L4,4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2L22,8c0,-1.1 -0.9,-2 -2,-2zM20,18L4,18L4,8h16v10z") }
    val Save by lazy { icon("Save", "M17,3L5,3c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2L21,7l-4,-4zM12,19c-1.66,0 -3,-1.34 -3,-3s1.34,-3 3,-3 3,1.34 3,3 -1.34,3 -3,3zM15,9L5,9L5,5h10v4z") }
    val Image by lazy { icon("Image", "M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2zM8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z") }
    val Design by lazy { icon("Design", "M3,13h8L11,3L3,3v10zM3,21h8v-6L3,15v6zM13,21h8L21,11h-8v10zM13,3v6h8L21,3h-8z") }
    val Upload by lazy { icon("Upload", "M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96zM14,13v4h-4v-4H7l5,-5 5,5h-3z") }
    val Cloud by lazy { icon("Cloud", "M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96zM17,13l-5,5 -5,-5h3V9h4v4h3z") }
    val Help by lazy { icon("Help", "M11,18h2v-2h-2v2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8zM12,6c-2.21,0 -4,1.79 -4,4h2c0,-1.1 0.9,-2 2,-2s2,0.9 2,2c0,2 -3,1.75 -3,5h2c0,-2.25 3,-2.5 3,-5 0,-2.21 -1.79,-4 -4,-4z") }
    val Restart by lazy { icon("Restart", "M12,5V1L7,6l5,5V7c3.31,0 6,2.69 6,6s-2.69,6 -6,6 -6,-2.69 -6,-6H4c0,4.42 3.58,8 8,8s8,-3.58 8,-8 -3.58,-8 -8,-8z") }
}

/** Postflaget: hvidt og orange delt på skrå. */
@Composable
fun ControlFlag(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        drawRect(Color.White)
        val p = Path().apply { moveTo(w, 0f); lineTo(w, w); lineTo(0f, w); close() }
        drawPath(p, OColors.Orange)
        drawRect(Color(0x33000000), style = Stroke(1.dp.toPx()))
    }
}

/** Baggrund med højdekurver som på et orienteringskort. */
@Composable
fun ContourBackground(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val hills = listOf(
            Triple(0.12f, 0.2f, 0.32f), Triple(0.85f, 0.15f, 0.26f), Triple(0.72f, 0.85f, 0.38f),
            Triple(0.18f, 0.9f, 0.22f), Triple(0.5f, 0.48f, 0.16f),
        )
        val unit = maxOf(size.width, size.height)
        hills.forEachIndexed { h, (fx, fy, fr) ->
            val center = Offset(size.width * fx, size.height * fy)
            for (ring in 0 until 7) {
                val radius = unit * fr * (1f - ring * 0.13f)
                if (radius <= 4f) continue
                val path = Path()
                val steps = 140
                for (i in 0..steps) {
                    val t = 2 * PI * i / steps
                    val wobble = 1 + 0.10 * sin(3 * t + h * 1.7 + ring * 0.35) + 0.06 * sin(5 * t + h * 0.9 - ring * 0.2)
                    val x = center.x + (radius * wobble * cos(t)).toFloat()
                    val y = center.y + (radius * wobble * sin(t) * 0.75).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                val index = ring % 5 == 0
                drawPath(path, OColors.Contour.copy(alpha = if (index) 0.22f else 0.13f), style = Stroke((if (index) 2.2f else 1.2f).dp.toPx()))
            }
        }
    }
}

/** Lille tegning af et startnummer – til startskærmens valg af løbstype. */
@Composable
fun MiniBib(number: String, band: Color? = null, rows: Int = 1, modifier: Modifier = Modifier) {
    Box(
        modifier.size(84.dp, 60.dp).background(Color.White, RoundedCornerShape(4.dp))
            .border(1.dp, Color(0x33000000), RoundedCornerShape(4.dp)),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (band != null) Box(Modifier.fillMaxWidth().height(9.dp).background(band, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
            Text(number, style = TextStyle(fontSize = if (band != null) 22.sp else 26.sp, fontWeight = FontWeight.Black, color = OColors.Ink))
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp, vertical = 5.dp)) {
            repeat(rows) {
                Box(Modifier.padding(top = 2.dp).fillMaxWidth().height(2.dp).background(Color(0x33000000)))
            }
        }
        // Foldehjørnet
        Canvas(Modifier.size(10.dp).align(Alignment.TopStart)) {
            val p = Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(0f, size.height); close() }
            drawPath(p, Color(0xFFDDDDDD))
        }
    }
}
