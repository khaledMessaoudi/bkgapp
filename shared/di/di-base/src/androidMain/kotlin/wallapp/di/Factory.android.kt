package wallapp.di

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import com.russhwolf.settings.SharedPreferencesSettings
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import org.koin.core.scope.Scope
import wallapp.account.data.AccountDataManager
import wallapp.account.data.AccountDataManagerDefault
import wallapp.account.data.AccountDataRepository
import wallapp.ad.reward.RewardAdConfig
import wallapp.ad.reward.RewardAdConfigDefault
import wallapp.ad.reward.internal.RewardAdInternalPlaybackManagerDefault
import wallapp.ads.AdSourceInitializer
import wallapp.ads.AdSourceInitializerAdMob
import wallapp.ads.AdSourceInitializerNoOp
import wallapp.ads.AdUnitIds
import wallapp.ads.AdUnitIdsAndroid
import wallapp.ads.initializerstate.AdInitializerState
import wallapp.ads.inline.InlineAdManager
import wallapp.ads.inline.InlineAdManagerCached
import wallapp.ads.inline.InlineAdManagerDefault
import wallapp.ads.inline.support.InlineAdConfigFactories
import wallapp.ads.inline.support.InlineAdConfigFactoryAdMob
import wallapp.ads.inline.support.InlineAdItemFactories
import wallapp.ads.inline.support.InlineAdItemFactoryAdMob
import wallapp.ads.reward.RewardAdPlaybackManager
import wallapp.ads.reward.RewardAdPlaybackManagerAdMobAndroid
import wallapp.ads.reward.internal.RewardAdInternalNavigator
import wallapp.ads.reward.internal.RewardAdInternalNavigatorAndroid
import wallapp.ads.reward.internal.RewardAdInternalPlaybackManager
import wallapp.appconfig.AppPlatformConfig
import wallapp.appicon.AppIconManager
import wallapp.appicon.AppIconManagerAndroid
import wallapp.apptypeface.TypefaceRepositoryAndroid
import wallapp.appvisibility.AppVisibility
import wallapp.appvisibility.AppVisibilityDefaultProcess
import wallapp.appvisibility.AppVisibilityWallpaperProcess
import wallapp.auth.firebase.FirebaseAuthManager
import wallapp.auth.firebase.FirebaseAuthManagerFirebase
import wallapp.auth.firebase.FirebaseAuthManagerNoOp
import wallapp.auth.google.GoogleAuthManager
import wallapp.auth.google.GoogleAuthManagerAndroid
import wallapp.billing.BillingManager
import wallapp.billing.BillingManagerRevenueCatAndroid
import wallapp.billing.revenuecat.RevenueCatManager
import wallapp.billing.revenuecat.RevenueCatManagerAndroid
import wallapp.billing.revenuecat.RevenueCatManagerNoOp
import wallapp.billing.revenuecat.RevenueCatUserManager
import wallapp.billing.revenuecat.RevenueCatUserManagerAndroid
import wallapp.billing.revenuecat.RevenueCatUserManagerNoOp
import wallapp.buildconfig.BuildConfig
import wallapp.buildconfig.BuildConfigDefault
import wallapp.coroutine.CoroutineContexts
import wallapp.coroutine.CoroutineContextsAndroid
import wallapp.coroutine.CoroutineContextsDispatchers
import wallapp.crashtracking.CrashTracking
import wallapp.crashtracking.CrashTrackingCrashlyticsAndroid
import wallapp.crashtracking.crashkios.CrashKiosInitializerAndroid
import wallapp.data.DataRepository
import wallapp.data.DataRepositoryDefault
import wallapp.data.favorite.AccountDataRepositoryFirebaseMobile
import wallapp.deeplink.DeepLinkManager
import wallapp.deeplink.DeepLinkManagerDefault
import wallapp.device.DeviceId
import wallapp.device.DeviceIdMock
import wallapp.device.DeviceIdSystem
import wallapp.device.DeviceSpec
import wallapp.device.DeviceSpecAndroid
import wallapp.device.memory.DeviceMemory
import wallapp.device.memory.DeviceMemoryMock
import wallapp.device.memory.DeviceMemorySystem
import wallapp.device.refreshrate.DeviceRefreshRate
import wallapp.device.refreshrate.DeviceRefreshRateSystem
import wallapp.device.refreshrate.DeviceRefreshRateSystemVariable
import wallapp.device.state.DeviceState
import wallapp.device.state.DeviceStateMock
import wallapp.device.state.DeviceStateSystem
import wallapp.download.CONTENT_HTTP_BASE_URL
import wallapp.download.CONTENT_KEY_FROM_FIRESTORE
import wallapp.download.FirebaseStorageDownloader
import wallapp.download.FirebaseStorageDownloaderAndroid
import wallapp.download.FirestoreKeyApiDownloader
import wallapp.download.HttpApiDownloader
import wallapp.download.UrlDownloader
import wallapp.download.UrlDownloaderAndroid
import wallapp.entitlement.EntitlementRepository
import wallapp.entitlement.EntitlementRepositoryDefault
import wallapp.entitlement.EntitlementSkuSpecs
import wallapp.entitlement.EntitlementSkuSpecsGooglePlay
import wallapp.executor.executorService
import wallapp.flavorconfig.FlavorConfig
import wallapp.image.cache.ImageCacheKeyManager
import wallapp.image.cache.ImageCacheKeyManagerCoilMemory
import wallapp.image.cache.ImageCacheKeyManagerNoOp
import wallapp.image.host.ImageHostPlatformConfig
import wallapp.image.host.ImageHostPlatformConfigAndroid
import wallapp.image.host.prefetch.ImagePrefetcherSeikoImageLoader
import wallapp.image.loader.ImageLoader
import wallapp.image.loader.ImageLoaderCoil
import wallapp.image.loader.ImageLoaderSeiko
import wallapp.image.prefetch.ImagePrefetcher
import wallapp.image.prefetch.ImagePrefetcherCoilAndroid
import wallapp.inappbrowser.InAppBrowserManager
import wallapp.inappbrowser.InAppBrowserManagerAndroid
import wallapp.inappreview.InAppReviewManager
import wallapp.inappreview.InAppReviewManagerAndroid
import wallapp.initializer.module.ModuleInitializer
import wallapp.instantapp.InstantAppManager
import wallapp.instantapp.InstantAppManagerNoOp
import wallapp.instantapp.InstantAppManagerSystem
import wallapp.license.state.LicenseStateSessionManager
import wallapp.license.state.LicenseStateSessionManagerNoOp
import wallapp.messaging.CloudMessagingManager
import wallapp.messaging.CloudMessagingManagerAndroid
import wallapp.navigation.AppNavigator
import wallapp.navigation.AppNavigatorDefault
import wallapp.network.NetworkState
import wallapp.network.NetworkStateAndroid
import wallapp.network.NetworkUserManager
import wallapp.network.NetworkUserManagerFirebase
import wallapp.pixel.alert.AlertManager
import wallapp.pixel.alert.AlertManagerComposable
import wallapp.pixel.typeface.TypefaceRepository
import wallapp.preferences.PreferenceDefinitions
import wallapp.preferences.SharedPreferencesMemory
import wallapp.preferences.SharedPreferencesUpgrader
import wallapp.privacymessaging.PrivacyMessagingManager
import wallapp.privacymessaging.PrivacyMessagingManagerGoogleAndroid
import wallapp.process.PROCESS_BRIDGE_INTENT_ACTION_SERVICE_UPDATE_DATA_MAIN
import wallapp.process.PROCESS_BRIDGE_INTENT_ACTION_SERVICE_UPDATE_DATA_WALLPAPER
import wallapp.process.Process
import wallapp.process.ProcessDefault
import wallapp.process.ProcessMockDefault
import wallapp.process.bridge.ProcessBridge
import wallapp.process.bridge.ProcessBridgeAltProcess
import wallapp.process.bridge.ProcessBridgeMainProcess
import wallapp.process.bus.ProcessBusAidl
import wallapp.process.bus.ProcessBusAlt
import wallapp.process.bus.ProcessBusMain
import wallapp.process.isDebugPhoenixProcess
import wallapp.process.isLiveWallpaperProcess
import wallapp.profileimage.ProfileImageManager
import wallapp.profileimage.ProfileImageManagerAndroid
import wallapp.remoteconfig.ConfigValueRepository
import wallapp.remoteconfig.ConfigValueRepositoryFirebase
import wallapp.remoteconfig.RemoteConfig
import wallapp.remoteconfig.RemoteConfigDataDefault
import wallapp.remoteconfig.RemoteConfigFirebase
import wallapp.remoteconfig.data.RemoteConfigData
import wallapp.remoteendpoint.RemoteEndpointMode
import wallapp.remotepaywall.RemotePaywallEventManager
import wallapp.remotepaywall.RemotePaywallEventManagerDefault
import wallapp.remotepaywall.RemotePaywallEventManagerDemo
import wallapp.remotepaywall.RemotePaywallEventRepository
import wallapp.remotepaywall.RemotePaywallEventRepositoryNoOp
import wallapp.remotepaywall.RemotePaywallEventRepositoryRevenueCat
import wallapp.remotepaywall.RemotePaywallInitializer
import wallapp.remotepaywall.RemotePaywallManager
import wallapp.remotepaywall.RemotePaywallManagerNoOp
import wallapp.remotepaywall.RemotePaywallManagerRevenueCatAndroid
import wallapp.runmode.isApp
import wallapp.runmode.isNotApp
import wallapp.runmode.isTest
import wallapp.security.EncryptionManager
import wallapp.security.EncryptionManagerAndroid
import wallapp.settings.Settings
import wallapp.settings.SettingsMemory
import wallapp.settings.SettingsMultiplatform
import wallapp.settings.SettingsSharedPreferences
import wallapp.system.photo.picker.SystemPhotoPicker
import wallapp.system.photo.picker.SystemPhotoPickerAndroid
import wallapp.system.photo.viewer.SystemPhotoViewer
import wallapp.system.photo.viewer.SystemPhotoViewerAndroid
import wallapp.system.platform.PlatformFeature
import wallapp.system.wallpaper.SystemWallpaperManager
import wallapp.system.wallpaper.SystemWallpaperManagerAndroid
import wallapp.system.window.WindowFrameManager
import wallapp.system.window.WindowFrameManagerAndroid
import wallapp.system.window.WindowFrameManagerNoOp
import wallapp.userprofile.UserProfileRepository
import wallapp.userprofile.UserProfileRepositoryFirebase
import wallapp.viewmodel.ViewModelFactory
import wallapp.viewmodel.ViewModelFactoryDefault
import wallapp.viewmodel.ViewModelFactoryPlatform
import wallapp.viewmodel.ViewModelFactoryPlatformAndroid
import wallapp.viewmodel.ViewModelProviderFactory
import wallapp.viewmodel.ViewModelProviderFactoryAndroid
import wallapp.wallpaper.cache.WallpaperImageCache
import wallapp.wallpaper.cache.WallpaperImageCacheAndroid
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import coil.ImageLoader as CoilImageLoader
import com.russhwolf.settings.Settings as SettingsKm

