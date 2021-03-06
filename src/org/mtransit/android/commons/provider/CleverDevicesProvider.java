package org.mtransit.android.commons.provider;

import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.mtransit.android.commons.ArrayUtils;
import org.mtransit.android.commons.CleanUtils;
import org.mtransit.android.commons.CollectionUtils;
import org.mtransit.android.commons.MTLog;
import org.mtransit.android.commons.PackageManagerUtils;
import org.mtransit.android.commons.R;
import org.mtransit.android.commons.SqlUtils;
import org.mtransit.android.commons.TimeUtils;
import org.mtransit.android.commons.UriUtils;
import org.mtransit.android.commons.data.POI;
import org.mtransit.android.commons.data.POIStatus;
import org.mtransit.android.commons.data.RouteTripStop;
import org.mtransit.android.commons.data.Schedule;
import org.mtransit.android.commons.data.Schedule.Timestamp;
import org.mtransit.android.commons.data.Trip;
import org.mtransit.android.commons.helpers.MTDefaultHandler;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;

@SuppressLint("Registered")
public class CleverDevicesProvider extends MTContentProvider implements StatusProviderContract {

	private static final String TAG = CleverDevicesProvider.class.getSimpleName();

	@Override
	public String getLogTag() {
		return TAG;
	}

	private static UriMatcher getNewUriMatcher(String authority) {
		UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
		StatusProvider.append(URI_MATCHER, authority);
		return URI_MATCHER;
	}

	private static UriMatcher uriMatcher = null;

	/**
	 * Override if multiple {@link CleverDevicesProvider} implementations in same app.
	 */
	private static UriMatcher getURIMATCHER(Context context) {
		if (uriMatcher == null) {
			uriMatcher = getNewUriMatcher(getAUTHORITY(context));
		}
		return uriMatcher;
	}

	private static String authority = null;

	/**
	 * Override if multiple {@link CleverDevicesProvider} implementations in same app.
	 */
	private static String getAUTHORITY(Context context) {
		if (authority == null) {
			authority = context.getResources().getString(R.string.clever_devices_authority);
		}
		return authority;
	}

	private static Uri authorityUri = null;

	/**
	 * Override if multiple {@link CleverDevicesProvider} implementations in same app.
	 */
	private static Uri getAUTHORITY_URI(Context context) {
		if (authorityUri == null) {
			authorityUri = UriUtils.newContentUri(getAUTHORITY(context));
		}
		return authorityUri;
	}

	private static String statusUrlAndRSNAndStopCode = null;

	/**
	 * Override if multiple {@link CleverDevicesProvider} implementations in same app.
	 */
	private static String getSTATUS_URL_AND_RSN_AND_STOP_CODE(Context context) {
		if (statusUrlAndRSNAndStopCode == null) {
			statusUrlAndRSNAndStopCode = context.getResources().getString(R.string.clever_devices_status_url_and_rsn_and_stop_code);
		}
		return statusUrlAndRSNAndStopCode;
	}

	private static java.util.List<String> scheduleHeadsignCleanRegex = null;

	/**
	 * Override if multiple {@link CleverDevicesProvider} implementations in same app.
	 */
	private static java.util.List<String> getSCHEDULE_HEADSIGN_CLEAN_REGEX(Context context) {
		if (scheduleHeadsignCleanRegex == null) {
			scheduleHeadsignCleanRegex = Arrays.asList(context.getResources().getStringArray(R.array.clever_devices_schedule_head_sign_clean_regex));
		}
		return scheduleHeadsignCleanRegex;
	}

	private static java.util.List<String> scheduleHeadsignCleanReplacement = null;

	/**
	 * Override if multiple {@link CleverDevicesProvider} implementations in same app.
	 */
	private static java.util.List<String> getSCHEDULE_HEADSIGN_CLEAN_REPLACEMENT(Context context) {
		if (scheduleHeadsignCleanReplacement == null) {
			scheduleHeadsignCleanReplacement = Arrays
					.asList(context.getResources().getStringArray(R.array.clever_devices_schedule_head_sign_clean_replacement));
		}
		return scheduleHeadsignCleanReplacement;
	}

	private static Boolean scheduleHeadsignToLowerCase = null;

