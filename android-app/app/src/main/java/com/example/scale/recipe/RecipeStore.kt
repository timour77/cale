package com.example.scale.recipe

import com.example.scale.ui.model.Recipe
import com.example.scale.ui.model.Stage

/**
 * Where recipes are kept.
 *
 * A [load] never returns nothing: a fresh install, an empty store, or unreadable data all yield
 * [DefaultRecipes]. Callers get a usable library or an exception, never a silent blank.
 */
interface RecipeStore {

    /** Everything saved, or [DefaultRecipes] if there is nothing readable. */
    fun load(): LoadResult

    fun save(recipes: List<Recipe>)

    /**
     * What came back, and whether it was actually the user's.
     *
     * [recoveredFromCorruption] exists so the UI can say something happened. Losing a recipe
     * library silently is the failure worth avoiding here.
     */
    data class LoadResult(
        val recipes: List<Recipe>,
        val recoveredFromCorruption: Boolean = false,
    )
}

/** A store that keeps recipes in memory. For tests and previews. */
class InMemoryRecipeStore(
    initial: List<Recipe> = DefaultRecipes.ALL,
) : RecipeStore {
    private var recipes: List<Recipe> = initial

    override fun load() = RecipeStore.LoadResult(recipes)

    override fun save(recipes: List<Recipe>) {
        this.recipes = recipes
    }
}

/**
 * What a new install starts with, and what a damaged store falls back to.
 *
 * Falling back to these rather than an empty list matters: an empty library is indistinguishable
 * from a first run, and the next save would overwrite the damaged data for good.
 */
object DefaultRecipes {
    val ALL: List<Recipe> = listOf(
        Recipe(
            "Decaf V60",
            listOf(
                Stage("Bloom", 0, 40, 50f, "Wet all grounds, wait 40s"),
                Stage("Pour 1", 40, 75, 180f, "Slow circular pour to 180g"),
                Stage("Pour 2", 75, 105, 320f, "Finish to 320g, thin stream"),
            ),
        ),
        Recipe(
            "4:6 (3 pours)",
            listOf(
                Stage("Pour 1", 0, 30, 60f, "Center pour to 60g"),
                Stage("Pour 2", 30, 60, 150f, "Circle to 150g"),
                Stage("Pour 3", 60, 120, 300f, "Finish to 300g"),
            ),
        ),
        Recipe(
            "Hoffmann V60",
            listOf(
                Stage("Bloom", 0, 45, 60f, "Bloom 2x dose"),
                Stage("Main Pour", 45, 120, 300f, "Continuous pour to 300g"),
            ),
        ),
        Recipe(
            "Tetsu 4:6 (5 pours)",
            listOf(
                Stage("Pour 1", 0, 30, 50f, "Start sweet"),
                Stage("Pour 2", 30, 60, 100f, "Balance"),
                Stage("Pour 3", 60, 90, 160f, "Strength"),
                Stage("Pour 4", 90, 120, 220f, "Body"),
                Stage("Pour 5", 120, 150, 300f, "Finish"),
            ),
        ),
        Recipe(
            "Kalita 155",
            listOf(
                Stage("Bloom", 0, 30, 40f, "Short bloom"),
                Stage("Pour 1", 30, 70, 120f, "Steady pour"),
                Stage("Pour 2", 70, 110, 200f, "Finish"),
            ),
        ),
        Recipe(
            "Bypass Iced",
            listOf(
                Stage("Bloom", 0, 30, 40f, "Bloom"),
                Stage("Pour", 30, 90, 180f, "Brew concentrate"),
                Stage("Bypass", 90, 90, 300f, "Add ice/water to 300g"),
            ),
        ),
    )
}
