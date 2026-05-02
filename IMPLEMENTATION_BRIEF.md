# Scale App: Compose UI Implementation Brief

## Overview
You are implementing a **Jetpack Compose redesign** of the Scale app's UI, based on a design prototype. The goal is to replace the XML layout with a modern, interactive Compose-based interface that maintains all existing Bluetooth functionality.

**Key constraint:** The MainActivity's Bluetooth logic, GATT callbacks, and state management stay intact. We're only changing the UI layer.

---

## Current State
- **Target SDK:** 34, Min SDK: 23
- **Current UI:** XML-based (activity_main.xml) with programmatic visibility toggling
- **Bluetooth:** Fully functional in MainActivity.kt (GATT, notifications, commands)
- **Data flow:** MainActivity updates TextViews/ProgressBars directly via runOnUiThread
- **Dependencies:** Material 3, MPAndroidChart, no Compose yet

**What we're keeping:**
- All Bluetooth logic (scan, connect, GATT callbacks, weight/flow updates)
- Recipe management (load/save via SharedPreferences)
- Timer, tare, calibration commands
- Chart data collection

**What we're replacing:**
- XML layout → Compose screen hierarchy
- Direct View updates → State-based Compose composables
- Modal visibility flags → Bottom sheet state management

---

## Design System (from prototype)

### Colors
```kotlin
object ScaleColors {
  const val BG_PRIMARY = 0x0A1410        // Dark mossy green
  const val BG_SURFACE = 0x0E1612        // Slightly lighter
  const val TEXT_PRIMARY = 0xFFFFFF
  const val TEXT_SECONDARY_55 = 0x8CFFFFFF // rgba(255,255,255,0.55)
  const val TEXT_SECONDARY_45 = 0x73FFFFFF // rgba(255,255,255,0.45)
  const val TEXT_SECONDARY_35 = 0x59FFFFFF // rgba(255,255,255,0.35)
  const val ACCENT_MINT = 0x86EFAC
  const val ACCENT_AMBER = 0xFBBF24
  const val ACCENT_SECONDARY = 0xFB923C   // Flow rate color
  const val DISABLED_DOT = 0x888888
}
```

### Typography
- **Monospace (numerics, labels):** JetBrains Mono or `FontFamily.Monospace`
- **Sans (body, headers):** Inter or `FontFamily.Default`
- **Weight numbers:** fontSize 84.sp, fontWeight Light, lineHeight 80.sp
- **Labels:** fontSize 9–11.sp, letterSpacing 1.5–2.sp, textTransform uppercase

### Spacing & Sizing
- **Padding:** 20.dp horizontal, 8.dp vertical (standard)
- **Hero weight:** 84.sp numerals
- **Stage strip:** 6.dp gaps, 4.dp height progress bars
- **Bottom buttons:** 12.dp padding, flex layout

---

## Architecture

### High-level Flow
```
MainActivity (Kotlin, unchanged Bluetooth logic)
  ↓ emits: weight, timer, flow, stageIndex, connected, battery
  ↓
ComposeActivity OR MainActivity.setContent() in onCreate
  ↓
@Composable BrewScreen(viewModel: BrewViewModel)
  ├─ TopBar(connected, battery, onSettings)
  ├─ RecipeCard(current recipe, onTap → RecipePickerSheet)
  ├─ HeroWeight(weight, taredAt, units, accent)
  ├─ MetaStrip(elapsed, flow, stageIndex)
  ├─ FlowChart (optional)
  ├─ StageStrip (optional)
  ├─ ControlBar(onTare, onStart/Stop, onRecipes)
  ├─ RecipePickerSheet (modal)
  └─ SettingsSheet (modal)
```