actual val Factory: FactoryCommon = FactoryAndroid

object FactoryAndroid : FactoryCommon() {

    override var remoteEndpointMode: RemoteEndpointMode = RemoteEndpointMode.Firebase
    var useMemorySettings = false
    var useCoil = false

    override fun platformModuleInitializers(scope: Scope): List<ModuleInitializer> {
        return listOfNotNull(
            scope.get<CrashKiosInitializerAndroid>(),
            remotePaywallInitializer(scope),
        )
    }

    override fun appIconManager(scope: Scope): AppIconManager {
        return scope.get<AppIconManagerAndroid>()
    }

    override fun viewModelProviderFactory(scope: Scope): ViewModelProviderFactory {
        return ViewModelProviderFactoryAndroid(scope.get())
    }

    override fun configValueRepository(scope: Scope): ConfigValueRepository {
        return ConfigValueRepositoryFirebase
    }

    override fun accountDataRepositoryServer(scope: Scope): AccountDataRepository {
        return scope.get<AccountDataRepositoryFirebaseMobile>()
    }

    override fun encryptionManager(scope: Scope): EncryptionManager {
        return EncryptionManagerAndroid()
    }

    override fun entitlementRepository(scope: Scope): EntitlementRepository {
//        val buildConfig = scope.get<BuildConfig>()
//        // Temp to unlock all content for Play Store builds (see #75 and #76)
//        return if (buildConfig.debug) {
//            scope.get<EntitlementRepositoryDefault>()
//        } else {
//            EntitlementRepositoryUnlockAll()
//        }
        return scope.get<EntitlementRepositoryDefault>()
    }

