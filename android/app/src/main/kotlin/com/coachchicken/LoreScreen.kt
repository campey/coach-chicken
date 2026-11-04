package com.coachchicken

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val MONKEY_ISLAND_URL = "https://monkeyisland.fandom.com/wiki/Rubber_Chicken_With_A_Pulley_In_The_Middle"

/** Why a chicken: the lore. */
@Composable
fun LoreScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "A chicken?", Modifier.weight(1f).padding(start = 64.dp, top = 18.dp, bottom = 18.dp),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
            )
            TextButton(onClose, Modifier.padding(end = 8.dp)) { Text("Close") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(R.drawable.ic_chicken), contentDescription = null, modifier = Modifier.height(150.dp))
            Text("Yes. A rubber one.", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text(
                buildAnnotatedString {
                    append("Coach Chicken is inspired by the rubber chicken with a pulley in the middle, first seen in ")
                    withLink(LinkAnnotation.Url(MONKEY_ISLAND_URL, TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)))) {
                        append("The Secret of Monkey Island")
                    }
                    append(". Guybrush Threepwood was sure it would be completely useless, until it turned out to have several uses.")
                },
                style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp, textAlign = TextAlign.Center,
            )
            LoreText(
                "The real one came from Malaysia. I found a squeaky rubber chicken with a pivoting neck, bought it, and through covid " +
                    "it was our exercise squeaker.",
            )
            LoreText(
                "So that is the job. A quiet cluck while you hold the plank, a louder one every ten seconds, a squawk when you reach " +
                    "the minute, and a long, building bwaaak if your form slips. This chicken is not useless either. It is helping you train!",
            )
        }
    }
}

@Composable
private fun LoreText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp, textAlign = TextAlign.Center)
}
