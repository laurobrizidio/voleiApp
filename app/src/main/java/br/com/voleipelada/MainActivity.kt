package br.com.voleipelada

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import br.com.voleipelada.domain.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import br.com.voleipelada.ui.LeagueScreen
import br.com.voleipelada.ui.LeagueViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VoleiApplication : Application()

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val model: LeagueViewModel by viewModels()

    private val tone by lazy { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 45) }.getOrNull() }

    override fun onDestroy() {
        if (toneInitialized) tone?.release()
        super.onDestroy()
    }

    private var toneInitialized = false
    private var sharing: Job? = null

    private fun shareRoster(session: Session, leagueName: String) {
        if (sharing?.isActive == true) return
        sharing = lifecycleScope.launch {
            try {
                val share = withContext(Dispatchers.IO) { buildRosterShare(applicationContext, session, leagueName) }
                startActivity(Intent.createChooser(share, "Compartilhar times"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(this@MainActivity, "Não foi possível compartilhar a imagem.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun scoreFullscreen(enabled: Boolean) {
        val orientation = if (enabled) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (requestedOrientation != orientation) requestedOrientation = orientation
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (enabled) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LeagueScreen(model, keepScreenOn = { enabled ->
                val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                if (enabled) window.addFlags(flag) else window.clearFlags(flag)
            }, playSound = { final ->
                toneInitialized = true
                tone?.startTone(if (final) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP, 150)
            }, onFullscreen = ::scoreFullscreen, onShareTeams = ::shareRoster)
        }
    }
}
