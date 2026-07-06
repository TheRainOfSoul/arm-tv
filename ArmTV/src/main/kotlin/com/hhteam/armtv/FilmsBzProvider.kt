package com.hhteam.armtv

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.mvvm.logError
import org.jsoup.nodes.Document

/**
 * films.bz — DLE, серверный HTML, без antibot, без Cloudflare.
 * Карточка каталога: <div class="short …"> с <a class="short_img" title="…"> и <img>.
 * Детали: /films/{id}-{slug}.html, /serial/{id}-{slug}.html и т.п.
 * Плеер: iframe.player, src которого JS выставляет на api.ortified.ws/embed/movie/{id}
 * (разбирается в DleProvider.loadLinks).
 *
 * Категории сверены по живому сайту (детальные ссылки на главной).
 */
class FilmsBzProvider : DleProvider() {
    override var mainUrl = "https://films.bz"
    override var name = "Films.bz"

    override val mainPage = mainPageOf(
        "$mainUrl/films/" to "Ֆիլմեր (Фильмы)",
        "$mainUrl/serial/" to "Սերիալներ (Сериалы)",
        "$mainUrl/hayeren-targmanutyamb/" to "Հայերեն թարգմանությամբ",
        "$mainUrl/cartoons/" to "Մուլտֆիլմեր (Мультфильмы)",
        "$mainUrl/anime/" to "Անիմե (Аниме)",
    )

    // Реальный контейнер карточки films.bz — это <article class="short …">, НЕ div.
    // Поэтому селектор по классу без тега (иначе каталог пуст).
    override val cardSelector = ".short"

    /**
     * У films.bz список серий НЕ на DLE-странице, а внутри балансёра ortified: эмбед
     * api.ortified.ws/embed/movie/{id} отдаёт JSON вида
     *   …"episode":"N"…"hls":"https://…interkh.com/…/master.m3u8?…"…"title":"… - N серия"…
     * (по объекту на серию, сезоны — в самом title). Тянем эмбед и парсим серии; каждый
     * Episode.data — это готовый master.m3u8 (в loadLinks уходит как прямой M3U8-поток).
     */
    override suspend fun parseEpisodes(doc: Document, pageUrl: String): List<Episode> {
        // Серии бывают только у сериалов — не дёргаем ortified на страницах фильмов.
        if (!pageUrl.contains("serial", ignoreCase = true)) return emptyList()

        val embed = Regex("""setURL\(['"](https?://api\.ortified\.ws/embed/[^'"]+)['"]\)""")
            .find(doc.html())?.groupValues?.get(1) ?: return emptyList()

        val json = try {
            app.get(embed, referer = mainUrl).text
        } catch (e: Exception) {
            logError(e); return emptyList()
        }

        // Дробим по началу объекта серии и в каждом берём первый hls + первый title.
        val episodes = json.split("\"episode\":\"").drop(1).mapNotNull { chunk ->
            val epNum = Regex("""^(\d+)"""").find(chunk)?.groupValues?.get(1)?.toIntOrNull()
            val hls = Regex(""""hls":"([^"]+)"""").find(chunk)?.groupValues?.get(1)
                ?.replace("\\/", "/") ?: return@mapNotNull null
            val title = Regex(""""title":"([^"]*)"""").find(chunk)?.groupValues?.get(1).orEmpty()
            buildEpisode(title.ifBlank { "Серия $epNum" }, hls, epNum = epNum)
        }

        // Одна «серия» — это фильм, а не сериал: пусть идёт по обычному пути loadLinks.
        return if (episodes.size > 1) episodes else emptyList()
    }
}
