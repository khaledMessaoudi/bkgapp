package wallapp.account

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.apps
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.initialize
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wallapp.account.data.AccountDataManager
import wallapp.account.data.AccountDataManagerNoOp
import wallapp.auth.firebase.FirebaseAuthManager
import wallapp.auth.firebase.FirebaseAuthManagerFirebase
import wallapp.buildconfig.BuildConfigMock
import wallapp.preferences.UserPreferences
import wallapp.process.ProcessMock
import wallapp.resources.string.StringRepositoryPreset
import wallapp.result.ResultEx
import wallapp.signin.SignInProviderControllerMock
import wallapp.signin.SignInProviderCredentials
import wallapp.system.toast.ToastDisplayControllerNoOp
import wallapp.time.TimeRepositoryMock
import wallapp.userprofile.LoginType
import wallapp.userprofile.UserProfileLocal
import wallapp.userprofile.UserProfileRepository
import wallapp.userprofile.UserProfileRepositoryFirebase

/**
 * Requirement for this test to pass:
 * 1. Start the Firebase emulator
 * 2. Comment out the following code in firestore.rules:
 * ```
 * match /databases/{database}/documents {
 *  match /{document=**} {
 *  allow read, write: if request.auth != null;
 *  }
 * }
 * ```
 * 3. Run the test on an Android emulator only
 * Reference for Firebase emulator: https://firebase.google.com/docs/emulator-suite/connect_and_prototype
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AccountManagerDefaultFirebaseTest {

    private val emulatorHost: String = "10.0.2.2"
    private val context = InstrumentationRegistry.getInstrumentation().context
    private val userPreferences = mockk<UserPreferences>()
    private val accountDataManager = mockk<AccountDataManager>(relaxed = true)
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var firebaseAuthManager: FirebaseAuthManager
    private lateinit var userProfileRepository: UserProfileRepository
    private lateinit var signInProviderController: SignInProviderControllerMock
    private lateinit var timeRepositoryMock: TimeRepositoryMock
    private lateinit var accountManagerDefault: AccountManagerDefault

    @Before
    fun initializeFirebase() = runTest {
        Firebase.apps(context).firstOrNull() ?: Firebase.initialize(
            context,
            FirebaseOptions(
                applicationId = "...",
                apiKey = "...",
                projectId = "superwall-c01dc",
            )
        )

        firebaseAuth = Firebase.auth.apply {
            useEmulator(emulatorHost, 9099)
        }

        // For some reason, after Firebase is initialised, it has a currentUser
        firebaseAuth.signOut()

        firestore = Firebase.firestore.apply {
            useEmulator(emulatorHost, 8080)
            setSettings(persistenceEnabled = false)
        }
    }

    @After
    fun cleanup() = runTest {
        Firebase.apps(context).forEach { app ->
            app.delete()
        }
    }

    @Test
    fun signIn_anonymous_userNotAlreadySignedIn() = runTest {
        val testScope = TestScope(UnconfinedTestDispatcher())
        firebaseAuthManager = FirebaseAuthManagerFirebase(ProcessMock(), testScope, BuildConfigMock())
        timeRepositoryMock = TimeRepositoryMock(_currentTime = 100)
        userProfileRepository = UserProfileRepositoryFirebase(timeRepositoryMock, firebaseAuthManager, testScope)
        signInProviderController = SignInProviderControllerMock()
        accountManagerDefault = AccountManagerDefault(
            firebaseAuthManager,
            accountDataManager,
            userPreferences,
            userProfileRepository,
            signInProviderController,
            timeRepositoryMock,
            ToastDisplayControllerNoOp,
            StringRepositoryPreset(),
            testScope
        )

        assertNull(userProfileRepository.currentUserProfile.value)
        assertNull(accountManagerDefault.signedInAccount.value)
        assertNull(firebaseAuth.currentUser)

        val result = accountManagerDefault.signIn(SignInMethod.Anonymous)

        assertEquals(ResultEx.Success(Unit), result)
        assertNull(accountManagerDefault.signedInAccount.value)
        assertNotNull(firebaseAuthManager.firebaseAuthUser.value)
        assertTrue(firebaseAuthManager.firebaseAuthUser.value?.isAnonymous == true)

        // User profile is created
        val expectedUserProfile = UserProfileLocal(
            userId = firebaseAuthManager.firebaseAuthUser.value?.userId!!,
            isAnonymous = true,
            epochCreated = 100,
            epochLastUpdated = 100,
            epochLastSeen = 100,
            loginType = LoginType.Anonymous,
            favoriteIds = emptyList(),
            currentWallpaperIds = emptyMap(),
            followingIds = emptyList(),
            purchaseRecords = emptyList(),
            deviceInfo = "test",
            currency = "$",
            newsletter = false,
            flags = emptyList(),
            wallpaperDownloadEvents = emptyList(),
        )
        assertEquals(expectedUserProfile, userProfileRepository.currentUserProfile.value)

        // local data is synced on new user creation
        coVerify { accountDataManager.syncLocalDataToRemote() }

        testScope.cancel()
    }

    @Test
    fun signIn_anonymous_userAlreadySignedIn_fails() = runTest {
        val testScope = TestScope(UnconfinedTestDispatcher())
        firebaseAuthManager = FirebaseAuthManagerFirebase(ProcessMock(), testScope, BuildConfigMock())
        timeRepositoryMock = TimeRepositoryMock(_currentTime = 100)
        userProfileRepository = UserProfileRepositoryFirebase(timeRepositoryMock, firebaseAuthManager, testScope)
        signInProviderController = SignInProviderControllerMock()
        accountManagerDefault = AccountManagerDefault(
            firebaseAuthManager,
            accountDataManager,
            userPreferences,
            userProfileRepository,
            signInProviderController,
            timeRepositoryMock,
            ToastDisplayControllerNoOp,
            StringRepositoryPreset(),
            testScope
        )

        assertNull(userProfileRepository.currentUserProfile.value)
        assertNull(accountManagerDefault.signedInAccount.value)
        assertNull(firebaseAuth.currentUser)

        val result = accountManagerDefault.signIn(SignInMethod.Anonymous)

        assertEquals(ResultEx.Success(Unit), result)
        assertNull(accountManagerDefault.signedInAccount.value)
        assertNotNull(firebaseAuthManager.firebaseAuthUser.value)
        assertTrue(firebaseAuthManager.firebaseAuthUser.value?.isAnonymous == true)

        // User profile is created
        val expectedUserProfile = UserProfileLocal(
            userId = firebaseAuthManager.firebaseAuthUser.value?.userId!!,
            isAnonymous = true,
            epochCreated = 100,
            epochLastUpdated = 100,
            epochLastSeen = 100,
            loginType = LoginType.Anonymous,
            favoriteIds = emptyList(),
            currentWallpaperIds = emptyMap(),
            followingIds = emptyList(),
            purchaseRecords = emptyList(),
            deviceInfo = "test",
            currency = "$",
            newsletter = false,
            flags = emptyList(),
            wallpaperDownloadEvents = emptyList(),
        )
        assertEquals(expectedUserProfile, userProfileRepository.currentUserProfile.value)

        // local data is synced on new user creation
        coVerify { accountDataManager.syncLocalDataToRemote() }

        val result2 = accountManagerDefault.signIn(SignInMethod.Anonymous)
        assertEquals(ResultEx.Error(AccountError.UserAlreadySignedInError), result2)

        testScope.cancel()
    }

    @Test
    fun signIn_email_succeeds() = runTest {
        val testScope = TestScope(UnconfinedTestDispatcher())
        firebaseAuthManager = FirebaseAuthManagerFirebase(ProcessMock(), testScope, BuildConfigMock())
        timeRepositoryMock = TimeRepositoryMock(_currentTime = 100)
        userProfileRepository = UserProfileRepositoryFirebase(timeRepositoryMock, firebaseAuthManager, testScope)
        signInProviderController = SignInProviderControllerMock().apply {
            performProviderSignInResult = ResultEx.Success(SignInProviderCredentials.Email("test@testing1.com", "password"))
        }
        accountManagerDefault = AccountManagerDefault(
            firebaseAuthManager,
            accountDataManager,
            userPreferences,
            userProfileRepository,
            signInProviderController,
            timeRepositoryMock,
            ToastDisplayControllerNoOp,
            StringRepositoryPreset(),
            testScope
        )

        assertNull(userProfileRepository.currentUserProfile.value)
        assertNull(accountManagerDefault.signedInAccount.value)
        assertNull(firebaseAuth.currentUser)

        val result = accountManagerDefault.signIn(SignInMethod.Email)

        assertEquals(ResultEx.Success(Unit), result)
        assertNotNull(firebaseAuth.currentUser)
        assertEquals("test@testing1.com", firebaseAuth.currentUser?.email)
        assertTrue(firebaseAuth.currentUser?.isAnonymous == false)
        val expectedUserProfile = UserProfileLocal(
            userId = firebaseAuth.currentUser?.uid!!,
            email = "test@testing1.com",
            isAnonymous = false,
            epochCreated = 100,
            epochLastUpdated = 100,
            epochLastSeen = 100,
            loginType = LoginType.Email,
            favoriteIds = emptyList(),
            currentWallpaperIds = emptyMap(),
            followingIds = emptyList(),
            purchaseRecords = emptyList(),
            deviceInfo = "test",
            currency = "$",
            newsletter = false,
            flags = emptyList(),
            wallpaperDownloadEvents = emptyList(),
        )
        assertEquals(expectedUserProfile, userProfileRepository.getUserProfile(firebaseAuth.currentUser?.uid!!))

        // local data is synced on new user creation
        coVerify { accountDataManager.syncLocalDataToRemote() }

        // cleanup
        userProfileRepository.deleteUserProfile(firebaseAuth.currentUser?.uid!!)
        firebaseAuth.currentUser?.delete()
        testScope.cancel()
    }

    @Test
    fun signIn_email_userAlreadySignedIn_fails() = runTest {
        val testScope = TestScope(UnconfinedTestDispatcher())
        firebaseAuthManager = FirebaseAuthManagerFirebase(ProcessMock(), testScope, BuildConfigMock())
        timeRepositoryMock = TimeRepositoryMock(_currentTime = 100)
        userProfileRepository = UserProfileRepositoryFirebase(timeRepositoryMock, firebaseAuthManager, testScope)
        signInProviderController = SignInProviderControllerMock().apply {
            performProviderSignInResult = ResultEx.Success(SignInProviderCredentials.Email("test1@testing.com", "password"))
        }
        accountManagerDefault = AccountManagerDefault(
            firebaseAuthManager,
            accountDataManager,
            userPreferences,
            userProfileRepository,
            signInProviderController,
            timeRepositoryMock,
            ToastDisplayControllerNoOp,
            StringRepositoryPreset(),
            testScope
        )

        assertNull(firebaseAuth.currentUser)

        val result = accountManagerDefault.signIn(SignInMethod.Email)

        assertEquals(ResultEx.Success(Unit), result)
        assertNotNull(firebaseAuth.currentUser)
        assertEquals("test1@testing.com", firebaseAuth.currentUser?.email)
        assertTrue(firebaseAuth.currentUser?.isAnonymous == false)

        val result2 = accountManagerDefault.signIn(SignInMethod.Email)
        assertEquals(ResultEx.Error(AccountError.UserAlreadySignedInError), result2)

        // cleanup
        userProfileRepository.deleteUserProfile(firebaseAuth.currentUser?.uid!!)
        firebaseAuth.currentUser?.delete()
        testScope.cancel()
    }

    @Test
    fun signIn_anonymous_linkCredentials_email_succeeds() = runTest {
        val testScope = TestScope(UnconfinedTestDispatcher())
        firebaseAuthManager =
            FirebaseAuthManagerFirebase(ProcessMock(), testScope, BuildConfigMock())
        timeRepositoryMock = TimeRepositoryMock(_currentTime = 100)
        userProfileRepository =
            UserProfileRepositoryFirebase(timeRepositoryMock, firebaseAuthManager, testScope)
        signInProviderController = SignInProviderControllerMock().apply {
            performProviderSignInResult =
                ResultEx.Success(SignInProviderCredentials.Email("test1@testing.com", "password"))
        }
        accountManagerDefault = AccountManagerDefault(
            firebaseAuthManager,
            accountDataManager,
            userPreferences,
            userProfileRepository,
            signInProviderController,
            timeRepositoryMock,
            ToastDisplayControllerNoOp,
            StringRepositoryPreset(),
            testScope
        )

        assertNull(firebaseAuth.currentUser)

        val result = accountManagerDefault.signIn(SignInMethod.Anonymous)
        assertEquals(ResultEx.Success(Unit), result)
        assertNotNull(firebaseAuth.currentUser)
        assertTrue(firebaseAuth.currentUser?.isAnonymous == true)
        val firstUserId = firebaseAuth.currentUser?.uid!!
        val expectedUserProfile1 = UserProfileLocal(
            userId = firebaseAuth.currentUser?.uid!!,
            email = null,
            isAnonymous = true,
            epochCreated = 100,
            epochLastUpdated = 100,
            epochLastSeen = 100,
            loginType = LoginType.Anonymous,
            favoriteIds = emptyList(),
            currentWallpaperIds = emptyMap(),
            followingIds = emptyList(),
            purchaseRecords = emptyList(),
            deviceInfo = "test",
            currency = "$",
            newsletter = false,
            flags = emptyList(),
            wallpaperDownloadEvents = emptyList(),
        )
        assertEquals(expectedUserProfile1, userProfileRepository.getUserProfile(firebaseAuth.currentUser?.uid!!))

        timeRepositoryMock._currentTime = 200

        val result2 = accountManagerDefault.signIn(SignInMethod.Email)
        assertEquals(ResultEx.Success(Unit), result2)
        assertNotNull(firebaseAuth.currentUser)
        assertEquals("test1@testing.com", firebaseAuth.currentUser?.email)
        assertTrue(firebaseAuth.currentUser?.isAnonymous == false)
        val secondUserId = firebaseAuth.currentUser?.uid!!
        assertEquals(firstUserId, secondUserId)
        val expectedUserProfile2 = UserProfileLocal(
            userId = firebaseAuth.currentUser?.uid!!,
            email = "test1@testing.com",
            isAnonymous = false,
            epochCreated = 100,
            epochLastUpdated = 200,
            epochLastSeen = 200,
            loginType = LoginType.Email,
            favoriteIds = emptyList(),
            currentWallpaperIds = emptyMap(),
            followingIds = emptyList(),
            purchaseRecords = emptyList(),
            deviceInfo = "test",
            currency = "$",
            newsletter = false,
            flags = emptyList(),
            wallpaperDownloadEvents = emptyList(),
        )
        assertEquals(expectedUserProfile2, userProfileRepository.getUserProfile(firebaseAuth.currentUser?.uid!!))

        // cleanup
        userProfileRepository.deleteUserProfile(firebaseAuth.currentUser?.uid!!)
        firebaseAuth.currentUser?.delete()
        testScope.cancel()
    }

    @Test
    fun signIn_email_anonymousUserExists_linkCredentials_accountAlreadyLinked_signInSucceeds() = runTest {
        val testScope = TestScope(UnconfinedTestDispatcher())
        firebaseAuthManager =
            FirebaseAuthManagerFirebase(ProcessMock(), testScope, BuildConfigMock())
        timeRepositoryMock = TimeRepositoryMock(_currentTime = 100)
        userProfileRepository =
            UserProfileRepositoryFirebase(timeRepositoryMock, firebaseAuthManager, testScope)
        signInProviderController = SignInProviderControllerMock().apply {
            performProviderSignInResult =
                ResultEx.Success(SignInProviderCredentials.Email("test1@testing.com", "password"))
        }
        accountManagerDefault = AccountManagerDefault(
            firebaseAuthManager,
            accountDataManager,
            userPreferences,
            userProfileRepository,
            signInProviderController,
            timeRepositoryMock,
            ToastDisplayControllerNoOp,
            StringRepositoryPreset(),
            testScope
        )

        assertNull(firebaseAuth.currentUser)

        val result = accountManagerDefault.signIn(SignInMethod.Email)
        assertEquals(ResultEx.Success(Unit), result)
        assertNotNull(firebaseAuth.currentUser)
        assertEquals("test1@testing.com", firebaseAuth.currentUser?.email)
        assertTrue(firebaseAuth.currentUser?.isAnonymous == false)
        val originalUserId = firebaseAuth.currentUser?.uid!!

        accountManagerDefault.signOut()

        assertNull(firebaseAuth.currentUser)

        val result2 = accountManagerDefault.signIn(SignInMethod.Anonymous)
        assertEquals(ResultEx.Success(Unit), result2)
        assertNotNull(firebaseAuth.currentUser)
        assertTrue(firebaseAuth.currentUser?.isAnonymous == true)
        val anonymousUserId = firebaseAuth.currentUser?.uid!!
        assertNotEquals(originalUserId, anonymousUserId)

        timeRepositoryMock._currentTime = 200

        val result3 = accountManagerDefault.signIn(SignInMethod.Email)
        assertEquals(ResultEx.Success(Unit), result3)
        assertNotNull(firebaseAuth.currentUser)
        assertEquals("test1@testing.com", firebaseAuth.currentUser?.email)
        assertTrue(firebaseAuth.currentUser?.isAnonymous == false)
        val newUserId = firebaseAuth.currentUser?.uid!!
        assertEquals(originalUserId, newUserId)

        // cleanup
        userProfileRepository.deleteUserProfile(firebaseAuth.currentUser?.uid!!)
        firebaseAuth.currentUser?.delete()
        testScope.cancel()
    }

    @Test
    fun init_authUserNull() = runTest {
        val testScope = TestScope(UnconfinedTestDispatcher())
        firebaseAuthManager = FirebaseAuthManagerFirebase(ProcessMock(), testScope, BuildConfigMock())
        timeRepositoryMock = TimeRepositoryMock(_currentTime = 100)
        userProfileRepository = UserProfileRepositoryFirebase(timeRepositoryMock, firebaseAuthManager, testScope)
        signInProviderController = SignInProviderControllerMock()
        accountManagerDefault = AccountManagerDefault(
            firebaseAuthManager,
            AccountDataManagerNoOp,
            userPreferences,
            userProfileRepository,
            signInProviderController,
            timeRepositoryMock,
            ToastDisplayControllerNoOp,
            StringRepositoryPreset(),
            testScope
        )

        accountManagerDefault.signedInAccount.test {
            assertNull(awaitItem())
        }

        testScope.cancel()
    }
}
