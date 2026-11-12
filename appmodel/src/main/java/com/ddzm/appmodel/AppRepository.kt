package com.ddzm.appmodel

import com.ddzm.core.contract.AppCatalog

interface AppRepository : AppCatalog {
    fun launch(packageName: String, activityName: String? = null): Boolean
}
