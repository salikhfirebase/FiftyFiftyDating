package com.quickdating.fastmeet.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.database.DataSnapshot
import com.quickdating.fastmeet.*
import com.quickdating.fastmeet._core.BaseActivity
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.onesignal.OneSignal
import com.quickdating.fastmeet.service.mUserIdClient
import com.uxcam.UXCam
import kotlinx.android.synthetic.main.activity_web_v.*
import com.yandex.metrica.YandexMetrica
import com.yandex.metrica.YandexMetricaConfig
import org.apache.http.client.methods.HttpPost
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.collections.HashMap


/**
 * Created by Andriy Deputat email(andriy.deputat@gmail.com) on 3/13/19.
 */
class SplashActivity : BaseActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    private lateinit var dataSnapshot: DataSnapshot

    private lateinit var database: DatabaseReference

    lateinit var firebaseAnalytic: FirebaseAnalytics

    var userId = ""

    lateinit var prefs: SharedPreferences

    private var client = mUserIdClient().build()

    var sp: String? = null
    val REFERRER_DATA = "REFERRER_DATA"
    var gclid: String? = null


    override fun getContentView(): Int = R.layout.activity_web_v


    private fun generateId() = client.generateId()


    override fun initUI() {
        webView = web_view
        progressBar = progress_bar
        firebaseAnalytic = FirebaseAnalytics.getInstance(this)
        prefs = getSharedPreferences("com.quickdating.fastmeet", Context.MODE_PRIVATE)
        if (getPreferer(this) != "Didn't got any referrer follow instructions") {
            gclid = getPreferer(this)
        } else {
            gclid = null
        }
        OneSignal.startInit(this)
            .inFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification)
            .unsubscribeWhenNotificationsAreDisabled(true)
            .init()
        UXCam.startWithKey("4pjyp80yk3zep9q")
    }

    override fun setUI() {
        logEvent("splash-screen")
        val config = YandexMetricaConfig.newConfigBuilder("d88738e9-41f8-4731-ac77-bf679f508706").build()
        // Initializing the AppMetrica SDK.
        YandexMetrica.activate(applicationContext, config)
        // Automatic tracking of user activity.
        YandexMetrica.enableActivityAutoTracking(this.application)
        getGclid()
        webView.webViewClient = object : WebViewClient() {
            /**
             * Check if url contains key words:
             * /money - needed user (launch WebVActivity or show in browser)
             * /main - bot or unsuitable user (launch ContentActivity)
             */
            @SuppressLint("deprecated")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url.contains("/money")) {
                    // task url for web view or browser
                    var value = prefs.getString("show_in", "chrome_tabs")
                    val map = HashMap<String, Any>()
                    if (prefs.getBoolean("firstrun",true)) {

                        value = dataSnapshot.child(SHOW_IN).value as String
                        prefs.edit().putString("show_in", value).apply()
                        if (value == WEB_VIEW) {

                            val bundle = Bundle()
                            bundle.putString("web_view_first_open", "")

                            firebaseAnalytic.logEvent("web_view_first_open", bundle)

                            map["show_in"] = "chrome_tabs"
                            database.updateChildren(map)

                        } else if (value == CHROMETABS) {

                            val bundle = Bundle()
                            bundle.putString("chrome_tabs_first_open", "")

                            firebaseAnalytic.logEvent("chrome_tabs_first_open", bundle)

                            map["show_in"] = "web_view"
                            database.updateChildren(map)

                        }

                        generateId().enqueue(object: Callback<String> {
                            override fun onFailure(call: Call<String>?, t: Throwable?) {
                                Log.d("UserId", "jopa")
                            }

                            override fun onResponse(call: Call<String>?, response: Response<String>?) {
                                if (response?.body() != null) {
                                    val userIdBundle = Bundle()
                                    userIdBundle.putString("userId", response.body())

                                    firebaseAnalytic.logEvent("userId", userIdBundle)

                                    prefs.edit().putBoolean("firstrun", false).apply()
                                }
                            }

                        })
                    }

                    if (value == WEB_VIEW) {

                        val bundle = Bundle()
                        bundle.putString("web_view_all_open", "")
                        firebaseAnalytic.logEvent("web_view_all_open", bundle)

                        val taskUrl = dataSnapshot.child(WEB_URL).value as String
                        if ((gclid != null) && (gclid != "")) {
                            startActivity(
                                Intent(this@SplashActivity, WebVActivity::class.java)
                                    .putExtra(EXTRA_TASK_URL, "$taskUrl?external_id=$gclid")
                            )
                            finish()
                        } else {
                            startActivity(
                                Intent(this@SplashActivity, WebVActivity::class.java)
                                    .putExtra(EXTRA_TASK_URL, taskUrl)
                            )
                            finish()
                        }
                    } else if (value == CHROMETABS) {

                        val bundle = Bundle()
                        bundle.putString("chrome_tabs_all_open", "")
                        firebaseAnalytic.logEvent("chrome_tabs_all_open", bundle)

                        val taskUrl = dataSnapshot.child(CHROME_URL).value as String
                        startActivity(
                            Intent(this@SplashActivity, ChromeTabsAdtivity::class.java)
                                .putExtra(EXTRA_TASK_URL, taskUrl)
                        )
                        finish()

                    }
                } else if (url.contains("/main")) {
                    val taskUrl = dataSnapshot.child(TASK_URL).value as String
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java)
                        .putExtra(EXTRA_TASK_URL, taskUrl))
                    finish()
                }
                progressBar.visibility = View.GONE
                return false
            }
        }

        database = FirebaseDatabase.getInstance().reference

        progressBar.visibility = View.VISIBLE

        getValuesFromDatabase({
            dataSnapshot = it


            // load needed url to determine if user is suitable
            webView.loadUrl(it.child(SPLASH_URL).value as String)
        }, {
            Log.d("SplashErrActivity", "didn't work fetchremote")
            progressBar.visibility = View.GONE
        })
    }

    fun getPreferer(context: Context): String? {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        if (!sp.contains(REFERRER_DATA)) {
            return "Didn't got any referrer follow instructions"
        }
        return sp.getString(REFERRER_DATA, null)
    }

    fun getGclid(){
        if (gclid != null) {
            if (gclid!!.contains("gclid")) {
                gclid = gclid?.substringAfter("gclid=")
                gclid = gclid?.substringBefore("&conv")
            } else {
                gclid = null
            }
        }
    }
}