package com.kazox.aufschlag.app.di

import com.kazox.aufschlag.feature.home.presentation.HomeViewModel
import com.kazox.aufschlag.feature.settings.presentation.SettingsViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.includes
import org.koin.dsl.module

fun initKoin(configuration: KoinAppDeclaration? = null) {
    startKoin {
        includes(configuration)
        modules(viewModelModule, platformModule)
    }
}

/** Swift cannot pass a default argument, so iOS calls this parameterless wrapper. */
fun doInitKoin() = initKoin()

val viewModelModule = module {
    viewModelOf(::HomeViewModel)
    viewModelOf(::SettingsViewModel)
}

/** Bindings that need a platform API (database drivers, preferences stores, …). */
expect val platformModule: Module
