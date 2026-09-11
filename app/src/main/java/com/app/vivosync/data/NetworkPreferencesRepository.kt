package com.app.vivosync.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.app.vivosync.parser.adapter.MirrorNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

interface NetworkPreferencesRepository {
    val selectedMirrorNodeFlow: Flow<MirrorNode>
    suspend fun setSelectedMirrorNode(node: MirrorNode)
}

private val Context.networkDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vivosync_network_preferences"
)

class NetworkPreferencesRepositoryImpl(
    private val context: Context
) : NetworkPreferencesRepository {

    private object Keys {
        val SELECTED_MIRROR_NODE = stringPreferencesKey("key_selected_mirror_node")
    }

    override val selectedMirrorNodeFlow: Flow<MirrorNode> = context.networkDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            MirrorNode.fromId(preferences[Keys.SELECTED_MIRROR_NODE])
        }

    override suspend fun setSelectedMirrorNode(node: MirrorNode) {
        context.networkDataStore.edit { preferences ->
            preferences[Keys.SELECTED_MIRROR_NODE] = node.id
        }
    }
}
