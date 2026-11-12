package com.ddzm.config

import com.ddzm.core.LauncherPlugin
import com.ddzm.core.LauncherScope
import com.ddzm.core.ServiceKeys

class ConfigPlugin(private val store: ConfigStore) : LauncherPlugin {

    override val id: String = "config"

    override fun install(scope: LauncherScope) {
        scope.services.put(ServiceKeys.CONFIG_STORE, store)
    }
}
