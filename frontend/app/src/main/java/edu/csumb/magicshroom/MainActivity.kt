package edu.csumb.magicshroom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import edu.csumb.magicshroom.ui.nav.AppNavHost
import edu.csumb.magicshroom.ui.theme.MagicShroomTheme

/** Hosts the MagicShroom Compose application. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MagicShroomTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }
}
