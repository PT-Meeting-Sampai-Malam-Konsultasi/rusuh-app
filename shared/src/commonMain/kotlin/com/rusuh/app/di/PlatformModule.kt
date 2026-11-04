package com.rusuh.app.di

import org.koin.core.module.Module

/** Platform-specific Koin module (e.g. HTTP engine, storage, platform services). */
expect val platformModule: Module
