package io.aicactus.sdk.sample;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.aicactus.sdk.AicactusSDK;
import io.github.inflationx.viewpump.ViewPumpContextWrapper;

public class MainActivity extends Activity {
    @BindView(R.id.user_id)
    EditText userId;

    /** Returns true if the string is null, or empty (when trimmed). */
    public static boolean isNullOrEmpty(String text) {
        return TextUtils.isEmpty(text) || text.trim().length() == 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
    }

    @OnClick(R.id.action_track_a)
    void onButtonAClicked() {
        AicactusSDK.with(this).track("Button A Clicked");
    }

    @OnClick(R.id.action_track_b)
    void onButtonBClicked() {
        AicactusSDK.with(this).track("Button B Clicked");
    }

    @OnClick(R.id.action_identify)
    void onIdentifyButtonClicked() {
        String id = userId.getText().toString();
        if (isNullOrEmpty(id)) {
            Toast.makeText(this, R.string.id_required, Toast.LENGTH_LONG).show();
        } else {
            AicactusSDK.with(this).identify(id);
        }
    }

    @OnClick(R.id.action_flush)
    void onFlushButtonClicked() {
        AicactusSDK.with(this).flush();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_view_docs) {
            Intent intent =
                    new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/aicactus/aicactus-sdk-android-lib"));
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.no_browser_available, Toast.LENGTH_LONG).show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ViewPumpContextWrapper.wrap(newBase));
    }
}
