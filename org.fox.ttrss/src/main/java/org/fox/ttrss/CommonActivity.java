package org.fox.ttrss;


import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.customtabs.CustomTabsCallback;
import android.support.customtabs.CustomTabsClient;
import android.support.customtabs.CustomTabsIntent;
import android.support.customtabs.CustomTabsServiceConnection;
import android.support.customtabs.CustomTabsSession;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import org.fox.ttrss.util.DatabaseHelper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.util.Arrays;

public class CommonActivity extends ActionBarActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
	private final String TAG = this.getClass().getSimpleName();
	
	public final static String FRAG_HEADLINES = "headlines";
	public final static String FRAG_ARTICLE = "article";
	public final static String FRAG_FEEDS = "feeds";
	public final static String FRAG_CATS = "cats";

	public final static String THEME_DARK = "THEME_DARK";
	public final static String THEME_LIGHT = "THEME_LIGHT";
	//public final static String THEME_SEPIA = "THEME_SEPIA";
    //public final static String THEME_AMBER = "THEME_AMBER";
	public final static String THEME_DEFAULT = CommonActivity.THEME_LIGHT;

    public static final int EXCERPT_MAX_LENGTH = 256;
    public static final int EXCERPT_MAX_QUERY_LENGTH = 2048;

	public static final int PENDING_INTENT_CHROME_SHARE = 1;

	private DatabaseHelper m_databaseHelper;

	//private SQLiteDatabase m_readableDb;
	//private SQLiteDatabase m_writableDb;

	private boolean m_smallScreenMode = true;
	private String m_theme;
	private boolean m_needRestart;

	protected CustomTabsClient m_customTabClient;
	protected CustomTabsServiceConnection m_customTabServiceConnection = new CustomTabsServiceConnection() {
		@Override
		public void onCustomTabsServiceConnected(ComponentName componentName, CustomTabsClient customTabsClient) {
			m_customTabClient = customTabsClient;

			m_customTabClient.warmup(0);
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			m_customTabClient = null;
		}
	};

	protected SharedPreferences m_prefs;

	protected void setSmallScreen(boolean smallScreen) {
		Log.d(TAG, "m_smallScreenMode=" + smallScreen);
		m_smallScreenMode = smallScreen;
	}

	public DatabaseHelper getDatabaseHelper() {
		return m_databaseHelper;
	}

	public SQLiteDatabase getDatabase() {
		return m_databaseHelper.getWritableDatabase();
	}

	public boolean getUnreadOnly() {
		return m_prefs.getBoolean("show_unread_only", true);
	}

    // not the same as isSmallScreen() which is mostly about layout being loaded
    public boolean isTablet() {
        return getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }

	public void setUnreadOnly(boolean unread) {
		SharedPreferences.Editor editor = m_prefs.edit();
		editor.putBoolean("show_unread_only", unread);
		editor.apply();
	}

	public void toast(int msgId) {
		Toast toast = Toast.makeText(CommonActivity.this, msgId, Toast.LENGTH_SHORT);
		toast.show();
	}

	public void toast(String msg) {
		Toast toast = Toast.makeText(CommonActivity.this, msg, Toast.LENGTH_SHORT);
		toast.show();
	}

	@Override
	public void onResume() {
		super.onResume();

		if (m_needRestart) {
			Log.d(TAG, "restart requested");
			
			finish();
			startActivity(getIntent());
		}
	}

	@Override
	public void onDestroy() {

		if (m_customTabServiceConnection != null) {
			unbindService(m_customTabServiceConnection);
		}

		super.onDestroy();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		m_databaseHelper = DatabaseHelper.getInstance(this);

		m_prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());

		m_prefs.registerOnSharedPreferenceChangeListener(this);

        if (savedInstanceState != null) {
			m_theme = savedInstanceState.getString("theme");
		} else {
			m_theme = m_prefs.getString("theme", CommonActivity.THEME_DEFAULT);
		}

		CustomTabsClient.bindCustomTabsService(this, "com.android.chrome", m_customTabServiceConnection);

		super.onCreate(savedInstanceState);
	}

	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);
		
		out.putString("theme", m_theme);
	}
	
	public boolean isSmallScreen() {
		return m_smallScreenMode;
	}

	@SuppressWarnings("deprecation")
	public boolean isPortrait() {
		Display display = getWindowManager().getDefaultDisplay(); 
		
	    int width = display.getWidth();
	    int height = display.getHeight();
		
	    return width < height;
	}

	@SuppressLint({ "NewApi", "ServiceCast" })
	@SuppressWarnings("deprecation")
	public void copyToClipboard(String str) {
		if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {				
			android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
			clipboard.setText(str);
		} else {
			android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
			clipboard.setText(str);
		}		

		Toast toast = Toast.makeText(this, R.string.text_copied_to_clipboard, Toast.LENGTH_SHORT);
		toast.show();
	}

	protected void setAppTheme(SharedPreferences prefs) {
		String theme = prefs.getString("theme", CommonActivity.THEME_DEFAULT);
		
		if (theme.equals(THEME_DARK)) {
            setTheme(R.style.DarkTheme);
		} else {
			setTheme(R.style.LightTheme);
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		Log.d(TAG, "onSharedPreferenceChanged:" + key);

		String[] filter = new String[] { "theme", "enable_cats", "headline_mode" };

		m_needRestart = Arrays.asList(filter).indexOf(key) != -1;
	}

	private CustomTabsSession getCustomTabSession() {
		return m_customTabClient.newSession(new CustomTabsCallback() {
			@Override
			public void onNavigationEvent(int navigationEvent, Bundle extras) {
				super.onNavigationEvent(navigationEvent, extras);
			}
		});
	}

	protected void preloadUriIfAllowed(Uri uri) {
		boolean enableCustomTabs = m_prefs.getBoolean("enable_custom_tabs", true);

		if (m_customTabClient != null && enableCustomTabs) {
			ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo info = cm.getActiveNetworkInfo();

			if (info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI) {
				CustomTabsSession session = getCustomTabSession();
				session.mayLaunchUrl(uri, null, null);

				//toast("Preloading: " + uri.toString());
			}
		}
	}

	private void openUriWithCustomTab(Uri uri) {
		if (m_customTabClient != null) {
			TypedValue tvBackground = new TypedValue();
			getTheme().resolveAttribute(R.attr.colorPrimary, tvBackground, true);

			CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder(getCustomTabSession());

			builder.setStartAnimations(this, R.anim.slide_in_right, R.anim.slide_out_left);
			builder.setExitAnimations(this, R.anim.slide_in_left, R.anim.slide_out_right);

			builder.setToolbarColor(tvBackground.data);

			Intent shareIntent = new Intent(Intent.ACTION_SEND);
			shareIntent.setType("text/plain");
			shareIntent.putExtra(Intent.EXTRA_TEXT, uri.toString());

			PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(),
					CommonActivity.PENDING_INTENT_CHROME_SHARE, shareIntent, PendingIntent.FLAG_UPDATE_CURRENT);

			builder.setActionButton(BitmapFactory.decodeResource(getResources(), R.drawable.ic_share),
					getString(R.string.share_article), pendingIntent);

			CustomTabsIntent intent = builder.build();

			intent.launchUrl(this, uri);
		}
	}

	// uses chrome custom tabs when available
	public void openUri(final Uri uri) {
		boolean enableCustomTabs = m_prefs.getBoolean("enable_custom_tabs", true);
		final boolean askEveryTime = m_prefs.getBoolean("custom_tabs_ask_always", true);

		if (enableCustomTabs && m_customTabClient != null) {

			if (askEveryTime) {

				View dialogView = View.inflate(this, R.layout.dialog_open_link_askcb, null);
				final CheckBox askEveryTimeCB = (CheckBox) dialogView.findViewById(R.id.open_link_ask_checkbox);

				AlertDialog.Builder builder = new AlertDialog.Builder(
						CommonActivity.this)
						.setView(dialogView)
						.setMessage(uri.toString())
						.setPositiveButton(R.string.quick_preview,
								new Dialog.OnClickListener() {
									public void onClick(DialogInterface dialog,
														int which) {

										if (!askEveryTimeCB.isChecked()) {
											SharedPreferences.Editor editor = m_prefs.edit();
											editor.putBoolean("custom_tabs_ask_always", false);
											editor.apply();
										}

										openUriWithCustomTab(uri);

									}
								})
						.setNegativeButton(R.string.open_with,
								new Dialog.OnClickListener() {
									public void onClick(DialogInterface dialog,
														int which) {

										if (!askEveryTimeCB.isChecked()) {
											SharedPreferences.Editor editor = m_prefs.edit();
											editor.putBoolean("custom_tabs_ask_always", false);
											editor.putBoolean("enable_custom_tabs", false);
											editor.apply();
										}

										Intent intent = new Intent(Intent.ACTION_VIEW, uri);

										startActivity(intent);

									}
								});
						/*.setNegativeButton(R.string.cancel,
							new Dialog.OnClickListener() {
								public void onClick(DialogInterface dialog,
													int which) {

									if (!askEveryTimeCB.isChecked()) {
										SharedPreferences.Editor editor = m_prefs.edit();
										editor.putBoolean("custom_tabs_ask_always", false);
										editor.apply();
									}

								}
							});*/

				AlertDialog dlg = builder.create();
				dlg.show();

			} else {
				openUriWithCustomTab(uri);
			}

		} else {
			Intent intent = new Intent(Intent.ACTION_VIEW, uri);

			startActivity(intent);
		}
	}

	public void displayImageCaption(String url, String htmlContent) {
		// Android doesn't give us an easy way to access title tags;
		// we'll use Jsoup on the body text to grab the title text
		// from the first image tag with this url. This will show
		// the wrong text if an image is used multiple times.
		Document doc = Jsoup.parse(htmlContent);
		Elements es = doc.getElementsByAttributeValue("src", url);
		if (es.size() > 0) {
			if (es.get(0).hasAttr("title")) {

				AlertDialog.Builder builder = new AlertDialog.Builder(this)
					.setCancelable(true)
					.setMessage(es.get(0).attr("title"))
					.setPositiveButton(R.string.dialog_close, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								dialog.cancel();
								}
							}
					);

				AlertDialog dialog = builder.create();
				dialog.show();

			} else {
				toast(R.string.no_caption_to_display);
			}
		} else {
			toast(R.string.no_caption_to_display);
		}
	}

}

