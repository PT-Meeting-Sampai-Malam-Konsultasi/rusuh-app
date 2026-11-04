# iosApp

iOS application host for the Rusuh Booking Tiket Compose Multiplatform UI.

This directory contains the Swift entry point (`iosApp/iOSApp.swift`) that embeds
the shared Compose UI via `MainViewControllerKt.MainViewController()`.

> **Note:** The Xcode project (`iosApp.xcodeproj`) is not generated in this scaffold
> commit — it must be created on a machine with Xcode by adding these sources to a
> new iOS App target that links the `shared` framework produced by
> `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` (or the appropriate iOS
> target). The Swift source is already wired to the shared framework's
> `MainViewController`.
