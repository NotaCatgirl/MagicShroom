package edu.csumb.magicshroom.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.csumb.magicshroom.ui.theme.BrandPurple

/** Shared branding for the public search page and Google sign-in page. */
@Composable
fun MagicShroomLogo(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(74.dp),
            color = BrandPurple,
            shape = RoundedCornerShape(22.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("♠", color = Color.White, fontSize = 48.sp, lineHeight = 48.sp)
            }
        }
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            "MagicShroom",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 35.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
