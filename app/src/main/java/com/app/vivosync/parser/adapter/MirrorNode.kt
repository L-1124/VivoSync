package com.app.vivosync.parser.adapter

import androidx.annotation.StringRes
import com.app.vivosync.R

enum class MirrorNode(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int
) {
    AUTO("auto", R.string.mirror_node_auto, R.string.mirror_node_auto_desc),
    GITEE("gitee", R.string.mirror_node_gitee, R.string.mirror_node_gitee_desc),
    JSDELIVR("jsdelivr", R.string.mirror_node_jsdelivr, R.string.mirror_node_jsdelivr_desc),
    GHFAST("ghfast", R.string.mirror_node_ghfast, R.string.mirror_node_ghfast_desc),
    GITHUB("github", R.string.mirror_node_github, R.string.mirror_node_github_desc);

    companion object {
        fun fromId(id: String?): MirrorNode {
            return entries.firstOrNull { it.id == id } ?: AUTO
        }
    }
}
