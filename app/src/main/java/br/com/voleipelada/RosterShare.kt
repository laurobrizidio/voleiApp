package br.com.voleipelada

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import br.com.voleipelada.domain.Session
import java.io.File

private const val IMAGE_WIDTH = 900
private const val PAGE_HEIGHT = 2000
private const val SIDE = 50
private const val HEADER = 160
private const val GAP = 18

private data class RosterCard(val name: StaticLayout, val status: String, val roster: StaticLayout, val accent: Int) {
    val height: Int get() = 94 + name.height + roster.height
}

fun buildRosterShare(context: Context, session: Session, leagueName: String = ""): Intent {
    val ids = (session.court + session.queue + session.teams.map { it.id }).distinct()
    val cards = ids.mapNotNull(session::team).flatMap { team ->
        val court = session.court.indexOf(team.id)
        val queue = session.queue.indexOf(team.id)
        val status = when {
            court == 0 -> "EM QUADRA · LADO A"
            court == 1 -> "EM QUADRA · LADO B"
            queue >= 0 -> "FILA · ${queue + 1}º"
            else -> "FORA DA FILA"
        }
        val names = session.roster(team.id).map { it.name }.ifEmpty { listOf("Sem jogadores") }
        names.chunked(10).mapIndexed { index, players ->
            RosterCard(
                textLayout(team.name, 38f, 0xFF142448.toInt(), bold = true, maxLines = 2),
                if (index == 0) status else "$status · CONTINUAÇÃO",
                textLayout(players.joinToString("  ·  "), 30f, 0xFF374D78.toInt()),
                team.color,
            )
        }
    }
    require(cards.isNotEmpty())

    val pages = mutableListOf<MutableList<RosterCard>>()
    var height = HEADER + SIDE
    for (card in cards) {
        if (pages.isEmpty() || height + card.height + GAP > PAGE_HEIGHT) {
            pages.add(mutableListOf())
            height = HEADER + SIDE
        }
        pages.last().add(card)
        height += card.height + GAP
    }
    val directory = File(context.cacheDir, "shared_rosters").apply { mkdirs() }
    val cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
    directory.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    val title = leagueName.ifBlank { "Times da pelada" }
    val files = pages.mapIndexed { index, page ->
        val file = File.createTempFile("times-", ".png", directory)
        drawPage(context, title, page, index, pages.size, file)
        FileProvider.getUriForFile(context, "${context.packageName}.rosterfiles", file)
    }
    return Intent(if (files.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/png"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, title, files.first()).apply {
            files.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        if (files.size == 1) putExtra(Intent.EXTRA_STREAM, files.single())
        else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files))
    }
}

private fun textLayout(text: String, size: Float, color: Int, bold: Boolean = false, maxLines: Int = Int.MAX_VALUE,
                       width: Int = IMAGE_WIDTH - 2 * SIDE - 78): StaticLayout {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
        .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).build()
}

private fun drawPage(context: Context, title: String, cards: List<RosterCard>, index: Int, count: Int, file: File) {
    val height = HEADER + SIDE + cards.sumOf { it.height + GAP }
    val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, height, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(0xFFF0F5FF.toInt())
        paint.color = 0xFF0349BA.toInt()
        canvas.drawRect(0f, 0f, IMAGE_WIDTH.toFloat(), 128f, paint)
        paint.color = 0xFFFFD819.toInt()
        canvas.drawRect(0f, 128f, IMAGE_WIDTH.toFloat(), 137f, paint)
        paint.color = 0xFFE52B43.toInt()
        canvas.drawRect(IMAGE_WIDTH - 102f, 128f, IMAGE_WIDTH.toFloat(), 137f, paint)
        val badgeX = SIDE.toFloat()
        paint.color = Color.WHITE
        canvas.drawRoundRect(badgeX, 22f, badgeX + 84, 106f, 18f, 18f, paint)
        context.getDrawable(R.drawable.ic_volei_logo)?.apply {
            setBounds(SIDE + 7, 29, SIDE + 77, 99)
            draw(canvas)
        }
        val header = textLayout(title, 42f, Color.WHITE, bold = true, maxLines = 2, width = IMAGE_WIDTH - 2 * SIDE - 105)
        canvas.drawLayout(header, SIDE + 105f, (128f - header.height) / 2)
        if (count > 1) {
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = 24f
            paint.textAlign = Paint.Align.RIGHT
            paint.color = 0xFF374D78.toInt()
            canvas.drawText("${index + 1} / $count", (IMAGE_WIDTH - SIDE).toFloat(), height - 16f, paint)
            paint.textAlign = Paint.Align.LEFT
        }

        var top = HEADER.toFloat()
        val left = SIDE.toFloat()
        for (card in cards) {
            val bottom = top + card.height
            paint.color = Color.WHITE
            canvas.drawRoundRect(left, top, IMAGE_WIDTH - left, bottom, 26f, 26f, paint)
            paint.color = card.accent
            canvas.drawRoundRect(left + 17, top + 20, left + 23, bottom - 20, 3f, 3f, paint)
            paint.color = 0xFF374D78.toInt()
            paint.textSize = 21f
            canvas.drawText(card.status, left + 41, top + 39, paint)
            canvas.drawLayout(card.name, left + 41, top + 48)
            canvas.drawLayout(card.roster, left + 41, top + 61 + card.name.height)
            top = bottom + GAP
        }
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    } finally {
        bitmap.recycle()
    }
}

private fun Canvas.drawLayout(layout: StaticLayout, x: Float, y: Float) {
    save()
    translate(x, y)
    layout.draw(this)
    restore()
}
