package io.aicactus.sample

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import io.aicactus.sdk.AicactusSDK
import io.aicactus.sdk.Properties
import io.aicactus.sdk.Traits

import io.github.inflationx.viewpump.ViewPumpContextWrapper

class MainActivity : Activity() {
    @BindView(R.id.user_id)
    var userId: EditText? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ButterKnife.bind(this)
    }

    @OnClick(R.id.action_track_a)
    fun onButtonAClicked() {
        AicactusSDK.with(this).track("Button A Clicked")
    }

    @OnClick(R.id.action_track_b)
    fun onButtonBClicked() {
        AicactusSDK.with(this).track("Button B Clicked", Properties().putTitle("B").putPrice(10.0))
    }

    @OnClick(R.id.action_identify)
    fun onIdentifyButtonClicked() {
        val id = userId!!.text.toString()
        if (isNullOrEmpty(id)) {
            Toast.makeText(this, R.string.id_required, Toast.LENGTH_LONG).show()
        } else {
            AicactusSDK.with(this).screen("")
            AicactusSDK.with(this).identify(
                id,
                Traits().putName("Jack London").putEmail("jack@aicactus.ai")
                    .putPhone("555-444-3333"),
                null
            )
        }
    }

    @OnClick(R.id.action_flush)
    fun onFlushButtonClicked() {
        AicactusSDK.with(this).flush()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.action_view_docs) {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://github.com/aicactus/aicactus-sdk-android-lib")
            )
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, R.string.no_browser_available, Toast.LENGTH_LONG).show()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ViewPumpContextWrapper.wrap(newBase))
    }

    companion object {
        /** Returns true if the string is null, or empty (when trimmed).  */
        fun isNullOrEmpty(text: String): Boolean {
            return TextUtils.isEmpty(text) || text.trim { it <= ' ' }.length == 0
        }
    }
}