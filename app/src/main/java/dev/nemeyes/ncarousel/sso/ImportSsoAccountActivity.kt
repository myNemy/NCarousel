package dev.nemeyes.ncarousel.sso

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.Constants
import com.nextcloud.android.sso.exceptions.AccountImportCancelledException
import com.nextcloud.android.sso.exceptions.AndroidGetAccountsPermissionNotGranted
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppNotInstalledException
import com.nextcloud.android.sso.exceptions.SSOException

/**
 * Transparent trampoline that drives [AccountImporter]'s multi-step SSO flow and returns a
 * [com.nextcloud.android.sso.model.SingleSignOnAccount] to [ImportSsoAccount].
 */
class ImportSsoAccountActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        try {
            AccountImporter.pickNewAccount(this)
        } catch (_: AndroidGetAccountsPermissionNotGranted) {
            AccountImporter.requestAndroidAccountPermissionsAndPickAccount(this)
        } catch (e: NextcloudFilesAppNotInstalledException) {
            showErrorAndFinish(e)
        } catch (e: SSOException) {
            showErrorAndFinish(e)
        } catch (t: Throwable) {
            Log.e(TAG, t.message, t)
            finishCancelled()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        try {
            AccountImporter.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults,
                this,
            )
        } catch (t: Throwable) {
            Log.e(TAG, t.message, t)
            finishCancelled()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            try {
                AccountImporter.onActivityResult(
                    requestCode,
                    resultCode,
                    data,
                    this,
                ) { ssoAccount ->
                    val result = Intent().putExtra(Constants.NEXTCLOUD_SSO, ssoAccount)
                    setResult(RESULT_OK, result)
                    finish()
                }
            } catch (_: AccountImportCancelledException) {
                finishCancelled()
            } catch (t: Throwable) {
                Log.e(TAG, t.message, t)
                finishCancelled()
            }
            return
        }

        when (requestCode) {
            AccountImporter.CHOOSE_ACCOUNT_SSO,
            AccountImporter.REQUEST_GET_ACCOUNTS_PERMISSION,
            -> finishCancelled()
            AccountImporter.REQUEST_AUTH_TOKEN_SSO -> {
                try {
                    AccountImporter.handleFailedAuthRequest(this, data)
                } catch (e: SSOException) {
                    showErrorAndFinish(e)
                } catch (t: Throwable) {
                    Log.e(TAG, t.message, t)
                    finishCancelled()
                }
            }
            else -> finishCancelled()
        }
    }

    private fun showErrorAndFinish(exception: SSOException) {
        MaterialAlertDialogBuilder(this)
            .setMessage(exception.message)
            .setPositiveButton(android.R.string.ok, null)
            .setOnDismissListener { finishCancelled() }
            .show()
    }

    private fun finishCancelled() {
        setResult(RESULT_CANCELED)
        finish()
    }

    companion object {
        private const val TAG = "ImportSsoAccountActivity"
    }
}
