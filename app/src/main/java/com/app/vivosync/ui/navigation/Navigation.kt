package com.app.vivosync.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.app.vivosync.R
import kotlinx.serialization.Serializable

sealed interface AppRoute : NavKey {
    @Serializable
    data object Home : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data object SchoolSelector : AppRoute

    @Serializable
    data object WebImport : AppRoute
}

enum class AppDestination(
    val route: AppRoute,
    @param:StringRes val labelResId: Int,
    @param:DrawableRes val selectedIconResId: Int,
    @param:DrawableRes val unselectedIconResId: Int,
    @param:StringRes val contentDescriptionResId: Int
) {
    HOME(
        route = AppRoute.Home,
        labelResId = R.string.nav_home,
        selectedIconResId = R.drawable.ic_calendar_month_filled,
        unselectedIconResId = R.drawable.ic_calendar_month_outlined,
        contentDescriptionResId = R.string.nav_home_desc
    ),
    SETTINGS(
        route = AppRoute.Settings,
        labelResId = R.string.nav_settings,
        selectedIconResId = R.drawable.ic_settings_filled,
        unselectedIconResId = R.drawable.ic_settings_outlined,
        contentDescriptionResId = R.string.nav_settings_desc
    );

    @get:DrawableRes
    val iconResId: Int get() = selectedIconResId

    companion object {
        fun fromRoute(route: AppRoute): AppDestination? = when (route) {
            is AppRoute.Home -> HOME
            is AppRoute.Settings -> SETTINGS
            else -> null
        }
    }
}

class TopLevelBackStack<T : Any>(startKey: T) {
    private val topLevelStacks: LinkedHashMap<T, SnapshotStateList<T>> = linkedMapOf(
        startKey to mutableStateListOf(startKey)
    )

    var topLevelKey by mutableStateOf(startKey)
        private set

    val backStack = mutableStateListOf(startKey)

    private fun updateBackStack() {
        backStack.clear()
        backStack.addAll(topLevelStacks.flatMap { it.value })
    }

    fun addTopLevel(key: T) {
        if (topLevelStacks[key] == null) {
            topLevelStacks[key] = mutableStateListOf(key)
        } else {
            topLevelStacks.remove(key)?.let {
                topLevelStacks[key] = it
            }
        }
        topLevelKey = key
        updateBackStack()
    }

    fun add(key: T) {
        topLevelStacks[topLevelKey]?.add(key)
        updateBackStack()
    }

    fun removeLast() {
        val currentStack = topLevelStacks[topLevelKey]
        if ((currentStack != null) && (currentStack.size > 1)) {
            currentStack.removeLastOrNull()
        } else if (topLevelStacks.size > 1) {
            topLevelStacks.remove(topLevelKey)
            topLevelKey = topLevelStacks.keys.last()
        }
        updateBackStack()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
internal data class BottomSheetScene<T : Any>(
    override val key: T,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    private val modalBottomSheetProperties: ModalBottomSheetProperties,
    private val onBack: () -> Unit,
) : OverlayScene<T> {

    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable (() -> Unit) = {
        val lifecycleOwner = LocalLifecycleOwner.current
        ModalBottomSheet(
            onDismissRequest = onBack,
            properties = modalBottomSheetProperties,
        ) {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                entry.Content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val lastEntry = entries.lastOrNull() ?: return null
        val bottomSheetProperties = lastEntry.metadata[BottomSheetKey] ?: return null
        return bottomSheetProperties.let { properties ->
            @Suppress("UNCHECKED_CAST")
            BottomSheetScene(
                key = lastEntry.contentKey as T,
                previousEntries = entries.dropLast(1),
                overlaidEntries = entries.dropLast(1),
                entry = lastEntry,
                modalBottomSheetProperties = properties,
                onBack = onBack
            )
        }
    }

    companion object {
        fun bottomSheet(modalBottomSheetProperties: ModalBottomSheetProperties = ModalBottomSheetProperties()) =
            metadata {
                put(BottomSheetKey, modalBottomSheetProperties)
            }

        object BottomSheetKey : NavMetadataKey<ModalBottomSheetProperties>
    }
}


