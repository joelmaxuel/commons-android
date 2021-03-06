package org.mtransit.android.commons;

import org.mtransit.android.commons.ui.ModuleRedirectActivity;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

public final class PackageManagerUtils {

	private static final String TAG = PackageManagerUtils.class.getSimpleName();

	public static void removeModuleLauncherIcon(@Nullable Context context) {
		removeLauncherIcon(context, ModuleRedirectActivity.class);
	}

	public static void removeLauncherIcon(@Nullable Context context, Class<?> activityClass) {
		try {
			if (context != null) {
				context.getPackageManager().setComponentEnabledSetting(new ComponentName(context.getPackageName(), activityClass.getCanonicalName()),
						PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
			}
		} catch (Exception e) {
			MTLog.w(TAG, e, "Error while removing launcher icon!");
		}
	}

	public static void openApp(@NonNull Context context, String pkg, int... intentFlags) {
		try {
			Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
			if (intent == null) {
				throw new PackageManager.NameNotFoundException();
			}
			intent.addCategory(Intent.CATEGORY_LAUNCHER);
			if (intentFlags != null) {
				for (int intentFlag : intentFlags) {
					intent.addFlags(intentFlag);
				}
			}
			context.startActivity(intent);
		} catch (Exception e) {
			MTLog.w(TAG, e, "Error while opening the application!");
		}
	}

	public static boolean isAppInstalled(@NonNull Context context, String pkg) {
		try {
			context.getPackageManager().getPackageInfo(pkg, PackageManager.GET_ACTIVITIES);
			return true;
		} catch (PackageManager.NameNotFoundException e) {
			return false;
		}
	}

	public static void uninstallApp(Activity activity, String pkg) {
		Uri uri = Uri.parse("package:" + pkg);
		Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, uri);
		activity.startActivity(intent);
	}

	public static void showAppDetailsSettings(@NonNull Activity activity, String pkg) {
		Uri uri = Uri.parse("package:" + pkg);
		Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri);
		activity.startActivity(intent);
	}

	@Nullable
	public static ProviderInfo[] findContentProvidersWithMetaData(@NonNull Context context, @Nullable String packageName) {
		if (TextUtils.isEmpty(packageName)) {
			return null;
		}
		PackageManager pm = context.getPackageManager();
		for (PackageInfo packageInfo : pm.getInstalledPackages(PackageManager.GET_PROVIDERS | PackageManager.GET_META_DATA)) {
			if (packageInfo.packageName.equals(packageName)) {
				return packageInfo.providers;
			}
		}
		return null;
	}

	public static CharSequence getAppName(@NonNull Context context) {
		try {
			ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);
			return context.getPackageManager().getApplicationLabel(appInfo);
		} catch (PackageManager.NameNotFoundException e) {
			MTLog.w(TAG, e, "Error while looking up app name!");
			return context.getString(R.string.ellipsis);
		}
	}

	public static String getAppVersionName(@NonNull Context context) {
		if (!TextUtils.isEmpty(BuildConfig.VERSION_NAME)) {
			return BuildConfig.VERSION_NAME;
		}
		return getAppVersionName(context, context.getPackageName());
	}

	public static String getAppVersionName(@NonNull Context context, String pkg) {
		try {
			return context.getPackageManager().getPackageInfo(pkg, 0).versionName;
		} catch (PackageManager.NameNotFoundException e) {
			MTLog.w(TAG, e, "Error while looking up '%s' version name!", pkg);
			return context.getString(R.string.ellipsis);
		}
	}

	public static int getAppVersionCode(@NonNull Context context) {
		if (BuildConfig.VERSION_CODE > 0) {
			return BuildConfig.VERSION_CODE;
		}
		return getAppVersionCode(context, context.getPackageName());
	}

	public static int getAppVersionCode(@NonNull Context context, String pkg) {
		try {
			return context.getPackageManager().getPackageInfo(pkg, 0).versionCode;
		} catch (PackageManager.NameNotFoundException e) {
			MTLog.w(TAG, e, "Error while looking up '%s' version code!", pkg);
			return -1;
		}
	}

	private PackageManagerUtils() {
	}
}
