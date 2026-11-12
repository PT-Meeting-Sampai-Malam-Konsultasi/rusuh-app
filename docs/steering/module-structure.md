---
inclusion: always
---
# Module Structure

Every feature module follows this exact structure. No exceptions.

```
features/{module}/
├── app/                              ← Presentation (MVI)
│   ├── {screen}/                     ← Contract + ViewModel + Screen/Sheet
│   │   ├── {Screen}Contract.kt
│   │   ├── {Screen}ViewModel.kt
│   │   ├── {Screen}Screen.kt        ← or {Screen}Sheet.kt for bottom sheets
│   │   ├── {Screen}Wcms.kt          ← co-located WCMS keys (Pair objects)
│   │   └── component/               ← screen-specific sub-composables (optional)
│   └── {screen2}/
├── navigation/                       ← Module-owned nav wiring
│   ├── {Module}Routes.kt            ← @Serializable route definitions
│   ├── {Module}NavGraph.kt          ← NavGraphBuilder extension
│   ├── {Module}TabContent.kt        ← bottom nav tab composable (if applicable)
│   └── {Module}DeeplinkHandler.kt   ← deeplink parser (if applicable)
├── data/
│   ├── remote/                       ← API service files
│   │   ├── {Module}ApiService.kt
│   │   ├── dto/                      ← @Serializable DTOs only
│   │   └── mapper/                   ← DTO → Domain mappers (optional)
│   ├── cache/                        ← Module-specific cache (uses generic prefs.get/set)
│   └── repository/                   ← RepositoryImpl files
├── domain/
│   ├── model/                        ← Domain models (data classes)
│   ├── repository/                   ← Repository interfaces
│   └── usecase/                      ← UseCases implementing CoreUseCase<P, R>
├── di/                               ← Koin module ({Module}Module.kt)
└── build.gradle.kts
```

## Rules

- Presentation always in `app/`. Each screen or sheet gets its own subfolder.
- WCMS keys co-located as `*Wcms.kt` next to the screen that uses them.
- DTOs live in `data/remote/dto/`. Domain models in `domain/model/`. Never mix them.
- Shared UI lives in `:features:core/app/`.
- No flat `domain/` — always use `model/`, `repository/`, `usecase/` subfolders.
