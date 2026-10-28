package com.example.findme.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.findme.R
import com.example.findme.ui.theme.YColors
import com.example.findme.ui.theme.yPalette

/** Search rubric: what is shown and what is searched for. */
data class Category(val title: String, @param:DrawableRes val icon: Int, val query: String = title)

/** Rubrics of the full-screen search, in the order of Yandex Maps. */
val Categories = listOf(
    Category("Sights", R.drawable.ms_bolt),
    Category("Restaurants", R.drawable.ms_restaurant),
    Category("Groceries", R.drawable.ms_shopping_basket),
    Category("Pharmacies", R.drawable.ic_cross),
    Category("Beauty salons", R.drawable.ms_content_cut),
    Category("ATMs", R.drawable.ms_local_atm),
    Category("Gas", R.drawable.ms_local_gas_station),
    Category("Hospitals", R.drawable.ic_cross),
    Category("Cafes", R.drawable.ms_local_cafe),
    Category("Hotels", R.drawable.ms_hotel),
    Category("Parking", R.drawable.ms_local_parking),
    Category("Banks", R.drawable.ms_account_balance),
    Category("Wi-Fi hotspots", R.drawable.ms_wifi, "Free Wi-Fi"),
    Category("Bars and pubs", R.drawable.ms_wine_bar),
    Category("Shopping malls", R.drawable.ms_local_mall),
    Category("Cinemas", R.drawable.ms_videocam),
    Category("Delivery", R.drawable.ms_shopping_bag, "Food delivery"),
    Category("Carwashes", R.drawable.ms_local_car_wash),
    Category("Auto repair", R.drawable.ms_car_repair),
    Category("Fitness", R.drawable.ms_fitness_center),
)

/** Chip row on the main sheet. */
val QuickCategories = listOf(
    Category("Coffee", R.drawable.ms_local_cafe, "Cafes"),
    Category("Restaurants", R.drawable.ms_restaurant),
    Category("Gas", R.drawable.ms_local_gas_station, "Gas stations"),
    Category("Pharmacy", R.drawable.ms_local_pharmacy, "Pharmacies"),
    Category("Groceries", R.drawable.ms_shopping_basket),
    Category("Transport", R.drawable.ms_directions_bus, "Public transport stops"),
    Category("ATMs", R.drawable.ms_local_atm),
    Category("Hotels", R.drawable.ms_hotel),
)

@Composable
fun YIcon(
    @DrawableRes res: Int,
    contentDescription: String? = null,
    tint: Color = yPalette.icon,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier,
) {
    Icon(painterResource(res), contentDescription, tint = tint, modifier = modifier.size(size))
}

@Composable
fun Grabber() {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(yPalette.grabber)
        )
    }
}

/** White floating map button: rounded square by default, or a circle. */
@Composable
fun MapButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = shape,
        color = yPalette.button,
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center, content = content)
    }
}

/** White pill on the map with an icon and optional text (home/work buttons). */
@Composable
fun MapPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(16.dp),
        color = yPalette.button,
        shadowElevation = 4.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** Light circle with a gray cross: the close button of cards and sheets. */
@Composable
fun CloseCircle(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(yPalette.closeCircle)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        YIcon(R.drawable.ms_close, "Close", tint = yPalette.textSecondary, size = size * 0.55f)
    }
}

/** Gray circle with an icon, e.g. the back button of the route sheet. */
@Composable
fun CircleIconButton(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    background: Color = yPalette.closeCircle,
    tint: Color = yPalette.icon,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        YIcon(icon, contentDescription, tint = tint, size = size * 0.55f)
    }
}

/**
 * Card action button: blue primary ("Directions", "Go"), light-blue secondary,
 * or coral for meet-up actions ("Meet here").
 */
