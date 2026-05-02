package com.example.scale.ui.model

data class Stage(
    val name: String,
    val startSec: Int,
    val endSec: Int,
    val targetWeight: Float,
    val note: String,
)

data class Recipe(
    val title: String,
    val stages: List<Stage>,
)
