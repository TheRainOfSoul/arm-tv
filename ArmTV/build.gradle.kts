// Версия расширения. Увеличивай при каждой публикации нового .cs3 —
// по ней приложение понимает, что вышло обновление.
version = 1

android {
    namespace = "com.hhteam.armtv"
}

cloudstream {
    description = "Армянские фильмы и сериалы: films.bz, hayertv.com, armfilm.co"
    language = "hy"
    authors = listOf("hhteam")

    /**
     * status: 0 = не работает, 1 = работает, 2 = медленно/частично, 3 = бета.
     */
    status = 3 // beta — пока идёт первичная подгонка селекторов

    // Типы контента, которые отдаёт расширение.
    tvTypes = listOf("Movie", "TvSeries")

    iconUrl = "https://www.google.com/s2/favicons?domain=films.bz&sz=64"
}
