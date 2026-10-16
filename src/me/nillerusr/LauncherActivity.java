package me.nillerusr;

import com.magd.source.engine.R;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Date;
import java.text.DateFormat;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Environment;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.app.Dialog;
import android.content.DialogInterface;
import java.lang.Thread;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.util.Linkify;
import android.util.Log;
import android.widget.LinearLayout.LayoutParams;
import android.view.*;
import android.widget.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;

import me.nillerusr.UpdateService;
import me.nillerusr.UpdateSystem;
import me.nillerusr.ExtractAssets;
import me.nillerusr.DirchActivity;

import org.libsdl.app.SDLActivity;

public class LauncherActivity extends Activity {
	public static String PKG_NAME;

	public static boolean can_write = true;
	static EditText cmdArgs, GamePath = null, EnvEdit, res_width, res_height;
	private Button launchButton;
	private boolean launchInProgress = false;
	private LinearLayout libraryItems;
	private TextView librarySummary;
	private boolean scanInProgress = false;
	public SharedPreferences mPref;
	public static final int sdk = Integer.valueOf(Build.VERSION.SDK).intValue();
	static CheckBox useVolumeButtons, check_updates;

	final static int REQUEST_PERMISSIONS = 42;
	private static final int REQUEST_STORAGE_PICKER = 604;

	public void applyPermissions( final String permissions[], final int code ) {
		if (Build.VERSION.SDK_INT < 23) return;
		List<String> requestPermissions = new ArrayList<String>();
		for( int i = 0; i < permissions.length; i++ ) {
			if( checkSelfPermission(permissions[i]) != PackageManager.PERMISSION_GRANTED )
				requestPermissions.add(permissions[i]);
		}

		if( !requestPermissions.isEmpty() ) {
			String[] requestPermissionsArray = new String[requestPermissions.size()];
			for( int i = 0; i < requestPermissions.size(); i++ )
				requestPermissionsArray[i] = requestPermissions.get(i);
			requestPermissions(requestPermissionsArray, code);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode != REQUEST_STORAGE_PICKER || resultCode != RESULT_OK || data == null) return;
		String selectedPath = data.getStringExtra("selected_path");
		String selectedGameDir = data.getStringExtra("selected_gamedir");
		if (selectedPath == null || selectedPath.trim().length() == 0) {
			showStartupError(getString(R.string.srceng_storage_unavailable));
			return;
		}
		File selected = new File(selectedPath);
		if (!selected.isDirectory() || !selected.canRead()) {
			showStartupError(getString(R.string.srceng_storage_unavailable));
			return;
		}
		String normalized = canonicalPath(selectedPath);
		if (GamePath != null) GamePath.setText(normalized);
		SharedPreferences.Editor editor = mPref.edit().putString("gamepath", normalized);
		if (selectedGameDir != null && selectedGameDir.trim().length() > 0) editor.putString("gamedir", selectedGameDir);
		editor.apply();
		scanLibrary(true, true);
	}

