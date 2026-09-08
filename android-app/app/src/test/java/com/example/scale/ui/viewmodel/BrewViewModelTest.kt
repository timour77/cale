package com.example.scale.ui.viewmodel

import com.example.scale.recipe.DefaultRecipes
import com.example.scale.recipe.InMemoryRecipeStore
import com.example.scale.recipe.RecipeStore
import com.example.scale.scale.FakeScaleLink
import com.example.scale.scale.LinkState
import com.example.scale.scale.ScaleCommand
import com.example.scale.scale.ScriptedSample
import com.example.scale.support.MainDispatcherRule
import com.example.scale.ui.model.Recipe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The assembly, exercised through the fake link.
 *
 * `BrewSessionTest` covers the rules and `ScaleProtocolTest` covers the bytes; what is left, and
 * what these cover, is the wiring between them — the part that used to require a scale, a phone
 * and a jug of water to try at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        link: FakeScaleLink,
        store: RecipeStore = InMemoryRecipeStore(),
    ) = BrewViewModel(link, store)

    @Test
    fun `a scripted pour drives the brew`() = runTest {
        val link = FakeScaleLink(
            scope = this,
            script = listOf(
                ScriptedSample(100, 0f),
                ScriptedSample(100, 5f),
                ScriptedSample(100, 25f),
            ),
        )
        val vm = viewModel(link)
        advanceUntilIdle()

        vm.toggleConnection()
        advanceUntilIdle()

        assertEquals(25f, vm.brew.value.weightGrams, 0.001f)
        assertTrue("the pour should have started the timer", vm.brew.value.timerRunning)
        assertTrue(link.sent.contains(ScaleCommand.Timer(running = true)))
    }

    @Test
    fun `battery readings reach the ui`() = runTest {
        val link = FakeScaleLink(scope = this, script = emptyList(), batteryPercent = 64)
        val vm = viewModel(link)
        advanceUntilIdle()

        vm.toggleConnection()
        advanceUntilIdle()

        assertEquals(64, vm.battery.value)
    }

    @Test
    fun `losing the link clears the brew`() = runTest {
        val link = FakeScaleLink(
            scope = this,
            script = listOf(ScriptedSample(100, 40f)),
        )
        val vm = viewModel(link)
        advanceUntilIdle()
        vm.toggleConnection()
        advanceUntilIdle()
        assertTrue(vm.brew.value.timerRunning)

        link.disconnect()
        advanceUntilIdle()

        assertFalse(vm.brew.value.timerRunning)
        assertEquals(0f, vm.brew.value.weightGrams, 0.001f)
        assertEquals(null, vm.battery.value)
    }

    @Test
    fun `losing the link keeps the loaded recipe`() = runTest {
        val link = FakeScaleLink(scope = this, script = emptyList())
        val vm = viewModel(link)
        advanceUntilIdle()
        vm.toggleConnection()
        advanceUntilIdle()

        link.disconnect()
        advanceUntilIdle()

        assertEquals(DefaultRecipes.ALL.first().stages, vm.brew.value.stages)
    }

    @Test
    fun `tare goes straight to the scale`() = runTest {
        val link = FakeScaleLink(scope = this, script = emptyList())
        val vm = viewModel(link)
        advanceUntilIdle()

        vm.tareNow()

        assertEquals(listOf(ScaleCommand.Tare), link.sent.filterNot { it is ScaleCommand.ClearStage })
    }

    @Test
    fun `a calibration reply is reported to the user`() = runTest {
        val link = FakeScaleLink(scope = this, script = emptyList())
        val vm = viewModel(link)
        advanceUntilIdle()
        vm.toggleConnection()
        advanceUntilIdle()

        vm.requestCalibration()
        advanceUntilIdle()

        assertEquals("Zero 8123, factor -895.9", vm.messages.replayCache.last())
    }

    @Test
    fun `recipes come from the store`() = runTest {
        val mine = listOf(DefaultRecipes.ALL.last())
        val vm = viewModel(FakeScaleLink(this, emptyList()), InMemoryRecipeStore(mine))
        advanceUntilIdle()

        assertEquals(mine, vm.recipes.value)
        assertEquals(mine.first().stages, vm.brew.value.stages)
    }

    @Test
    fun `saving a recipe persists it`() = runTest {
        val store = InMemoryRecipeStore(emptyList())
        val vm = viewModel(FakeScaleLink(this, emptyList()), store)
        advanceUntilIdle()

        val recipe = DefaultRecipes.ALL.first()
        vm.saveRecipe(null, recipe)

        assertEquals(listOf(recipe), store.load().recipes)
        assertEquals(recipe.stages, vm.brew.value.stages)
    }

    @Test
    fun `unreadable recipes are reported rather than silently lost`() = runTest {
        val store = object : RecipeStore {
            override fun load() =
                RecipeStore.LoadResult(DefaultRecipes.ALL, recoveredFromCorruption = true)

            override fun save(recipes: List<Recipe>) = Unit
        }
        val vm = viewModel(FakeScaleLink(this, emptyList()), store)
        advanceUntilIdle()

        assertTrue(
            "the user should be told",
            vm.messages.replayCache.any { it.contains("could not be read") },
        )
        assertEquals(DefaultRecipes.ALL, vm.recipes.value)
    }

    @Test
    fun `connecting is a toggle`() = runTest {
        val link = FakeScaleLink(scope = this, script = emptyList())
        val vm = viewModel(link)
        advanceUntilIdle()

        vm.toggleConnection()
        advanceUntilIdle()
        assertEquals(LinkState.Ready, link.state.value)

        vm.toggleConnection()
        advanceUntilIdle()
        assertEquals(LinkState.Idle, link.state.value)
    }
}
