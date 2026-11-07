package dev.nemeyes.ncarousel.sso

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.nextcloud.android.sso.Constants
import com.nextcloud.android.sso.model.SingleSignOnAccount

/**
 * Activity Result contract for Nextcloud Files SSO import.
 *
 * Android-SingleSignOn 1.3.5+ ships an equivalent contract but requires compileSdk 37 / AGP 9.1;
 * we keep 1.3.4 and host a thin trampoline instead.
 */
class ImportSsoAccount : ActivityResultContract<Void?, SingleSignOnAccount?>() {
    override fun createIntent(context: Context, input: Void?): Intent =
        Intent(context, ImportSsoAccountActivity::class.java)

    override fun parseResult(resultCode: Int, intent: Intent?): SingleSignOnAccount? {
        if (resultCode != Activity.RESULT_OK || intent == null) return null
        @Suppress("DEPRECATION")
        return intent.getSerializableExtra(Constants.NEXTCLOUD_SSO) as? SingleSignOnAccount
    }
}
