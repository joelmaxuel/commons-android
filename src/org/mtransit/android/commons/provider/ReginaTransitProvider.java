package org.mtransit.android.commons.provider;

import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mtransit.android.commons.ArrayUtils;
import org.mtransit.android.commons.CleanUtils;
import org.mtransit.android.commons.FileUtils;
import org.mtransit.android.commons.MTLog;
import org.mtransit.android.commons.PackageManagerUtils;
import org.mtransit.android.commons.R;
import org.mtransit.android.commons.SqlUtils;
import org.mtransit.android.commons.StringUtils;
import org.mtransit.android.commons.ThreadSafeDateFormatter;
import org.mtransit.android.commons.TimeUtils;
import org.mtransit.android.commons.UriUtils;
import org.mtransit.android.commons.data.POI;
import org.mtransit.android.commons.data.POIStatus;
import org.mtransit.android.commons.data.RouteTripStop;
import org.mtransit.android.commons.data.Schedule;
import org.mtransit.android.commons.data.Trip;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

@SuppressLint("Registered")
public class ReginaTransitProvider extends ContentProviderExtra implements StatusProviderContract {

	private static final String LOG_TAG = ReginaTransitProvider.class.getSimpleName();

	@NonNull
	@Override
	public String getLogTag() {
		return LOG_TAG;
	}

	@NonNull
	public static UriMatcher getNewUriMatcher(@NonNull String authority) {
		UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
		StatusProvider.append(URI_MATCHER, authority);
		return URI_MATCHER;
	}

	@Nullable
	private static UriMatcher uriMatcher = null;

	/**
	 * Override if multiple {@link ReginaTransitProvider} implementations in same app.
	 */
	@NonNull
	private static UriMatcher getURIMATCHER(@NonNull Context context) {
		if (uriMatcher == null) {
			uriMatcher = getNewUriMatcher(getAUTHORITY(context));
		}
		return uriMatcher;
	}

	@Nullable
	private static String authority = null;

	/**
	 * Override if multiple {@link ReginaTransitProvider} implementations in same app.
	 */
	@NonNull
	private static String getAUTHORITY(@NonNull Context context) {
		if (authority == null) {
			authority = context.getResources().getString(R.string.regina_transit_authority);
		}
		return authority;
	}

	@Nullable
	private static Uri authorityUri = null;

	/**
	 * Override if multiple {@link ReginaTransitProvider} implementations in same app.
	 */
	@NonNull
	private static Uri getAUTHORITY_URI(@NonNull Context context) {
		if (authorityUri == null) {
			authorityUri = UriUtils.newContentUri(getAUTHORITY(context));
		}
		return authorityUri;
	}

	private static final long TRANSIT_LIVE_STATUS_MAX_VALIDITY_IN_MS = TimeUnit.HOURS.toMillis(1L);
	private static final long TRANSIT_LIVE_STATUS_VALIDITY_IN_MS = TimeUnit.MINUTES.toMillis(10L);
	private static final long TRANSIT_LIVE_STATUS_VALIDITY_IN_FOCUS_IN_MS = TimeUnit.MINUTES.toMillis(1L);
	private static final long TRANSIT_LIVE_STATUS_MIN_DURATION_BETWEEN_REFRESH_IN_MS = TimeUnit.MINUTES.toMillis(1L);
	private static final long TRANSIT_LIVE_STATUS_MIN_DURATION_BETWEEN_REFRESH_IN_FOCUS_IN_MS = TimeUnit.MINUTES.toMillis(1L);

	@Override
	public long getStatusMaxValidityInMs() {
		return TRANSIT_LIVE_STATUS_MAX_VALIDITY_IN_MS;
	}

	@Override
	public long getStatusValidityInMs(boolean inFocus) {
		if (inFocus) {
			return TRANSIT_LIVE_STATUS_VALIDITY_IN_FOCUS_IN_MS;
		}
		return TRANSIT_LIVE_STATUS_VALIDITY_IN_MS;
	}

	@Override
	public long getMinDurationBetweenRefreshInMs(boolean inFocus) {
		if (inFocus) {
			return TRANSIT_LIVE_STATUS_MIN_DURATION_BETWEEN_REFRESH_IN_FOCUS_IN_MS;
		}
		return TRANSIT_LIVE_STATUS_MIN_DURATION_BETWEEN_REFRESH_IN_MS;
	}

