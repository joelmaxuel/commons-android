package org.mtransit.android.commons.provider;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.mtransit.android.commons.ArrayUtils;
import org.mtransit.android.commons.CleanUtils;
import org.mtransit.android.commons.CollectionUtils;
import org.mtransit.android.commons.FileUtils;
import org.mtransit.android.commons.HtmlUtils;
import org.mtransit.android.commons.LocaleUtils;
import org.mtransit.android.commons.MTLog;
import org.mtransit.android.commons.PackageManagerUtils;
import org.mtransit.android.commons.PreferenceUtils;
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
import org.mtransit.android.commons.data.ServiceUpdate;
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
import android.support.annotation.Nullable;
import android.text.Html;
import android.text.TextUtils;

@SuppressLint("Registered")
public class OCTranspoProvider extends MTContentProvider implements StatusProviderContract, ServiceUpdateProviderContract {

	private static final String LOG_TAG = OCTranspoProvider.class.getSimpleName();

	@Override
	public String getLogTag() {
		return LOG_TAG;
	}

	/**
	 * Override if multiple {@link OCTranspoProvider} implementations in same app.
	 */
	private static final String PREF_KEY_AGENCY_SERVICE_UPDATE_LAST_UPDATE_MS = OCTranspoDbHelper.PREF_KEY_AGENCY_SERVICE_UPDATE_LAST_UPDATE_MS;

	@NonNull
	public static UriMatcher getNewUriMatcher(String authority) {
		UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
		StatusProvider.append(URI_MATCHER, authority);
		ServiceUpdateProvider.append(URI_MATCHER, authority);
		return URI_MATCHER;
	}

	@Nullable
	private static UriMatcher uriMatcher = null;

	/**
	 * Override if multiple {@link OCTranspoProvider} implementations in same app.
	 */
	@NonNull
	private static UriMatcher getURIMATCHER(Context context) {
		if (uriMatcher == null) {
			uriMatcher = getNewUriMatcher(getAUTHORITY(context));
		}
		return uriMatcher;
	}

	@Nullable
	private static String authority = null;

	/**
	 * Override if multiple {@link OCTranspoProvider} implementations in same app.
	 */
	@NonNull
	private static String getAUTHORITY(Context context) {
		if (authority == null) {
			authority = context.getResources().getString(R.string.oc_transpo_authority);
		}
		return authority;
	}

	@Nullable
	private static Uri authorityUri = null;

	/**
	 * Override if multiple {@link OCTranspoProvider} implementations in same app.
	 */
	@NonNull
	private static Uri getAUTHORITY_URI(Context context) {
		if (authorityUri == null) {
			authorityUri = UriUtils.newContentUri(getAUTHORITY(context));
		}
		return authorityUri;
	}

	@Nullable
	private static String serviceUpdateTargetAuthority = null;

	/**
	 * Override if multiple {@link OCTranspoProvider} implementations in same app.
	 */
	@NonNull
	private static String getSERVICE_UPDATE_TARGET_AUTHORITY(Context context) {
		if (serviceUpdateTargetAuthority == null) {
			serviceUpdateTargetAuthority = context.getResources().getString(R.string.oc_transpo_service_update_for_poi_authority);
		}
		return serviceUpdateTargetAuthority;
	}

	@Nullable
	private static String appId = null;

	/**
	 * Override if multiple {@link OCTranspoProvider} implementations in same app.
	 */
	@NonNull
	private static String getAPP_ID(Context context) {
		if (appId == null) {
			appId = context.getResources().getString(R.string.oc_transpo_app_id);
		}
		return appId;
	}

	@Nullable
	private static String apiKey = null;

	/**
	 * Override if multiple {@link OCTranspoProvider} implementations in same app.
	 */
	@NonNull
	private static String getAPI_KEY(Context context) {
		if (apiKey == null) {
			apiKey = context.getResources().getString(R.string.oc_transpo_api_key);
		}
		return apiKey;
	}

	private static final long LIVE_NEXT_BUS_ARRIVAL_DATA_FEED_STATUS_MAX_VALIDITY_IN_MS = TimeUnit.HOURS.toMillis(1L);
	private static final long LIVE_NEXT_BUS_ARRIVAL_DATA_FEED_STATUS_VALIDITY_IN_MS = TimeUnit.MINUTES.toMillis(10L);
	private static final long LIVE_NEXT_BUS_ARRIVAL_DATA_FEED_STATUS_VALIDITY_IN_FOCUS_IN_MS = TimeUnit.MINUTES.toMillis(1L);
	private static final long LIVE_NEXT_BUS_ARRIVAL_DATA_FEED_STATUS_MIN_DURATION_BETWEEN_REFRESH_IN_MS = TimeUnit.MINUTES.toMillis(1L);
	private static final long LIVE_NEXT_BUS_ARRIVAL_DATA_FEED_STATUS_MIN_DURATION_BETWEEN_REFRESH_IN_FOCUS_IN_MS = TimeUnit.MINUTES.toMillis(1L);

	@Override
	public long getStatusMaxValidityInMs() {
		return LIVE_NEXT_BUS_ARRIVAL_DATA_FEED_STATUS_MAX_VALIDITY_IN_MS;
	}

	@Override
	public long getStatusValidityInMs(boolean inFocus) {
		if (inFocus) {
			return LIVE_NEXT_BUS_ARRIVAL_DATA_FEED_STATUS_VALIDITY_IN_FOCUS_IN_MS;
		}
		return LIVE_NEXT_BUS_ARRIVAL_DATA_FEED_STATUS_VALIDITY_IN_MS;
	}

	@Override
	public long getMinDurationBetweenRefreshInMs(boolean inFocus) {
		if (inFocus) {
			return LIVE_NEXT_BUS_ARRIVAL_DATA_FEED_STATUS_MIN_DURATION_BETWEEN_REFRESH_IN_FOCUS_IN_MS;
		}
		return LIVE_NEXT_BUS_ARRIVAL_DATA_FEED_STATUS_MIN_DURATION_BETWEEN_REFRESH_IN_MS;
	}