### State Management Strategy
**BrewViewModel** (holds Compose-friendly state):
```kotlin
class BrewViewModel : ViewModel() {
  // From MainActivity Bluetooth layer
  val weight = MutableLiveData<Float>(0f)
  val elapsed = MutableLiveData<Float>(0f)
  val flowRate = MutableLiveData<Float>(0f)
  val stageIndex = MutableLiveData<Int>(0)
  val connected = MutableLiveData<Boolean>(false)
  val battery = MutableLiveData<Int>(74)
  val timerRunning = MutableLiveData<Boolean>(false)
  val recipeModeEnabled = MutableLiveData<Boolean>(false)
  
  // Compose state (managed here)
  val taredWeight = MutableState<Float>(0f)
  val selectedRecipeIndex = MutableState<Int>(0)
  val pickerSheetOpen = MutableState<Boolean>(false)
  val settingsSheetOpen = MutableState<Boolean>(false)
  val showChart = MutableState<Boolean>(true)
  val showStages = MutableState<Boolean>(true)
  val units = MutableState<String>("g")
  val accentColor = MutableState<Int>(ScaleColors.ACCENT_MINT)
  
  // Commands (call back to MainActivity)
  fun tare(currentWeight: Float) { taredWeight.value = currentWeight }
  fun toggleTimer() { /* call MainActivity.toggleTimer() */ }
  fun selectRecipe(index: Int) { selectedRecipeIndex.value = index }
  fun changeAccent(color: Int) { accentColor.value = color }
}
```

### MainActivity Changes
Minimal, non-breaking changes:
1. Add Compose dependencies to build.gradle
2. In onCreate, call `setContent { BrewScreen(viewModel) }` instead of setContentView(R.layout.activity_main)
3. Keep all Bluetooth logic intact
4. When weight/timer/flow updates arrive via GATT, post to ViewModel LiveData (same as now, just updates state instead of TextViews)

---

## File Structure (create these)

```
app/src/main/java/com/example/scale/
  MainActivity.kt (existing, minimal changes)
  
  ui/
    theme/
      ScaleColors.kt
      ScaleTypography.kt
      Theme.kt
    
    screens/
      BrewScreen.kt (main entry point)
    
    components/
      TopBar.kt
      RecipeCard.kt
      HeroWeight.kt
      MetaStrip.kt
      FlowChart.kt
      StageStrip.kt
      ControlBar.kt
      RecipePickerSheet.kt
      SettingsSheet.kt
    
    viewmodel/
      BrewViewModel.kt
```

---

## Implementation Steps

### Step 1: Add Compose Dependencies
**File:** `app/build.gradle`

```gradle
android {
  buildFeatures {
    compose = true
  }
  composeOptions {
    kotlinCompilerExtensionVersion = '1.5.8'
  }
}

dependencies {
  // Existing
  implementation 'androidx.core:core-ktx:1.12.0'
  implementation 'androidx.appcompat:appcompat:1.6.1'
  implementation 'com.google.android.material:material:1.11.0'
  implementation 'com.github.PhilJay:MPAndroidChart:3.1.0'
  
  // NEW: Compose
  def compose_version = '1.6.0'
  implementation "androidx.compose.ui:ui:$compose_version"
  implementation "androidx.compose.material3:material3:1.1.2"
  implementation "androidx.compose.foundation:foundation:$compose_version"
  implementation "androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2"
  implementation "androidx.activity:activity-compose:1.8.1"
  
  // For bottom sheets
  implementation "androidx.compose.material:material:$compose_version"
}
```

### Step 2: Create Theme Files

**File:** `ui/theme/ScaleColors.kt`
```kotlin
package com.example.scale.ui.theme

import androidx.compose.ui.graphics.Color

object ScaleColors {
  val BG_PRIMARY = Color(0xFF0A1410)
  val BG_SURFACE = Color(0xFF0E1612)
  val TEXT_PRIMARY = Color.White
  val TEXT_SECONDARY_55 = Color.White.copy(alpha = 0.55f)
  val TEXT_SECONDARY_45 = Color.White.copy(alpha = 0.45f)
  val TEXT_SECONDARY_35 = Color.White.copy(alpha = 0.35f)
  val ACCENT_MINT = Color(0xFF86EFAC)
  val ACCENT_AMBER = Color(0xFFFBBF24)
  val ACCENT_SKY = Color(0xFF7DD3FC)
  val ACCENT_ROSE = Color(0xFFEDA4AF)
  val ACCENT_VIOLET = Color(0xFFC4B5FD)
  val ACCENT_SECONDARY = Color(0xFFFB923C)
  val DISABLED_DOT = Color(0xFF888888)
}
```