	@Override
	public void cacheStatus(@NonNull POIStatus newStatusToCache) {
		StatusProvider.cacheStatusS(this, newStatusToCache);
	}

	@Override
	public POIStatus getCachedStatus(@NonNull StatusProviderContract.Filter statusFilter) {
		if (!(statusFilter instanceof Schedule.ScheduleStatusFilter)) {
			MTLog.w(this, "getNewStatus() > Can't find new schedule without schedule filter!");
			return null;
		}
		Schedule.ScheduleStatusFilter scheduleStatusFilter = (Schedule.ScheduleStatusFilter) statusFilter;
		RouteTripStop rts = scheduleStatusFilter.getRouteTripStop();
		POIStatus status = StatusProvider.getCachedStatusS(this, rts.getUUID());
		if (status != null) {
			status.setTargetUUID(rts.getUUID());
			if (status instanceof Schedule) {
				((Schedule) status).setDescentOnly(rts.isDescentOnly());
			}
		}
		return status;
	}

	@Override
	public boolean purgeUselessCachedStatuses() {
		return StatusProvider.purgeUselessCachedStatuses(this);
	}

	@Override
	public boolean deleteCachedStatus(int cachedStatusId) {
		return StatusProvider.deleteCachedStatus(this, cachedStatusId);
	}

	@Override
	public String getStatusDbTableName() {
		return ReginaTransitDbHelper.T_TRANSIT_LIVE_STATUS;
	}

	@Override
	public int getStatusType() {
		return POI.ITEM_STATUS_TYPE_SCHEDULE;
	}

	@Override
	public POIStatus getNewStatus(@NonNull StatusProviderContract.Filter statusFilter) {
		if (!(statusFilter instanceof Schedule.ScheduleStatusFilter)) {
			MTLog.w(this, "getNewStatus() > Can't find new schedule without schedule filter!");
			return null;
		}
		Schedule.ScheduleStatusFilter scheduleStatusFilter = (Schedule.ScheduleStatusFilter) statusFilter;
		RouteTripStop rts = scheduleStatusFilter.getRouteTripStop();
		loadRealTimeStatusFromWWW(rts);
		return getCachedStatus(statusFilter);
	}

	private static final TimeZone REGINA_TZ = TimeZone.getTimeZone("America/Regina");


	private static final TimeZone UTC_TZ = TimeZone.getTimeZone("UTC");

	protected static final ThreadSafeDateFormatter DATE_FORMATTER_UTC;

	static {
		ThreadSafeDateFormatter dateFormatter = new ThreadSafeDateFormatter("hh:mm a");
		dateFormatter.setTimeZone(UTC_TZ);
		DATE_FORMATTER_UTC = dateFormatter;
	}

	private static final String REAL_TIME_URL_PART_1_BEFORE_STOP_CODE = "https://transitlive.com/ajax/livemap.php?action=stop_times&stop=";
	private static final String REAL_TIME_URL_PART_2_BEFORE_ROUTE_SHORT_NAME = "&routes=";
	private static final String REAL_TIME_URL_PART_3 = "&lim=21";

	private static String getRealTimeStatusUrlString(@NonNull RouteTripStop rts) {
		return new StringBuilder() //
				.append(REAL_TIME_URL_PART_1_BEFORE_STOP_CODE) //
				.append(rts.getStop().getCode()) //
				.append(REAL_TIME_URL_PART_2_BEFORE_ROUTE_SHORT_NAME) //
				.append(rts.getRoute().getShortName()) //
				.append(REAL_TIME_URL_PART_3) //
				.toString();
	}

