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
	private ProgressBar scanProgress;
	private ProgressBar launcherProgress;
	private LinearLayout advancedPanel;
	private Button advancedToggle;
	public SharedPreferences mPref;
	public static final int sdk = Integer.valueOf(Build.VERSION.SDK).intValue();
	static CheckBox useVolumeButtons, check_updates;

	final static int REQUEST_PERMISSIONS = 42;
	private static final int REQUEST_STORAGE_PICKER = 604;

	public void applyPermissions(final String[] permissions, final int code) {
		if (Build.VERSION.SDK_INT < 23 || permissions == null || permissions.length == 0) return;
		List<String> missing = new ArrayList<String>();
		for (String permission : permissions) {
			if (permission != null && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
				missing.add(permission);
			}
		}
		if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[missing.size()]), code);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode != REQUEST_STORAGE_PICKER) return;
		if (resultCode != RESULT_OK || data == null) {
			if (resultCode == RESULT_CANCELED) Toast.makeText(this, R.string.srceng_storage_selection_cancelled, Toast.LENGTH_SHORT).show();
			return;
		}
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
		if (normalized.length() == 0) {
			showStartupError(getString(R.string.srceng_storage_unavailable));
			return;
		}
		if (Build.VERSION.SDK_INT >= 21) {
			String grantedRoot = StorageAccess.getValidatedSelectedPath(this);
			if (grantedRoot == null || !LauncherLogic.isPathWithinRoot(grantedRoot, normalized)) {
				showStartupError(getString(R.string.srceng_storage_scope_mismatch));
				return;
			}
		}
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
			if (granted) {
				for (int result : grantResults) {
					if (result != PackageManager.PERMISSION_GRANTED) { granted = false; break; }
				}
			}
			if (!granted) {
				launchInProgress = false;
				if (launchButton != null) {
					launchButton.setEnabled(true);
					launchButton.setText(R.string.srceng_launcher_launch);
				}
				if (launcherProgress != null) launcherProgress.setVisibility(View.GONE);
				showStoragePermissionHelp();
			}
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
		if (!directory.isDirectory() && !directory.mkdirs()) {
			Log.e("SRCAPK", "Could not create the optional game data directory.");
			return "";
		}
		if (!directory.isDirectory() || !directory.canWrite()) {
			Log.e("SRCAPK", "The optional game data directory is not writable.");
			return "";
		}
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
			catch (Exception e) {
				Log.w("SRCAPK", "Unable to apply legacy launcher control styling.", e);
			}
		}
	}



	private static final String LIBRARY_ROOTS = "library_roots";
	private static final String LIBRARY_HIDDEN = "library_hidden_paths";

	private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
	private Button makeActionButton(int textResource) { Button b = new Button(this); b.setText(textResource); b.setMinHeight(dp(48)); b.setFocusable(true); return b; }

	private void setupGameLibrary(LinearLayout body) {
		LinearLayout section = new LinearLayout(this);
		section.setOrientation(LinearLayout.VERTICAL);
		section.setPadding(dp(14), dp(14), dp(14), dp(14));
		android.graphics.drawable.GradientDrawable panel = new android.graphics.drawable.GradientDrawable();
		panel.setColor(0xEE202B38);
		panel.setCornerRadius(dp(22));
		panel.setStroke(dp(1), 0xAA55D9D0);
		section.setBackground(panel);
		TextView title = new TextView(this);
		title.setText(R.string.srceng_library_title);
		title.setTextColor(0xFFFFFFFF);
		title.setTextSize(25);
		title.setTypeface(null, Typeface.BOLD);
		section.addView(title, new LinearLayout.LayoutParams(-1, -2));
		TextView intro = new TextView(this);
		intro.setText(R.string.srceng_library_intro);
		intro.setTextColor(0xFFD0DCE8);
		intro.setTextSize(14);
		intro.setPadding(0, dp(5), 0, dp(10));
		intro.setLineSpacing(dp(2), 1.0f);
		section.addView(intro, new LinearLayout.LayoutParams(-1, -2));
		librarySummary = new TextView(this);
		librarySummary.setTextColor(0xFFCFD8E3);
		librarySummary.setTextSize(13);
		librarySummary.setPadding(0, dp(4), 0, dp(8));
		if (Build.VERSION.SDK_INT >= 19) librarySummary.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
		section.addView(librarySummary, new LinearLayout.LayoutParams(-1, -2));
		scanProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
		scanProgress.setIndeterminate(true);
		scanProgress.setVisibility(View.GONE);
		scanProgress.setContentDescription(getString(R.string.srceng_library_scanning));
		section.addView(scanProgress, new LinearLayout.LayoutParams(-1, dp(3)));
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
		return LauncherLogic.normalizePath(path);
	}

	private void scanLibrary(boolean includeCurrentRoot, boolean unhideCurrentRoot) {
		final String selectedPath = GamePath == null ? "" : GamePath.getText().toString().trim();
		if (includeCurrentRoot && (selectedPath.length() == 0
				|| !new File(selectedPath).isDirectory() || !new File(selectedPath).canRead())) {
			styleGlassDialog(new AlertDialog.Builder(this).setTitle(R.string.srceng_launcher_error)
					.setMessage(R.string.srceng_library_scan_invalid)
					.setPositiveButton(R.string.srceng_launcher_ok, null).show());
			return;
		}
		if (scanInProgress) return;
		scanInProgress = true;
		librarySummary.setText(R.string.srceng_library_scanning);
		if (scanProgress != null) scanProgress.setVisibility(View.VISIBLE);
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
				final String[] scanFailure = new String[1];
				final boolean[] scanIncomplete = new boolean[1];
				// inaccessible roots/children, depth-limited roots, directory-limit hits
				final int[] incompleteCounts = new int[3];
				try {
					for (String root : roots) {
						GameDiscovery.ScanResult result = GameDiscovery.scanDetailed(LauncherActivity.this, root);
						if (!result.rootAccessible) {
							scanIncomplete[0] = true;
							incompleteCounts[0] += Math.max(1, result.inaccessibleDirectories);
							continue;
						}
						validRoots.add(root);
						if (!result.isComplete()) {
							scanIncomplete[0] = true;
							incompleteCounts[0] += result.inaccessibleDirectories;
							if (result.depthLimitReached) incompleteCounts[1]++;
							if (result.directoryLimitReached) incompleteCounts[2]++;
						}
						for (GameDiscovery.GameEntry entry : result.entries) {
							if (entry != null && !hidden.contains(entry.path) && new File(entry.path).isDirectory()) discovered.add(entry.path);
						}
					}
				} catch (RuntimeException error) {
					scanFailure[0] = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
				}
				runOnUiThread(new Runnable() {
					@Override public void run() {
						scanInProgress = false;
						if (scanProgress != null) scanProgress.setVisibility(View.GONE);
						boolean completed = scanFailure[0] == null && !scanIncomplete[0];
						// An incomplete traversal must not replace the last known-good library snapshot.
						if (completed) {
							mPref.edit().putStringSet(LIBRARY_ROOTS, new HashSet<String>(validRoots))
									.putStringSet(LIBRARY_HIDDEN, new HashSet<String>(hidden)).apply();
							GameDiscovery.savePaths(LauncherActivity.this, discovered);
						}
						renderGameLibrary();
						if (scanFailure[0] != null) {
							librarySummary.setText(getString(R.string.srceng_library_scan_failed, scanFailure[0]));
						} else if (scanIncomplete[0]) {
							librarySummary.setText(getString(R.string.srceng_library_scan_incomplete,
									incompleteCounts[0], incompleteCounts[1], incompleteCounts[2]));
						} else if (discovered.isEmpty()) {
							librarySummary.setText(R.string.srceng_library_no_candidates);
						} else {
							librarySummary.setText(getString(R.string.srceng_library_scan_done, discovered.size()));
						}
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
			applyGlassTheme();
			return;
		}
		int ready = 0;
		for (final GameDiscovery.GameEntry entry : entries) {
			if (entry.launchable) ready++;
			LinearLayout card = new LinearLayout(this);
			card.setOrientation(LinearLayout.VERTICAL);
			card.setTag("liquid_glass_card");
			card.setPadding(dp(12), dp(10), dp(12), dp(10));
			GradientDrawable cardBg = new GradientDrawable();
			cardBg.setColor(0xDD253441);
			cardBg.setCornerRadius(dp(17));
			cardBg.setStroke(dp(1), entry.launchable ? 0x8855D9B8 : 0x667C93A6);
			card.setBackground(cardBg);
			card.setFocusable(true);
			card.setClickable(true);
			card.setContentDescription(getString(R.string.srceng_library_open_details, entry.displayName));
			card.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View view) { showGameDetails(entry); } });
			LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
			cardParams.topMargin = 7;
			card.setLayoutParams(cardParams);
			TextView name = new TextView(this);
			name.setText("\uD83C\uDFAE  " + entry.displayName);
			name.setTextColor(0xFFFFFFFF);
			name.setTextSize(18);
			name.setMaxLines(2);
			name.setEllipsize(android.text.TextUtils.TruncateAt.END);
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
			path.setTextSize(12);
			path.setMaxLines(2);
			path.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
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
			Button use = makeActionButton(R.string.srceng_library_use);
			use.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View view) {
					selectGame(entry);
					Toast.makeText(LauncherActivity.this,
							getString(R.string.srceng_library_selected, entry.displayName), Toast.LENGTH_SHORT).show();
				}
			});
			Button play = makeActionButton(R.string.srceng_library_play);
			play.setEnabled(entry.launchable);
			play.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View view) {
					if (!entry.launchable) { showStartupError(entry.detail); return; }
					selectGame(entry);
					startSource(view);
				}
			});
			Button remove = makeActionButton(R.string.srceng_library_remove);
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
			Button details = makeActionButton(R.string.srceng_library_details_action);
			details.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View view) { showGameDetails(entry); } });
			card.addView(details, new LinearLayout.LayoutParams(-1, -2));
			card.addView(buttons, new LinearLayout.LayoutParams(-1, -2));
			libraryItems.addView(card);
		}
		librarySummary.setText(getString(R.string.srceng_library_count, ready, entries.size()));
		applyGlassTheme();
	}

	private void showGameDetails(final GameDiscovery.GameEntry entry) {
		if (entry == null || isFinishing()) return;
		ScrollView scroll = new ScrollView(this);
		LinearLayout content = new LinearLayout(this);
		content.setOrientation(LinearLayout.VERTICAL);
		content.setPadding(dp(18), dp(8), dp(18), dp(8));
		TextView details = new TextView(this);
		details.setText(getString(R.string.srceng_library_details, entry.displayName, entry.status, entry.detail, entry.path, entry.launchRoot, entry.directoryName, entry.optionalContentAvailable ? getString(R.string.srceng_library_optional_found) : getString(R.string.srceng_library_optional_none), entry.lastLaunched > 0 ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(entry.lastLaunched)) : getString(R.string.srceng_library_last_never)));
		details.setTextColor(0xFFE5EDF5); details.setTextSize(15); details.setLineSpacing(dp(3), 1.0f); details.setTextIsSelectable(true);
		content.addView(details, new LinearLayout.LayoutParams(-1, -2));
		TextView caveat = new TextView(this); caveat.setText(R.string.srceng_library_compatibility); caveat.setTextColor(0xFFFFC98B); caveat.setPadding(0, dp(12), 0, dp(4)); content.addView(caveat, new LinearLayout.LayoutParams(-1, -2));
		scroll.addView(content);
		AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(R.string.srceng_library_details_title).setView(scroll).setNegativeButton(R.string.srceng_launcher_ok, null);
		if (entry.launchable) builder.setPositiveButton(R.string.srceng_library_play, new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int which) { selectGame(entry); startSource(null); } });
		else builder.setPositiveButton(R.string.srceng_library_use, new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int which) { selectGame(entry); Toast.makeText(LauncherActivity.this, getString(R.string.srceng_library_selected, entry.displayName), Toast.LENGTH_SHORT).show(); } });
		styleGlassDialog(builder.show());
	}

	private void styleGlassDialog(AlertDialog dialog) {
		if (dialog == null) return;
		if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(
				LiquidGlassTheme.panel(0xEE152737, 0xAA5DE6DF, 22));
			Button positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
			Button negative = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
			if (positive != null) { positive.setBackgroundDrawable(LiquidGlassTheme.panel(0xD52A5965, 0xAA8CF5EB, 14)); positive.setTextColor(0xFFF2F7FC); }
			if (negative != null) { negative.setBackgroundDrawable(LiquidGlassTheme.panel(0xB9274051, 0x775DE6DF, 14)); negative.setTextColor(0xFFF2F7FC); }
	}

	private void applyGlassTheme() {
		LiquidGlassTheme.apply(this, findViewById(android.R.id.content),
				mPref != null && mPref.getBoolean("liquid_motion", true));
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
		LauncherPreferences.migrate(mPref, getDefaultDir() + "/srceng");

		setContentView(R.layout.activity_launcher);

		LinearLayout body = (LinearLayout)findViewById(R.id.body);

		cmdArgs = (EditText)findViewById(R.id.edit_cmdline);
		EnvEdit = (EditText)findViewById(R.id.edit_env);
		GamePath = (EditText)findViewById(R.id.edit_gamepath);
		advancedPanel = (LinearLayout)findViewById(R.id.advanced_panel);
		advancedToggle = (Button)findViewById(R.id.button_advanced);
		setupGameLibrary(body);
		if (advancedPanel != null) {
			CheckBox motionToggle = new CheckBox(this);
			motionToggle.setText(R.string.srceng_liquid_motion);
			motionToggle.setTextColor(0xFFF2F7FC);
			motionToggle.setMinHeight(dp(48));
			motionToggle.setChecked(mPref.getBoolean("liquid_motion", true));
			motionToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				@Override public void onCheckedChanged(CompoundButton button, boolean checked) {
					mPref.edit().putBoolean("liquid_motion", checked).apply();
					applyGlassTheme();
				}
			});
			advancedPanel.addView(motionToggle, new LinearLayout.LayoutParams(-1, -2));

			Button diagnosticsButton = new Button(this);
			diagnosticsButton.setText(R.string.srceng_compatibility_diagnostics);
			diagnosticsButton.setContentDescription(getString(R.string.srceng_compatibility_diagnostics));
			diagnosticsButton.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View view) {
					showCompatibilityDiagnostics();
				}
			});
			advancedPanel.addView(diagnosticsButton, new LinearLayout.LayoutParams(-1, -2));
 
			Button onlineStatusButton = new Button(this);
			onlineStatusButton.setText(R.string.srceng_online_features);
			onlineStatusButton.setContentDescription(getString(R.string.srceng_online_features));
			onlineStatusButton.setMinHeight(dp(48));
			onlineStatusButton.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View view) {
					showOnlineFeaturesStatus();
				}
			});
			advancedPanel.addView(onlineStatusButton, new LinearLayout.LayoutParams(-1, -2));
			advancedPanel.setVisibility(View.GONE);
		}
		if (advancedToggle != null) advancedToggle.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View view) { if (advancedPanel == null) return; boolean show = advancedPanel.getVisibility() != View.VISIBLE; advancedPanel.setVisibility(show ? View.VISIBLE : View.GONE); advancedToggle.setText(show ? R.string.srceng_library_advanced_hide : R.string.srceng_library_advanced); } });



		launchButton = (Button)findViewById(R.id.button_launch);
		launcherProgress = (ProgressBar)findViewById(R.id.launcher_progress);
		if (launcherProgress != null) launcherProgress.setVisibility(View.GONE);
		launchButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				LauncherActivity.this.startSource(v);
			}
		});

		Button dirButton = (Button)findViewById(R.id.button_gamedir);
		dirButton.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View view) {
				Intent intent = new Intent(LauncherActivity.this, DirchActivity.class);
				startActivityForResult(intent, REQUEST_STORAGE_PICKER);
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
				if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(
						LiquidGlassTheme.panel(0xEE152737, 0xAA5DE6DF, dp(22)));
			}
		});

		




		cmdArgs.setText(mPref.getString("argv", "-console"));
		GamePath.setText(mPref.getString("gamepath", getDefaultDir() + "/srceng"));
		EnvEdit.setText(mPref.getString("env", "LIBGL_USEVBO=0"));



		changeButtonsStyle((ViewGroup)this.getWindow().getDecorView());
		applyGlassTheme();

		// permissions check
		if( sdk >= 23 )
			applyPermissions( new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQUEST_PERMISSIONS );

	}

	public void saveSettings(SharedPreferences.Editor editor)
	{
		String argv = cmdArgs.getText().toString();
		String gamepath = GamePath.getText().toString();
		String env = EnvEdit.getText().toString();

		editor.putString("argv", argv);
		editor.putString("gamepath", gamepath);
		editor.putString("env", env);

		editor.commit();
	}

	public void startSource(View view)
	{
		if (launchInProgress) return;
		String gamepath = GamePath == null ? "" : GamePath.getText().toString().trim();
		if (gamepath.length() == 0) { showStartupError(getString(R.string.srceng_launcher_error_empty_path)); return; }
		gamepath = canonicalPath(gamepath);
		if (gamepath.length() == 0) {
			showStartupError(getString(R.string.srceng_launcher_error_empty_path));
			return;
		}
		if (Build.VERSION.SDK_INT >= 21) {
			String expectedTree = mPref.getString(StorageAccess.TREE_PATH_KEY, null);
			if (expectedTree != null) {
				String grantedRoot = StorageAccess.getValidatedSelectedPath(this);
				if (grantedRoot == null) {
					showStartupError(getString(R.string.srceng_storage_revoked));
					return;
				}
				if (!LauncherLogic.isPathWithinRoot(grantedRoot, gamepath)) {
					showStartupError(getString(R.string.srceng_storage_scope_mismatch));
					return;
				}
			}
		}
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

		if (sdk >= 19)
			editor.putBoolean("immersive_mode", true);
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
			if (launchButton != null) { launchButton.setEnabled(false); launchButton.setText(R.string.srceng_library_launching); }
			if (launcherProgress != null) launcherProgress.setVisibility(View.VISIBLE);
			startActivity(intent);
		}
		catch (RuntimeException e) { launchInProgress = false; if (launchButton != null) { launchButton.setEnabled(true); launchButton.setText(R.string.srceng_launcher_launch); } if (launcherProgress != null) launcherProgress.setVisibility(View.GONE); Log.e("SRCAPK", "Unable to start SDL activity (" + e.getClass().getSimpleName() + ")."); showStartupError(getString(R.string.srceng_launcher_error_start_failed, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())); }


	}


	/**
	 * Reports network state without pretending that a working server browser exists.
	 * No server discovery request is made because no verified native browser/join bridge is wired.
	 */
	private void showOnlineFeaturesStatus() {
		StringBuilder report = new StringBuilder();
		android.net.ConnectivityManager manager = (android.net.ConnectivityManager)
				getSystemService(CONNECTIVITY_SERVICE);
		android.net.NetworkInfo active = manager == null ? null : manager.getActiveNetworkInfo();
		if (active != null && active.isConnected()) {
			report.append(getString(R.string.srceng_online_network_connected));
		} else {
			report.append(getString(R.string.srceng_online_network_disconnected));
		}
		report.append("\n\n").append(getString(R.string.srceng_online_server_browser_unavailable));
		report.append("\n\n").append(getString(R.string.srceng_online_multiplayer_note));
		styleGlassDialog(new AlertDialog.Builder(this)
				.setTitle(R.string.srceng_online_title)
				.setMessage(report.toString())
				.setPositiveButton(R.string.srceng_launcher_ok, null).show());
	}

	/**
	 * Reports device/runtime facts that can be inspected without launching a game.
	 * This is diagnostic evidence, not a benchmark or a guarantee of game compatibility.
	 */
	private void showCompatibilityDiagnostics() {
		StringBuilder report = new StringBuilder();
		android.content.pm.PackageManager pm = getPackageManager();
		android.content.pm.FeatureInfo[] features = pm.getSystemAvailableFeatures();
		int glesVersion = 0;
		if (features != null) {
			for (int i = 0; i < features.length; i++) {
				if (features[i] != null && "android.hardware.opengles.aep".equals(features[i].name)) {
					// AEP is optional and does not represent the actual GLES version.
				}
				if (features[i] != null && features[i].reqGlEsVersion > glesVersion)
					glesVersion = features[i].reqGlEsVersion;
			}
		}
		android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
		android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
		if (wm != null && wm.getDefaultDisplay() != null) wm.getDefaultDisplay().getMetrics(metrics);
		android.app.ActivityManager.MemoryInfo memory = new android.app.ActivityManager.MemoryInfo();
		android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
		if (am != null) am.getMemoryInfo(memory);
		String abi = Build.CPU_ABI == null ? "unknown" : Build.CPU_ABI;
		if (Build.VERSION.SDK_INT >= 21) abi = Api21.getSupportedAbis();
		File nativeDir = new File(getApplicationInfo().nativeLibraryDir);
		String[] required = new String[] {"libtierhook.so", "libmain.so", "libRegal.so", "libclient.so", "libserver.so"};
		StringBuilder missing = new StringBuilder();
		for (int i = 0; i < required.length; i++) {
			if (!new File(nativeDir, required[i]).isFile()) {
				if (missing.length() > 0) missing.append(", ");
				missing.append(required[i]);
			}
		}
		report.append(getString(R.string.srceng_compatibility_device, Build.MANUFACTURER, Build.MODEL));
		report.append("\\n").append(getString(R.string.srceng_compatibility_android, Build.VERSION.RELEASE, Build.VERSION.SDK_INT));
		report.append("\\n").append(getString(R.string.srceng_compatibility_abi, abi));
		report.append("\\n").append(getString(R.string.srceng_compatibility_display, metrics.widthPixels, metrics.heightPixels, metrics.densityDpi));
		if (wm != null && wm.getDefaultDisplay() != null)
			report.append("\\n").append(getString(R.string.srceng_compatibility_refresh, wm.getDefaultDisplay().getRefreshRate()));
		if (glesVersion == 0) {
			report.append("\\n").append(getString(R.string.srceng_compatibility_gles_unknown));
		} else {
			report.append("\\n").append(getString(R.string.srceng_compatibility_gles, (glesVersion >> 16) & 0xffff, glesVersion & 0xffff));
		}
		report.append("\\n").append(getString(R.string.srceng_compatibility_memory, memory.availMem / (1024L * 1024L), memory.lowMemory ? getString(R.string.srceng_compatibility_memory_low) : getString(R.string.srceng_compatibility_memory_ok)));
		if (Build.VERSION.SDK_INT >= 23) {
			int outputs = Api23.getAudioOutputCount(this);
			report.append("\\n").append(getString(R.string.srceng_compatibility_audio_outputs, outputs));
		} else {
			report.append("\\n").append(getString(R.string.srceng_compatibility_audio_unknown));
		}
		if (missing.length() > 0)
			report.append("\\n\\n").append(getString(R.string.srceng_compatibility_native_missing, missing.toString()));
		else
			report.append("\\n\\n").append(getString(R.string.srceng_compatibility_native_ok));
		report.append("\\n\\n").append(getString(R.string.srceng_compatibility_limits));
		styleGlassDialog(new AlertDialog.Builder(this)
				.setTitle(R.string.srceng_compatibility_title)
				.setMessage(report.toString())
				.setPositiveButton(R.string.srceng_launcher_ok, null).show());
	}


	private static final class Api21 {
		private static String getSupportedAbis() {
			if (Build.SUPPORTED_ABIS == null || Build.SUPPORTED_ABIS.length == 0)
				return Build.CPU_ABI == null ? "unknown" : Build.CPU_ABI;
			return joinValues(Build.SUPPORTED_ABIS);
		}
	}

	private static final class Api23 {
		private static int getAudioOutputCount(Activity activity) {
			android.media.AudioManager audio = (android.media.AudioManager) activity.getSystemService("audio");
			return audio == null ? 0 : audio.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).length;
		}
	}

	private static String joinValues(String[] values) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			if (i > 0) result.append(", ");
			result.append(values[i]);
		}
		return result.toString();
	}

	private void showStoragePermissionHelp() {
		AlertDialog dialog = new AlertDialog.Builder(this)
				.setTitle(R.string.srceng_storage_error_title)
				.setMessage(R.string.srceng_storage_permission_required)
				.setNegativeButton(R.string.srceng_launcher_ok, null)
				.setPositiveButton(R.string.srceng_storage_open_settings, new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface dialog, int which) {
						try {
							Intent settings = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
							settings.setData(Uri.parse("package:" + getPackageName()));
							startActivity(settings);
						} catch (RuntimeException e) {
							Toast.makeText(LauncherActivity.this, R.string.srceng_storage_settings_unavailable,
									Toast.LENGTH_LONG).show();
						}
					}
				}).show();
		styleGlassDialog(dialog);
	}

	private void showStartupError(String message) {
		if (isFinishing()) return;
		styleGlassDialog(new AlertDialog.Builder(this).setTitle(R.string.srceng_launcher_error).setMessage(message).setPositiveButton(R.string.srceng_launcher_ok, null).show());
	}
	@Override protected void onResume() {
		super.onResume();
		launchInProgress = false;
		if (launchButton != null) launchButton.setEnabled(true);
		renderGameLibrary();
		if (launcherProgress != null) launcherProgress.setVisibility(View.GONE);
		if (launchButton != null) launchButton.setText(R.string.srceng_launcher_launch);
	}
	@Override protected void onPause() { Log.v("SRCAPK", "onPause"); LiquidGlassTheme.pause(this); if (mPref != null && cmdArgs != null && GamePath != null && EnvEdit != null) saveSettings(mPref.edit()); super.onPause(); }
}

