package wallapp.account

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.apps
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.initialize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wallapp.auth.firebase.FirebaseAuthManager
import wallapp.auth.firebase.FirebaseAuthManagerMock
import wallapp.auth.firebase.FirebaseAuthUser
import wallapp.time.TimeRepositoryMock
import wallapp.userprofile.LoginType
import wallapp.userprofile.UserProfileLocal
import wallapp.userprofile.UserProfileRemote
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
class UserProfileRepositoryTest {

    private val emulatorHost: String = "10.0.2.2"
    private val context = InstrumentationRegistry.getInstrumentation().context
    private lateinit var firestore: FirebaseFirestore

    private lateinit var firebaseAuthManager: FirebaseAuthManager

    private val firebaseAuthUserMock = FirebaseAuthUser(
        userId = "uid",
        displayName = "displayName",
        email = "email",
        photoUrl = "photoUrl",
        isAnonymous = false,
        providerId = "providerId",
    )

    @Before
    fun initializeFirebase() {
        Firebase.apps(context).firstOrNull() ?: Firebase.initialize(
            context,
            FirebaseOptions(
                applicationId = "...",
                apiKey = "...",
                projectId = "superwall-c01dc",
            )
        )

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
    fun currentUserProfile_noFirebaseUser() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher())

        firebaseAuthManager = FirebaseAuthManagerMock(initialFirebaseAuthUser = null)
        val userProfileRepository = UserProfileRepositoryFirebase(
            timeRepository = TimeRepositoryMock(_currentTime = 100),
            firebaseAuthManager = firebaseAuthManager,
            coroutineScopeMain = scope,
        )

        userProfileRepository.currentUserProfile.test {
            assertNull(awaitItem())
            expectNoEvents()
        }