	private void loadRealTimeStatusFromWWW(RouteTripStop rts) {
		try {
			String urlString = getRealTimeStatusUrlString(rts);
			URL url = new URL(urlString);
			URLConnection urlc = url.openConnection();
			HttpURLConnection httpUrlConnection = (HttpURLConnection) urlc;
			switch (httpUrlConnection.getResponseCode()) {
			case HttpURLConnection.HTTP_OK:
				long newLastUpdateInMs = TimeUtils.currentTimeMillis();
				String jsonString = FileUtils.getString(urlc.getInputStream());
				Collection<POIStatus> statuses = parseAgencyJSON(requireContext(), parseAgencyJSON(jsonString), rts, newLastUpdateInMs);
				StatusProvider.deleteCachedStatus(this, ArrayUtils.asArrayList(rts.getUUID()));
				if (statuses != null) {
					for (POIStatus status : statuses) {
						StatusProvider.cacheStatusS(this, status);
					}
				}
				return;
			default:
				MTLog.w(this, "ERROR: HTTP URL-Connection Response Code %s (Message: %s)", httpUrlConnection.getResponseCode(),
						httpUrlConnection.getResponseMessage());
			}
		} catch (UnknownHostException uhe) {
			if (MTLog.isLoggable(android.util.Log.DEBUG)) {
				MTLog.w(this, uhe, "No Internet Connection!");
			} else {
				MTLog.w(this, "No Internet Connection!");
			}
		} catch (SocketException se) {
			MTLog.w(LOG_TAG, se, "No Internet Connection!");
		} catch (Exception e) { // Unknown error
			MTLog.e(LOG_TAG, e, "INTERNAL ERROR: Unknown Exception");
		}
	}


	private static long PROVIDER_PRECISION_IN_MS = TimeUnit.SECONDS.toMillis(10L);

	protected Collection<POIStatus> parseAgencyJSON(@NonNull Context context, ArrayList<JResult> jResults, RouteTripStop rts, long newLastUpdateInMs) {
		try {
			ArrayList<POIStatus> result = new ArrayList<>();
			if (jResults != null && jResults.size() > 0) {
				Schedule newSchedule = new Schedule(rts.getUUID(), newLastUpdateInMs, getStatusMaxValidityInMs(), newLastUpdateInMs, PROVIDER_PRECISION_IN_MS,
						false);
				Calendar beginningOfTodayCal = Calendar.getInstance(REGINA_TZ);
				beginningOfTodayCal.set(Calendar.HOUR_OF_DAY, 0);
				beginningOfTodayCal.set(Calendar.MINUTE, 0);
				beginningOfTodayCal.set(Calendar.SECOND, 0);
				beginningOfTodayCal.set(Calendar.MILLISECOND, 0);
				long beginningOfTodayMs = beginningOfTodayCal.getTimeInMillis();
				long after = newLastUpdateInMs - TimeUnit.HOURS.toMillis(1L);
				for (JResult jResult : jResults) {
					if (TextUtils.isEmpty(jResult.predictionTime)) {
						continue;
					}
					long predictionTimeMs = DATE_FORMATTER_UTC.parseThreadSafe(jResult.predictionTime).getTime();
					long t = beginningOfTodayMs + TimeUtils.timeToTheTensSecondsMillis(predictionTimeMs);
					if (t < after) {
						t += TimeUnit.DAYS.toMillis(1L); // TOMORROW
					}
					Schedule.Timestamp timestamp = new Schedule.Timestamp(t);
					try {
						boolean isLastStop = jResult.lastStop != null
								&& Integer.parseInt(jResult.lastStop) == Integer.parseInt(rts.getStop().getCode());
						if (isLastStop) {
							timestamp.setHeadsign(Trip.HEADSIGN_TYPE_DESCENT_ONLY, null);
						} else if (!TextUtils.isEmpty(jResult.lineName)) {
							String headsignValue = cleanTripHeadsign(jResult.lineName);
							boolean sameHeadSign = rts.getTrip().getHeading(context).equalsIgnoreCase(headsignValue);
							if (!sameHeadSign) {
								timestamp.setHeadsign(Trip.HEADSIGN_TYPE_STRING, headsignValue);
							}
						}
					} catch (Exception e) {
						MTLog.w(this, e, "Error while adding destination name %s!", jResult);
					}
					newSchedule.addTimestampWithoutSort(timestamp);
				}
				newSchedule.sortTimestamps();
				result.add(newSchedule);
			}
			return result;
		} catch (Exception e) {
			MTLog.w(this, e, "Error while parsing '%s'!", jResults);
			return null;
		}
	}

	private static final String JSON_PRED_TIME = "pred_time";
	private static final String JSON_LINE_NAME = "line_name";
	private static final String JSON_LAST_STOP = "last_stop";