	@Override
	public void onRequestPermissionsResult( int requestCode, String[] permissions, int[] grantResults ) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == REQUEST_PERMISSIONS) {
			boolean granted = grantResults != null && grantResults.length > 0;
			if (granted) for (int result : grantResults) if (result != PackageManager.PERMISSION_GRANTED) { granted = false; break; }
			if (!granted) { Toast.makeText(this, R.string.srceng_launcher_error_no_permission, Toast.LENGTH_LONG).show(); launchInProgress = false; if (launchButton != null) launchButton.setEnabled(true); }
		}
	}

	public static String getDefaultDir() {
		File dir = Environment.getExternalStorageDirectory();
		if (dir == null || !dir.exists())
			return "/sdcard/";
		return dir.getPath();
	}

	public static String getAndroidDataDir() {
		String path = getDefaultDir() + "/Android/data/" + PKG_NAME + "/files";
		File directory = new File(path);
		if (!directory.exists())
			directory.mkdirs();
		return path;
	}

	public static void changeButtonsStyle( ViewGroup parent )
	{
		if( sdk >= 21 )
			return;

		for( int i = parent.getChildCount() - 1; i >= 0; i-- )
		{
			try
			{
				final View child = parent.getChildAt(i);

				if( child == null )
					continue;

				if( child instanceof ViewGroup )
				{
					changeButtonsStyle((ViewGroup) child);
					// DO SOMETHING WITH VIEWGROUP, AFTER CHILDREN HAS BEEN LOOPED
				}
				else if( child instanceof Button )
				{
					final Button b = (Button)child;
					final Drawable bg = b.getBackground();
					if(bg!= null)bg.setAlpha( 96 );
					b.setTextColor( 0xFFFFFFFF );
					b.setTextSize( 15f );
					//b.setText(b.getText().toString().toUpperCase());
					b.setTypeface( b.getTypeface(),Typeface.BOLD );
				}
				else if( child instanceof EditText )
				{
					final EditText b = ( EditText )child;
					b.setBackgroundColor( 0xFF272727 );
					b.setTextColor( 0xFFFFFFFF );
					b.setTextSize( 15f );
				}
			}
			catch( Exception e )
			{
			}
		}
	}



	private static final String LIBRARY_ROOTS = "library_roots";
	private static final String LIBRARY_HIDDEN = "library_hidden_paths";

	private void setupGameLibrary(LinearLayout body) {
		LinearLayout section = new LinearLayout(this);
		section.setOrientation(LinearLayout.VERTICAL);
		section.setPadding(10, 10, 10, 12);
		android.graphics.drawable.GradientDrawable panel = new android.graphics.drawable.GradientDrawable();
		panel.setColor(0xFF292F36);
		panel.setCornerRadius(16 * getResources().getDisplayMetrics().density);
		panel.setStroke(1, 0x664BD8D0);
		section.setBackground(panel);
		TextView title = new TextView(this);
		title.setText(R.string.srceng_library_title);
		title.setTextColor(0xFFFFFFFF);
		title.setTextSize(21);
		title.setTypeface(null, android.graphics.Typeface.BOLD);
		section.addView(title, new LinearLayout.LayoutParams(-1, -2));
		librarySummary = new TextView(this);
		librarySummary.setTextColor(0xFFCFD8E3);
		librarySummary.setTextSize(13);
		librarySummary.setPadding(0, 4, 0, 8);
		section.addView(librarySummary, new LinearLayout.LayoutParams(-1, -2));
		LinearLayout actions = new LinearLayout(this);
		actions.setOrientation(LinearLayout.HORIZONTAL);
		Button scanCurrent = new Button(this);
		scanCurrent.setText(R.string.srceng_library_scan_current);
		scanCurrent.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View view) { scanLibrary(true, true); }
		});
		Button rescan = new Button(this);
		rescan.setText(R.string.srceng_library_rescan);
		rescan.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View view) { scanLibrary(false, false); }
		});
		actions.addView(scanCurrent, new LinearLayout.LayoutParams(0, -2, 1));
		actions.addView(rescan, new LinearLayout.LayoutParams(0, -2, 1));
		section.addView(actions, new LinearLayout.LayoutParams(-1, -2));
		libraryItems = new LinearLayout(this);
		libraryItems.setOrientation(LinearLayout.VERTICAL);
		section.addView(libraryItems, new LinearLayout.LayoutParams(-1, -2));
		body.addView(section, 0, new LinearLayout.LayoutParams(-1, -2));
		renderGameLibrary();
	}

	private Set<String> preferenceSet(String key) {
		Set<String> stored = mPref.getStringSet(key, new HashSet<String>());
		return stored == null ? new HashSet<String>() : new HashSet<String>(stored);
	}

	private String canonicalPath(String path) {
		try { return new File(path).getCanonicalPath(); }
		catch (IOException e) { return new File(path).getAbsolutePath(); }
	}

	private void scanLibrary(boolean includeCurrentRoot, boolean unhideCurrentRoot) {
		final String selectedPath = GamePath == null ? "" : GamePath.getText().toString().trim();
		if (includeCurrentRoot && (selectedPath.length() == 0
				|| !new File(selectedPath).isDirectory() || !new File(selectedPath).canRead())) {
			new AlertDialog.Builder(this).setTitle(R.string.srceng_launcher_error)
					.setMessage(R.string.srceng_library_scan_invalid)
					.setPositiveButton(R.string.srceng_launcher_ok, null).show();
			return;
		}
		if (scanInProgress) return;
		scanInProgress = true;
		librarySummary.setText(R.string.srceng_library_scanning);
		final Set<String> roots = preferenceSet(LIBRARY_ROOTS);
		final Set<String> hidden = preferenceSet(LIBRARY_HIDDEN);
		if (includeCurrentRoot) {
			final String currentRoot = canonicalPath(selectedPath);
			roots.add(currentRoot);
			if (unhideCurrentRoot) {
				java.util.Iterator<String> iterator = hidden.iterator();
				while (iterator.hasNext()) {
					String hiddenPath = iterator.next();
					if (hiddenPath.equals(currentRoot) || hiddenPath.startsWith(currentRoot + File.separator)) iterator.remove();
				}
			}
		}
		new Thread(new Runnable() {
			@Override public void run() {
				final Set<String> discovered = new HashSet<String>();
				final Set<String> validRoots = new HashSet<String>();
				for (String root : roots) {
					File directory = new File(root);
					if (!directory.isDirectory() || !directory.canRead()) continue;
					validRoots.add(root);
					List<GameDiscovery.GameEntry> entries = GameDiscovery.scan(LauncherActivity.this, root);
					for (GameDiscovery.GameEntry entry : entries) {
						if (entry != null && !hidden.contains(entry.path) && new File(entry.path).isDirectory()) discovered.add(entry.path);
					}
				}
				runOnUiThread(new Runnable() {
					@Override public void run() {
						scanInProgress = false;
						mPref.edit().putStringSet(LIBRARY_ROOTS, new HashSet<String>(validRoots))
								.putStringSet(LIBRARY_HIDDEN, new HashSet<String>(hidden)).apply();
						GameDiscovery.savePaths(LauncherActivity.this, discovered);
						renderGameLibrary();
						if (discovered.isEmpty()) librarySummary.setText(R.string.srceng_library_no_candidates);
						else librarySummary.setText(getString(R.string.srceng_library_scan_done, discovered.size()));
					}
				});
			}
		}, "SourceGameScan").start();
	}

	private void renderGameLibrary() {
		if (libraryItems == null || mPref == null) return;
		libraryItems.removeAllViews();
		Set<String> paths = GameDiscovery.getSavedPaths(this);
		Set<String> livePaths = new HashSet<String>();
		List<GameDiscovery.GameEntry> entries = new ArrayList<GameDiscovery.GameEntry>();
		for (String path : paths) {
			GameDiscovery.GameEntry entry = GameDiscovery.inspectPath(this, path);
			if (entry != null && new File(path).isDirectory()) {
				livePaths.add(path);
				entries.add(entry);
			}
		}
		if (!livePaths.equals(paths)) GameDiscovery.savePaths(this, livePaths);
		java.util.Collections.sort(entries, new java.util.Comparator<GameDiscovery.GameEntry>() {
			@Override public int compare(GameDiscovery.GameEntry left, GameDiscovery.GameEntry right) {
				if (left.launchable != right.launchable) return left.launchable ? -1 : 1;
				return left.displayName.compareToIgnoreCase(right.displayName);
			}
		});
		if (entries.isEmpty()) {
			TextView empty = new TextView(this);
			empty.setText(R.string.srceng_library_empty);
			empty.setTextColor(0xFFD0D7E0);
			empty.setTextSize(14);
			empty.setPadding(0, 12, 0, 12);
			libraryItems.addView(empty, new LinearLayout.LayoutParams(-1, -2));
			librarySummary.setText(R.string.srceng_library_empty);
			return;
		}
		int ready = 0;
		for (final GameDiscovery.GameEntry entry : entries) {
			if (entry.launchable) ready++;
			LinearLayout card = new LinearLayout(this);
			card.setOrientation(LinearLayout.VERTICAL);
			card.setPadding(10, 8, 10, 8);
			card.setBackgroundColor(0xFF202830);
			LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
			cardParams.topMargin = 7;
			card.setLayoutParams(cardParams);
			TextView name = new TextView(this);
			name.setText("\uD83C\uDFAE  " + entry.displayName);
			name.setTextColor(0xFFFFFFFF);
			name.setTextSize(17);
			name.setTypeface(null, android.graphics.Typeface.BOLD);
			card.addView(name, new LinearLayout.LayoutParams(-1, -2));
			TextView status = new TextView(this);
			status.setText(entry.status + " — " + entry.detail);
			status.setTextColor(entry.launchable ? 0xFF7BE0B5 : 0xFFFFC27A);
			status.setTextSize(12);
			status.setPadding(0, 3, 0, 3);
			card.addView(status, new LinearLayout.LayoutParams(-1, -2));
			TextView path = new TextView(this);
			path.setText(getString(R.string.srceng_library_path, entry.path));
			path.setTextColor(0xFFB8C4D1);
			path.setTextSize(11);
			card.addView(path, new LinearLayout.LayoutParams(-1, -2));
			TextView optional = new TextView(this);
			optional.setText(entry.optionalContentAvailable
					? R.string.srceng_library_optional_found : R.string.srceng_library_optional_none);
			optional.setTextColor(0xFFB8C4D1);
			optional.setTextSize(11);
			card.addView(optional, new LinearLayout.LayoutParams(-1, -2));
			TextView last = new TextView(this);
			if (entry.lastLaunched > 0) {
				last.setText(getString(R.string.srceng_library_last,
						DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(entry.lastLaunched))));
			} else last.setText(R.string.srceng_library_last_never);
			last.setTextColor(0xFFB8C4D1);
			last.setTextSize(11);
			card.addView(last, new LinearLayout.LayoutParams(-1, -2));
			LinearLayout buttons = new LinearLayout(this);
			buttons.setOrientation(LinearLayout.HORIZONTAL);
			Button use = new Button(this);
			use.setText(R.string.srceng_library_use);
			use.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View view) {
					selectGame(entry);
					Toast.makeText(LauncherActivity.this,
							getString(R.string.srceng_library_selected, entry.displayName), Toast.LENGTH_SHORT).show();
				}
			});
			Button play = new Button(this);
			play.setText(R.string.srceng_library_play);
			play.setEnabled(entry.launchable);
			play.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View view) {
					if (!entry.launchable) { showStartupError(entry.detail); return; }
					selectGame(entry);
					startSource(view);
				}
			});
			Button remove = new Button(this);
			remove.setText(R.string.srceng_library_remove);
			remove.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View view) {
					Set<String> saved = GameDiscovery.getSavedPaths(LauncherActivity.this);
					saved.remove(entry.path);
					GameDiscovery.savePaths(LauncherActivity.this, saved);
					Set<String> hidden = preferenceSet(LIBRARY_HIDDEN);
					hidden.add(entry.path);
					mPref.edit().putStringSet(LIBRARY_HIDDEN, hidden).apply();
					renderGameLibrary();
					Toast.makeText(LauncherActivity.this, R.string.srceng_library_removed, Toast.LENGTH_SHORT).show();
				}
			});
			buttons.addView(use, new LinearLayout.LayoutParams(0, -2, 1));
			buttons.addView(play, new LinearLayout.LayoutParams(0, -2, 1));
			buttons.addView(remove, new LinearLayout.LayoutParams(0, -2, 1));
			card.addView(buttons, new LinearLayout.LayoutParams(-1, -2));
			libraryItems.addView(card);
		}
		librarySummary.setText(getString(R.string.srceng_library_count, ready, entries.size()));
	}

	private void selectGame(GameDiscovery.GameEntry entry) {
		if (entry == null) return;
		if (GamePath != null) GamePath.setText(entry.launchRoot);
		mPref.edit().putString("gamepath", entry.launchRoot)
				.putString("gamedir", entry.directoryName).apply();
		if (cmdArgs != null && EnvEdit != null) saveSettings(mPref.edit());
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		PKG_NAME = getApplication().getPackageName();
		requestWindowFeature(1);

		if (sdk >= 21)
			super.setTheme(0x01030224);
		else
			super.setTheme(0x01030005);

		mPref = getSharedPreferences("mod", 0);

		setContentView(R.layout.activity_launcher);

		LinearLayout body = (LinearLayout)findViewById(R.id.body);

		cmdArgs = (EditText)findViewById(R.id.edit_cmdline);
		EnvEdit = (EditText)findViewById(R.id.edit_env);
		GamePath = (EditText)findViewById(R.id.edit_gamepath);
		setupGameLibrary(body);

//		immersiveMode = (CheckBox)findViewById(R.id.checkbox_immersive_mode);

/*		useVolumeButtons = (CheckBox)findViewById(R.id.checkbox_use_volume);

		useVolumeButtons.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
				if( isChecked )
					Toast.makeText(LauncherActivity.this, R.string.srceng_launcher_volume_buttons_desc, 5000).show();
			}
		});*/

		launchButton = (Button)findViewById(R.id.button_launch);
		launchButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				LauncherActivity.this.startSource(v);
			}
		});

		Button aboutButton = (Button) findViewById(R.id.button_about);
		aboutButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Dialog dialog = new Dialog(LauncherActivity.this);
				dialog.setTitle(R.string.srceng_launcher_about);
				ScrollView scroll = new ScrollView(LauncherActivity.this);
				scroll.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
				scroll.setPadding(5,5,5,5);
				TextView text = new TextView(LauncherActivity.this);
				text.setText(R.string.srceng_launcher_about_text);
				text.setLinksClickable(true);
				text.setTextIsSelectable(true);
				Linkify.addLinks(text, Linkify.WEB_URLS|Linkify.EMAIL_ADDRESSES);
				scroll.addView(text);
				dialog.setContentView(scroll);
				dialog.show();
			}
		});

		Button dirButton = findViewById(R.id.button_gamedir);
		dirButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(LauncherActivity.this, DirchActivity.class);
				startActivityForResult(intent, REQUEST_STORAGE_PICKER);
			}
		});

