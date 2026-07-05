package com.hhteam.armtv

import com.lagradost.cloudstream3.mainPageOf

/**
 * armfilm.co — DLE, локаль в пути: /hy/. Без Cloudflare.
 * На страницах присутствует трекер liveinternet.ru и соц-виджеты — они уже
 * попадут в адблок-лист (см. AdBlock-заметку в README); в плеер не проходят.
 * Серии: /hy/serialner/{id}-...-seria-N.html — соседние страницы.
 */
class ArmFilmProvider : DleProvider() {
    override var mainUrl = "https://armfilm.co"
    override var name = "ArmFilm"

    // База с локалью /hy/.
    private val hy = "$mainUrl/hy"

    override val mainPage = mainPageOf(
        "$hy/haykakan-filmer/" to "Հայկական ֆիլմեր (Фильмы)",
        "$hy/serialner/" to "Սերիալներ (Сериалы)",
        "$hy/doramaner-hayeren/" to "Դորամաներ (Дорамы)",
        "$hy/multfilmer/" to "Մուլտֆիլմեր (Мультфильмы)",
    )

    // TODO: сверить с реальным HTML armfilm.co/hy/.
    override val cardSelector = "div.th-item, div.shortstory, div.short, article"
    override val titleInCardSelector = "h6, h3, .th-title, .title"
}