	@NonNull
	private ArrayList<JResult> parseAgencyJSON(String jsonString) {
		ArrayList<JResult> result = new ArrayList<>();
		try {
			JSONArray jStopTimes = jsonString == null ? null : new JSONArray(jsonString);
			if (jStopTimes != null && jStopTimes.length() > 0) {
				for (int s = 0; s < jStopTimes.length(); s++) {
					JSONObject jStopTime = jStopTimes.getJSONObject(s);
					if (jStopTime == null //
							|| !jStopTime.has(JSON_PRED_TIME)) {
						continue;
					}
					result.add(new JResult(
							jStopTime.getString(JSON_PRED_TIME),
							jStopTime.optString(JSON_LINE_NAME),
							jStopTime.optString(JSON_LAST_STOP)));
				}
			}
		} catch (Exception e) {
			MTLog.w(this, e, "Error while parsing JSON '%s'!", jsonString);
		}
		return result;
	}

	private static final String INDUSTRIAL_SHORT = "Ind";

	private static final Pattern EXPRESS = Pattern.compile("((^|\\W){1}(express)(\\W|$){1})", Pattern.CASE_INSENSITIVE);

	private static final Pattern INDUSTRIAL = Pattern.compile("((^|\\W){1}(industrial|indust)(\\W|$){1})", Pattern.CASE_INSENSITIVE);
	private static final String INDUSTRIAL_REPLACEMENT = "$2" + INDUSTRIAL_SHORT + "$4";

	private static final String WHITMORE = "Whitmore";

	private static final Pattern WHITMORE_PARK_ = Pattern.compile("((^|\\W){1}(whitmore|whitmore park|whitmore pk)(\\W|$){1})", Pattern.CASE_INSENSITIVE);
	private static final String WHITMORE_PARK_REPLACEMENT = "$2" + WHITMORE + "$4";

	private static final Pattern ARCOLA_VICTORIA_DOWNTOWN_EAST_ = Pattern.compile("((^|\\W){1}((arcola|victoria) (downtown|east))(\\W|$){1})",
			Pattern.CASE_INSENSITIVE);
	private static final String ARCOLA_VICTORIA_DOWNTOWN_EAST_REPLACEMENT = "$2" + "$5 $4 " + "$6";

	private String cleanTripHeadsign(String tripHeadsign) {
		try {
			tripHeadsign = tripHeadsign.toLowerCase(Locale.ENGLISH);
			tripHeadsign = EXPRESS.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
			tripHeadsign = INDUSTRIAL.matcher(tripHeadsign).replaceAll(INDUSTRIAL_REPLACEMENT);
			tripHeadsign = WHITMORE_PARK_.matcher(tripHeadsign).replaceAll(WHITMORE_PARK_REPLACEMENT);
			tripHeadsign = ARCOLA_VICTORIA_DOWNTOWN_EAST_.matcher(tripHeadsign).replaceAll(ARCOLA_VICTORIA_DOWNTOWN_EAST_REPLACEMENT);
			tripHeadsign = CleanUtils.cleanStreetTypes(tripHeadsign);
			tripHeadsign = CleanUtils.removePoints(tripHeadsign);
			return CleanUtils.cleanLabel(tripHeadsign);
		} catch (Exception e) {
			MTLog.w(this, e, "Error while cleaning trip head sign '%s'!", tripHeadsign);
			return tripHeadsign;
		}
	}

	@Override
	public boolean onCreateMT() {
		ping();
		return true;
	}

	@Override
	public void ping() {
		PackageManagerUtils.removeModuleLauncherIcon(getContext());
	}

	@Nullable
	private ReginaTransitDbHelper dbHelper;

	private static int currentDbVersion = -1;

	@NonNull
	private ReginaTransitDbHelper getDBHelper(@NonNull Context context) {
		if (dbHelper == null) { // initialize
			dbHelper = getNewDbHelper(context);
			currentDbVersion = getCurrentDbVersion();
		} else { // reset
			try {
				if (currentDbVersion != getCurrentDbVersion()) {
					dbHelper.close();
					dbHelper = null;
					return getDBHelper(context);
				}
			} catch (Exception e) { // fail if locked, will try again later
				MTLog.w(this, e, "Can't check DB version!");
			}
		}
		return dbHelper;
	}

	/**
	 * Override if multiple {@link ReginaTransitProvider} implementations in same app.
	 */
	public int getCurrentDbVersion() {
		return ReginaTransitDbHelper.getDbVersion(requireContext());
	}

