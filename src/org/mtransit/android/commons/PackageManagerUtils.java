package org.mtransit.android.commons;

import org.mtransit.android.commons.ui.ModuleRedirectActivity;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

public final class PackageManagerUtils {

	private static final String TAG = PackageManagerUtils.class.getSimpleName();

	public static void removeModuleLauncherIcon(Context context) {
		removeLauncherIcon(context, ModuleRedirectActivity.class);
	}

	public static void removeLauncherIcon(Context context, Class<?> activityClass) {
		removeLauncherIcon(context, context.getPackageName(), activityClass.getCanonicalName());
	}

	public static void removeLauncherIcon(Context context, String pkg, String activityName) {
		try {
			context.getPackageManager().setComponentEnabledSetting(new ComponentName(pkg, activityName), PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
					PackageManager.DONT_KILL_APP);
		} catch (Throwable t) {
			MTLog.w(TAG, t, "Error while removing launcher icon!");
		}
	}

	public static void openApp(Context context, String pkg) {
		try {
			Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
			if (intent == null) {
				throw new PackageManager.NameNotFoundException();
			}
			intent.addCategory(Intent.CATEGORY_LAUNCHER);
			context.startActivity(intent);
		} catch (Throwable t) {
			MTLog.w(TAG, t, "Error while opening the application!");
		}
	}

	public static boolean isAppInstalled(Context context, String pkg) {
		try {
			context.getPackageManager().getPackageInfo(pkg, PackageManager.GET_ACTIVITIES);
			return true;
		} catch (PackageManager.NameNotFoundException e) {
			return false;
		}
	}

	public static void uninstallApp(Activity activity, String pkg) {
		final Uri uri = Uri.parse("package:" + pkg);
		final Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, uri);
		activity.startActivity(intent);
	}

	private PackageManagerUtils() {
	}

}