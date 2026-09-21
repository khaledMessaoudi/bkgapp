package wallapp.account

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.apps
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wallapp.auth.firebase.FirebaseAuthManager
import wallapp.auth.firebase.FirebaseAuthManagerFirebase
import wallapp.buildconfig.BuildConfigMock
import wallapp.process.ProcessMockDefault
import wallapp.result.ResultEx
import wallapp.signin.SignInProviderCredentials


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
class FirebaseAuthManagerFirebaseTest {

    private val emulatorHost: String = "10.0.2.2"
    private val context = InstrumentationRegistry.getInstrumentation().context
    private lateinit var firebaseAuth: FirebaseAuth

    private lateinit var firebaseAuthManager: FirebaseAuthManager

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
    }

    @After
    fun cleanup() = runTest {
        Firebase.apps(context).forEach { app ->
            app.delete()
        }
    }

    @Test
    fun signInAnonymously() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher())

        firebaseAuthManager = FirebaseAuthManagerFirebase(
            process = ProcessMockDefault(),
            coroutineScopeMain = scope,
            buildConfig = BuildConfigMock(),
        )

        val result = firebaseAuthManager.signInAnonymously()

        assertTrue(result is ResultEx.Success)
        assertTrue((result as ResultEx.Success).data.isAnonymous)
        assertEquals(result.data.userId, firebaseAuth.currentUser?.uid)
        firebaseAuthManager.firebaseAuthUser.test {
            assertEquals(result.data, awaitItem())
            expectNoEvents()
        }

        // cleanup
        firebaseAuth.currentUser?.delete()
        scope.cancel()
    }

    @Test
    fun signIn() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher())

        firebaseAuthManager = FirebaseAuthManagerFirebase(
            process = ProcessMockDefault(),
            coroutineScopeMain = scope,
            buildConfig = BuildConfigMock(),
        )

        val result = firebaseAuthManager.signIn(
            signInProviderCredentials = SignInProviderCredentials.Email(
                email = "testEmail@testingE.com",
                password = "testPassword",
            )
        )

        assertTrue(result is ResultEx.Success)
        assertFalse((result as ResultEx.Success).data.isAnonymous)
        assertEquals(result.data.userId, firebaseAuth.currentUser?.uid)
        assertEquals(result.data.email, firebaseAuth.currentUser?.email)
        assertEquals(result.data.displayName, firebaseAuth.currentUser?.displayName)
        firebaseAuthManager.firebaseAuthUser.test {
            assertEquals(result.data, awaitItem())
            expectNoEvents()
        }

        // cleanup
        firebaseAuth.currentUser?.delete()
        scope.cancel()
    }

    @Test
    fun linkCredentials_anonymous_to_email() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher())

        firebaseAuthManager = FirebaseAuthManagerFirebase(
            process = ProcessMockDefault(),
            coroutineScopeMain = scope,
            buildConfig = BuildConfigMock(),
        )

        val result = firebaseAuthManager.signInAnonymously()

        assertTrue(result is ResultEx.Success)
        assertTrue((result as ResultEx.Success).data.isAnonymous)
        assertEquals(result.data.userId, firebaseAuth.currentUser?.uid)
        val currentUserId = firebaseAuth.currentUser?.uid

        val resultLink = firebaseAuthManager.linkWithCredential(
            signInProviderCredentials = SignInProviderCredentials.Email(
                email = "testEmail@testingE.com",
                password = "testPassword",
            )
        )

        assertTrue(resultLink is ResultEx.Success)
        assertFalse((resultLink as ResultEx.Success).data.isAnonymous)
        assertEquals(resultLink.data.userId, firebaseAuth.currentUser?.uid)
        assertEquals(resultLink.data.email, firebaseAuth.currentUser?.email)
        assertEquals(resultLink.data.displayName, firebaseAuth.currentUser?.displayName)
        firebaseAuthManager.firebaseAuthUser.test {
            assertEquals(resultLink.data, awaitItem())
            expectNoEvents()
        }
        assertEquals(currentUserId, firebaseAuth.currentUser?.uid)

        // cleanup
        firebaseAuth.currentUser?.delete()
        scope.cancel()
    }

    @Test
    fun linkCredentials_noCurrentUser() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher())

        firebaseAuthManager = FirebaseAuthManagerFirebase(
            process = ProcessMockDefault(),
            coroutineScopeMain = scope,
            buildConfig = BuildConfigMock(),
        )

        val resultLink = firebaseAuthManager.linkWithCredential(
            signInProviderCredentials = SignInProviderCredentials.Email(
                email = "testEmail@testingE.com",
                password = "testPassword",
            )
        )

        assertTrue(resultLink is ResultEx.Error)
        assertEquals("No current user", (resultLink as ResultEx.Error).exception.message)

        // cleanup
        scope.cancel()
    }

    @Test
    fun signOut() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher())

        firebaseAuthManager = FirebaseAuthManagerFirebase(
            process = ProcessMockDefault(),
            coroutineScopeMain = scope,
            buildConfig = BuildConfigMock(),
        )

        val result = firebaseAuthManager.signInAnonymously()

        assertTrue(result is ResultEx.Success)
        assertTrue((result as ResultEx.Success).data.isAnonymous)
        assertEquals(result.data.userId, firebaseAuth.currentUser?.uid)
        firebaseAuthManager.firebaseAuthUser.test {
            assertEquals(result.data, awaitItem())
            expectNoEvents()
        }

        firebaseAuthManager.signOut()

        assertNull(firebaseAuth.currentUser)
        firebaseAuthManager.firebaseAuthUser.test {
            while (awaitItem() != null) {
                // wait for null
            }
            expectNoEvents()
        }

        // cleanup
        scope.cancel()
    }
}