    override fun imageHostPlatformConfig(scope: Scope): ImageHostPlatformConfig {
        return ImageHostPlatformConfigAndroid
    }

    override fun imageLoader(scope: Scope): ImageLoader {
        return if (useCoil) {
            ImageLoaderCoil(scope.get())
        } else {
            scope.get<ImageLoaderSeiko>()
        }
    }

    override fun imagePrefetcher(scope: Scope): ImagePrefetcher {
        return if (useCoil) {
            scope.get<ImagePrefetcherCoilAndroid>()
        } else {
            scope.get<ImagePrefetcherSeikoImageLoader>()
        }
    }

//    override fun appShortcutsManager(scope: Scope): AppShortcutsManager {
//        val flavorConfig = scope.get<FlavorConfig>()
//        val instantAppManager = scope.get<InstantAppManager>()
//        return if (flavorConfig.usesAppShortcuts
//            && PlatformFeature.AppShortcutsSupported
//            && !instantAppManager.isInstantApp) {
//            AppShortcutsManagerSystem(scope.get(), scope.get(), scope.get(), scope.get(), flavorConfig)
//        } else {
//            AppShortcutsManagerNoOp()
//        }
//    }

    override fun adSourceInitializer(scope: Scope): AdSourceInitializer =
        adSourceInitializer(scope.get(), scope.get(NamedScope.LazyAdSourceInitializerAdMob), scope.get(NamedScope.LazyAdSourceInitializerNoOp))
    fun adSourceInitializer(
        adInitializerState: AdInitializerState,
        adMobInitializer: Lazy<AdSourceInitializerAdMob>,
        noOpInitializer: Lazy<AdSourceInitializerNoOp>,
    ): AdSourceInitializer = if (adInitializerState.initializeAds) {
        adMobInitializer.get()
    } else {
        noOpInitializer.get()
    }