**File:** `ui/theme/ScaleTypography.kt`
```kotlin
package com.example.scale.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val ScaleTypography = Typography(
  // Monospace (for numerics and labels)
  bodyMedium = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    letterSpacing = 0.5.sp
  ),
  labelSmall = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.W600,
    fontSize = 11.sp,
    letterSpacing = 1.5.sp
  )
  // ... add more as needed
)
```

**File:** `ui/theme/Theme.kt`
```kotlin
package com.example.scale.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
  primary = ScaleColors.ACCENT_MINT,
  secondary = ScaleColors.ACCENT_SECONDARY,
  background = ScaleColors.BG_PRIMARY,
  surface = ScaleColors.BG_SURFACE
)

@Composable
fun ScaleTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = DarkColorScheme,
    typography = ScaleTypography,
    content = content
  )
}
```

### Step 3: Create BrewViewModel

**File:** `ui/viewmodel/BrewViewModel.kt`
```kotlin
package com.example.scale.ui.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateOf
import com.example.scale.ui.theme.ScaleColors

class BrewViewModel : ViewModel() {
  // From MainActivity (observed as LiveData)
  val weight = MutableLiveData<Float>(0f)
  val elapsed = MutableLiveData<Float>(0f)
  val flowRate = MutableLiveData<Float>(0f)
  val stageIndex = MutableLiveData<Int>(0)
  val connected = MutableLiveData<Boolean>(false)
  val battery = MutableLiveData<Int>(74)
  val timerRunning = MutableLiveData<Boolean>(false)
  
  // Compose state (managed in ViewModel)
  val taredWeight = mutableStateOf(0f)
  val selectedRecipeIndex = mutableStateOf(0)
  val pickerSheetOpen = mutableStateOf(false)
  val settingsSheetOpen = mutableStateOf(false)
  val showChart = mutableStateOf(true)
  val showStages = mutableStateOf(true)
  val units = mutableStateOf("g")
  val accentColor = mutableStateOf(ScaleColors.ACCENT_MINT)
  
  fun tare(currentWeight: Float) {
    taredWeight.value = currentWeight
  }
  
  fun openRecipePicker() {
    pickerSheetOpen.value = true
  }
  
  fun closeRecipePicker() {
    pickerSheetOpen.value = false
  }
  
  fun selectRecipe(index: Int) {
    selectedRecipeIndex.value = index
  }
  
  fun toggleSettings() {
    settingsSheetOpen.value = !settingsSheetOpen.value
  }
  
  fun toggleChart() {
    showChart.value = !showChart.value
  }
  
  fun toggleStages() {
    showStages.value = !showStages.value
  }
  
  fun setUnits(unit: String) {
    units.value = unit
  }
  
  fun setAccentColor(color: Int) {
    accentColor.value = color
  }
}
```

### Step 4: Create Component Composables

See detailed implementations below in **Component Examples** section.

### Step 5: Create Main BrewScreen