@Composable
fun ActionPill(
    text: String?,
    @DrawableRes icon: Int?,
    onClick: () -> Unit,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    iconTint: Color? = null,
    coral: Boolean = false,
) {
    val bg = when {
        coral -> yPalette.coral
        primary -> yPalette.accent
        else -> yPalette.accentSoft
    }
    val fg = when {
        coral -> yPalette.onCoral
        primary -> yPalette.onAccent
        else -> yPalette.accent
    }
    Row(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = if (text == null) 16.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) YIcon(icon, text, tint = iconTint ?: fg, size = 24.dp)
        if (icon != null && text != null) Spacer(Modifier.width(10.dp))
        if (text != null) {
            Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Gray circle with a dark icon and a caption below, used in the rubric grid. */
@Composable
fun CategoryTile(category: Category, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(yPalette.field),
            contentAlignment = Alignment.Center,
        ) {
            YIcon(category.icon, null, tint = yPalette.iconSecondary, size = 26.dp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            category.title,
            style = MaterialTheme.typography.bodyMedium,
            color = yPalette.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/**
 * Segmented switch like "Categories | History" or "Auto | Day | Night": a gray track with the
 * chosen segment raised. [fill] stretches it to the full width; otherwise it hugs its labels.
 */
@Composable
fun SegmentedControl(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
) {
    Row(
        modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .clip(RoundedCornerShape(14.dp))
            .background(yPalette.field)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { index, title ->
            val isSelected = index == selected
            Box(
                Modifier
                    .then(if (fill) Modifier.weight(1f) else Modifier)
                    .height(36.dp)
                    .shadow(if (isSelected) 2.dp else 0.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) yPalette.selectedTab else Color.Transparent)
                    .selectable(isSelected, role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) yPalette.onSelectedTab else yPalette.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Pill switch: blue track when on, gray when off, white knob. With [onChange] null it only
 * shows the state (the whole row is the toggle then).
 */
@Composable
fun YSwitch(checked: Boolean, onChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val knob by animateDpAsState(if (checked) 20.dp else 0.dp, label = "switch knob")
    Box(
        modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(CircleShape)
            .background(if (checked) yPalette.accent else yPalette.grabber)
            .then(
                if (onChange != null) Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange)
                else Modifier
            )
            .padding(3.dp),
    ) {
        Box(Modifier.offset(x = knob).size(22.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            color = yPalette.textPrimary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke(this)
    }
}

/** Standard list row: round gray icon, title with subtitle, optional trailing text. */
@Composable
fun ListRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    iconBackground: Color = yPalette.field,
    iconTint: Color = yPalette.iconSecondary,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            YIcon(icon, null, tint = iconTint, size = 22.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = yPalette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = yPalette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, style = MaterialTheme.typography.bodyMedium, color = yPalette.textSecondary)
        }
    }
}

/** "★★★★½ 4.3 102 ratings" row of the place card. */
@Composable
fun RatingRow(rating: Float, count: Int?, starSize: Dp = 18.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Stars(rating, starSize)
        Spacer(Modifier.width(8.dp))
        Text(
            String.format(java.util.Locale.US, "%.1f", rating),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = yPalette.textPrimary,
        )
        if (count != null && count > 0) {
            Spacer(Modifier.width(6.dp))
            Text(ratingsText(count), style = MaterialTheme.typography.bodyLarge, color = yPalette.textSecondary)
        }
    }
}

@Composable
private fun Stars(rating: Float, size: Dp) {
    val empty = yPalette.textSecondary.copy(alpha = 0.3f)
    Row {
        repeat(5) { i ->
            val fill = (rating - i).coerceIn(0f, 1f)
            Box {
                YIcon(R.drawable.ms_star, null, tint = empty, size = size)
                if (fill > 0f) {
                    YIcon(
                        R.drawable.ms_star, null, tint = YColors.Star, size = size,
                        modifier = Modifier.drawWithContent {
                            clipRect(right = this.size.width * fill) { this@drawWithContent.drawContent() }
                        },
                    )
                }
            }
        }
    }
}

fun ratingsText(count: Int): String = if (count == 1) "1 rating" else "$count ratings"

enum class StatusKind { Success, Warning, Danger, Info, Neutral }

/** Small status badge: "On time", "+7 min", "Arrived", "Not sharing". */
@Composable
fun StatusBadge(text: String, kind: StatusKind, modifier: Modifier = Modifier) {
    val p = yPalette
    val (bg, fg) = when (kind) {
        StatusKind.Success -> p.successSoft to p.onSuccessSoft
        StatusKind.Warning -> p.warningSoft to p.onWarningSoft
        StatusKind.Danger -> p.dangerSoft to p.onDangerSoft
        StatusKind.Info -> p.accentSoft to p.accent
        StatusKind.Neutral -> p.field to p.textSecondary
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Small caps section label above a group ("GUIDANCE", "WHO IS COMING"). */
@Composable
fun CapsLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = com.example.findme.ui.theme.CapsLabelStyle,
        color = yPalette.textSecondary,
        modifier = modifier,
    )
}

/**
 * Pill over the map with a 28dp round [lead], bold [text] and a quieter [detail]:
 * "(camera) Camera · 60  in 400 m". Used for the chips under the guidance card.
 */
@Composable
fun MapChip(text: String, detail: String? = null, modifier: Modifier = Modifier, lead: @Composable () -> Unit) {
    Surface(shape = CircleShape, color = yPalette.sheet, shadowElevation = 4.dp, modifier = modifier.heightIn(min = 40.dp)) {
        Row(Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { lead() }
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.titleSmall,
                color = yPalette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (detail != null) {
                Spacer(Modifier.width(6.dp))
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = yPalette.textSecondary, maxLines = 1)
            }
        }
    }
}
