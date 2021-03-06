package org.mtransit.android.commons.receiver;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import org.mtransit.android.commons.Constants;
import org.mtransit.android.commons.MTLog;

public class DataChange implements MTLog.Loggable {

	private static final String LOG_TAG = DataChange.class.getSimpleName();

	@Override
	public String getLogTag() {
		return LOG_TAG;
	}

	public static final String ACTION_DATA_CHANGE = "org.mtransit.android.intent.action.DATA_CHANGE";
	public static final String PERMISSION_BROADCAST_RECEIVER = "org.mtransit.android.receiver.permission.BROADCAST_RECEIVER";

	public static final String FORCE = "force";
	public static final String AUTHORITY = "authority";
	public static final String PKG = "pkg";

	public static void broadcastDataChange(@NonNull Context context, String authority, String pkg, boolean force) {
		try {
			Intent intent = new Intent();
			intent.setPackage(Constants.MAIN_APP_PACKAGE_NAME);
			intent.setAction(ACTION_DATA_CHANGE);
			intent.putExtra(FORCE, force);
			intent.putExtra(AUTHORITY, authority);
			intent.putExtra(PKG, pkg);
			context.sendBroadcast(intent, PERMISSION_BROADCAST_RECEIVER);
		} catch (Exception e) {
			MTLog.w(LOG_TAG, "Error while sending broadcast!", e);
		}
	}
}
