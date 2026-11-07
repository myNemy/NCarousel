package dev.nemeyes.ncarousel.data

import android.content.Context
import dev.nemeyes.ncarousel.data.accounts.NextcloudAccount
import okhttp3.OkHttpClient

object NextcloudDavAccessFactory {

    fun create(
        context: Context,
        http: OkHttpClient,
        account: NextcloudAccount,
    ): NextcloudDavAccess {
        val ssoName = account.ssoAccountName?.takeIf { it.isNotBlank() }
        return if (ssoName != null) {
            SsoNextcloudDavAccess(context.applicationContext, ssoName, account.userId)
        } else {
            NextcloudWebDavClient(
                http,
                account.serverBaseUrl,
                account.userId,
                account.loginName,
                account.appPassword,
            )
        }
    }
}
