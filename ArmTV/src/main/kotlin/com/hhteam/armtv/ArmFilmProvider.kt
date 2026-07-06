package com.hhteam.armtv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import org.jsoup.nodes.Document

/**
 * armfilm.co — DLE, локаль в пути: /hy/. Без Cloudflare.
 * Карточка каталога: <div class="shortstory …"> (.short-title, .short-imgposter).
 * Плеер: iframe, src которого JS выставляет на armdb.org/player… (разбирается в DleProvider.loadLinks).
 *
 * Категории сверены по живому сайту.
 */
class ArmFilmProvider : DleProvider() {
    override var mainUrl = "https://armfilm.co"
    override var name = "ArmFilm"

    // База с локалью /hy/.
    private val hy = "$mainUrl/hy"

    override val mainPage = mainPageOf(
        "$hy/hayeren-filmer/" to "Հայերեն ֆիլմեր (Фильмы)",
        "$hy/serialner/" to "Սերիալներ (Сериалы)",
        "$hy/serialner-hayeren-tarkmanutyamb/" to "Սերիալներ (Հայ. թարգմ.)",
        "$hy/doramaner-hayeren/" to "Դորամաներ (Дорамы)",
        "$hy/hayeren-multer/" to "Մուլտֆիլմեր (Мультфильмы)",
    )

    // Карточки armfilm: обычные div.shortstory + блок премьер article.shortstory-premiere.
    override val cardSelector = ".shortstory, .shortstory-premiere"

    /**
     * Обычный DLE-поиск (index.php?do=search) у armfilm отдаёт «MySQL Fatal Error»,
     * а кастомный ajax-эндпоинт удалён (404). Рабочий поиск сайта — friendly-URL
     * /hy/search/<запрос>/ (GET), результаты в тех же .shortstory-карточках.
     */
    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim().replace(" ", "+")
        val doc = app.get("$hy/search/$q/", referer = "$hy/").document
        return doc.select(cardSelector).mapNotNull { it.toSearchResult() }.distinctBy { it.url }
    }

    /**
     * У armfilm две разные раскладки сериалов:
     *
     *  A) Сериалы С ОЗВУЧКОЙ (/serialner-hayeren-tarkmanutyamb/): один DLE-артикул, разбитый
     *     на страницы-серии через <select name="sel_page"> (page,1,{slug}.html … page,N,…).
     *     Опции селектора — прямые URL серий.
     *
     *  B) Обычные сериалы (/serialner/): КАЖДАЯ серия — отдельная страница
     *     (…/{id}-{база}-seria-{N}.html), общего артикула нет. Но есть тег-листинг серий
     *     сериала: /hy/{категория}/{база}/ (+ /page/2/…) — новые сверху, ~18 на страницу.
     *
     * data каждого Episode — URL страницы серии; поток из неё достаёт обычный DLE-путь
     * loadLinks (iframe armdb.org → Playerjs file:).
     */
    override suspend fun parseEpisodes(doc: Document, pageUrl: String): List<Episode> {
        // --- A) Артикул с пагинацией серий (<select name="sel_page">). ---
        val paged = doc.select("select[name=sel_page] option")
            .map { it.attr("value").trim() to it.text().trim() }
            .filter { it.first.startsWith("http") }
        if (paged.size >= 2) {
            return paged.mapNotNull { (url, label) ->
                val n = Regex("""\d+""").find(label)?.value?.toIntOrNull()
                buildEpisode("Սերիա ${label.ifBlank { n?.toString() ?: "" }}".trim(), url, epNum = n)
            }
        }

        // --- B) Тег-листинг отдельных серий (слаг вида {id}-{база}-seria-{N}). ---
        val slug = pageUrl.substringAfterLast('/').removeSuffix(".html")
        val base = Regex("""^\d+-(.+?)-seri[ya]+-\d+$""").find(slug)?.groupValues?.get(1)
            ?: return emptyList()

        val catPath = pageUrl.substringBeforeLast('/')          // …/hy/serialner
        val catSeg = catPath.substringAfterLast('/')            // serialner, doramaner-hayeren, …
        val epRe = Regex("""href="(https?://[^"]*/${Regex.escape(catSeg)}/\d+-(.+?)-seri[ya]+-(\d+)\.html)"""")

        val byNum = LinkedHashMap<Int, String>()                // номер серии -> URL (первый вариант)
        var page = 1
        while (page <= 15) {
            val url = if (page == 1) "$catPath/$base/" else "$catPath/$base/page/$page/"
            val html = try {
                app.get(url, referer = "$hy/").text
            } catch (e: Exception) {
                logError(e); break
            }
            var added = 0
            epRe.findAll(html).forEach { m ->
                if (m.groupValues[2] != base) return@forEach     // только этот сериал
                val n = m.groupValues[3].toIntOrNull() ?: return@forEach
                if (!byNum.containsKey(n)) { byNum[n] = m.groupValues[1]; added++ }
            }
            if (added == 0) break                               // страница без новых серий — конец
            page++
        }

        // Одна серия — это не сериал (пусть идёт обычным фильмом).
        if (byNum.size < 2) return emptyList()
        return byNum.entries.sortedBy { it.key }
            .mapNotNull { (n, url) -> buildEpisode("Սերիա $n", fixUrl(url), epNum = n) }
    }
}
