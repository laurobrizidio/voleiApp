package br.com.voleipro

import android.content.Intent
import android.graphics.BitmapFactory
import br.com.voleipro.domain.Player
import br.com.voleipro.domain.Session
import br.com.voleipro.domain.Team
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RosterShareTest {
    @Test fun aRosterProducesAReadablePngAndGrantsReceiverAccess() {
        val context = RuntimeEnvironment.getApplication()
        val team = Team(1, "Time Azul", 0xFF2563EB.toInt())
        val session = Session(teams = listOf(team), court = listOf(team.id),
            players = listOf(Player(1, "Ana", assignedTeamId = team.id)))

        val intent = buildRosterShare(context, session, "Vôlei de sábado")
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/png", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val clip = requireNotNull(intent.clipData)
        assertEquals("Vôlei de sábado", clip.description.label.toString())
        val uri = clip.getItemAt(0).uri
        assertEquals("content", uri.scheme)
        val bitmap = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
        assertNotNull(bitmap)
        assertEquals(900, bitmap.width)
        assertTrue(bitmap.height > 200)
        bitmap.recycle()
    }
}