	/**
	 * Override if multiple {@link CleverDevicesProvider} implementations in same app.
	 */
	private static boolean isSCHEDULE_HEADSIGN_TO_LOWER_CASE(Context context) {
		if (scheduleHeadsignToLowerCase == null) {
			scheduleHeadsignToLowerCase = context.getResources().getBoolean(R.bool.clever_devices_schedule_head_sign_to_lower_case);
		}
		return scheduleHeadsignToLowerCase;
	}

	private static final long CLEVER_DEVICES_STATUS_MAX_VALIDITY_IN_MS = TimeUnit.HOURS.toMillis(1);
	private static final long CLEVER_DEVICES_STATUS_VALIDITY_IN_MS = TimeUnit.MINUTES.toMillis(10);
	private static final long CLEVER_DEVICES_STATUS_VALIDITY_IN_FOCUS_IN_MS = TimeUnit.MINUTES.toMillis(1);
	private static final long CLEVER_DEVICES_STATUS_MIN_DURATION_BETWEEN_REFRESH_IN_MS = TimeUnit.MINUTES.toMillis(1);
	private static final long CLEVER_DEVICES_STATUS_MIN_DURATION_BETWEEN_REFRESH_IN_FOCUS_IN_MS = TimeUnit.MINUTES.toMillis(1);

	@Override
	public long getStatusMaxValidityInMs() {
		return CLEVER_DEVICES_STATUS_MAX_VALIDITY_IN_MS;
	}

	@Override
	public long getStatusValidityInMs(boolean inFocus) {
		if (inFocus) {
			return CLEVER_DEVICES_STATUS_VALIDITY_IN_FOCUS_IN_MS;
		}
		return CLEVER_DEVICES_STATUS_VALIDITY_IN_MS;
	}

	@Override
	public long getMinDurationBetweenRefreshInMs(boolean inFocus) {
		if (inFocus) {
			return CLEVER_DEVICES_STATUS_MIN_DURATION_BETWEEN_REFRESH_IN_FOCUS_IN_MS;
		}
		return CLEVER_DEVICES_STATUS_MIN_DURATION_BETWEEN_REFRESH_IN_MS;
	}

	@Override
	public void cacheStatus(POIStatus newStatusToCache) {
		StatusProvider.cacheStatusS(this, newStatusToCache);
	}

	@Override
	public POIStatus getCachedStatus(StatusProviderContract.Filter statusFilter) {
		if (!(statusFilter instanceof Schedule.ScheduleStatusFilter)) {
			MTLog.w(this, "getCachedStatus() > Can't find new schecule whithout schedule filter!");
			return null;
		}
		Schedule.ScheduleStatusFilter scheduleStatusFilter = (Schedule.ScheduleStatusFilter) statusFilter;
		RouteTripStop rts = scheduleStatusFilter.getRouteTripStop();
		if (rts == null || TextUtils.isEmpty(rts.getStop().getCode())) {
			return null;
		}
		String uuid = getAgencyRouteStopTargetUUID(rts);
		POIStatus status = StatusProvider.getCachedStatusS(this, uuid);
		if (status != null) {
			status.setTargetUUID(rts.getUUID()); // target RTS UUID instead of custom Clever Devices tags
			if (status instanceof Schedule) {
				((Schedule) status).setDescentOnly(rts.isDescentOnly());
			}
		}
		return status;
	}

	private static String getAgencyRouteStopTargetUUID(RouteTripStop rts) {
		return rts.getUUID();
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
		return CleverDevicesDbHelper.T_CLEVER_DEVICES_STATUS;
	}

	@Override
	public int getStatusType() {
		return POI.ITEM_STATUS_TYPE_SCHEDULE;
	}

	@Override
	public POIStatus getNewStatus(StatusProviderContract.Filter statusFilter) {
		if (statusFilter == null || !(statusFilter instanceof Schedule.ScheduleStatusFilter)) {
			MTLog.w(this, "getNewStatus() > Can't find new schecule whithout schedule filter!");
			return null;
		}
		Schedule.ScheduleStatusFilter scheduleStatusFilter = (Schedule.ScheduleStatusFilter) statusFilter;
		RouteTripStop rts = scheduleStatusFilter.getRouteTripStop();
		if (rts == null || TextUtils.isEmpty(rts.getStop().getCode())) {
			return null;
		}
		loadRealTimeStatusFromWWW(rts);
		return getCachedStatus(statusFilter);
	}

