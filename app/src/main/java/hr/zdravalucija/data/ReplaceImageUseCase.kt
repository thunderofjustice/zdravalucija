package hr.zdravalucija.data

/** Keep the existing picture until an alternative has been downloaded successfully. */
class ReplaceImageUseCase(
    private val dao: LucijaDao,
    private val search: ImageSearchRepository,
    private val store: ImageStore,
    private val translator: NameTranslator,
) {
    suspend operator fun invoke(type: String, id: Long) {
        val name: String
        val previous: String?
        val previousQuery: String?
        when (type) {
            "foods" -> requireNotNull(dao.food(id)).let { name = it.name; previous = it.imageUrl; previousQuery = it.imageQuery }
            "categories" -> requireNotNull(dao.category(id)).let { name = it.name; previous = it.imageUrl; previousQuery = it.imageQuery }
            "meals" -> requireNotNull(dao.meal(id)).let { name = it.name; previous = it.imageUrl; previousQuery = it.imageQuery }
            else -> error("Nepoznata stavka.")
        }
        val query = previousQuery?.takeIf { it.isNotBlank() } ?: translator.toEnglish(name)
        val url = search.searchAlternative(query, previous)?.takeUnless { it == previous }
            ?: error("Nema druge fotografije za ovaj naziv. Trenutna slika ostaje spremljena.")
        store.download(url)
        val changed = when (type) {
            "foods" -> dao.replaceFoodImage(id, name, previous, previousQuery, url, query)
            "categories" -> dao.replaceCategoryImage(id, name, previous, previousQuery, url, query)
            else -> dao.replaceMealImage(id, name, previous, previousQuery, url, query)
        }
        check(changed == 1) { "Stavka se u međuvremenu promijenila. Pokušaj ponovno." }
    }
}
