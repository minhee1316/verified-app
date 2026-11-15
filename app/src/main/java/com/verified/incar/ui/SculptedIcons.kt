package com.verified.incar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.verified.incar.R

/** Native hit targets and labels remain separate from the decorative, generated icon atlas. */
@Composable
fun SculptedIcon(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val name = icon.name.substringAfterLast('.')
    val cell = when (name) {
        "LocalParking" -> 0
        "Build", "Handyman", "CarRepair", "Construction" -> 1
        "Key", "VpnKey", "Lock", "LockOpen" -> 2
        "Description", "Receipt", "ReceiptLong", "Article", "Assignment" -> 3
        "PhotoLibrary", "PhotoCamera", "Image", "Collections", "CameraAlt" -> 4
        "People", "Groups", "FamilyRestroom", "Accessible", "Person", "Group" -> 6
        "MeetingRoom", "ExitToApp", "Logout", "SensorDoor", "Luggage" -> 7
        "History", "ListAlt", "MenuBook", "FactCheck", "QueryStats", "AutoStories" -> 8
        else -> 5
    }
    SculptedIconCell(cell, modifier, size)
}

@Composable
fun SculptedIconCell(cell: Int, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val atlas = ImageBitmap.imageResource(R.drawable.reference_icons)
    Canvas(modifier.size(size)) {
        val w = atlas.width / 3
        val h = atlas.height / 3
        val index = cell.coerceIn(0, 8)
        // The sedan's front bumper crosses the nominal grid line; preserve its full silhouette
        // without drawing that bumper next to the neighbouring photo stack.
        val left = if (index == 5) (atlas.width * 0.632f).toInt() else (index % 3) * w
        val right = if (index == 4) (atlas.width * 0.632f).toInt() else ((index % 3) + 1) * w
        val sourceWidth = right - left
        val scale = minOf(this.size.width / sourceWidth, this.size.height / h)
        val target = IntSize((sourceWidth * scale).toInt(), (h * scale).toInt())
        drawImage(atlas, srcOffset = IntOffset(left, (index / 3) * h),
            srcSize = IntSize(sourceWidth, h), dstOffset = IntOffset(((this.size.width - target.width) / 2).toInt(), ((this.size.height - target.height) / 2).toInt()), dstSize = target,
            filterQuality = FilterQuality.High)
    }
}
