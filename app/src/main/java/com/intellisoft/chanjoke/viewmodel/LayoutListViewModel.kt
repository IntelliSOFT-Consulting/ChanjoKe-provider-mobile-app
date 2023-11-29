package com.intellisoft.chanjoke.viewmodel


import android.app.Application
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.intellisoft.chanjoke.R

class LayoutListViewModel(application: Application, private val state: SavedStateHandle) :
    AndroidViewModel(application) {

    fun getLayoutList(): List<Layout> {
        return Layout.values().toList()
    }

    enum class Layout(
        @DrawableRes val iconId: Int,
        val textId: String,
    ) {
        SEARCH_CLIENT(R.drawable.ic_action_searches, "Search Client"),
        REGISTER_CLIENT(R.drawable.ic_action_register_client, "Register Client"),
        UPDATE_CLIENT_HISTORY(R.drawable.ic_action_update_client, "Update Client History"),
        ADMINISTER_VACCINE(R.drawable.ic_action_administer_vaccine, "Administer vaccine"),
        AEFI(R.drawable.ic_action_aefi, "AEFI"),
    }
}