package com.hhteam.armtv

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.WebViewResolver   // переехал в пакет network
import com.lagradost.cloudstream3.mvvm.logError             // логер-хелпер лежит в mvvm
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Базовый провайдер для сайтов на движке DLE (DataLife Engine).
 *
 * films.bz, hayertv.com, armfilm.co — все три на DLE, серверный HTML, без Cloudflare.
 * Поэтому 90% логики (каталог, поиск, карточка) общая — она здесь.
 * Наследники задают только: mainUrl, name, mainPage-ряды и, при необходимости,
 * переопределяют CSS-селекторы карточек/описания.
 *
 * ВАЖНО (честно): точные CSS-классы карточек WebFetch по домашним страницам дать не смог
 * (плеер и часть вёрстки подставляются JS). Селекторы ниже — типовые для DLE и заданы как
 * open-поля. Первый шаг после открытия проекта в Android Studio — один раз сверить их
 * с реальным HTML (Elements Inspector в браузере или прямой лог doc.html()) и поправить.
 * Логика извлечения потока (loadLinks) от селекторов НЕ зависит — она универсальна.
 */
abstract class DleProvider : MainAPI() {

    override val hasMainPage = true
    override var lang = "hy"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // -------- Селекторы (переопределяемые в наследниках; дефолты — типовой DLE) --------

    /** Контейнер одной карточки фильма/сериала. Наследники задают точный класс. */
    open val cardSelector = "div.short, div.shortstory, div.item, article"

    /** Заголовок внутри карточки. */
    open val titleInCardSelector = "h6, h3, .th-title, .title, .short-title"

    /** Описание на странице деталей. */
    open val descriptionSelector = ".fdesc, .full-text, [itemprop=description], .fst-item p"

    /**
     * Ссылки на серии внутри страницы сериала (плейлист DLE-балансёра).
     * Если пусто — контент трактуется как фильм.
     */
    open val episodeListSelector = ".pgs-links a, .video-nav a, .serial-tabs a"