    override fun adUnitIds(scope: Scope): AdUnitIds {
        return AdUnitIdsAndroid(scope.get())
    }

    override fun appNavigator(scope: Scope): AppNavigator {
        return scope.get<AppNavigatorDefault>()
    }

    override fun appPlatformConfig(scope: Scope): AppPlatformConfig {
        val buildConfig: BuildConfig = scope.get()
        return AppPlatformConfig(
            packageName = buildConfig.packageName,
            mainActivityClassName = "wallapp.app.WallAppActivity",
            wallpaperServiceClassName = if (liveWallpaperAllowed) "wallapp.livewallpaper.WallpaperService" else null,
        )
    }

    override fun appVisibility(scope: Scope): AppVisibility {
        val process: Process = scope.get()
        val default: Lazy<AppVisibilityDefaultProcess> = scope.get(NamedScope.LazyAppVisibilityDefaultProcess)
        val wallpaper: Lazy<AppVisibilityWallpaperProcess> = scope.get(NamedScope.LazyAppVisibilityWallpaperProcess)
        return when {
            process.isLiveWallpaperProcess() -> { wallpaper.get() }
            process.isDefaultProcess -> { default.get() }
            process.isDebugPhoenixProcess() -> { default.get() }
            else -> {
                throw IllegalArgumentException("Unsupported process: ${process.processNameSuffix}")
            }
        }
    }

    override fun billingManager(scope: Scope): BillingManager {
        return if (canConfigureRevenueCat(scope)) {
            scope.get<BillingManagerRevenueCatAndroid>()
        } else {
            scope.get<BillingManager>(NamedScope.BillingManagerFallback)
        }
    }

    override fun cacheFileMediaMapSettings(scope: Scope): Settings {
        if (useMemorySettings) {
            return SettingsMemory()
        }

        val sharedPreferences = scope.get<SharedPreferences>(NamedScope.CacheFileMediaMap)
        val settings: SettingsKm = SharedPreferencesSettings(sharedPreferences)
        return SettingsMultiplatform(settings)
    }

    override fun cloudMessagingManager(scope: Scope): CloudMessagingManager {
        return scope.get<CloudMessagingManagerAndroid>()
    }

    override fun coroutineContexts(scope: Scope): CoroutineContexts {
        return if (runMode.isTest) {
            CoroutineContextsDispatchers
        } else {
            CoroutineContextsAndroid
        }
    }

    override fun crashTrackingRemote(scope: Scope): CrashTracking {
        return CrashTrackingCrashlyticsAndroid(
            scope.get(),
            scope.get(NamedScope.CoroutineScopeMain)
        )
    }

    override fun dataRepository(scope: Scope): DataRepository {
        return scope.get<DataRepositoryDefault>()
    }

