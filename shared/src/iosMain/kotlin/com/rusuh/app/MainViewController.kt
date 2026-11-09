package com.rusuh.app

import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

@Suppress("FunctionName", "ktlint:standard:function-naming")
fun MainViewController(): UIViewController =
    ComposeUIViewController(
        configure = {
            // Screens resize with imePadding; the default pan-above-keyboard would shift twice.
            onFocusBehavior = OnFocusBehavior.DoNothing
        },
    ) { App() }
