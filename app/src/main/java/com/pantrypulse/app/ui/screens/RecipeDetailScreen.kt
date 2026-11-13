package com.pantrypulse.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pantrypulse.app.ui.MainViewModel
import com.pantrypulse.app.ui.components.EyebrowText
import com.pantrypulse.app.ui.components.PulseCard
import com.pantrypulse.app.ui.components.RoundButton
import com.pantrypulse.app.ui.components.SecondaryButton
import com.pantrypulse.app.ui.theme.DmMono
import com.pantrypulse.app.ui.theme.PulseTheme

@Composable
fun RecipeDetailScreen(id: String, vm: MainViewModel, rvm: RecipesViewModel, onBack: () -> Unit) {
    val c = PulseTheme.colors
    val context = LocalContext.current
    val products by vm.products.collectAsState()
    LaunchedEffect(id) { rvm.open(id) }
    val recipe = rvm.detail?.takeIf { it.id == id }
    val pantry = products.map { it.name.lowercase() }

    Column(Modifier.fillMaxSize().background(c.paper).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RoundButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack, size = 44.dp)
        when {
            rvm.detailError != null -> Text(rvm.detailError!!, style = MaterialTheme.typography.bodyLarge, color = c.muted)
            recipe == null -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
            else -> {
                if (recipe.imageUrl != null) {
                    AsyncImage(recipe.imageUrl, null, Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(28.dp)), contentScale = ContentScale.Crop)
                }
                Text(recipe.title, style = MaterialTheme.typography.headlineLarge)
                if (recipe.minutes > 0) EyebrowText("${recipe.minutes} minutes")
                PulseCard {
                    EyebrowText("Ingredients")
                    recipe.ingredients.forEach { ing ->
                        val have = pantry.any { p -> ing.lowercase().split(' ').any { w -> w.length > 3 && p.contains(w) } }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(22.dp).clip(CircleShape).background(if (have) c.accent else c.paper), contentAlignment = Alignment.Center) {
                                if (have) Icon(Icons.Outlined.Check, "In your pantry", Modifier.size(14.dp), tint = c.ink)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(ing, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                PulseCard {
                    EyebrowText("Method")
                    recipe.steps.forEachIndexed { i, step ->
                        Row {
                            Text("%02d".format(i + 1), fontFamily = DmMono, color = c.accent, modifier = Modifier.width(34.dp))
                            Text(step, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                recipe.sourceUrl?.let { url ->
                    SecondaryButton("Open original recipe", {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    }, icon = Icons.Outlined.OpenInNew)
                }
            }
        }
    }
}
