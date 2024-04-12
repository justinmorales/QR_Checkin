package com.example.qrcheckin

import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.qrcheckin.databinding.ActivityMainBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpStatusCodes
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.security.GeneralSecurityException
import java.util.Collections
import kotlin.system.exitProcess


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var uniqueID: String
    private lateinit var mGoogleSignInClient: GoogleSignInClient
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>
    private lateinit var httpTransport: HttpTransport
    private var jsonFactory: GsonFactory = GsonFactory.getDefaultInstance()
    private var APPLICATION_NAME: String = "qrcheckin"
    private var TOKENS_DIRECTORY_PATH = "tokens"

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private var SCOPES: List<String> = Collections.singletonList(SheetsScopes.SPREADSHEETS_READONLY)
    private var CREDENTIALS_FILE_PATH: String = "app/src/main/assets/credentials.json"


    private fun setResult(string: String) {
        // This snippet can be edited to grab whatever id from whatever qr code
        if (string.startsWith(getString(R.string.jotform_url))) {
            val lastIndex = string.lastIndexOf('/')
            uniqueID = string.substring(lastIndex + 1)

            val intent = Intent(this,MainActivity2::class.java)
            intent.putExtra("unique_ID", uniqueID)
            startActivity(intent)

            //binding.textResult.text = unique_ID
        }
        else{
            Toast.makeText(this, "Not a valid QR Code",Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initBinding()

        // Set Button
        initViews()

        httpTransport = try {
            GoogleNetHttpTransport.newTrustedTransport()
        } catch (exception: GeneralSecurityException) {
            // Handle security exception
            throw RuntimeException("Error initializing HTTP transport", exception)
        } catch (exception: IOException) {
            // Handle IO exception
            throw RuntimeException("Error initializing HTTP transport", exception)
        }

        // Get Google Account
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)

        signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // Handle successful sign-in
                val data: Intent? = result.data
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                handleSignInResult(task)
            } else {
                // Handle failed sign-in
                finish()
                exitProcess(0)
            }
        }

        // Launch Google Sign-In UI
        val signInIntent = mGoogleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)


        // Ignore
        /*ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.layout_result)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets

         */
    }

    /**
     * Creates an authorized Credential object.
     *
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */

    private fun getCredentials(HTTP_TRANSPORT: NetHttpTransport): Credential {

        val inStream: InputStream = assets.open("/credentials.json")
            ?: throw FileNotFoundException("Asset not found: $CREDENTIALS_FILE_PATH")
        val clientSecrets = GoogleClientSecrets.load(jsonFactory, InputStreamReader(inStream))

        val flow = GoogleAuthorizationCodeFlow.Builder(
            HTTP_TRANSPORT, jsonFactory, clientSecrets, SCOPES
        )
            .setDataStoreFactory(FileDataStoreFactory(java.io.File(TOKENS_DIRECTORY_PATH)))
            .setAccessType("offline")
            .build()
        val receiver = LocalServerReceiver.Builder().build()
        return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    }

    private fun handleSignInResult(task: Task<GoogleSignInAccount>) {
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken // Obtain the ID token from the GoogleSignInAccount
            if (idToken != null) {
                // Successfully obtained ID token, use it to create credentials
                verifyAccessToGoogleSheet(idToken)
            } else {
                // ID token is null, handle error
                // You might want to prompt the user to sign in again or handle the error accordingly
            }
        } catch (e: ApiException) {
            // Sign in failed, handle error
            Log.w(TAG, "signInResult:failed code=" + e.statusCode)
        }
    }

    // Function to create Google API client credentials using the ID token
    private fun verifyAccessToGoogleSheet(idToken: String) {
        try {
            val verifier = GoogleIdTokenVerifier.Builder(httpTransport, jsonFactory)
                .setAudience(listOf(R.string.clientID.toString()))
                .build()

            val token = verifier.verify(idToken)
            if (token != null) {
                // ID token is valid, get Google account information
                val payload = token.payload
                val accessToken = payload["access_token"] as? String

                if (accessToken != null) {
                    // Construct the Sheets service object with the access token
                    val sheetsService = Sheets.Builder(httpTransport)
                        .setApplicationName("QRCheckin")
                        .setHttpRequestInitializer { request ->
                            // Set the ID token in the Authorization header
                            request.headers.authorization = "Bearer $accessToken"
                        }
                        .build()

                    // Sample request to access the Google Sheet
                    // You can customize this request based on your specific needs
                    val spreadsheetId = R.string.Sheet_ID
                    val range = "Sheet1!A1:B2"
                    val response = sheetsService.spreadsheets().values()
                        .get(spreadsheetId.toString(), range)
                        .execute()
                }
            }

            // If the request is successful, the account has access to the Google Sheet
            // Proceed with further actions here
        } catch (e: GoogleJsonResponseException) {
            if (e.statusCode == HttpStatusCodes.STATUS_CODE_UNAUTHORIZED) {
                // User does not have permission to access the Google Sheet
                // Prompt the user to sign in with a different account
                Toast.makeText(this, "This account is not authorized to access this data", Toast.LENGTH_LONG).show()
                promptSignInAgain()
            } else {
                // Other errors
                Toast.makeText(this, "AAAAAAAAAAAAAAAAAAAAAAAAAAAHHHHHHHHHHHHHHHHHHHHHHHH", Toast.LENGTH_LONG).show()
                promptSignInAgain()
            }
        } catch (e: Exception) {
            // Other exceptions, handle as needed
            promptSignInAgain()
        }
    }

    private fun promptSignInAgain() {
        // Sign out the current user
        mGoogleSignInClient.signOut().addOnCompleteListener {
            // Launch Google Sign-In UI to prompt user to sign in again
            val signInIntent = mGoogleSignInClient.signInIntent
            signInLauncher.launch(signInIntent)
        }
    }


    private val  requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                showCamera()
            } else {
                //Explain why you need permission
            }
        }

    private val scanLauncher =
        registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
            run {
                if (result.contents == null) {
                    Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show()
                } else {
                    setResult(result.contents)
                }
            }

        }

    private fun initViews() {
        binding.fab.setOnClickListener {
            checkPermissionCamera(this)
        }
    }

    private fun initBinding() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    private fun checkPermissionCamera(context: Context) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            showCamera()
        }
        else if (shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)) {
            Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
        else {
            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun showCamera() {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setPrompt("Scan QR code")
        options.setCameraId(0)
        options.setBeepEnabled(false)
        options.setBarcodeImageEnabled(true)
        options.setOrientationLocked(false)

        scanLauncher.launch(options)
    }

    //To do a search via key
    //val rowJSONObject = SearchSheet.searchInSheet(this, QRCode_url_key)
}