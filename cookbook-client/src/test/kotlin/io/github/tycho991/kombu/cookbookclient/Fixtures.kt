package io.github.tycho991.kombu.cookbookclient

/** Invented recipes in the field shapes real Cookbook recipes use; see `src/test/resources/recipes`. */
internal object Fixtures {
    fun text(name: String): String =
        checkNotNull(Fixtures::class.java.getResource("/recipes/$name.json")) { "fixture $name missing" }.readText()

    fun recipe(name: String): RemoteRecipe = checkNotNull(RemoteRecipe.parse(text(name)))

    /** Has a numeric id, `nutrition` as an object, `author` as a string and `dateModified` `null`. */
    val numberId: RemoteRecipe get() = recipe("number-id")

    /** Has a string id, `nutrition` as a list, `author` as an object and no `dateModified` at all. */
    val stringId: RemoteRecipe get() = recipe("string-id")
}
