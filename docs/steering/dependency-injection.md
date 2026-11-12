---
inclusion: always
---
# Dependency Injection (Koin)

```kotlin
val shopModule = module {
    single { ShopApiService(get(named("mytelkomsel")), get(named("baseUrl"))) }
    single { ShopCache(get()) }
    single<ShopRepository> { ShopRepositoryImpl(get(), get(), get()) }
    factory { GetCategoriesUseCase(get()) }
    viewModelOf(::ShopViewModel)
}
```

- One Koin module file per feature in `di/`.
- ApiService + Repository + Cache = `single`. UseCases = `factory`. ViewModels = `viewModelOf`.
- Never provide core infra — `coreModule` owns HttpClient, AppPreferences, WCMS, RemoteConfig. `commonsModule` owns business API services, repositories, usecases.

## Dependency Graph

```
coreModule     ← pure infra (HttpClients, AppPreferences, WCMS, RemoteConfig, analytics)
    ↑
commonsModule  ← shared business (API services, repositories, account domain, usecases)
               includes(coreModule) automatically
    ↑
loginModule    ← auth-specific (AuthApiService, RefreshTokenHandler)
    ↑
appModule      ← secrets (BuildKonfig) + includes(all modules)
    ↑
feature modules (dashboard, account, shop, pay, lifestyle, tcmpp, fst)
```