	/**
	 * Override if multiple {@link ReginaTransitProvider} implementations in same app.
	 */
	@NonNull
	public ReginaTransitDbHelper getNewDbHelper(@NonNull Context context) {
		return new ReginaTransitDbHelper(context.getApplicationContext());
	}

	@NonNull
	@Override
	public UriMatcher getURI_MATCHER() {
		return getURIMATCHER(requireContext());
	}

	@NonNull
	@Override
	public Uri getAuthorityUri() {
		return getAUTHORITY_URI(requireContext());
	}

	@NonNull
	@Override
	public SQLiteOpenHelper getDBHelper() {
		return getDBHelper(requireContext());
	}

	@Override
	public Cursor queryMT(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		Cursor cursor = StatusProvider.queryS(this, uri, selection);
		if (cursor != null) {
			return cursor;
		}
		throw new IllegalArgumentException(String.format("Unknown URI (query): '%s'", uri));
	}

	@Override
	public String getTypeMT(@NonNull Uri uri) {
		String type = StatusProvider.getTypeS(this, uri);
		if (type != null) {
			return type;
		}
		throw new IllegalArgumentException(String.format("Unknown URI (type): '%s'", uri));
	}

	@Override
	public int deleteMT(@NonNull Uri uri, String selection, String[] selectionArgs) {
		MTLog.w(this, "The delete method is not available.");
		return 0;
	}

	@Override
	public int updateMT(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		MTLog.w(this, "The update method is not available.");
		return 0;
	}

	@Override
	public Uri insertMT(@NonNull Uri uri, ContentValues values) {
		MTLog.w(this, "The insert method is not available.");
		return null;
	}

	public static class JResult {

		private String predictionTime;
		@Nullable
		private String lineName;
		@Nullable
		private String lastStop;

		public JResult(String predictionTime, @Nullable String lineName, @Nullable String lastStop) {
			this.predictionTime = predictionTime;
			this.lineName = lineName;
			this.lastStop = lastStop;
		}

		@NonNull
		@Override
		public String toString() {
			return JResult.class.getSimpleName() + "{" +
					"predictionTime='" + predictionTime + '\'' + "," +
					"lineName='" + lineName + '\'' + "," +
					"lastStop='" + lastStop + '\'' +
					'}';
		}
	}

	public static class ReginaTransitDbHelper extends MTSQLiteOpenHelper {

		private static final String TAG = ReginaTransitDbHelper.class.getSimpleName();

		@NonNull
		@Override
		public String getLogTag() {
			return TAG;
		}

		/**
		 * Override if multiple {@link ReginaTransitDbHelper} implementations in same app.
		 */
		protected static final String DB_NAME = "reginatransit.db";

		public static final String T_TRANSIT_LIVE_STATUS = StatusProvider.StatusDbHelper.T_STATUS;

		private static final String T_TRANSIT_LIVE_STATUS_SQL_CREATE = StatusProvider.StatusDbHelper.getSqlCreateBuilder(T_TRANSIT_LIVE_STATUS).build();

		private static final String T_TRANSIT_LIVE_STATUS_SQL_DROP = SqlUtils.getSQLDropIfExistsQuery(T_TRANSIT_LIVE_STATUS);

		private static int dbVersion = -1;

		/**
		 * Override if multiple {@link ReginaTransitDbHelper} in same app.
		 */
		public static int getDbVersion(@NonNull Context context) {
			if (dbVersion < 0) {
				dbVersion = context.getResources().getInteger(R.integer.regina_transit_db_version);
			}
			return dbVersion;
		}

		public ReginaTransitDbHelper(@NonNull Context context) {
			super(context, DB_NAME, null, getDbVersion(context));
		}

		@Override
		public void onCreateMT(@NonNull SQLiteDatabase db) {
			initAllDbTables(db);
		}

		@Override
		public void onUpgradeMT(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
			db.execSQL(T_TRANSIT_LIVE_STATUS_SQL_DROP);
			initAllDbTables(db);
		}

		public boolean isDbExist(@NonNull Context context) {
			return SqlUtils.isDbExist(context, DB_NAME);
		}

		private void initAllDbTables(@NonNull SQLiteDatabase db) {
			db.execSQL(T_TRANSIT_LIVE_STATUS_SQL_CREATE);
		}
	}
}