	@Override
	public void cacheStatus(POIStatus newStatusToCache) {
		StatusProvider.cacheStatusS(this, newStatusToCache);
	}

	@Nullable
	@Override
	public POIStatus getCachedStatus(StatusProviderContract.Filter statusFilter) {
		if (!(statusFilter instanceof Schedule.ScheduleStatusFilter)) {
			return null;
		}
		Schedule.ScheduleStatusFilter scheduleStatusFilter = (Schedule.ScheduleStatusFilter) statusFilter;
		RouteTripStop rts = scheduleStatusFilter.getRouteTripStop();
		POIStatus status = StatusProvider.getCachedStatusS(this, rts.getUUID());
		if (status != null) {
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
		return OCTranspoDbHelper.T_LIVE_NEXT_BUS_ARRIVAL_DATA_FEED_STATUS;
	}

	@Override
	public int getStatusType() {
		return POI.ITEM_STATUS_TYPE_SCHEDULE;
	}

	@Nullable
	@Override
	public POIStatus getNewStatus(StatusProviderContract.Filter statusFilter) {
		if (statusFilter == null || !(statusFilter instanceof Schedule.ScheduleStatusFilter)) {
			return null;
		}
		Schedule.ScheduleStatusFilter scheduleStatusFilter = (Schedule.ScheduleStatusFilter) statusFilter;
		RouteTripStop rts = scheduleStatusFilter.getRouteTripStop();
		loadPredictionsFromWWW(rts);
		return getCachedStatus(statusFilter);
	}

	private static final String GET_NEXT_TRIPS_FOR_STOP_URL = "https://api.octranspo1.com/v1.2/GetNextTripsForStop";

	private static final String URL_POST_PARAM_APP_ID = "appID";
	private static final String URL_POST_PARAM_APP_KEY = "apiKey";
	private static final String URL_POST_PARAM_ROUTE_NUMBER = "routeNo";
	private static final String URL_POST_PARAM_STOP_NUMBER = "stopNo";

	private static String getPostParameters(Context context, RouteTripStop rts) {
		return new StringBuilder() //
				.append(URL_POST_PARAM_APP_ID).append(HtmlUtils.URL_PARAM_EQ).append(getAPP_ID(context)) //
				.append(HtmlUtils.URL_PARAM_AND) //
				.append(URL_POST_PARAM_APP_KEY).append(HtmlUtils.URL_PARAM_EQ).append(getAPI_KEY(context)) //
				.append(HtmlUtils.URL_PARAM_AND) //
				.append(URL_POST_PARAM_ROUTE_NUMBER).append(HtmlUtils.URL_PARAM_EQ).append(rts.getRoute().getId()) //
				.append(HtmlUtils.URL_PARAM_AND) //
				.append(URL_POST_PARAM_STOP_NUMBER).append(HtmlUtils.URL_PARAM_EQ).append(rts.getStop().getId()) //
				.toString();
	}

	private void loadPredictionsFromWWW(RouteTripStop rts) {
		try {
			String urlString = GET_NEXT_TRIPS_FOR_STOP_URL;
			String postParams = getPostParameters(getContext(), rts);
			URL url = new URL(urlString);
			URLConnection urlc = url.openConnection();
			urlc.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			HttpsURLConnection httpUrlConnection = (HttpsURLConnection) urlc;
			try {
				httpUrlConnection.setDoOutput(true);
				httpUrlConnection.setChunkedStreamingMode(0);
				OutputStream os = httpUrlConnection.getOutputStream();
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, FileUtils.UTF_8));
				writer.write(postParams);
				writer.flush();
				writer.close();
				os.close();
				httpUrlConnection.connect();
				long newLastUpdateInMs = TimeUtils.currentTimeMillis();
				SAXParserFactory spf = SAXParserFactory.newInstance();
				SAXParser sp = spf.newSAXParser();
				XMLReader xr = sp.getXMLReader();
				OCTranspoGetNextTripsForStopDataHandler handler = new OCTranspoGetNextTripsForStopDataHandler(this, newLastUpdateInMs, rts);
				xr.setContentHandler(handler);
				xr.parse(new InputSource(httpUrlConnection.getInputStream()));
				Collection<POIStatus> statuses = handler.getStatuses();
				StatusProvider.deleteCachedStatus(this, ArrayUtils.asArrayList(rts.getUUID()));
				for (POIStatus status : statuses) {
					StatusProvider.cacheStatusS(this, status);
				}
			} catch (Exception e) {
				MTLog.w(this, e, "Error while posting query!");
			} finally {
				httpUrlConnection.disconnect();
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

	private static final long SERVICE_UPDATE_MAX_VALIDITY_IN_MS = TimeUnit.DAYS.toMillis(1L);
	private static final long SERVICE_UPDATE_VALIDITY_IN_MS = TimeUnit.HOURS.toMillis(1L);
	private static final long SERVICE_UPDATE_VALIDITY_IN_FOCUS_IN_MS = TimeUnit.MINUTES.toMillis(10L);
	private static final long SERVICE_UPDATE_MIN_DURATION_BETWEEN_REFRESH_IN_MS = TimeUnit.MINUTES.toMillis(10L);
	private static final long SERVICE_UPDATE_MIN_DURATION_BETWEEN_REFRESH_IN_FOCUS_IN_MS = TimeUnit.MINUTES.toMillis(1L);

	@Override
	public long getMinDurationBetweenServiceUpdateRefreshInMs(boolean inFocus) {
		if (inFocus) {
			return SERVICE_UPDATE_MIN_DURATION_BETWEEN_REFRESH_IN_FOCUS_IN_MS;
		}
		return SERVICE_UPDATE_MIN_DURATION_BETWEEN_REFRESH_IN_MS;
	}

	@Override
	public long getServiceUpdateMaxValidityInMs() {
		return SERVICE_UPDATE_MAX_VALIDITY_IN_MS;
	}

	@Override
	public long getServiceUpdateValidityInMs(boolean inFocus) {
		if (inFocus) {
			return SERVICE_UPDATE_VALIDITY_IN_FOCUS_IN_MS;
		}
		return SERVICE_UPDATE_VALIDITY_IN_MS;
	}

	@Override
	public String getServiceUpdateDbTableName() {
		return OCTranspoDbHelper.T_OC_TRANSPO_SERVICE_UPDATE;
	}

	@Override
	public void cacheServiceUpdates(ArrayList<ServiceUpdate> newServiceUpdates) {
		ServiceUpdateProvider.cacheServiceUpdatesS(this, newServiceUpdates);
	}

	@Override
	public ArrayList<ServiceUpdate> getCachedServiceUpdates(ServiceUpdateProviderContract.Filter serviceUpdateFilter) {
		if (serviceUpdateFilter.getPoi() == null || !(serviceUpdateFilter.getPoi() instanceof RouteTripStop)) {
			MTLog.w(this, "getCachedServiceUpdates() > no service update (poi null or not RTS)");
			return null;
		}
		RouteTripStop rts = (RouteTripStop) serviceUpdateFilter.getPoi();
		ArrayList<ServiceUpdate> serviceUpdates = ServiceUpdateProvider.getCachedServiceUpdatesS(this, getTargetUUIDs(rts));
		enhanceRTServiceUpdateForStop(serviceUpdates, rts);
		return serviceUpdates;
	}

	private void enhanceRTServiceUpdateForStop(ArrayList<ServiceUpdate> serviceUpdates, RouteTripStop rts) {
		try {
			if (CollectionUtils.getSize(serviceUpdates) > 0) {
				for (ServiceUpdate serviceUpdate : serviceUpdates) {
					serviceUpdate.setTargetUUID(rts.getUUID()); // route trip service update targets stop
					enhanceRTServiceUpdateForStop(serviceUpdate, rts);
				}
			}
		} catch (Exception e) {
			MTLog.w(this, e, "Error while trying to enhance route trip service update for stop!");
		}
	}

	private static final String CLEAN_THAT_STOP_CODE = "(#%s \\-\\- [^\\<]*)";
	private static final String CLEAN_THAT_STOP_CODE_REPLACEMENT = HtmlUtils.applyBold("$1");

	private void enhanceRTServiceUpdateForStop(ServiceUpdate serviceUpdate, RouteTripStop rts) {
		try {
			if (serviceUpdate.getText().contains(rts.getStop().getCode())) {
				if (ServiceUpdate.isSeverityWarning(serviceUpdate.getSeverity())) {
					serviceUpdate.setSeverity(ServiceUpdate.SEVERITY_WARNING_POI);
				} else {
					serviceUpdate.setSeverity(ServiceUpdate.SEVERITY_INFO_POI);
				}
				serviceUpdate.setTextHTML(Pattern.compile(String.format(CLEAN_THAT_STOP_CODE, rts.getStop().getCode())).matcher(serviceUpdate.getTextHTML())
						.replaceAll(CLEAN_THAT_STOP_CODE_REPLACEMENT));
			}
		} catch (Exception e) {
			MTLog.w(this, e, "Error while trying to enhance route trip service update '%s' for stop!", serviceUpdate);
		}
	}

	private HashSet<String> getTargetUUIDs(RouteTripStop rts) {
		HashSet<String> targetUUIDs = new HashSet<String>();
		targetUUIDs.add(getAgencyTargetUUID(rts.getAuthority()));
		targetUUIDs.add(getAgencyRouteShortNameTargetUUID(rts.getAuthority(), rts.getRoute().getShortName()));
		return targetUUIDs;
	}

	protected static String getAgencyRouteShortNameTargetUUID(String agencyAuthority, String routeShortName) {
		return POI.POIUtils.getUUID(agencyAuthority, routeShortName);
	}

	protected static String getAgencyTargetUUID(String agencyAuthority) {
		return POI.POIUtils.getUUID(agencyAuthority);
	}

	@Override
	public boolean purgeUselessCachedServiceUpdates() {
		return ServiceUpdateProvider.purgeUselessCachedServiceUpdates(this);
	}

	@Override
	public boolean deleteCachedServiceUpdate(Integer serviceUpdateId) {
		return ServiceUpdateProvider.deleteCachedServiceUpdate(this, serviceUpdateId);
	}

	@Override
	public boolean deleteCachedServiceUpdate(String targetUUID, String sourceId) {
		return ServiceUpdateProvider.deleteCachedServiceUpdate(this, targetUUID, sourceId);
	}

	private int deleteAllAgencyServiceUpdateData() {
		int affectedRows = 0;
		try {
			String selection = SqlUtils.getWhereEqualsString(ServiceUpdateProviderContract.Columns.T_SERVICE_UPDATE_K_SOURCE_ID, AGENCY_SOURCE_ID);
			affectedRows = getDBHelper().getWritableDatabase().delete(getServiceUpdateDbTableName(), selection, null);
		} catch (Exception e) {
			MTLog.w(this, e, "Error while deleting all agency service update data!");
		}
		return affectedRows;
	}

	@Override
	public ArrayList<ServiceUpdate> getNewServiceUpdates(ServiceUpdateProviderContract.Filter serviceUpdateFilter) {
		if (serviceUpdateFilter == null || serviceUpdateFilter.getPoi() == null || !(serviceUpdateFilter.getPoi() instanceof RouteTripStop)) {
			MTLog.w(this, "getNewServiceUpdates() > no new service update (filter null or poi null or not RTS): %s", serviceUpdateFilter);
			return null;
		}
		RouteTripStop rts = (RouteTripStop) serviceUpdateFilter.getPoi();
		updateAgencyServiceUpdateDataIfRequired(rts.getAuthority(), serviceUpdateFilter.isInFocusOrDefault());
		ArrayList<ServiceUpdate> cachedServiceUpdates = getCachedServiceUpdates(serviceUpdateFilter);
		if (CollectionUtils.getSize(cachedServiceUpdates) == 0) {
			String agencyTargetUUID = getAgencyTargetUUID(getSERVICE_UPDATE_TARGET_AUTHORITY(getContext()));
			cachedServiceUpdates = ArrayUtils.asArrayList(getServiceUpdateNone(agencyTargetUUID));
			enhanceRTServiceUpdateForStop(cachedServiceUpdates, rts); // convert to stop service update
		}
		return cachedServiceUpdates;
	}

	public ServiceUpdate getServiceUpdateNone(String agencyTargetUUID) {
		return new ServiceUpdate(null, agencyTargetUUID, TimeUtils.currentTimeMillis(), getServiceUpdateMaxValidityInMs(), null, null,
				ServiceUpdate.SEVERITY_NONE, AGENCY_SOURCE_ID, AGENCY_SOURCE_LABEL, getServiceUpdateLanguage());
	}

	@Override
	public String getServiceUpdateLanguage() {
		return LocaleUtils.isFR() ? Locale.FRENCH.getLanguage() : Locale.ENGLISH.getLanguage();
	}

	private static final String AGENCY_SOURCE_ID = "octranspo1_com_feeds_updates";

	private static final String AGENCY_SOURCE_LABEL = "octranspo1.com";

	private void updateAgencyServiceUpdateDataIfRequired(String tagetAuthority, boolean inFocus) {
		long lastUpdateInMs = PreferenceUtils.getPrefLcl(getContext(), PREF_KEY_AGENCY_SERVICE_UPDATE_LAST_UPDATE_MS, 0L);
		long minUpdateMs = Math.min(getServiceUpdateMaxValidityInMs(), getServiceUpdateValidityInMs(inFocus));
		long nowInMs = TimeUtils.currentTimeMillis();
		if (lastUpdateInMs + minUpdateMs > nowInMs) {
			return;
		}
		updateAgencyServiceUpdateDataIfRequiredSync(tagetAuthority, lastUpdateInMs, inFocus);
	}

	private synchronized void updateAgencyServiceUpdateDataIfRequiredSync(String tagetAuthority, long lastUpdateInMs, boolean inFocus) {
		if (PreferenceUtils.getPrefLcl(getContext(), PREF_KEY_AGENCY_SERVICE_UPDATE_LAST_UPDATE_MS, 0L) > lastUpdateInMs) {
			return; // too late, another thread already updated
		}
		long nowInMs = TimeUtils.currentTimeMillis();
		boolean deleteAllRequired = false;
		if (lastUpdateInMs + getServiceUpdateMaxValidityInMs() < nowInMs) {
			deleteAllRequired = true; // too old to display
		}
		long minUpdateMs = Math.min(getServiceUpdateMaxValidityInMs(), getServiceUpdateValidityInMs(inFocus));
		if (deleteAllRequired || lastUpdateInMs + minUpdateMs < nowInMs) {
			updateAllAgencyServiceUpdateDataFromWWW(tagetAuthority, deleteAllRequired); // try to update
		}
	}

	private void updateAllAgencyServiceUpdateDataFromWWW(String tagetAuthority, boolean deleteAllRequired) {
		boolean deleteAllDone = false;
		if (deleteAllRequired) {
			deleteAllAgencyServiceUpdateData();
			deleteAllDone = true;
		}
		ArrayList<ServiceUpdate> newServiceUpdates = loadAgencyServiceUpdateDataFromWWW(tagetAuthority);
		if (newServiceUpdates != null) { // empty is OK
			long nowInMs = TimeUtils.currentTimeMillis();
			if (!deleteAllDone) {
				deleteAllAgencyServiceUpdateData();
			}
			cacheServiceUpdates(newServiceUpdates);
			PreferenceUtils.savePrefLcl(getContext(), PREF_KEY_AGENCY_SERVICE_UPDATE_LAST_UPDATE_MS, nowInMs, true); // sync
		} // else keep whatever we have until max validity reached
	}

	private static final String AGENCY_URL_PART_1_BEFORE_LANG = "http://octranspo1.com/feeds/updates-";
	private static final String AGENCY_URL_PART_2_AFTER_LANG = "/";
	private static final String AGENCY_URL_LANG_DEFAULT = "en";
	private static final String AGENCY_URL_LANG_FRENCH = "fr";

	private static String getAgencyUrlString() {
		return new StringBuilder() //
				.append(AGENCY_URL_PART_1_BEFORE_LANG) //
				.append(LocaleUtils.isFR() ? AGENCY_URL_LANG_FRENCH : AGENCY_URL_LANG_DEFAULT) // language
				.append(AGENCY_URL_PART_2_AFTER_LANG) //
				.toString();
	}

	private ArrayList<ServiceUpdate> loadAgencyServiceUpdateDataFromWWW(String tagetAuthority) {
		try {
			String urlString = getAgencyUrlString();
			URL url = new URL(urlString);
			URLConnection urlc = url.openConnection();
			HttpURLConnection httpUrlConnection = (HttpURLConnection) urlc;
			switch (httpUrlConnection.getResponseCode()) {
			case HttpURLConnection.HTTP_OK:
				long newLastUpdateInMs = TimeUtils.currentTimeMillis();
				SAXParserFactory spf = SAXParserFactory.newInstance();
				SAXParser sp = spf.newSAXParser();
				XMLReader xr = sp.getXMLReader();
				OCTranspoFeedsUpdatesDataHandler handler = //
						new OCTranspoFeedsUpdatesDataHandler(getSERVICE_UPDATE_TARGET_AUTHORITY(getContext()), newLastUpdateInMs,
								getServiceUpdateMaxValidityInMs(), getServiceUpdateLanguage());
				xr.setContentHandler(handler);
				xr.parse(new InputSource(urlc.getInputStream()));
				return handler.getServiceUpdates();
			default:
				MTLog.w(this, "ERROR: HTTP URL-Connection Response Code %s (Message: %s)", httpUrlConnection.getResponseCode(),
						httpUrlConnection.getResponseMessage());
				return null;
			}
		} catch (UnknownHostException uhe) {
			if (MTLog.isLoggable(android.util.Log.DEBUG)) {
				MTLog.w(this, uhe, "No Internet Connection!");
			} else {
				MTLog.w(this, "No Internet Connection!");
			}
			return null;
		} catch (SocketException se) {
			MTLog.w(LOG_TAG, se, "No Internet Connection!");
			return null;
		} catch (Exception e) {
			MTLog.e(LOG_TAG, e, "INTERNAL ERROR: Unknown Exception");
			return null;
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

	private OCTranspoDbHelper dbHelper;

	private static int currentDbVersion = -1;

	private OCTranspoDbHelper getDBHelper(Context context) {
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
	 * Override if multiple {@link OCTranspoProvider} implementations in same app.
	 */
	public int getCurrentDbVersion() {
		return OCTranspoDbHelper.getDbVersion(getContext());
	}

	/**
	 * Override if multiple {@link OCTranspoProvider} implementations in same app.
	 */
	public OCTranspoDbHelper getNewDbHelper(Context context) {
		return new OCTranspoDbHelper(context.getApplicationContext());
	}

	@NonNull
	@Override
	public UriMatcher getURI_MATCHER() {
		return getURIMATCHER(getContext());
	}

	@NonNull
	@Override
	public Uri getAuthorityUri() {
		return getAUTHORITY_URI(getContext());
	}

	@NonNull
	@Override
	public SQLiteOpenHelper getDBHelper() {
		return getDBHelper(getContext());
	}

	@Override
	public Cursor queryMT(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		Cursor cursor = StatusProvider.queryS(this, uri, selection);
		if (cursor != null) {
			return cursor;
		}
		cursor = ServiceUpdateProvider.queryS(this, uri, selection);
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
		type = ServiceUpdateProvider.getTypeS(this, uri);
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

	private static class OCTranspoFeedsUpdatesDataHandler extends MTDefaultHandler {

		private static final String LOG_TAG = OCTranspoProvider.LOG_TAG + ">" + OCTranspoFeedsUpdatesDataHandler.class.getSimpleName();

		@Override
		public String getLogTag() {
			return LOG_TAG;
		}

		private static final String RSS = "rss";
		private static final String TITLE = "title";
		private static final String LINK = "link";
		private static final String DESCRIPTION = "description";
		private static final String ATOM_LINK = "atom:link";
		private static final String ITEM = "item";
		private static final String PUBLICATION_DATE = "pubDate";
		private static final String CATEGORY = "category";
		private static final String GUID = "guid";

		private String currentLocalName = RSS;
		private boolean currentItem = false;
		private StringBuilder currentTitleSb = new StringBuilder();
		private String currentCategory1;
		private String currentCategory2;
		private StringBuilder currentLinkSb = new StringBuilder();
		private StringBuilder currentDescriptionSb = new StringBuilder();

		private ArrayList<ServiceUpdate> serviceUpdates = new ArrayList<ServiceUpdate>();

		private String targetAuthority;
		private long newLastUpdateInMs;
		private long serviceUpdateMaxValidityInMs;
		private String language;

		public OCTranspoFeedsUpdatesDataHandler(String targetAuthority, long newLastUpdateInMs, long serviceUpdateMaxValidityInMs, String language) {
			this.targetAuthority = targetAuthority;
			this.newLastUpdateInMs = newLastUpdateInMs;
			this.serviceUpdateMaxValidityInMs = serviceUpdateMaxValidityInMs;
			this.language = language;
		}

		public ArrayList<ServiceUpdate> getServiceUpdates() {
			return this.serviceUpdates;
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
			super.startElement(uri, localName, qName, attributes);
			this.currentLocalName = localName;
			if (ITEM.equals(this.currentLocalName)) {
				this.currentItem = true;
				this.currentTitleSb.setLength(0); // reset
				this.currentCategory1 = null; // reset
				this.currentCategory2 = null; // reset
				this.currentLinkSb.setLength(0); // reset
				this.currentDescriptionSb.setLength(0); // reset
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
				if (this.currentItem) {
					if (TITLE.equals(this.currentLocalName)) {
						this.currentTitleSb.append(string);
					} else if (PUBLICATION_DATE.equals(this.currentLocalName)) { // ignore
					} else if (CATEGORY.equals(this.currentLocalName)) {
						if (!TextUtils.isEmpty(string.trim())) {
							if (TextUtils.isEmpty(this.currentCategory1)) {
								this.currentCategory1 = string;
							} else {
								this.currentCategory2 = string;
							}
						}
					} else if (LINK.equals(this.currentLocalName)) {
						this.currentLinkSb.append(string);
					} else if (DESCRIPTION.equals(this.currentLocalName)) {
						this.currentDescriptionSb.append(string);
					} else if (GUID.equals(this.currentLocalName)) { // ignore
					} else {
						MTLog.w(this, "characters() > Unexpected item element '%s'", this.currentLocalName);
					}
				} else if (TITLE.equals(this.currentLocalName)) { // ignore
				} else if (LINK.equals(this.currentLocalName)) { // ignore
				} else if (DESCRIPTION.equals(this.currentLocalName)) { // ignore
				} else if (ATOM_LINK.equals(this.currentLocalName)) { // ignore
				} else {
					MTLog.w(this, "characters() > Unexpected element '%s'", this.currentLocalName);
				}
			} catch (Exception e) {
				MTLog.w(this, e, "Error while parsing '%s' value '%s, %s, %s'!", this.currentLocalName, ch, start, length);
			}
		}

		private static final String DETOURS = "Detours";
		private static final String DELAYS = "Delays";
		private static final String CANCELLED_TRIPS = "Cancelled trips";
		private static final String TODAYS_SERVICE = "Todays Service";
		private static final String ROUTE_SERVICE_CHANGE = "Route Service Change";
		private static final String GENERAL_SERVICE_CHANGE = "General service change";
		private static final String GENERAL_MESSAGE = "General Message"; // Escalator / Elevator
		private static final String OTHER = "Other";

		private static final String AFFECTED_ROUTES_START_WITH = "affectedRoutes-";

		private static final Pattern EXTRACT_NUMBERS_REGEX = Pattern.compile("[\\d]+");

		private static final String COLON = ": ";

		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException {
			super.endElement(uri, localName, qName);
			try {
				if (ITEM.equals(localName)) {
					String title = this.currentTitleSb.toString().trim();
					String desc = this.currentDescriptionSb.toString().trim();
					desc = HtmlUtils.fixTextViewBR(desc);
					String link = this.currentLinkSb.toString().trim();
					String text = title + COLON + Html.fromHtml(desc);
					String textHtml = HtmlUtils.applyBold(title) + HtmlUtils.BR + desc + HtmlUtils.BR + HtmlUtils.linkify(link);
					HashSet<String> routeShortNames = extractRouteShortNames(this.currentCategory2);
					int severity = extractSeverity(this.currentCategory1, routeShortNames);
					if (CollectionUtils.getSize(routeShortNames) == 0) { // AGENCY
						String targetUUID = OCTranspoProvider.getAgencyTargetUUID(this.targetAuthority);
						ServiceUpdate serviceUpdate = //
								new ServiceUpdate(null, targetUUID, this.newLastUpdateInMs, this.serviceUpdateMaxValidityInMs, text, textHtml, severity,
										AGENCY_SOURCE_ID, AGENCY_SOURCE_LABEL, this.language);
						this.serviceUpdates.add(serviceUpdate);
					} else { // AGENCY ROUTE
						for (String routeShortName : routeShortNames) {
							String targetUUID = OCTranspoProvider.getAgencyRouteShortNameTargetUUID(this.targetAuthority, routeShortName);
							ServiceUpdate serviceUpdate = //
									new ServiceUpdate(null, targetUUID, this.newLastUpdateInMs, this.serviceUpdateMaxValidityInMs, text, textHtml, severity,
											AGENCY_SOURCE_ID, AGENCY_SOURCE_LABEL, this.language);
							this.serviceUpdates.add(serviceUpdate);
						}
					}
					this.currentItem = false;
				}
			} catch (Exception e) {
				MTLog.w(this, e, "Error while parsing '%s' end element!", this.currentLocalName);
			}
		}

		@NonNull
		private static HashSet<String> extractRouteShortNames(String category) {
			HashSet<String> routeShortNames = new HashSet<String>();
			if (category.startsWith(AFFECTED_ROUTES_START_WITH)) {
				Matcher matcher = EXTRACT_NUMBERS_REGEX.matcher(category);
				while (matcher.find()) {
					routeShortNames.add(matcher.group());
				}
			}
			return routeShortNames;
		}

		private static int extractSeverity(String category, HashSet<String> routeShortNames) {
			int severity = ServiceUpdate.SEVERITY_INFO_UNKNOWN;
			if (CANCELLED_TRIPS.equals(category)) {
				if (routeShortNames.size() > 0) {
					severity = ServiceUpdate.SEVERITY_WARNING_RELATED_POI;
				} else {
					severity = ServiceUpdate.SEVERITY_NONE; // too general
				}
			} else if (DELAYS.equals(category)) {
				if (routeShortNames.size() > 0) {
					severity = ServiceUpdate.SEVERITY_INFO_RELATED_POI;
				} else {
					severity = ServiceUpdate.SEVERITY_NONE; // too general
				}
			} else if (DETOURS.equals(category)) {
				if (routeShortNames.size() > 0) {
					severity = ServiceUpdate.SEVERITY_INFO_RELATED_POI;
				} else {
					severity = ServiceUpdate.SEVERITY_NONE; // too general
				}
			} else if (GENERAL_SERVICE_CHANGE.equals(category)) {
				if (routeShortNames.size() > 0) {
					severity = ServiceUpdate.SEVERITY_INFO_RELATED_POI;
				} else {
					severity = ServiceUpdate.SEVERITY_NONE; // too general
				}
			} else if (ROUTE_SERVICE_CHANGE.equals(category)) {
				if (routeShortNames.size() > 0) {
					severity = ServiceUpdate.SEVERITY_INFO_RELATED_POI;
				} else {
					severity = ServiceUpdate.SEVERITY_NONE; // too general
				}
			} else if (OTHER.equals(category)) {
				if (routeShortNames.size() > 0) {
					severity = ServiceUpdate.SEVERITY_INFO_RELATED_POI;
				} else {
					severity = ServiceUpdate.SEVERITY_NONE; // too general
				}
			} else if (GENERAL_MESSAGE.equals(category)) {
				if (routeShortNames.size() > 0) {
					severity = ServiceUpdate.SEVERITY_INFO_RELATED_POI;
				} else {
					severity = ServiceUpdate.SEVERITY_NONE; // too general
				}
			} else if (TODAYS_SERVICE.equals(category)) {
				severity = ServiceUpdate.SEVERITY_NONE; // not shown on http://www.octranspo1.com/updates
			}
			return severity;
		}
	}

	private static class OCTranspoGetNextTripsForStopDataHandler extends MTDefaultHandler {

		private static final String LOG_TAG = OCTranspoProvider.LOG_TAG + ">" + OCTranspoGetNextTripsForStopDataHandler.class.getSimpleName();

		@Override
		public String getLogTag() {
			return LOG_TAG;
		}

		private static final String SOAP_ENVELOPE = "soap:Envelope";
		private static final String ROUTE_DIRECTION = "RouteDirection";
		private static final String ROUTE_LABEL = "RouteLabel";
		private static final String REQUEST_PROCESSING_TIME = "RequestProcessingTime";
		private static final String TRIP_DESTINATION = "TripDestination";
		private static final String ADJUSTED_SCHEDULE_TIME = "AdjustedScheduleTime"; // minutes until departure

		private static long PROVIDER_PRECISION_IN_MS = TimeUnit.SECONDS.toMillis(10L);

		private String currentLocalName = SOAP_ENVELOPE;
		private OCTranspoProvider provider;
		private long lastUpdateInMs;
		private RouteTripStop rts;

		private HashSet<POIStatus> statuses = new HashSet<POIStatus>();
		private String currentRouteLabel;
		private String currentRequestProcessingTime;
		private ArrayList<String> currentAdjustedScheduleTimes = new ArrayList<String>();
		private ArrayList<String> currentTripDestinations = new ArrayList<String>();

		private static final String DATE_FORMAT_PATTERN = "yyyyMMddHHmmss";
		private static final String TIME_ZONE = "America/Montreal";
		private static ThreadSafeDateFormatter dateFormat;

		public static ThreadSafeDateFormatter getDateFormat(Context context) {
			if (dateFormat == null) {
				dateFormat = new ThreadSafeDateFormatter(DATE_FORMAT_PATTERN);
				dateFormat.setTimeZone(TimeZone.getTimeZone(TIME_ZONE));
			}
			return dateFormat;
		}

		public OCTranspoGetNextTripsForStopDataHandler(OCTranspoProvider provider, long lastUpdateInMs, RouteTripStop rts) {
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
			if (ROUTE_DIRECTION.equals(this.currentLocalName)) {
				this.currentRouteLabel = null; // reset
				this.currentRequestProcessingTime = null; // reset
				this.currentAdjustedScheduleTimes.clear();
				this.currentTripDestinations.clear();
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
				if (ROUTE_LABEL.equals(this.currentLocalName)) {
					this.currentRouteLabel = string;
				} else if (REQUEST_PROCESSING_TIME.equals(this.currentLocalName)) {
					this.currentRequestProcessingTime = string;
				} else if (ADJUSTED_SCHEDULE_TIME.equals(this.currentLocalName)) {
					this.currentAdjustedScheduleTimes.add(string);
				} else if (TRIP_DESTINATION.equals(this.currentLocalName)) {
					this.currentTripDestinations.add(string);
				}
			} catch (Exception e) {
				MTLog.w(this, e, "Error while parsing '%s' value '%s, %s, %s'!", this.currentLocalName, ch, start, length);
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException {
			super.endElement(uri, localName, qName);
			if (ROUTE_DIRECTION.equals(localName)) {
				String heading = this.provider.getContext() == null ? //
						this.rts.getTrip().getHeading() : this.rts.getTrip().getHeading(this.provider.getContext());
				if (!StringUtils.equals(heading, this.currentRouteLabel)) {
					return;
				}
				try {
					Schedule schedule = new Schedule(this.rts.getUUID(), this.lastUpdateInMs, this.provider.getStatusMaxValidityInMs(), this.lastUpdateInMs,
							PROVIDER_PRECISION_IN_MS, false);
					long requestProcessingTimeInMs = getDateFormat(this.provider.getContext()).parseThreadSafe(this.currentRequestProcessingTime).getTime();
					requestProcessingTimeInMs = TimeUtils.timeToTheTensSecondsMillis(requestProcessingTimeInMs);
					boolean tripDestinationsUsable = this.currentTripDestinations.size() == this.currentAdjustedScheduleTimes.size();
					HashSet<String> processedTrips = new HashSet<String>();
					for (int i = 0; i < this.currentAdjustedScheduleTimes.size(); i++) {
						String adjustedScheduleTime = this.currentAdjustedScheduleTimes.get(i);
						long t = requestProcessingTimeInMs + TimeUnit.MINUTES.toMillis(Long.parseLong(adjustedScheduleTime));
						Schedule.Timestamp newTimestamp = new Schedule.Timestamp(t);
						try {
							if (tripDestinationsUsable) {
								String tripDestination = this.currentTripDestinations.get(i);
								if (!TextUtils.isEmpty(tripDestination)) {
									newTimestamp.setHeadsign(Trip.HEADSIGN_TYPE_STRING, cleanTripHeadsign(tripDestination, heading));
								}
							}
						} catch (Exception e) {
							MTLog.w(this, e, "Error while adding trip destination %s!", this.currentTripDestinations);
						}
						if (processedTrips.contains(newTimestamp.toString())) {
							continue;
						}
						schedule.addTimestampWithoutSort(newTimestamp);
						processedTrips.add(newTimestamp.toString());
					}
					this.statuses.add(schedule);
				} catch (Exception e) {
					MTLog.w(this, e, "Error while adding new schedule!");
				}
			}
		}

		private static final String SLASH = " / ";

		private static final Pattern UNIVERSITY = Pattern.compile("((^|\\W){1}(university)(\\W|$){1})", Pattern.CASE_INSENSITIVE);
		private static final String UNIVERSITY_REPLACEMENT = "$2U$4";

		private static final Pattern RIDEAU = //
				Pattern.compile("((^|\\W){1}(Rideau Centre|Downtown Rideau Ctr|Centre Rideau|Centre-ville Ctre Rideau)(\\W|$){1})", Pattern.CASE_INSENSITIVE);
		private static final String RIDEAU_REPLACEMENT = "$2Rideau$4";

		private static final Pattern CENTRE_VILLE = Pattern.compile("((^|\\W){1}(centre-ville)(\\W|$){1})", Pattern.CASE_INSENSITIVE);
		private static final String CENTRE_VILLE_REPLACEMENT = "$2Ctre-ville$4";

		private String cleanTripHeadsign(String tripHeadsign, String optRTSTripHeadsign) {
			try {
				if (!TextUtils.isEmpty(optRTSTripHeadsign) && Trip.isSameHeadsign(optRTSTripHeadsign, tripHeadsign)) {
					return tripHeadsign; // not cleaned in data parser => keep same as route trip head sign
				}
				tripHeadsign = UNIVERSITY.matcher(tripHeadsign).replaceAll(UNIVERSITY_REPLACEMENT);
				tripHeadsign = RIDEAU.matcher(tripHeadsign).replaceAll(RIDEAU_REPLACEMENT);
				tripHeadsign = CENTRE_VILLE.matcher(tripHeadsign).replaceAll(CENTRE_VILLE_REPLACEMENT);
				tripHeadsign = CleanUtils.cleanStreetTypes(tripHeadsign);
				tripHeadsign = CleanUtils.cleanStreetTypesFRCA(tripHeadsign);
				tripHeadsign = CleanUtils.cleanNumbers(tripHeadsign);
				tripHeadsign = CleanUtils.removePoints(tripHeadsign);
				tripHeadsign = CleanUtils.cleanLabel(tripHeadsign);
				if (!TextUtils.isEmpty(optRTSTripHeadsign) && Trip.isSameHeadsign(optRTSTripHeadsign, tripHeadsign)) {
					return tripHeadsign; // not cleaned in data parser => keep same as route trip head sign
				}
				int indexOfSlash = tripHeadsign.indexOf(SLASH);
				if (indexOfSlash > 0) {
					if (LocaleUtils.isFR()) {
						tripHeadsign = tripHeadsign.substring(indexOfSlash + SLASH.length());
					} else {
						tripHeadsign = tripHeadsign.substring(0, indexOfSlash);
					}
				}
				return tripHeadsign;
			} catch (Exception e) {
				MTLog.w(this, e, "Error while cleaning trip head sign '%s'!", tripHeadsign);
				return tripHeadsign;
			}
		}
	}

	public static class OCTranspoDbHelper extends MTSQLiteOpenHelper {

		private static final String TAG = OCTranspoDbHelper.class.getSimpleName();

		@Override
		public String getLogTag() {
			return TAG;
		}

		/**
		 * Override if multiple {@link OCTranspoDbHelper} implementations in same app.
		 */
		protected static final String DB_NAME = "octranspo.db";

		/**
		 * Override if multiple {@link OCTranspoDbHelper} implementations in same app.
		 */
		protected static final String PREF_KEY_AGENCY_SERVICE_UPDATE_LAST_UPDATE_MS = "pOcTranspoServiceUpdatesLastUpdate";

		public static final String T_LIVE_NEXT_BUS_ARRIVAL_DATA_FEED_STATUS = StatusProvider.StatusDbHelper.T_STATUS;

		private static final String T_LIVE_NEXT_BUS_ARRIVAL_DATA_FEED_STATUS_SQL_CREATE = //
				StatusProvider.StatusDbHelper.getSqlCreateBuilder(T_LIVE_NEXT_BUS_ARRIVAL_DATA_FEED_STATUS).build();

		private static final String T_LIVE_NEXT_BUS_ARRIVAL_DATA_FEED_STATUS_SQL_DROP = //
				SqlUtils.getSQLDropIfExistsQuery(T_LIVE_NEXT_BUS_ARRIVAL_DATA_FEED_STATUS);

		public static final String T_OC_TRANSPO_SERVICE_UPDATE = ServiceUpdateProvider.ServiceUpdateDbHelper.T_SERVICE_UPDATE;

		private static final String T_OC_TRANSPO_SERVICE_UPDATE_SQL_CREATE = //
				ServiceUpdateProvider.ServiceUpdateDbHelper.getSqlCreateBuilder(T_OC_TRANSPO_SERVICE_UPDATE).build();

		private static final String T_OC_TRANSPO_SERVICE_UPDATE_SQL_DROP = SqlUtils.getSQLDropIfExistsQuery(T_OC_TRANSPO_SERVICE_UPDATE);

		private static int dbVersion = -1;

		/**
		 * Override if multiple {@link OCTranspoDbHelper} in same app.
		 */
		public static int getDbVersion(Context context) {
			if (dbVersion < 0) {
				dbVersion = context.getResources().getInteger(R.integer.oc_transpo_db_version);
			}
			return dbVersion;
		}

		private Context context;

		public OCTranspoDbHelper(Context context) {
			super(context, DB_NAME, null, getDbVersion(context));
			this.context = context;
		}

		@Override
		public void onCreateMT(SQLiteDatabase db) {
			initAllDbTables(db);
		}

		@Override
		public void onUpgradeMT(SQLiteDatabase db, int oldVersion, int newVersion) {
			db.execSQL(T_LIVE_NEXT_BUS_ARRIVAL_DATA_FEED_STATUS_SQL_DROP);
			db.execSQL(T_OC_TRANSPO_SERVICE_UPDATE_SQL_DROP);
			PreferenceUtils.savePrefLcl(this.context, PREF_KEY_AGENCY_SERVICE_UPDATE_LAST_UPDATE_MS, 0L, true);
			initAllDbTables(db);
		}

		public boolean isDbExist(@NonNull Context context) {
			return SqlUtils.isDbExist(context, DB_NAME);
		}

		private void initAllDbTables(SQLiteDatabase db) {
			db.execSQL(T_LIVE_NEXT_BUS_ARRIVAL_DATA_FEED_STATUS_SQL_CREATE);
			db.execSQL(T_OC_TRANSPO_SERVICE_UPDATE_SQL_CREATE);
		}
	}
}
