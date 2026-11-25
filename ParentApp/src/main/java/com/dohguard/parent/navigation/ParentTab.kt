package com.dohguard.parent.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

enum class ParentTab(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    OVERVIEW("overview", "Visão Geral", Icons.Default.Home),
    APPS("apps", "Apps & Conteúdo", Icons.Default.Apps),
    MAP("map", "Mapa GPS", Icons.Default.Map),
    RULES("rules", "Regras", Icons.Default.Tune)
}