    override fun deepLinkManager(scope: Scope): DeepLinkManager {
        return scope.get<DeepLinkManagerDefault>()
    }

    override fun deviceSettings(scope: Scope): Settings {
        if (useMemorySettings) {
            return SettingsMemory()
        }

        val sharedPreferences = scope.get<SharedPreferences>(NamedScope.DeviceSharedPrefs)
        val settings: SettingsKm = SharedPreferencesSettings(sharedPreferences)
        return SettingsMultiplatform(settings)
    }

    override fun deviceRefreshRate(scope: Scope) : DeviceRefreshRate {
        val systemRefreshRate: DeviceRefreshRateSystem = scope.get()
        val appVisibility: AppVisibility = scope.get()
        val process: Process = scope.get()
        val coroutineScopeMain: CoroutineScope = scope.get(NamedScope.CoroutineScopeMain)
        return if (systemRefreshRate.deviceSupportedRefreshRates.size > 1) {
            DeviceRefreshRateSystemVariable(systemRefreshRate, appVisibility, process, coroutineScopeMain)
        } else {
            systemRefreshRate
        }
    }

    override fun deviceSpec(scope: Scope): DeviceSpec {
        return DeviceSpecAndroid(scope.get(), scope.get())
    }

    fun createCoilImageLoader(scope: Scope): CoilImageLoader {
        val context: Context = scope.get()
        val okHttpClient: OkHttpClient = scope.get()
        val buildConfig: BuildConfig = scope.get()
        return CoilImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.2)
                    .build()
            }
            .apply {
                if (buildConfig.debug) {
                    logger(DebugLogger())
                }
            }
            .build()
    }

    fun createImageCacheKeyManager(scope: Scope): ImageCacheKeyManager {
        return if (useCoil) {
            ImageCacheKeyManagerCoilMemory(scope.get())
        } else {
            ImageCacheKeyManagerNoOp
        }
    }

    fun deviceId(scope: Scope): DeviceId {
        if (runMode.isTest) {
            return DeviceIdMock()
        }
        return DeviceIdSystem(scope.get())
    }

    fun deviceMemory(scope: Scope): DeviceMemory {
        if (runMode.isTest) {
            return DeviceMemoryMock()
        }

        return DeviceMemorySystem(scope.get())
    }

    override fun deviceState(scope: Scope): DeviceState {
        val instantAppManager: InstantAppManager = scope.get()
        val deviceStateSystem: Lazy<DeviceStateSystem> = scope.get(NamedScope.LazyDeviceStateSystem)
        return if (instantAppManager.isInstantApp) {
            DeviceStateMock()
        } else {
            deviceStateSystem.get()
        }
    }

    override fun dialogManager(scope: Scope): AlertManager {
        return AlertManagerComposable()
    }

    override fun entitlementSkusSpecs(scope: Scope): EntitlementSkuSpecs {
        return scope.get<EntitlementSkuSpecsGooglePlay>()
    }

    override fun inlineAdItemFactories(scope: Scope): InlineAdItemFactories =
        InlineAdItemFactories(
            listOf(
//                inlineAdItemFactoryInternal.get(),
                scope.get<InlineAdItemFactoryAdMob>(),
            )
        )

    override fun inlineAdConfigFactories(scope: Scope): InlineAdConfigFactories =
        InlineAdConfigFactories(
            listOf(scope.get<InlineAdConfigFactoryAdMob>())
        )

    fun inlineAdManager(scope: Scope): InlineAdManager {
        val defaultManager = scope.get<InlineAdManagerDefault>()
        return InlineAdManagerCached(defaultManager)
    }

    fun createBuildConfig(context: Context, debug: Boolean) =
        BuildConfigDefault(context, debug)

    fun appMainIntent(scope: Scope): Intent {
        val appPlatformConfig: AppPlatformConfig = scope.get()
        return Intent().apply {
            component = ComponentName(appPlatformConfig.packageName, appPlatformConfig.mainActivityClassName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
    }

    override fun systemWallpaperManager(scope: Scope): SystemWallpaperManager = scope.get<SystemWallpaperManagerAndroid>()

    override fun typefaceRepository(scope: Scope): TypefaceRepository = scope.get<TypefaceRepositoryAndroid>()

    fun getRelevantContext(scope: Scope): Context =
        if (PlatformFeature.DeviceProtectedStorageContext) {
            scope.get<Context>().createDeviceProtectedStorageContext()
        } else {
            scope.get<Context>()
        }

    fun userSharedPrefs(scope: Scope) : SharedPreferences {
        return getRelevantContext(scope)
            .getSharedPreferences(PreferenceDefinitions.UserSettingsFilename, Application.MODE_PRIVATE).also {
                scope.get<SharedPreferencesUpgrader>().update(it)
            }
    }

    fun cacheFileMediaMap(scope: Scope): SharedPreferences {
        return getRelevantContext(scope).getSharedPreferences(
            PreferenceDefinitions.CacheFileMediaMapFilename,
            Context.MODE_PRIVATE
        ).
        also {
            scope.get<SharedPreferencesUpgrader>().update(it)
        }
    }

    fun deviceSharedPrefs(scope: Scope): SharedPreferences {
        return getRelevantContext(scope).getSharedPreferences(
            PreferenceDefinitions.DeviceSettingsFilename,
            Context.MODE_PRIVATE
        ).
        also {
            scope.get<SharedPreferencesUpgrader>().update(it)
        }
    }

    fun licenseBackupSharedPrefs(scope: Scope): SharedPreferences {
        return getRelevantContext(scope)
            .getSharedPreferences(
                PreferenceDefinitions.LicenseBackupSettingsFilename,
                Application.MODE_PRIVATE)
    }

    override fun licenseSettings(scope: Scope): Settings {
        if (useMemorySettings) {
            return SettingsMemory()
        }

        return SettingsSharedPreferences(scope.get(NamedScope.LicenseSharedPrefs))
    }

    override fun licenseStateSessionManager(scope: Scope): LicenseStateSessionManager {
//        return LicenseStateSessionManagerActive(scope.get(), scope.get(NamedScope.CoroutineScopeIo))
        return LicenseStateSessionManagerNoOp
    }

    fun licenseSharedPrefs(scope: Scope): SharedPreferences {
        val instantAppManager: InstantAppManager = scope.get()
        return if (instantAppManager.isInstantApp || runMode.isNotApp) {
            SharedPreferencesMemory()
        } else {
            val relevantContext = getRelevantContext(scope)
            EncryptedSharedPreferences.create(
                relevantContext,
                PreferenceDefinitions.LicenseSettingsFilename,
                MasterKey.Builder(relevantContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    override fun toAltProcessBus(scope: Scope): ProcessBusAlt {
//            = ProcessBusIntraBroadcast(context, PROCESS_BRIDGE_INTENT_ACTION_UPDATE_DATA_WALLPAPER)
        val context: Context = scope.get()
        val process: Process = scope.get()
        return ProcessBusAidl(context, process, PROCESS_BRIDGE_INTENT_ACTION_SERVICE_UPDATE_DATA_WALLPAPER)
    }

    override fun toMainProcessBus(scope: Scope): ProcessBusMain {
        val context: Context = scope.get()
        val process: Process = scope.get()
        return ProcessBusAidl(context, process, PROCESS_BRIDGE_INTENT_ACTION_SERVICE_UPDATE_DATA_MAIN)
    }

    override fun inAppBrowserManager(scope: Scope): InAppBrowserManager {
        return scope.get<InAppBrowserManagerAndroid>()
    }

    override fun inAppReviewManager(scope: Scope): InAppReviewManager {
        return scope.get<InAppReviewManagerAndroid>()
    }

    override fun instantAppManager(scope: Scope): InstantAppManager {
        val flavorConfig: FlavorConfig = scope.get()
        val noOp: Lazy<InstantAppManagerNoOp> = scope.get(NamedScope.LazyInstantAppManagerNoOp)
        val system: Lazy<InstantAppManagerSystem> = scope.get(NamedScope.LazyInstantAppManagerSystem)
        return if (flavorConfig.supportsInstantApps) {
            system.get()
        } else {
            noOp.get()
        }
    }

    fun applicationId(scope: Scope): String {
        if (runMode.isTest) {
            return "com.wallapp.test"
        }
        val context: Context = scope.get()
        return context.packageName
    }

    fun accessibilityServiceName(): String {
        return if (liveWallpaperAllowed) {
            TODO("Restore MainAccessibilityService")
//            MainAccessibilityService::class.java.canonicalName ?: ""
        } else {
            ""
        }
    }

    fun executor(): Executor = executorService

    fun executorService(): ExecutorService = executorService

    override fun networkState(scope: Scope) : NetworkState {
        return scope.get<NetworkStateAndroid>()
    }

    override fun networkUserManager(scope: Scope): NetworkUserManager {
        return when (remoteEndpointMode) {
            RemoteEndpointMode.Firebase -> scope.get<NetworkUserManagerFirebase>()
            RemoteEndpointMode.FirebaseAdmin -> error("FirebaseAdmin not supported on Android")
        }
    }

    fun process(scope: Scope): Process {
        if (runMode.isTest) {
            return ProcessMockDefault()
        }
        return ProcessDefault(scope.get())
    }

    override fun processBridge(scope: Scope): ProcessBridge {
        val process: Process = scope.get()
        val processBridgeMainProcess: Lazy<ProcessBridgeMainProcess> = scope.get(NamedScope.LazyProcessBridgeMainProcess)
        val processBridgeAltProcess: Lazy<ProcessBridgeAltProcess> = scope.get(NamedScope.LazyProcessBridgeAltProcess)
        return if (process.isDefaultProcess) {
            processBridgeMainProcess.get()
        } else {
            processBridgeAltProcess.get()
        }
    }

    override fun privacyMessagingManager(scope: Scope): PrivacyMessagingManager {
        return scope.get<PrivacyMessagingManagerGoogleAndroid>()
    }

    override fun profileImageManager(scope: Scope): ProfileImageManager {
        return scope.get<ProfileImageManagerAndroid>()
    }

    override fun remoteConfig(scope: Scope): RemoteConfig {
        return RemoteConfigFirebase(
            buildConfig = scope.get(),
            coroutineScopeIo = scope.get(NamedScope.CoroutineScopeIo),
            deviceState = scope.get(),
        )
    }

    override fun remoteConfigData(scope: Scope): RemoteConfigData {
        return RemoteConfigDataDefault(
            remoteConfig = scope.get(),
            configValueRepository = scope.get(),
            coroutineScopeMain = scope.get(NamedScope.CoroutineScopeMain),
        )
    }

    override fun remoteServerContentSettings(scope: Scope): Settings {
        val context: Context = scope.get()
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val encryptedPreferences = EncryptedSharedPreferences.create(
            context,
            PreferenceDefinitions.RemoteServerContentFilename,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        return SettingsMultiplatform(SharedPreferencesSettings(encryptedPreferences))
    }

    private fun remotePaywallInitializer(scope: Scope): RemotePaywallInitializer? {
        return null
    }

    override fun remotePaywallManager(scope: Scope): RemotePaywallManager {
        return if (canConfigureRevenueCatPaywalls(scope)) {
            RemotePaywallManagerRevenueCatAndroid()
        } else {
            RemotePaywallManagerNoOp
        }
    }

    override fun remotePaywallEventManager(scope: Scope): RemotePaywallEventManager {
        return if (canConfigureRevenueCatPaywalls(scope)) {
            scope.get<RemotePaywallEventManagerDefault>()
        } else {
            RemotePaywallEventManagerDemo(
                scope.get(),
                scope.get(),
                scope.get(),
                scope.get(NamedScope.CoroutineScopeMain)
            )
        }
    }

    override fun remotePaywallEventRepository(scope: Scope): RemotePaywallEventRepository {
        return if (canConfigureRevenueCatPaywalls(scope)) {
            RemotePaywallEventRepositoryRevenueCat
        } else {
            RemotePaywallEventRepositoryNoOp
        }
    }

    override fun revenueCatManager(scope: Scope): RevenueCatManager {
        return if (canConfigureRevenueCat(scope)) {
            scope.get<RevenueCatManagerAndroid>()
        } else {
            scope.get<RevenueCatManagerNoOp>()
        }
    }

    override fun revenueCatUserManager(scope: Scope): RevenueCatUserManager {
        return if (canConfigureRevenueCat(scope)) {
            scope.get<RevenueCatUserManagerAndroid>()
        } else {
            scope.get<RevenueCatUserManagerNoOp>()
        }
    }

    override fun rewardAdConfig(scope: Scope): RewardAdConfig {
        return RewardAdConfigDefault(scope.get(), scope.get(), scope.get(), scope.get(NamedScope.CoroutineScopeMain))
    }

    override fun rewardAdInternalNavigator(scope: Scope): RewardAdInternalNavigator {
        return scope.get<RewardAdInternalNavigatorAndroid>()
    }

    override fun rewardAdInternalPlaybackManager(scope: Scope): RewardAdInternalPlaybackManager {
        return scope.get<RewardAdInternalPlaybackManagerDefault>()
    }

    override fun rewardAdPlaybackManager(scope: Scope): RewardAdPlaybackManager {
        return rewardAdPlaybackManagerDefault(scope) ?: rewardAdPlaybackManagerAdMob(scope)
    }

    override fun rewardAdPlaybackManagerAdMob(scope: Scope): RewardAdPlaybackManager {
        val adUnitIds = scope.get<AdUnitIds>()
        return RewardAdPlaybackManagerAdMobAndroid(
            scope.get(),
            scope.get(),
            adUnitIds.rewardAdUnitId,
            scope.get(NamedScope.LazyGlobalFullScreenAdShowCallbacks),
            scope.get(),
        )
    }

    override fun systemPhotoPicker(scope: Scope): SystemPhotoPicker {
        return scope.get<SystemPhotoPickerAndroid>()
    }

    override fun systemPhotoViewer(scope: Scope): SystemPhotoViewer {
        return scope.get<SystemPhotoViewerAndroid>()
    }

    override fun userProfileRepository(scope: Scope): UserProfileRepository {
        return when (remoteEndpointMode) {
            RemoteEndpointMode.Firebase -> scope.get<UserProfileRepositoryFirebase>()
            RemoteEndpointMode.FirebaseAdmin -> error("FirebaseAdmin not supported on Android")
        }
    }

    override fun userSettings(scope: Scope): Settings {
        if (useMemorySettings) {
            return SettingsMemory()
        }

        val sharedPreferences = scope.get<SharedPreferences>(NamedScope.UserSharedPrefs)
        val settings: SettingsKm = SharedPreferencesSettings(sharedPreferences)
        return SettingsMultiplatform(settings)
    }

    override fun viewModelFactory(scope: Scope): ViewModelFactory {
        // Android is not cached, given AndroidX's ViewModel caching
        return scope.get<ViewModelFactoryDefault>()
    }

    override fun viewModelFactoryPlatform(scope: Scope): ViewModelFactoryPlatform {
        return ViewModelFactoryPlatformAndroid()
    }

    override fun accountDataManager(scope: Scope): AccountDataManager {
        return scope.get<AccountDataManagerDefault>()
    }

    override fun firebaseAuthManager(scope: Scope): FirebaseAuthManager {
        return when (remoteEndpointMode) {
            RemoteEndpointMode.Firebase -> {
                if (scope.isDefaultProcess && runMode.isApp) {
                    FirebaseAuthManagerFirebase(scope.get(), scope.get(NamedScope.CoroutineScopeMain), scope.get())
                } else {
                    FirebaseAuthManagerNoOp
                }
            }
            RemoteEndpointMode.FirebaseAdmin -> {
                error("FirebaseAdmin not supported on Android")
            }
        }
    }

    override fun firebaseStorageDownloader(scope: Scope): FirebaseStorageDownloader {
        if (CONTENT_HTTP_BASE_URL.isNotBlank()) {
            val http = HttpApiDownloader(scope.get<UrlDownloaderAndroid>(), CONTENT_HTTP_BASE_URL)
            return if (CONTENT_KEY_FROM_FIRESTORE) FirestoreKeyApiDownloader(http) else http
        }
        return when (remoteEndpointMode) {
            RemoteEndpointMode.Firebase -> scope.get<FirebaseStorageDownloaderAndroid>()
            RemoteEndpointMode.FirebaseAdmin -> scope.get<FirebaseStorageDownloaderAndroid>()
        }
    }

    override fun googleAuthManager(scope: Scope): GoogleAuthManager {
        return GoogleAuthManagerAndroid(scope.get(), scope.get())
    }

    override fun urlDownloader(scope: Scope): UrlDownloader {
        return scope.get<UrlDownloaderAndroid>()
    }

    override fun wallpaperImageCache(scope: Scope): WallpaperImageCache {
        return WallpaperImageCacheAndroid(scope.get(), scope.get(), scope.get(NamedScope.CoroutineScopeIo), scope.get())
    }

    override fun windowFrameManager(scope: Scope): WindowFrameManager {
        return if (runMode.isNotApp) {
            WindowFrameManagerNoOp
        } else {
            scope.get<WindowFrameManagerAndroid>()
        }
    }
}