        scope.cancel()
    }

    @Test
    fun currentUserProfile_hasFirebaseUser_doesNotHaveUserProfile() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher())

        firebaseAuthManager = FirebaseAuthManagerMock(initialFirebaseAuthUser = firebaseAuthUserMock)
        val userProfileRepository = UserProfileRepositoryFirebase(
            timeRepository = TimeRepositoryMock(_currentTime = 100),
            firebaseAuthManager = firebaseAuthManager,
            coroutineScopeMain = scope,
        )

        userProfileRepository.currentUserProfile.test {
            assertNull(awaitItem())
            expectNoEvents()
        }

        scope.cancel()
    }

    @Test
    fun currentUserProfile_hasFirebaseUser_hasUserProfile() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher())

        firestore.collection("users").document(firebaseAuthUserMock.userId).set(
            UserProfileRemote(
                userId = firebaseAuthUserMock.userId,
                email = firebaseAuthUserMock.email,
                isAnonymous = firebaseAuthUserMock.isAnonymous,
                loginType = LoginType.Anonymous.label,
                epochCreated = 100,
                epochLastUpdated = 100,
                epochLastSeen = 100,
                favoriteIds = emptyList(),
                currentWallpaperIds = mapOf(),
                followingIds = emptyList(),
                purchaseRecords = emptyList(),
            )
        )

        firebaseAuthManager = FirebaseAuthManagerMock(initialFirebaseAuthUser = firebaseAuthUserMock)
        val userProfileRepository = UserProfileRepositoryFirebase(
            timeRepository = TimeRepositoryMock(_currentTime = 100),
            firebaseAuthManager = firebaseAuthManager,
            coroutineScopeMain = scope,
        )

        userProfileRepository.currentUserProfile.test {
            assertNull(awaitItem())
            assertEquals(
                UserProfileLocal(
                    userId = firebaseAuthUserMock.userId,
                    email = firebaseAuthUserMock.email,
                    isAnonymous = firebaseAuthUserMock.isAnonymous,
                    loginType = LoginType.Anonymous,
                    epochCreated = 100,
                    epochLastUpdated = 100,
                    epochLastSeen = 100,
                    favoriteIds = emptyList(),
                    currentWallpaperIds = mapOf(),
                    followingIds = emptyList(),
                    purchaseRecords = emptyList(),
                    deviceInfo = "test",
                    currency = "$",
                    newsletter = false,
                    flags = emptyList(),
                    wallpaperDownloadEvents = emptyList(),
                ),
                awaitItem()
            )
            expectNoEvents()
        }

        scope.cancel()
    }

    @Test
    fun createUserProfile() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher())

        firebaseAuthManager = FirebaseAuthManagerMock(initialFirebaseAuthUser = null)
        val userProfileRepository = UserProfileRepositoryFirebase(
            timeRepository = TimeRepositoryMock(_currentTime = 100),
            firebaseAuthManager = firebaseAuthManager,
            coroutineScopeMain = scope,
        )
        val firebaseAuthUser = firebaseAuthUserMock
        userProfileRepository.createUserProfile(firebaseAuthUser, LoginType.Anonymous)
        val userProfileRemote = firestore.collection("users").document(firebaseAuthUser.userId).get().data<UserProfileRemote>()
        assertEquals(
            UserProfileRemote(
                userId = firebaseAuthUser.userId,
                email = firebaseAuthUser.email,
                isAnonymous = firebaseAuthUser.isAnonymous,
                loginType = LoginType.Anonymous.label,
                epochCreated = 100,
                epochLastUpdated = 100,
                epochLastSeen = 100,
                favoriteIds = emptyList(),
                currentWallpaperIds = mapOf(),
                followingIds = emptyList(),
                purchaseRecords = emptyList(),
            ),
            userProfileRemote
        )
        scope.cancel()
    }

    @Test
    fun updateUserProfile() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher())

        firebaseAuthManager = FirebaseAuthManagerMock(initialFirebaseAuthUser = null)
        val userProfileRepository = UserProfileRepositoryFirebase(
            timeRepository = TimeRepositoryMock(_currentTime = 100),
            firebaseAuthManager = firebaseAuthManager,
            coroutineScopeMain = scope,
        )
        firestore.collection("users").document(firebaseAuthUserMock.userId).set(
            UserProfileRemote(
                userId = firebaseAuthUserMock.userId,
                email = firebaseAuthUserMock.email,
                isAnonymous = firebaseAuthUserMock.isAnonymous,
                loginType = LoginType.Anonymous.label,
                epochCreated = 100,
                epochLastUpdated = 100,
                epochLastSeen = 100,
                favoriteIds = emptyList(),
                currentWallpaperIds = mapOf(),
                followingIds = emptyList(),
                purchaseRecords = emptyList(),
            )
        )

        val updatedUserProfileLocal = UserProfileLocal(
            userId = firebaseAuthUserMock.userId,
            email = firebaseAuthUserMock.email,
            isAnonymous = firebaseAuthUserMock.isAnonymous,
            loginType = LoginType.Anonymous,
            epochCreated = 100,
            epochLastUpdated = 200,
            epochLastSeen = 300,
            favoriteIds = emptyList(),
            currentWallpaperIds = mapOf(),
            followingIds = emptyList(),
            purchaseRecords = emptyList(),
            deviceInfo = "test",
            currency = "$",
            newsletter = false,
            flags = emptyList(),
            wallpaperDownloadEvents = emptyList(),
        )

        userProfileRepository.updateUserProfile(updatedUserProfileLocal)

        val userProfileRemote = firestore.collection("users").document(firebaseAuthUserMock.userId).get().data<UserProfileRemote>()
        assertEquals(
            UserProfileRemote(
                userId = firebaseAuthUserMock.userId,
                email = firebaseAuthUserMock.email,
                isAnonymous = firebaseAuthUserMock.isAnonymous,
                loginType = LoginType.Anonymous.label,
                epochCreated = 100,
                epochLastUpdated = 200,
                epochLastSeen = 300,
                favoriteIds = emptyList(),
                currentWallpaperIds = mapOf(),
                followingIds = emptyList(),
                purchaseRecords = emptyList(),
            ),
            userProfileRemote
        )

        scope.cancel()
    }

    @Test
    fun userProfileExists() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher())

        firebaseAuthManager = FirebaseAuthManagerMock(initialFirebaseAuthUser = null)
        val userProfileRepository = UserProfileRepositoryFirebase(
            timeRepository = TimeRepositoryMock(_currentTime = 100),
            firebaseAuthManager = firebaseAuthManager,
            coroutineScopeMain = scope,
        )
        firestore.collection("users").document(firebaseAuthUserMock.userId).set(
            UserProfileRemote(
                userId = firebaseAuthUserMock.userId,
                email = firebaseAuthUserMock.email,
                isAnonymous = firebaseAuthUserMock.isAnonymous,
                loginType = LoginType.Anonymous.label,
                epochCreated = 100,
                epochLastUpdated = 100,
                epochLastSeen = 100,
                favoriteIds = emptyList(),
                currentWallpaperIds = mapOf(),
                followingIds = emptyList(),
                purchaseRecords = emptyList(),
            )
        )

        val userProfileExists = userProfileRepository.userProfileExists(firebaseAuthUserMock.userId)
        assertEquals(true, userProfileExists)

        scope.cancel()
    }

    @Test
    fun userProfileExists_notExists() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher())

        firebaseAuthManager = FirebaseAuthManagerMock(initialFirebaseAuthUser = null)
        val userProfileRepository = UserProfileRepositoryFirebase(
            timeRepository = TimeRepositoryMock(_currentTime = 100),
            firebaseAuthManager = firebaseAuthManager,
            coroutineScopeMain = scope,
        )

        val userProfileExists = userProfileRepository.userProfileExists("non-existent-user-id")
        assertEquals(false, userProfileExists)

        scope.cancel()
    }

    @Test
    fun getUserProfile() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher())

        firebaseAuthManager = FirebaseAuthManagerMock(initialFirebaseAuthUser = null)
        val userProfileRepository = UserProfileRepositoryFirebase(
            timeRepository = TimeRepositoryMock(_currentTime = 100),
            firebaseAuthManager = firebaseAuthManager,
            coroutineScopeMain = scope,
        )
        firestore.collection("users").document(firebaseAuthUserMock.userId).set(
            UserProfileRemote(
                userId = firebaseAuthUserMock.userId,
                email = firebaseAuthUserMock.email,
                isAnonymous = firebaseAuthUserMock.isAnonymous,
                loginType = LoginType.Anonymous.label,
                epochCreated = 100,
                epochLastUpdated = 100,
                epochLastSeen = 100,
                favoriteIds = emptyList(),
                currentWallpaperIds = mapOf(),
                followingIds = emptyList(),
                purchaseRecords = emptyList(),
            )
        )

        val userProfileLocal = userProfileRepository.getUserProfile(firebaseAuthUserMock.userId)
        assertEquals(
            UserProfileLocal(
                userId = firebaseAuthUserMock.userId,
                email = firebaseAuthUserMock.email,
                isAnonymous = firebaseAuthUserMock.isAnonymous,
                loginType = LoginType.Anonymous,
                epochCreated = 100,
                epochLastUpdated = 100,
                epochLastSeen = 100,
                favoriteIds = emptyList(),
                currentWallpaperIds = mapOf(),
                followingIds = emptyList(),
                purchaseRecords = emptyList(),
                deviceInfo = "test",
                currency = "$",
                newsletter = false,
                flags = emptyList(),
                wallpaperDownloadEvents = emptyList(),
            ),
            userProfileLocal
        )

        scope.cancel()
    }

    @Test
    fun getUserProfile_notExists() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher())

        firebaseAuthManager = FirebaseAuthManagerMock(initialFirebaseAuthUser = null)
        val userProfileRepository = UserProfileRepositoryFirebase(
            timeRepository = TimeRepositoryMock(_currentTime = 100),
            firebaseAuthManager = firebaseAuthManager,
            coroutineScopeMain = scope,
        )

        val userProfileLocal = userProfileRepository.getUserProfile("non-existent-user-id")
        assertEquals(null, userProfileLocal)

        scope.cancel()
    }

    @Test
    fun deleteUserProfile() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher())

        firebaseAuthManager = FirebaseAuthManagerMock(initialFirebaseAuthUser = null)
        val userProfileRepository = UserProfileRepositoryFirebase(
            timeRepository = TimeRepositoryMock(_currentTime = 100),
            firebaseAuthManager = firebaseAuthManager,
            coroutineScopeMain = scope,
        )
        firestore.collection("users").document(firebaseAuthUserMock.userId).set(
            UserProfileRemote(
                userId = firebaseAuthUserMock.userId,
                email = firebaseAuthUserMock.email,
                isAnonymous = firebaseAuthUserMock.isAnonymous,
                loginType = LoginType.Anonymous.label,
                epochCreated = 100,
                epochLastUpdated = 100,
                epochLastSeen = 100,
                favoriteIds = emptyList(),
                currentWallpaperIds = mapOf(),
                followingIds = emptyList(),
                purchaseRecords = emptyList(),
            )
        )

        userProfileRepository.deleteUserProfile(firebaseAuthUserMock.userId)

        val userProfileRemote = firestore.collection("users").document(firebaseAuthUserMock.userId).get()
        assertFalse(userProfileRemote.exists)

        scope.cancel()
    }

    @Test
    fun deleteUserProfile_notExists() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher())

        firebaseAuthManager = FirebaseAuthManagerMock(initialFirebaseAuthUser = null)
        val userProfileRepository = UserProfileRepositoryFirebase(
            timeRepository = TimeRepositoryMock(_currentTime = 100),
            firebaseAuthManager = firebaseAuthManager,
            coroutineScopeMain = scope,
        )

        userProfileRepository.deleteUserProfile("non-existent-user-id")

        scope.cancel()
    }
}
