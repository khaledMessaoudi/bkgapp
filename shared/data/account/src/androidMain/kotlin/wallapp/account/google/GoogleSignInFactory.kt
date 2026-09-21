package wallapp.account.google

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

object GoogleSignInFactory {

    fun createGoogleSignInClient(context: Context): GoogleSignInClient {
        // This id token must match the `client_id` in `app/android/src/google-services.json`.
        // The value is also in "R.string.default_web_client_id", but that's inaccessible from here.
        val requestIdToken = "1091720592113-0pa7pkf8pvlcnoc05bo3ko0q9u8ctcg6.apps.googleusercontent.com"

        val signInOptions: GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(requestIdToken)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, signInOptions)
    }
}