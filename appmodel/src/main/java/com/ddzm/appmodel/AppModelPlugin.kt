package com.ddzm.appmodel

import com.ddzm.core.LauncherPlugin
import com.ddzm.core.LauncherScope
import com.ddzm.core.ServiceKeys

class AppModelPlugin(private val repository: AppRepository) : LauncherPlugin {

    override val id: String = "appmodel"

    override fun install(scope: LauncherScope) {
        scope.services.put(ServiceKeys.APP_CATALOG, repository)
        scope.services.put(ServiceKeys.APP_REPOSITORY, repository)
    }
}
