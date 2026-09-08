package com.example.scale

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import android.content.pm.PackageManager
import com.example.scale.recipe.PrefsRecipeStore
import com.example.scale.scale.GattScaleLink
import com.example.scale.ui.screens.BrewScreen
import com.example.scale.ui.theme.ScaleTheme
import com.example.scale.ui.viewmodel.BrewViewModel

/**
 * Hosts the screen and owns the one thing a ViewModel cannot: asking for permissions.
 *
 * Everything else — the radio, the brew, the recipes — lives behind [BrewViewModel] and survives
 * this Activity being recreated.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: BrewViewModel

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) viewModel.toggleConnection()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(
            this,
            BrewViewModel.Factory(
                link = GattScaleLink(applicationContext),
                recipeStore = PrefsRecipeStore(applicationContext),
            ),
        )[BrewViewModel::class.java]

        setContent {
            ScaleTheme {
                BrewScreen(
                    viewModel = viewModel,
                    onConnectToggle = ::connectWithPermissions,
                )
            }
        }
    }

    /**
     * Connecting needs scan and connect permissions on Android 12+, and coarse location before
     * that. Ask if we lack them; the result callback retries.
     */
    private fun connectWithPermissions() {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            viewModel.toggleConnection()
        } else {
            requestPermissions.launch(missing.toTypedArray())
        }
    }
}