	private static String getRealTimeStatusUrlString(Context context, RouteTripStop rts) {
		if (TextUtils.isEmpty(rts.getStop().getCode())) {
			MTLog.w(TAG, "Can't create real-time status URL (no stop code) for %s", rts);
			return null;
		}
		return String.format(getSTATUS_URL_AND_RSN_AND_STOP_CODE(context), rts.getRoute().getShortName(), rts.getStop().getCode());
	}

	private void loadRealTimeStatusFromWWW(RouteTripStop rts) {
		try {
			String urlString = getRealTimeStatusUrlString(getContext(), rts);
			if (TextUtils.isEmpty(urlString)) {
				return;
			}
			URL url = new URL(urlString);
			URLConnection urlc = url.openConnection();
			HttpURLConnection httpUrlConnection = (HttpURLConnection) urlc;
			switch (httpUrlConnection.getResponseCode()) {
			case HttpURLConnection.HTTP_OK:
				long newLastUpdateInMs = TimeUtils.currentTimeMillis();
				SAXParserFactory spf = SAXParserFactory.newInstance();
				SAXParser sp = spf.newSAXParser();
				XMLReader xr = sp.getXMLReader();
				CleverDevicesPredictionsDataHandler handler = new CleverDevicesPredictionsDataHandler(this, newLastUpdateInMs, rts);
				xr.setContentHandler(handler);
				xr.parse(new InputSource(httpUrlConnection.getInputStream()));
				Collection<POIStatus> statuses = handler.getStatuses();
				StatusProvider.deleteCachedStatus(this, ArrayUtils.asArrayList(getAgencyRouteStopTargetUUID(rts)));
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
			MTLog.w(TAG, se, "No Internet Connection!");
		} catch (Exception e) { // Unknown error
			MTLog.e(TAG, e, "INTERNAL ERROR: Unknown Exception");
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

	private static CleverDevicesDbHelper dbHelper;

	private static int currentDbVersion = -1;

	private CleverDevicesDbHelper getDBHelper(Context context) {
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
	 * Override if multiple {@link CleverDevicesProvider} implementations in same app.
	 */
	public int getCurrentDbVersion() {
		return CleverDevicesDbHelper.getDbVersion(getContext());
	}

	/**
	 * Override if multiple {@link CleverDevicesProvider} implementations in same app.
	 */
	public CleverDevicesDbHelper getNewDbHelper(Context context) {
		return new CleverDevicesDbHelper(context.getApplicationContext());
	}

	@Override
	public UriMatcher getURI_MATCHER() {
		return getURIMATCHER(getContext());
	}

	@Override
	public Uri getAuthorityUri() {
		return getAUTHORITY_URI(getContext());
	}

	@Override
	public SQLiteOpenHelper getDBHelper() {
		return getDBHelper(getContext());
	}

	@Override
	public Cursor queryMT(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		Cursor cursor = StatusProvider.queryS(this, uri, selection);
		if (cursor != null) {
			return cursor;
		}
		throw new IllegalArgumentException(String.format("Unknown URI (query): '%s'", uri));
	}

	@Override
	public String getTypeMT(Uri uri) {
		String type = StatusProvider.getTypeS(this, uri);
		if (type != null) {
			return type;
		}
		throw new IllegalArgumentException(String.format("Unknown URI (type): '%s'", uri));
	}

	@Override
	public int deleteMT(Uri uri, String selection, String[] selectionArgs) {
		MTLog.w(this, "The delete method is not available.");
		return 0;
	}

	@Override
	public int updateMT(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		MTLog.w(this, "The update method is not available.");
		return 0;
	}

	@Override
	public Uri insertMT(Uri uri, ContentValues values) {
		MTLog.w(this, "The insert method is not available.");
		return null;
	}

	private static class CleverDevicesPredictionsDataHandler extends MTDefaultHandler {

		private static final String TAG = CleverDevicesProvider.TAG + ">" + CleverDevicesPredictionsDataHandler.class.getSimpleName();

		@Override
		public String getLogTag() {
			return TAG;
		}

		private static final String STOP = "stop";
		private static final String NO_PREDICTION_MESSAGE = "noPredictionMessage";
		private static final String PRE = "pre";
		private static final String PT = "pt";
		private static final String PU = "pu";
		private static final String FD = "fd";
		private static final String RN = "rn";
		private static final String RD = "rd";
		private static final String V = "v";
		private static final String ZONE = "zone";

		private static final String MINUTES = "MINUTES";
		private static final String DELAYED = "DELAYED"; // 0 minutes (0 minutes in official app)
		private static final String APPROACHING = "APPROACHING"; // 1-0 minutes

		private String currentLocalName = STOP;

		private CleverDevicesProvider provider;
		private long lastUpdateInMs;
		private RouteTripStop rts;

		private StringBuilder currentPt = new StringBuilder();
		private StringBuilder currentPu = new StringBuilder();
		private StringBuilder currentFd = new StringBuilder();
		@NonNull
		private ArrayList<Timestamp> currentTimestamps = new ArrayList<Schedule.Timestamp>();

		private HashSet<POIStatus> statuses = new HashSet<POIStatus>();

		public CleverDevicesPredictionsDataHandler(CleverDevicesProvider provider, long lastUpdateInMs, RouteTripStop rts) {
			this.provider = provider;
			this.lastUpdateInMs = lastUpdateInMs;
			this.rts = rts;
		}

		public Collection<POIStatus> getStatuses() {
			return this.statuses;
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
			super.startElement(uri, localName, qName, attributes);
			this.currentLocalName = localName;
			if (PRE.equals(this.currentLocalName)) {
				this.currentPt.setLength(0); // reset
				this.currentPu.setLength(0); // reset
				this.currentFd.setLength(0); // reset
			}
		}

		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			super.characters(ch, start, length);
			try {
				String string = new String(ch, start, length);
				if (TextUtils.isEmpty(string)) {
					return;
				}
				if (PT.equals(this.currentLocalName)) {
					this.currentPt.append(string);
				} else if (PU.equals(this.currentLocalName)) {
					this.currentPu.append(string);
				} else if (FD.equals(this.currentLocalName)) { //
					this.currentFd.append(string);
				} else if (STOP.equals(this.currentLocalName)) { // ignore
				} else if (PRE.equals(this.currentLocalName)) { // ignore
				} else if (RN.equals(this.currentLocalName)) { // ignore
				} else if (RD.equals(this.currentLocalName)) { // ignore
				} else if (V.equals(this.currentLocalName)) { // ignore
				} else if (ZONE.equals(this.currentLocalName)) { // ignore
				} else if (NO_PREDICTION_MESSAGE.equals(this.currentLocalName)) { // ignore
				} else {
					MTLog.w(this, "characters() > unexpected characters '%s' for '%s'", string.trim(), this.currentLocalName);
				}
			} catch (Exception e) {
				MTLog.w(this, e, "Error while parsing '%s' value '%s, %s, %s'!", this.currentLocalName, ch, start, length);
			}
		}

		private static long PROVIDER_PRECISION_IN_MS = TimeUnit.SECONDS.toMillis(10);

		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException {
			super.endElement(uri, localName, qName);
			if (PRE.equals(localName)) {
				int minutes;
				String pu = this.currentPu.toString().trim();
				if (APPROACHING.equals(pu)) {
					minutes = 0;
				} else if (DELAYED.equals(pu)) {
					minutes = 0;
				} else if (MINUTES.equals(pu)) {
					minutes = Integer.parseInt(this.currentPt.toString().trim());
				} else {
					MTLog.w(this, "endElement() > Unexpected PU: %s (skip)", pu);
					return;
				}
				Long t = TimeUtils.timeToTheMinuteMillis(this.lastUpdateInMs) + TimeUnit.MINUTES.toMillis(minutes);
				Schedule.Timestamp timestamp = new Schedule.Timestamp(t);
				if (!TextUtils.isEmpty(this.currentFd)) {
					timestamp.setHeadsign(Trip.HEADSIGN_TYPE_STRING, cleanTripHeadsign(this.currentFd.toString().trim(), rts));
				}
				this.currentTimestamps.add(timestamp);
			}
			if (STOP.equals(localName)) {
				if (CollectionUtils.getSize(this.currentTimestamps) == 0) {
					MTLog.w(this, "endElement() > No timestamp for %s", this.rts);
					return;
				}
				Schedule newSchedule = new Schedule(getAgencyRouteStopTargetUUID(this.rts), this.lastUpdateInMs, this.provider.getStatusMaxValidityInMs(),
						this.lastUpdateInMs, PROVIDER_PRECISION_IN_MS, false);
				newSchedule.setTimestampsAndSort(this.currentTimestamps);
				this.statuses.add(newSchedule);
			}
		}

		private String cleanTripHeadsign(String tripHeadsign, RouteTripStop optRTS) {
			try {
				if (isSCHEDULE_HEADSIGN_TO_LOWER_CASE(this.provider.getContext())) {
					tripHeadsign = tripHeadsign.toLowerCase(Locale.ENGLISH);
				}
				for (int c = 0; c < getSCHEDULE_HEADSIGN_CLEAN_REGEX(this.provider.getContext()).size(); c++) {
					try {
						tripHeadsign = Pattern.compile(getSCHEDULE_HEADSIGN_CLEAN_REGEX(this.provider.getContext()).get(c), Pattern.CASE_INSENSITIVE)
								.matcher(tripHeadsign).replaceAll(getSCHEDULE_HEADSIGN_CLEAN_REPLACEMENT(this.provider.getContext()).get(c));
					} catch (Exception e) {
						MTLog.w(this, e, "Error while cleaning trip head sign %s for %s cleaning configuration!", tripHeadsign, c);
					}
				}
				tripHeadsign = CleanUtils.removePoints(tripHeadsign);
				tripHeadsign = CleanUtils.cleanStreetTypes(tripHeadsign);
				if (optRTS != null) {
					String heading =
							this.provider.getContext() == null ? optRTS.getTrip().getHeading() : optRTS.getTrip().getHeading(this.provider.getContext());
					tripHeadsign = Pattern.compile("((^|\\W){1}(" + heading + ")(\\W|$){1})", Pattern.CASE_INSENSITIVE).matcher(tripHeadsign).replaceAll(" ");
				}
				tripHeadsign = CleanUtils.cleanLabel(tripHeadsign);
				return tripHeadsign;
			} catch (Exception e) {
				MTLog.w(this, e, "Error while cleaning trip head sign '%s'!", tripHeadsign);
				return tripHeadsign;
			}
		}
	}

	public static class CleverDevicesDbHelper extends MTSQLiteOpenHelper {

		private static final String TAG = CleverDevicesDbHelper.class.getSimpleName();

		@Override
		public String getLogTag() {
			return TAG;
		}

		/**
		 * Override if multiple {@link CleverDevicesDbHelper} implementations in same app.
		 */
		protected static final String DB_NAME = "cleverdevices.db";

		public static final String T_CLEVER_DEVICES_STATUS = StatusProvider.StatusDbHelper.T_STATUS;

		private static final String T_CLEVER_DEVICES_STATUS_SQL_CREATE = StatusProvider.StatusDbHelper.getSqlCreateBuilder(T_CLEVER_DEVICES_STATUS).build();

		private static final String T_CLEVER_DEVICES_STATUS_SQL_DROP = SqlUtils.getSQLDropIfExistsQuery(T_CLEVER_DEVICES_STATUS);

		private static int dbVersion = -1;

		/**
		 * Override if multiple {@link CleverDevicesDbHelper} in same app.
		 */
		public static int getDbVersion(Context context) {
			if (dbVersion < 0) {
				dbVersion = context.getResources().getInteger(R.integer.clever_devices_db_version);
			}
			return dbVersion;
		}

		public CleverDevicesDbHelper(Context context) {
			super(context, DB_NAME, null, getDbVersion(context));
		}

		@Override
		public void onCreateMT(SQLiteDatabase db) {
			initAllDbTables(db);
		}

		@Override
		public void onUpgradeMT(SQLiteDatabase db, int oldVersion, int newVersion) {
			db.execSQL(T_CLEVER_DEVICES_STATUS_SQL_DROP);
			initAllDbTables(db);
		}

		public boolean isDbExist(Context context) {
			return SqlUtils.isDbExist(context, DB_NAME);
		}

		private void initAllDbTables(SQLiteDatabase db) {
			db.execSQL(T_CLEVER_DEVICES_STATUS_SQL_CREATE);
		}
	}
}