**File:** `ui/screens/BrewScreen.kt`
```kotlin
package com.example.scale.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scale.ui.components.*
import com.example.scale.ui.theme.ScaleColors
import com.example.scale.ui.viewmodel.BrewViewModel

@Composable
fun BrewScreen(viewModel: BrewViewModel = viewModel()) {
  val weight by viewModel.weight.observeAsState(0f)
  val elapsed by viewModel.elapsed.observeAsState(0f)
  val flow by viewModel.flowRate.observeAsState(0f)
  val stageIdx by viewModel.stageIndex.observeAsState(0)
  val connected by viewModel.connected.observeAsState(false)
  val battery by viewModel.battery.observeAsState(74)
  val timerRunning by viewModel.timerRunning.observeAsState(false)
  
  val taredWeight = viewModel.taredWeight.value
  val selectedRecipeIdx = viewModel.selectedRecipeIndex.value
  val showChart = viewModel.showChart.value
  val showStages = viewModel.showStages.value
  val units = viewModel.units.value
  val accent = viewModel.accentColor.value
  
  Surface(
    modifier = Modifier.fillMaxSize(),
    color = ScaleColors.BG_PRIMARY
  ) {
    if (!connected) {
      DisconnectedView(accent = Color(accent), onConnect = { /* TODO */ })
    } else {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
      ) {
        TopBar(
          connected = connected,
          battery = battery,
          onSettingsClick = { viewModel.toggleSettings() },
          accent = Color(accent)
        )
        
        RecipeCard(
          recipeName = "", // TODO: get from recipe list
          dose = 18,
          ratio = "1:17",
          onClick = { viewModel.openRecipePicker() }
        )
        
        HeroWeight(
          weight = weight,
          taredWeight = taredWeight,
          units = units,
          accent = Color(accent)
        )
        
        MetaStrip(
          elapsed = elapsed,
          flow = flow,
          stageName = "", // TODO: from stages
          units = units,
          accent = Color(accent),
          secondary = Color(ScaleColors.ACCENT_SECONDARY)
        )
        
        if (showChart) {
          FlowChart(
            weight = weight,
            flow = flow,
            history = emptyList(), // TODO: pass from ViewModel
            accent = Color(accent),
            secondary = Color(ScaleColors.ACCENT_SECONDARY)
          )
        }
        
        if (showStages) {
          StageStrip(
            stages = emptyList(), // TODO: from recipe
            currentStageIndex = stageIdx,
            weight = weight,
            taredWeight = taredWeight,
            accent = Color(accent),
            units = units
          )
        }
        
        ControlBar(
          onTare = { viewModel.tare(weight) },
          onStartStop = { /* TODO: call MainActivity */ },
          onRecipes = { viewModel.openRecipePicker() },
          timerRunning = timerRunning,
          accent = Color(accent)
        )
      }
    }
  }
  
  // Modal sheets
  if (viewModel.pickerSheetOpen.value) {
    RecipePickerSheet(
      onDismiss = { viewModel.closeRecipePicker() },
      onSelectRecipe = { viewModel.selectRecipe(it) },
      recipes = emptyList(), // TODO: from MainActivity
      currentIndex = selectedRecipeIdx,
      accent = Color(accent),
      units = units
    )
  }
  
  if (viewModel.settingsSheetOpen.value) {
    SettingsSheet(
      onDismiss = { viewModel.toggleSettings() },
      accent = Color(accent)
    )
  }
}

@Composable
fun DisconnectedView(accent: Color, onConnect: () -> Unit) {
  // TODO: Implement based on prototype
}
```

### Step 6: Update MainActivity

**File:** `MainActivity.kt`

```kotlin
// At the top, add import
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scale.ui.screens.BrewScreen
import com.example.scale.ui.theme.ScaleTheme
import com.example.scale.ui.viewmodel.BrewViewModel

class MainActivity : AppCompatActivity() {
  private lateinit var viewModel: BrewViewModel
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Initialize ViewModel
    viewModel = ViewModelProvider(this).get(BrewViewModel::class.java)
    
    // Set Compose content instead of XML
    setContent {
      ScaleTheme {
        BrewScreen(viewModel)
      }
    }
    
    // ... keep all Bluetooth init code ...
    // ... but update how you post updates:
    
    // BEFORE: weightText.text = "WEIGHT: $value g"
    // AFTER: viewModel.weight.postValue(parsedValue)
    
    // Example (in onCharacteristicChanged):
    // runOnUiThread {
    //   viewModel.weight.value = parsed
    //   viewModel.elapsed.value = (System.currentTimeMillis() - timerStartMs) / 1000f
    //   viewModel.flowRate.value = currentFlow
    //   viewModel.stageIndex.value = stageIdx
    // }
  }
  
  // Keep all your Bluetooth methods (connect, scan, sendCommand, etc.)
  // Just change how they update the UI:
  //  TextViews → ViewModel LiveData values
}
```

---

## Component Examples