    // ------------------------------- Главная -------------------------------

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        // DLE-пагинация категорий: .../category/page/2/
        val url = if (page <= 1) request.data else "${request.data.trimEnd('/')}/page/$page/"
        val doc = app.get(url, referer = mainUrl).document
        val items = doc.select(cardSelector).mapNotNull { it.toSearchResult() }.distinctBy { it.url }
        return newHomePageResponse(request.name, items)
    }

    // ------------------------------- Поиск -------------------------------

    override suspend fun search(query: String): List<SearchResponse> {
        // Стандартный поиск DLE — один и тот же на всех трёх сайтах.
        val doc = app.post(
            "$mainUrl/index.php?do=search",
            referer = mainUrl,
            data = mapOf(
                "do" to "search",
                "subaction" to "search",
                "search_start" to "0",
                "full_search" to "0",
                "result_from" to "1",
                "story" to query,
            )
        ).document
        return doc.select(cardSelector).mapNotNull { it.toSearchResult() }.distinctBy { it.url }
    }

    // ------------------------------- Карточка -------------------------------

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, referer = mainUrl).document

        val title = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content").orEmpty()
        val poster = fixUrlNull(
            doc.selectFirst("meta[property=og:image]")?.attr("content")
                ?: doc.selectFirst(".fposter img, .short-poster img")?.absUrl("src")
        )
        val plot = doc.selectFirst("meta[property=og:description]")?.attr("content")
            ?: doc.selectFirst(descriptionSelector)?.text()
        val year = Regex("""\b(19|20)\d{2}\b""")
            .find(doc.selectFirst(".fmeta, .short-meta")?.text() ?: "")
            ?.value?.toIntOrNull()

        val episodes = parseEpisodes(doc, url)

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        } else {
            // dataUrl = url страницы: именно её потом «нюхает» loadLinks
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        }
    }

    /**
     * Разбор списка серий. На DLE-сайтах серии обычно — соседние страницы
     * (…-seria-11.html, …-seria-12.html), собранные в плейлист на странице.
     * Дефолт покрывает частый случай; при необходимости переопредели в наследнике.
     */
    protected open suspend fun parseEpisodes(doc: Document, pageUrl: String): List<Episode> {
        return doc.select(episodeListSelector).mapNotNull { a ->
            val href = a.absUrl("href").ifBlank { a.attr("data-url") }
            if (href.isBlank() || href == "#") return@mapNotNull null
            val label = a.text().trim().ifBlank { "Серия" }
            newEpisode(fixUrl(href)) {
                this.name = label
                this.episode = Regex("""\d+""").find(label)?.value?.toIntOrNull()
            }
        }
    }

    // ------------------------------- Извлечение потока -------------------------------

    /**
     * Извлечение потока. У всех трёх сайтов плеер — Playerjs, и ссылка на поток лежит
     * прямо в его конфиге (file: "…", часто завёрнута в atob(base64)), а НЕ в сетевом
     * запросе. Поэтому WebView-сниффер её не видит: Playerjs грузит поток только по клику Play.
     * Стратегия:
     *   1) собрать URL балансёра (iframe/data-атрибуты + JS-инъекции: ortified.ws, armdb.org, fsst.online);
     *   2) вытащить file: из HTML эмбеда (Playerjs / прямой m3u8) — быстро, без WebView;
     *   3) если не вышло — фолбэк: WebView-сниффер.
     */
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, referer = mainUrl).document
        val pageHtml = doc.html()

        // 1) Явные iframe'ы/DLE-атрибуты + URL плеера, который JS вписывает в iframe
        //    (films.bz → api.ortified.ws/embed, armfilm → armdb.org/player, hayertv → fsst.online/embed).
        val fromTags = doc.select("iframe[src], iframe[data-src], [data-file], [data-player]")
            .map {
                it.attr("src")
                    .ifBlank { it.attr("data-src") }
                    .ifBlank { it.attr("data-file") }
                    .ifBlank { it.attr("data-player") }
            }
        val fromJs = Regex("""["'](https?://[^"']*/(?:embed|player)/[^"']*)["']""")
            .findAll(pageHtml).map { it.groupValues[1] }.toList()

        val embeds = (fromTags + fromJs)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { httpsify(fixUrl(it)) }
            .filterNot { u -> BLOCKED_EMBED_HOSTS.any { u.contains(it, ignoreCase = true) } }
            .distinct()
            .take(8)

        val targets = if (embeds.isNotEmpty()) embeds else listOf(data)

        // 2) Прямое извлечение из Playerjs-конфига эмбеда (быстро, без WebView).
        var found = false
        for (target in targets) {
            try {
                val body = app.get(target, referer = mainUrl).text
                if (extractFromEmbed(body, target, callback)) found = true
            } catch (e: Exception) {
                logError(e)
            }
        }
        if (found) return true

        // 3) Фолбэк: WebView-сниффер (для балансёров, отдающих поток только сетевым запросом).
        for (target in targets) {
            try {
                val resolved = app.get(
                    target,
                    referer = mainUrl,
                    interceptor = WebViewResolver(
                        interceptUrl = Regex("""\.m3u8|\.mp4"""),
                        additionalUrls = listOf(Regex("""\.m3u8|\.mp4"""))
                    )
                ).url
                when {
                    resolved.contains(".m3u8") -> {
                        M3u8Helper.generateM3u8(name, resolved, target).forEach(callback); found = true
                    }
                    resolved.contains(".mp4") -> {
                        callback(
                            newExtractorLink(name, name, resolved, ExtractorLinkType.VIDEO) {
                                this.referer = target
                                this.quality = Qualities.Unknown.value
                            }
                        )
                        found = true
                    }
                }
            } catch (e: Exception) {
                logError(e)
            }
        }
        return found
    }

    /**
     * Достаёт ссылки из HTML эмбеда: Playerjs `file:` (в т.ч. atob(base64)) формата
     * "[480p]url,[720p]url" или одиночный URL; либо прямой master.m3u8 в HTML (ortified).
     */
    private suspend fun extractFromEmbed(
        body: String,
        referer: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val b64 = Regex("""file\s*:\s*atob\(\s*["']([A-Za-z0-9+/=]+)["']\s*\)""")
            .find(body)?.groupValues?.get(1)
        val fileVal = if (b64 != null) {
            runCatching { String(Base64.decode(b64, Base64.DEFAULT)) }.getOrNull()
        } else {
            Regex("""["']?file["']?\s*:\s*["']([^"']+)["']""").find(body)?.groupValues?.get(1)
        }

        var any = false
        if (fileVal != null && fileVal.contains("http")) {
            // Playerjs: "[480p]url1,[720p]url2" либо просто "url".
            val entries = if (fileVal.contains("[")) {
                Regex("""\[([^\]]*)]\s*(https?://[^,\s]+)""")
                    .findAll(fileVal).map { it.groupValues[1] to it.groupValues[2] }.toList()
            } else {
                Regex("""https?://[^,\s]+""").findAll(fileVal).map { "" to it.value }.toList()
            }
            for ((label, url) in entries) {
                // CDN потока часто отвергает кросс-доменный referer эмбеда (hayertv → 403).
                // Свой origin ссылки CDN принимает всегда — берём его.
                val streamReferer = Regex("""^https?://[^/]+""").find(url)?.value ?: ""
                // Отдаём ссылку целиком (для m3u8 — type M3U8): ExoPlayer сам сведёт
                // отдельные аудио/видео дорожки и покажет выбор дорожки и качества.
                callback(
                    newExtractorLink(
                        name,
                        if (label.isBlank()) name else "$name $label",
                        url,
                        if (url.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                    ) {
                        this.referer = streamReferer
                        this.quality = getQualityFromName(label)
                    }
                )
                any = true
            }
        }
        if (any) return true

        // Прямой m3u8 в HTML (напр. ortified.ws у films.bz).
        val direct = Regex("""https?://[^\s"']+\.m3u8[^\s"']*""").find(body)?.value
        if (direct != null) {
            // Целый master (type M3U8): у films.bz/ortified аудио вынесено в отдельные
            // EXT-X-MEDIA-дорожки (ru/fr). M3u8Helper бы разбил master на видео-варианты
            // без звука — поэтому отдаём master целиком, ExoPlayer сведёт видео+аудио
            // и даст выбрать дорожку/качество прямо в плеере.
            callback(
                newExtractorLink(name, name, direct, ExtractorLinkType.M3U8) {
                    this.referer = referer
                }
            )
            return true
        }
        return false
    }

    companion object {
        /** Хосты рекламы/соцсетей/аналитики — это не видео-балансёры, пропускаем. */
        private val BLOCKED_EMBED_HOSTS = listOf(
            "youtube.", "youtu.be", "facebook.", "google.", "gstatic.", "doubleclick",
            "yandex.", "vk.com", "ok.ru", "mail.ru", "caramel.am", "adfinity",
            "telegram.", "twitter.", "instagram.", "disqus.", "gravatar.",
        )
    }

    // ------------------------------- Общий парсер карточки -------------------------------

    protected open fun Element.toSearchResult(): SearchResponse? {
        // Берём именно ссылку на страницу деталей DLE: /{категория}/{id}-{slug}.html.
        // Признак /{цифры}- отсекает меню и служебные блоки, попавшие под селектор.
        val a = select("a[href]").firstOrNull { it.attr("href").contains(Regex("""/\d+-""")) }
            ?: return null
        val href = fixUrl(a.attr("href"))

        val title = selectFirst(titleInCardSelector)?.text()?.trim().takeUnless { it.isNullOrBlank() }
            ?: a.attr("title").takeUnless { it.isBlank() }
            ?: selectFirst("img[alt]")?.attr("alt")?.trim().takeUnless { it.isNullOrBlank() }
            ?: return null

        val poster = fixUrlNull(
            selectFirst("img")?.let { img ->
                img.attr("data-src").ifBlank { img.attr("src") }
            }
        )

        // Признак сериала: в URL сегмент серий/сериалов (serial/seriali/serialner).
        val isSeries = href.contains(Regex("""seri|serial"""))
        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = poster }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
        }
    }
}
