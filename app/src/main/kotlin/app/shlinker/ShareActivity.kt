package app.shlinker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.shlinker.core.ui.theme.ShlinkerTheme
import app.shlinker.feature.shorturls.SharedCreateRoute
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val shared = readIncomingShare(intent)
        setContent {
            ShlinkerTheme {
                SharedCreateRoute(
                    url = shared.url,
                    title = shared.title,
                    onClose = { finish() },
                    onOpenApp = { startActivity(Intent(this, MainActivity::class.java)) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Even a sender's SINGLE_TOP flag must not replace an in-flight draft.
        startActivity(Intent(intent).setClass(this, ShareActivity::class.java).setFlags(0))
    }
}