### TopBar
```kotlin
@Composable
fun TopBar(
  connected: Boolean,
  battery: Int,
  onSettingsClick: () -> Unit,
  accent: Color
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 6.dp)
      .height(40.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.gap(8.dp)
    ) {
      Box(
        modifier = Modifier
          .size(7.dp)
          .background(
            color = if (connected) accent else ScaleColors.DISABLED_DOT,
            shape = CircleShape
          )
      )
      Text(
        text = if (connected) "Linked · Scale 02" else "Disconnected",
        style = TextStyle(
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
          letterSpacing = 1.5.sp,
          color = ScaleColors.TEXT_SECONDARY_55
        )
      )
    }
    
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.gap(14.dp)
    ) {
      if (connected) {
        Text(
          text = "$battery%",
          style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = ScaleColors.TEXT_SECONDARY_45
          )
        )
      }
      IconButton(onClick = onSettingsClick) {
        Icon(
          painter = painterResource(id = android.R.drawable.ic_menu_manage),
          contentDescription = "Settings",
          tint = ScaleColors.TEXT_SECONDARY_55,
          modifier = Modifier.size(17.dp)
        )
      }
    }
  }
}
```

### HeroWeight
```kotlin
@Composable
fun HeroWeight(
  weight: Float,
  taredWeight: Float,
  units: String,
  accent: Color
) {
  val displayWeight = (weight - taredWeight).coerceAtLeast(0f)
  
  Column(
    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .marginBottom(4.dp),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        "● Live weight",
        style = TextStyle(
          fontFamily = FontFamily.Monospace,
          fontSize = 9.sp,
          color = accent,
          letterSpacing = 2.sp
        )
      )
      if (taredWeight > 0) {
        Text(
          "tared ${String.format("%.1f", taredWeight)}$units",
          style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = ScaleColors.TEXT_SECONDARY_45
          )
        )
      }
    }
    
    Row(verticalAlignment = Alignment.Baseline) {
      Text(
        text = String.format("%.1f", displayWeight),
        style = TextStyle(
          fontFamily = FontFamily.Monospace,
          fontSize = 84.sp,
          fontWeight = FontWeight.Light,
          lineHeight = 80.sp,
          letterSpacing = (-2).sp
        ),
        color = ScaleColors.TEXT_PRIMARY
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        units,
        style = TextStyle(
          fontFamily = FontFamily.Monospace,
          fontSize = 22.sp,
          fontWeight = FontWeight.Light,
          color = ScaleColors.TEXT_SECONDARY_45
        )
      )
    }
  }
}
```

### StageStrip
```kotlin
@Composable
fun StageStrip(
  stages: List<Stage>,
  currentStageIndex: Int,
  weight: Float,
  taredWeight: Float,
  accent: Color,
  units: String
) {
  val displayWeight = (weight - taredWeight).coerceAtLeast(0f)
  
  Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
    Text(
      "Stages",
      style = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        color = ScaleColors.TEXT_SECONDARY_45,
        letterSpacing = 2.sp
      ),
      modifier = Modifier.marginBottom(8.dp)
    )
    
    Row(modifier = Modifier.fillMaxWidth().gap(6.dp)) {
      stages.forEachIndexed { i, stage ->
        val isActive = i == currentStageIndex
        val isPast = i < currentStageIndex
        val prevTarget = if (i > 0) stages[i - 1].targetWeight else 0f
        val fill = when {
          isActive -> (displayWeight - prevTarget) / (stage.targetWeight - prevTarget)
          isPast -> 1f
          else -> 0f
        }.coerceIn(0f, 1f)
        
        Column(modifier = Modifier.weight(1f)) {
          // Progress bar
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(4.dp)
              .background(
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(2.dp)
              )
          ) {
            Box(
              modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = fill)
                .background(
                  color = when {
                    isActive -> accent
                    isPast -> Color.White.copy(alpha = 0.3f)
                    else -> Color.Transparent
                  }
                )
            )
          }
          
          // Stage name
          Text(
            stage.name,
            style = TextStyle(
              fontSize = 11.sp,
              fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
              color = if (isActive) ScaleColors.TEXT_PRIMARY else ScaleColors.TEXT_SECONDARY_45
            ),
            modifier = Modifier.marginTop(6.dp)
          )
          
          // Target weight
          Text(
            "${stage.targetWeight.toInt()}$units",
            style = TextStyle(
              fontFamily = FontFamily.Monospace,
              fontSize = 10.sp,
              color = if (isActive) accent else ScaleColors.TEXT_SECONDARY_35
            ),
            modifier = Modifier.marginTop(1.dp)
          )
        }
      }
    }
  }
}
```

