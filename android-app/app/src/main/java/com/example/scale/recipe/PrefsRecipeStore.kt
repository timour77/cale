package com.example.scale.recipe

import android.content.Context
import android.content.SharedPreferences
import com.example.scale.ui.model.Recipe
import com.example.scale.ui.model.Stage
import org.json.JSONArray
import org.json.JSONObject

/**
 * Recipes in `SharedPreferences`, as a JSON array under [KEY].
 *
 * Hand-rolled JSON rather than a serialization library, because the schema is five fields and
 * adding a dependency to avoid thirty lines is a poor trade. The encoding is symmetric: whatever
 * [save] writes, [load] reads back.
 */
class PrefsRecipeStore(private val prefs: SharedPreferences) : RecipeStore {

    constructor(context: Context) : this(
        context.getSharedPreferences("scale_prefs", Context.MODE_PRIVATE),
    )

    override fun load(): RecipeStore.LoadResult {
        val raw = prefs.getString(KEY, null)
            ?: return RecipeStore.LoadResult(DefaultRecipes.ALL)

        val recipes = try {
            decode(raw)
        } catch (_: Exception) {
            null
        }

        if (recipes.isNullOrEmpty()) {
            // Keep whatever we could not read, rather than letting the next save overwrite it.
            // An empty library looks exactly like a first run, which is how data gets lost.
            prefs.edit().putString(KEY_CORRUPT, raw).apply()
            return RecipeStore.LoadResult(DefaultRecipes.ALL, recoveredFromCorruption = true)
        }
        return RecipeStore.LoadResult(recipes)
    }

    override fun save(recipes: List<Recipe>) {
        prefs.edit().putString(KEY, encode(recipes)).apply()
    }

    private fun encode(recipes: List<Recipe>): String {
        val root = JSONArray()
        for (recipe in recipes) {
            val stages = JSONArray()
            for (stage in recipe.stages) {
                stages.put(
                    JSONObject()
                        .put("name", stage.name)
                        .put("startSec", stage.startSec)
                        .put("endSec", stage.endSec)
                        .put("targetWeight", stage.targetWeight.toDouble())
                        .put("note", stage.note),
                )
            }
            root.put(JSONObject().put("title", recipe.title).put("stages", stages))
        }
        return root.toString()
    }

    private fun decode(raw: String): List<Recipe> {
        val root = JSONArray(raw)
        val recipes = mutableListOf<Recipe>()
        for (i in 0 until root.length()) {
            val recipeObj = root.optJSONObject(i) ?: continue
            val title = recipeObj.optString("title", "").trim()
            if (title.isEmpty()) continue

            val stagesJson = recipeObj.optJSONArray("stages") ?: JSONArray()
            val stages = mutableListOf<Stage>()
            for (j in 0 until stagesJson.length()) {
                val stageObj = stagesJson.optJSONObject(j) ?: continue
                val name = stageObj.optString("name", "").trim()
                if (name.isEmpty()) continue
                stages.add(
                    Stage(
                        name = name,
                        startSec = stageObj.optInt("startSec", 0),
                        endSec = stageObj.optInt("endSec", 0),
                        targetWeight = stageObj.optDouble("targetWeight", 0.0).toFloat(),
                        note = stageObj.optString("note", ""),
                    ),
                )
            }
            if (stages.isNotEmpty()) recipes.add(Recipe(title, stages))
        }
        return recipes
    }

    private companion object {
        const val KEY = "recipes_json"

        /** Where unreadable data is parked instead of being thrown away. */
        const val KEY_CORRUPT = "recipes_json_corrupt"
    }
}