/*		if (sdk >= 19) {
			immersiveMode.setChecked(true);
		}*/

//		check_updates = (CheckBox)findViewById(R.id.checkbox_check_updates);
		String last_commit = getResources().getString(R.string.last_commit);

		cmdArgs.setText(mPref.getString("argv", "-console"));
		GamePath.setText(mPref.getString("gamepath", getDefaultDir() + "/srceng"));
		EnvEdit.setText(mPref.getString("env", "LIBGL_USEVBO=0"));

//		useVolumeButtons.setChecked(mPref.getBoolean("use_volume_buttons", false));
//		check_updates.setChecked(mPref.getBoolean("check_updates", true));

		changeButtonsStyle((ViewGroup)this.getWindow().getDecorView());

		// permissions check
		if( sdk >= 23 )
			applyPermissions( new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO }, REQUEST_PERMISSIONS );
/*
		if( last_commit == null || last_commit.isEmpty() )
			check_updates.setVisibility(View.GONE);
		else if( check_updates.isChecked() ) {
			UpdateSystem update = new UpdateSystem(this);
			update.execute();
		}
*/
	}

	public void saveSettings(SharedPreferences.Editor editor)
	{
		String argv = cmdArgs.getText().toString();
		String gamepath = GamePath.getText().toString();
		String env = EnvEdit.getText().toString();

		editor.putString("argv", argv);
		editor.putString("gamepath", gamepath);
		editor.putString("env", env);
//		editor.putBoolean("use_volume_buttons", useVolumeButtons.isChecked());
//		editor.putBoolean("check_updates", check_updates.isChecked());
		editor.commit();
	}

	public void startSource(View view)
	{
		if (launchInProgress) return;
		String gamepath = GamePath == null ? "" : GamePath.getText().toString().trim();
		if (Build.VERSION.SDK_INT >= 21) {
			String savedTree = StorageAccess.getValidatedSelectedPath(this);
			String expectedTree = mPref.getString(StorageAccess.TREE_PATH_KEY, null);
			if (expectedTree != null && savedTree == null) {
				showStartupError(getString(R.string.srceng_storage_revoked));
				return;
			}
		}
		if (gamepath.length() == 0) { showStartupError(getString(R.string.srceng_launcher_error_empty_path)); return; }
		gamepath = canonicalPath(gamepath);
		if (GamePath != null) GamePath.setText(gamepath);
		File root = new File(gamepath);
		if (!root.isDirectory() || !root.canRead()) { showStartupError(getString(R.string.srceng_launcher_error_invalid_path, gamepath)); return; }
		String selectedDirectory = mPref.getString("gamedir", "hl2");
		GameDiscovery.GameEntry selectedEntry = GameDiscovery.inspectPath(this, new File(root, selectedDirectory).getPath());
		if (selectedEntry == null || !selectedEntry.launchable) {
			String reason = selectedEntry == null
					? getString(R.string.srceng_library_unrecognized)
					: selectedEntry.detail;
			showStartupError(reason + "\n\n" + getString(R.string.srceng_library_compatibility));
			return;
		}
		SharedPreferences.Editor editor = mPref.edit();
		saveSettings(editor);

//		boolean rodir = mPref.getBoolean("rodir", false);
//		boolean can_write = writeTest(gamepath);

		if (sdk >= 19)
			editor.putBoolean("immersive_mode", true /*immersiveMode.isChecked()*/ );
		else
			editor.putBoolean("immersive_mode", false);

		if (!editor.commit()) Log.w("SRCAPK", "Preferences commit failed; forwarding current values to SDL.");
		Intent intent = new Intent(LauncherActivity.this, SDLActivity.class);
		intent.putExtra("gamepath", gamepath);
		intent.putExtra("argv", cmdArgs.getText().toString().trim());
		intent.putExtra("env", EnvEdit.getText().toString());
		intent.putExtra("gamedir", mPref.getString("gamedir", "hl2"));
		intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		try {
			GameDiscovery.recordLaunch(this, selectedEntry.path);
			launchInProgress = true;
			if (launchButton != null) launchButton.setEnabled(false);
			startActivity(intent);
		}
		catch (RuntimeException e) { launchInProgress = false; if (launchButton != null) launchButton.setEnabled(true); Log.e("SRCAPK", "Unable to start SDL activity", e); showStartupError(getString(R.string.srceng_launcher_error_start_failed, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())); }


/*		if (can_write || rodir) {
			if (can_write) {
				editor.putBoolean("rodir", false);
				editor.commit();
				rodir = false;
			}

			if (sdk >= 19)
				editor.putBoolean("immersive_mode", true ); //immersiveMode.isChecked() );
			else
				editor.putBoolean("immersive_mode", false);

			editor.commit();

			Intent intent = new Intent(this, SDLActivity.class);
			intent.addFlags(268435456);
			startActivity(intent);

			return;
		}

		new AlertDialog.Builder(this).setTitle("Warning").setMessage(
			this.getResources().getString(R.string.srceng_launcher_error_test_write) + getAndroidDataDir()
		).setPositiveButton(R.string.srceng_launcher_ok, (DialogInterface.OnClickListener) null).show();

		editor.putBoolean("rodir", true);
		editor.commit();
*/
	}

	private void showStartupError(String message) {
		if (isFinishing()) return;
		new AlertDialog.Builder(this).setTitle(R.string.srceng_launcher_error).setMessage(message).setPositiveButton(R.string.srceng_launcher_ok, null).show();
	}
	@Override protected void onResume() {
		super.onResume();
		launchInProgress = false;
		if (launchButton != null) launchButton.setEnabled(true);
		renderGameLibrary();
	}
	@Override protected void onPause() { Log.v("SRCAPK", "onPause"); if (mPref != null && cmdArgs != null && GamePath != null && EnvEdit != null) saveSettings(mPref.edit()); super.onPause(); }
}