---

## Known Gotchas & Solutions

### 1. **Bluetooth Updates from Background Threads**
**Problem:** GATT callbacks arrive on non-UI threads. Can't update Compose state directly.

**Solution:** Use `runOnUiThread { viewModel.weight.postValue(...) }`
```kotlin
override fun onCharacteristicChanged(...) {
  runOnUiThread {
    viewModel.weight.postValue(parsed)
  }
}
```

### 2. **LiveData + Compose ObserveAsState**
**Problem:** LiveData from MainActivity needs to be observed in Compose.

**Solution:** Use `observeAsState()` extension:
```kotlin
val weight by viewModel.weight.observeAsState(0f)
```
This automatically recomposes when the value changes.

### 3. **Modal Sheet State**
**Problem:** Bottom sheets need to be composable but state is in ViewModel.

**Solution:** Use ModalBottomSheet from material3 or build custom with Box + Modifier.align:
```kotlin
if (viewModel.pickerSheetOpen.value) {
  ModalBottomSheet(onDismissRequest = { viewModel.closeRecipePicker() }) {
    // Sheet content
  }
}
```

### 4. **Chart Data (MPAndroidChart → Compose)**
**Problem:** The existing chart uses MPAndroidChart library (View-based).

**Solution (phase 1):** Keep the existing chart View wrapped in AndroidView:
```kotlin
@Composable
fun FlowChart(...) {
  AndroidView(
    factory = { context ->
      LineChart(context).apply {
        // Configure chart
      }
    },
    modifier = Modifier.fillMaxWidth().height(130.dp)
  )
}
```

**Solution (phase 2, later):** Replace with a pure Compose chart library (e.g., `io.github.bytebeam:compose-charts`).

### 5. **Recipe Data Model**
**Problem:** Recipes are loaded in MainActivity, but Compose needs them.

**Solution:** Expose recipes via ViewModel LiveData:
```kotlin
class BrewViewModel : ViewModel() {
  val recipes = MutableLiveData<List<Recipe>>()
  
  fun loadRecipes(prefs: SharedPreferences) {
    // Load from prefs, post to recipes
  }
}
```

Then in MainActivity.onCreate:
```kotlin
viewModel.loadRecipes(getSharedPreferences("scale_prefs", MODE_PRIVATE))
```

And in BrewScreen:
```kotlin
val recipes by viewModel.recipes.observeAsState(emptyList())
```

---

## Testing Checklist

- [ ] App compiles (check for Compose version conflicts)
- [ ] Bluetooth scan/connect still works
- [ ] Weight updates appear in real-time on hero weight
- [ ] Timer counts up correctly
- [ ] Flow rate updates in MetaStrip
- [ ] Tare button freezes baseline weight
- [ ] Recipe picker opens/closes
- [ ] Settings sheet opens/closes
- [ ] Stage strip progresses as weight increases
- [ ] Chart renders (if using AndroidView wrapper)
- [ ] All accent colors swappable via tweaks
- [ ] Units toggle (g ↔ oz) works throughout

---

## Next Steps (in order)

1. **Setup:** Add Compose deps, create theme files, create BrewViewModel
2. **Components:** Implement TopBar, HeroWeight, MetaStrip, ControlBar
3. **Main Screen:** Implement BrewScreen, wire ViewModel
4. **MainActivity:** Update to use setContent, post Bluetooth updates to ViewModel
5. **Modals:** Implement RecipePickerSheet, SettingsSheet
6. **Advanced:** Add chart, stage strip, disconnected view
7. **Polish:** Animations, transitions, edge cases
8. **Test:** Full end-to-end with real scale

---

## Files to Delete (after Compose migration)
- `res/layout/activity_main.xml` (no longer needed)
- `res/drawable/progress_bar_module.xml` (Compose rendering now)

---

## Questions for Claude Code

1. After analyzing the code, check: what's the exact Recipe data class structure in MainActivity?
2. Are there any other Activities or Fragments, or is it all in MainActivity?
3. What's your strategy for persisting the accent color preference—SharedPreferences or DataStore?

