package org.mtransit.android.commons.provider;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

import org.mtransit.android.commons.ArrayUtils;
import org.mtransit.android.commons.Constants;
import org.mtransit.android.commons.LocationUtils.Area;
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
import org.mtransit.android.commons.data.ScheduleTimestamps;
import org.mtransit.android.commons.data.ScheduleTimestampsFilter;
import org.mtransit.android.commons.provider.POIProvider.POIColumns;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;

@SuppressLint("Registered")
public class GTFSRouteTripStopProvider extends AgencyProvider implements POIProviderContract, StatusProviderContract, ScheduleTimestampsProviderContract {

	private static final String TAG = GTFSRouteTripStopProvider.class.getSimpleName();

	@Override
	public String getLogTag() {
		return TAG;
	}

	public static final String ROUTE_SORT_ORDER = GTFSRouteTripStopDbHelper.T_ROUTE + "." + GTFSRouteTripStopDbHelper.T_ROUTE_K_ID + " ASC";
	public static final String TRIP_SORT_ORDER = GTFSRouteTripStopDbHelper.T_TRIP + "." + GTFSRouteTripStopDbHelper.T_TRIP_K_ID + " ASC";
	public static final String TRIP_STOPS_SORT_ORDER = GTFSRouteTripStopDbHelper.T_TRIP_STOPS + "." + GTFSRouteTripStopDbHelper.T_TRIP_STOPS_K_ID + " ASC";
	public static final String STOP_SORT_ORDER = GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_ID + " ASC";
	public static final String ROUTE_TRIP_STOP_SORT_ORDER = ROUTE_SORT_ORDER + ", " + TRIP_SORT_ORDER + ", " + STOP_SORT_ORDER;
	public static final String ROUTE_TRIP_SORT_ORDER = ROUTE_SORT_ORDER + ", " + TRIP_SORT_ORDER;
	public static final String TRIP_STOP_SORT_ORDER = TRIP_SORT_ORDER + ", " + STOP_SORT_ORDER;

	protected static final int ROUTES = 1;
	protected static final int STOPS = 2;
	protected static final int TRIPS = 3;
	protected static final int ROUTES_TRIPS_STOPS = 4;
	protected static final int ROUTES_TRIPS_STOPS_SEARCH = 5;
	protected static final int ROUTES_TRIPS = 6;
	protected static final int TRIPS_STOPS = 7;
	protected static final int ROUTE_LOGO = 10;

	private static final HashMap<String, String> ROUTE_PROJECTION_MAP;
	private static final HashMap<String, String> TRIP_PROJECTION_MAP;
	private static final HashMap<String, String> STOP_PROJECTION_MAP;
	private static final HashMap<String, String> ROUTE_TRIP_STOP_PROJECTION_MAP;
	private static final HashMap<String, String> ROUTE_TRIP_PROJECTION_MAP;
	private static final HashMap<String, String> TRIP_STOP_PROJECTION_MAP;
	private static final HashMap<String, String> SIMPLE_SEARCH_SUGGEST_PROJECTION_MAP;
	static {

		HashMap<String, String> map;

		map = new HashMap<String, String>();
		map.put(RouteColumns.T_ROUTE_K_ID, GTFSRouteTripStopDbHelper.T_ROUTE + "." + GTFSRouteTripStopDbHelper.T_ROUTE_K_ID + " AS "
				+ RouteColumns.T_ROUTE_K_ID);
		map.put(RouteColumns.T_ROUTE_K_SHORT_NAME, GTFSRouteTripStopDbHelper.T_ROUTE + "." + GTFSRouteTripStopDbHelper.T_ROUTE_K_SHORT_NAME + " AS "
				+ RouteColumns.T_ROUTE_K_SHORT_NAME);
		map.put(RouteColumns.T_ROUTE_K_LONG_NAME, GTFSRouteTripStopDbHelper.T_ROUTE + "." + GTFSRouteTripStopDbHelper.T_ROUTE_K_LONG_NAME + " AS "
				+ RouteColumns.T_ROUTE_K_LONG_NAME);
		map.put(RouteColumns.T_ROUTE_K_COLOR, GTFSRouteTripStopDbHelper.T_ROUTE + "." + GTFSRouteTripStopDbHelper.T_ROUTE_K_COLOR + " AS "
				+ RouteColumns.T_ROUTE_K_COLOR);
		map.put(RouteColumns.T_ROUTE_K_TEXT_COLOR, GTFSRouteTripStopDbHelper.T_ROUTE + "." + GTFSRouteTripStopDbHelper.T_ROUTE_K_TEXT_COLOR + " AS "
				+ RouteColumns.T_ROUTE_K_TEXT_COLOR);
		ROUTE_PROJECTION_MAP = map;

		map = new HashMap<String, String>();
		map.put(TripColumns.T_TRIP_K_ID, GTFSRouteTripStopDbHelper.T_TRIP + "." + GTFSRouteTripStopDbHelper.T_TRIP_K_ID + " AS " + TripColumns.T_TRIP_K_ID);
		map.put(TripColumns.T_TRIP_K_HEADSIGN_TYPE, GTFSRouteTripStopDbHelper.T_TRIP + "." + GTFSRouteTripStopDbHelper.T_TRIP_K_HEADSIGN_TYPE + " AS "
				+ TripColumns.T_TRIP_K_HEADSIGN_TYPE);
		map.put(TripColumns.T_TRIP_K_HEADSIGN_VALUE, GTFSRouteTripStopDbHelper.T_TRIP + "." + GTFSRouteTripStopDbHelper.T_TRIP_K_HEADSIGN_VALUE + " AS "
				+ TripColumns.T_TRIP_K_HEADSIGN_VALUE);
		map.put(TripColumns.T_TRIP_K_ROUTE_ID, GTFSRouteTripStopDbHelper.T_TRIP + "." + GTFSRouteTripStopDbHelper.T_TRIP_K_ROUTE_ID + " AS "
				+ TripColumns.T_TRIP_K_ROUTE_ID);
		TRIP_PROJECTION_MAP = map;

		map = new HashMap<String, String>();
		map.put(StopColumns.T_STOP_K_ID, GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_ID + " AS " + StopColumns.T_STOP_K_ID);
		map.put(StopColumns.T_STOP_K_CODE, GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_CODE + " AS "
				+ StopColumns.T_STOP_K_CODE);
		map.put(StopColumns.T_STOP_K_NAME, GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_NAME + " AS "
				+ StopColumns.T_STOP_K_NAME);
		map.put(StopColumns.T_STOP_K_LAT, GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_LAT + " AS " + StopColumns.T_STOP_K_LAT);
		map.put(StopColumns.T_STOP_K_LNG, GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_LNG + " AS " + StopColumns.T_STOP_K_LNG);
		STOP_PROJECTION_MAP = map;

		map = new HashMap<String, String>();
		map.put(RouteTripStopColumns.T_STOP_K_ID, GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_ID + " AS "
				+ RouteTripStopColumns.T_STOP_K_ID);
		map.put(RouteTripStopColumns.T_STOP_K_CODE, GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_CODE + " AS "
				+ RouteTripStopColumns.T_STOP_K_CODE);
		map.put(RouteTripStopColumns.T_STOP_K_NAME, GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_NAME + " AS "
				+ RouteTripStopColumns.T_STOP_K_NAME);
		map.put(RouteTripStopColumns.T_STOP_K_LAT, GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_LAT + " AS "
				+ RouteTripStopColumns.T_STOP_K_LAT);
		map.put(RouteTripStopColumns.T_STOP_K_LNG, GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_LNG + " AS "
				+ RouteTripStopColumns.T_STOP_K_LNG);
		map.put(RouteTripStopColumns.T_TRIP_STOPS_K_STOP_SEQUENCE, GTFSRouteTripStopDbHelper.T_TRIP_STOPS + "."
				+ GTFSRouteTripStopDbHelper.T_TRIP_STOPS_K_STOP_SEQUENCE + " AS " + RouteTripStopColumns.T_TRIP_STOPS_K_STOP_SEQUENCE);
		map.put(RouteTripStopColumns.T_TRIP_STOPS_K_DECENT_ONLY, GTFSRouteTripStopDbHelper.T_TRIP_STOPS + "."
				+ GTFSRouteTripStopDbHelper.T_TRIP_STOPS_K_DECENT_ONLY + " AS " + RouteTripStopColumns.T_TRIP_STOPS_K_DECENT_ONLY);
		map.put(RouteTripStopColumns.T_TRIP_K_ID, GTFSRouteTripStopDbHelper.T_TRIP + "." + GTFSRouteTripStopDbHelper.T_TRIP_K_ID + " AS "
				+ RouteTripStopColumns.T_TRIP_K_ID);
		map.put(RouteTripStopColumns.T_TRIP_K_HEADSIGN_TYPE, GTFSRouteTripStopDbHelper.T_TRIP + "." + GTFSRouteTripStopDbHelper.T_TRIP_K_HEADSIGN_TYPE + " AS "
				+ RouteTripStopColumns.T_TRIP_K_HEADSIGN_TYPE);
		map.put(RouteTripStopColumns.T_TRIP_K_HEADSIGN_VALUE, GTFSRouteTripStopDbHelper.T_TRIP + "." + GTFSRouteTripStopDbHelper.T_TRIP_K_HEADSIGN_VALUE
				+ " AS " + RouteTripStopColumns.T_TRIP_K_HEADSIGN_VALUE);
		map.put(RouteTripStopColumns.T_TRIP_K_ROUTE_ID, GTFSRouteTripStopDbHelper.T_TRIP + "." + GTFSRouteTripStopDbHelper.T_TRIP_K_ROUTE_ID + " AS "
				+ RouteTripStopColumns.T_TRIP_K_ROUTE_ID);
		map.put(RouteTripStopColumns.T_ROUTE_K_ID, GTFSRouteTripStopDbHelper.T_ROUTE + "." + GTFSRouteTripStopDbHelper.T_ROUTE_K_ID + " AS "
				+ RouteTripStopColumns.T_ROUTE_K_ID);
		map.put(RouteTripStopColumns.T_ROUTE_K_SHORT_NAME, GTFSRouteTripStopDbHelper.T_ROUTE + "." + GTFSRouteTripStopDbHelper.T_ROUTE_K_SHORT_NAME + " AS "
				+ RouteTripStopColumns.T_ROUTE_K_SHORT_NAME);
		map.put(RouteTripStopColumns.T_ROUTE_K_LONG_NAME, GTFSRouteTripStopDbHelper.T_ROUTE + "." + GTFSRouteTripStopDbHelper.T_ROUTE_K_LONG_NAME + " AS "
				+ RouteTripStopColumns.T_ROUTE_K_LONG_NAME);
		map.put(RouteTripStopColumns.T_ROUTE_K_COLOR, GTFSRouteTripStopDbHelper.T_ROUTE + "." + GTFSRouteTripStopDbHelper.T_ROUTE_K_COLOR + " AS "
				+ RouteTripStopColumns.T_ROUTE_K_COLOR);
		map.put(RouteTripStopColumns.T_ROUTE_K_TEXT_COLOR, GTFSRouteTripStopDbHelper.T_ROUTE + "." + GTFSRouteTripStopDbHelper.T_ROUTE_K_TEXT_COLOR + " AS "
				+ RouteTripStopColumns.T_ROUTE_K_TEXT_COLOR);
		ROUTE_TRIP_STOP_PROJECTION_MAP = map;


		map = new HashMap<String, String>();
		map.put(SearchManager.SUGGEST_COLUMN_TEXT_1, GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_NAME + " AS "
				+ SearchManager.SUGGEST_COLUMN_TEXT_1);
		SIMPLE_SEARCH_SUGGEST_PROJECTION_MAP = map;

		map = new HashMap<String, String>();
		map.put(RouteTripColumns.T_TRIP_K_ID, GTFSRouteTripStopDbHelper.T_TRIP + "." + GTFSRouteTripStopDbHelper.T_TRIP_K_ID + " AS "
				+ RouteTripColumns.T_TRIP_K_ID);
		map.put(RouteTripColumns.T_TRIP_K_HEADSIGN_TYPE, GTFSRouteTripStopDbHelper.T_TRIP + "." + GTFSRouteTripStopDbHelper.T_TRIP_K_HEADSIGN_TYPE + " AS "
				+ RouteTripColumns.T_TRIP_K_HEADSIGN_TYPE);
		map.put(RouteTripColumns.T_TRIP_K_HEADSIGN_VALUE, GTFSRouteTripStopDbHelper.T_TRIP + "." + GTFSRouteTripStopDbHelper.T_TRIP_K_HEADSIGN_VALUE + " AS "
				+ RouteTripColumns.T_TRIP_K_HEADSIGN_VALUE);
		map.put(RouteTripColumns.T_TRIP_K_ROUTE_ID, GTFSRouteTripStopDbHelper.T_TRIP + "." + GTFSRouteTripStopDbHelper.T_TRIP_K_ROUTE_ID + " AS "
				+ RouteTripColumns.T_TRIP_K_ROUTE_ID);
		map.put(RouteTripColumns.T_ROUTE_K_ID, GTFSRouteTripStopDbHelper.T_ROUTE + "." + GTFSRouteTripStopDbHelper.T_ROUTE_K_ID + " AS "
				+ RouteTripColumns.T_ROUTE_K_ID);
		map.put(RouteTripColumns.T_ROUTE_K_SHORT_NAME, GTFSRouteTripStopDbHelper.T_ROUTE + "." + GTFSRouteTripStopDbHelper.T_ROUTE_K_SHORT_NAME + " AS "
				+ RouteTripColumns.T_ROUTE_K_SHORT_NAME);
		map.put(RouteTripColumns.T_ROUTE_K_LONG_NAME, GTFSRouteTripStopDbHelper.T_ROUTE + "." + GTFSRouteTripStopDbHelper.T_ROUTE_K_LONG_NAME + " AS "
				+ RouteTripColumns.T_ROUTE_K_LONG_NAME);
		map.put(RouteTripColumns.T_ROUTE_K_COLOR, GTFSRouteTripStopDbHelper.T_ROUTE + "." + GTFSRouteTripStopDbHelper.T_ROUTE_K_COLOR + " AS "
				+ RouteTripColumns.T_ROUTE_K_COLOR);
		map.put(RouteTripColumns.T_ROUTE_K_TEXT_COLOR, GTFSRouteTripStopDbHelper.T_ROUTE + "." + GTFSRouteTripStopDbHelper.T_ROUTE_K_TEXT_COLOR + " AS "
				+ RouteTripColumns.T_ROUTE_K_TEXT_COLOR);
		ROUTE_TRIP_PROJECTION_MAP = map;

		map = new HashMap<String, String>();
		map.put(TripStopColumns.T_STOP_K_ID, GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_ID + " AS "
				+ TripStopColumns.T_STOP_K_ID);
		map.put(TripStopColumns.T_STOP_K_CODE, GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_CODE + " AS "
				+ TripStopColumns.T_STOP_K_CODE);
		map.put(TripStopColumns.T_STOP_K_NAME, GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_NAME + " AS "
				+ TripStopColumns.T_STOP_K_NAME);
		map.put(TripStopColumns.T_STOP_K_LAT, GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_LAT + " AS "
				+ TripStopColumns.T_STOP_K_LAT);
		map.put(TripStopColumns.T_STOP_K_LNG, GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_LNG + " AS "
				+ TripStopColumns.T_STOP_K_LNG);
		map.put(TripStopColumns.T_TRIP_STOPS_K_STOP_SEQUENCE, GTFSRouteTripStopDbHelper.T_TRIP_STOPS + "."
				+ GTFSRouteTripStopDbHelper.T_TRIP_STOPS_K_STOP_SEQUENCE + " AS " + TripStopColumns.T_TRIP_STOPS_K_STOP_SEQUENCE);
		map.put(TripStopColumns.T_TRIP_STOPS_K_DECENT_ONLY, GTFSRouteTripStopDbHelper.T_TRIP_STOPS + "." + GTFSRouteTripStopDbHelper.T_TRIP_STOPS_K_DECENT_ONLY
				+ " AS " + TripStopColumns.T_TRIP_STOPS_K_DECENT_ONLY);
		map.put(TripStopColumns.T_TRIP_K_ID, GTFSRouteTripStopDbHelper.T_TRIP + "." + GTFSRouteTripStopDbHelper.T_TRIP_K_ID + " AS "
				+ TripStopColumns.T_TRIP_K_ID);
		map.put(TripStopColumns.T_TRIP_K_HEADSIGN_TYPE, GTFSRouteTripStopDbHelper.T_TRIP + "." + GTFSRouteTripStopDbHelper.T_TRIP_K_HEADSIGN_TYPE + " AS "
				+ TripStopColumns.T_TRIP_K_HEADSIGN_TYPE);
		map.put(TripStopColumns.T_TRIP_K_HEADSIGN_VALUE, GTFSRouteTripStopDbHelper.T_TRIP + "." + GTFSRouteTripStopDbHelper.T_TRIP_K_HEADSIGN_VALUE + " AS "
				+ TripStopColumns.T_TRIP_K_HEADSIGN_VALUE);
		map.put(TripStopColumns.T_TRIP_K_ROUTE_ID, GTFSRouteTripStopDbHelper.T_TRIP + "." + GTFSRouteTripStopDbHelper.T_TRIP_K_ROUTE_ID + " AS "
				+ TripStopColumns.T_TRIP_K_ROUTE_ID);
		TRIP_STOP_PROJECTION_MAP = map;

	}

	@SuppressWarnings("unused")
	private static final String TRIP_STOPS_STOP_JOIN = GTFSRouteTripStopDbHelper.T_TRIP_STOPS + SqlUtils.INNER_JOIN + GTFSRouteTripStopDbHelper.T_STOP + " ON "
			+ GTFSRouteTripStopDbHelper.T_TRIP_STOPS + "." + GTFSRouteTripStopDbHelper.T_TRIP_STOPS_K_STOP_ID + "=" + GTFSRouteTripStopDbHelper.T_STOP + "."
			+ GTFSRouteTripStopDbHelper.T_STOP_K_ID;

	private static final String ROUTE_TRIP_TRIP_STOPS_STOP_JOIN = GTFSRouteTripStopDbHelper.T_STOP + SqlUtils.INNER_JOIN
			+ GTFSRouteTripStopDbHelper.T_TRIP_STOPS + " ON " + GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_ID + "="
			+ GTFSRouteTripStopDbHelper.T_TRIP_STOPS + "." + GTFSRouteTripStopDbHelper.T_TRIP_STOPS_K_STOP_ID + SqlUtils.INNER_JOIN
			+ GTFSRouteTripStopDbHelper.T_TRIP + " ON " + GTFSRouteTripStopDbHelper.T_TRIP_STOPS + "." + GTFSRouteTripStopDbHelper.T_TRIP_STOPS_K_TRIP_ID + "="
			+ GTFSRouteTripStopDbHelper.T_TRIP + "." + GTFSRouteTripStopDbHelper.T_TRIP_K_ID + SqlUtils.INNER_JOIN + GTFSRouteTripStopDbHelper.T_ROUTE + " ON "
			+ GTFSRouteTripStopDbHelper.T_TRIP + "." + GTFSRouteTripStopDbHelper.T_TRIP_K_ROUTE_ID + "=" + GTFSRouteTripStopDbHelper.T_ROUTE + "."
			+ GTFSRouteTripStopDbHelper.T_ROUTE_K_ID;

	private static final String TRIP_TRIP_STOPS_STOP_JOIN = GTFSRouteTripStopDbHelper.T_STOP + SqlUtils.INNER_JOIN + GTFSRouteTripStopDbHelper.T_TRIP_STOPS
			+ " ON " + GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_ID + "=" + GTFSRouteTripStopDbHelper.T_TRIP_STOPS + "."
			+ GTFSRouteTripStopDbHelper.T_TRIP_STOPS_K_STOP_ID + SqlUtils.INNER_JOIN + GTFSRouteTripStopDbHelper.T_TRIP + " ON "
			+ GTFSRouteTripStopDbHelper.T_TRIP_STOPS + "." + GTFSRouteTripStopDbHelper.T_TRIP_STOPS_K_TRIP_ID + "=" + GTFSRouteTripStopDbHelper.T_TRIP + "."
			+ GTFSRouteTripStopDbHelper.T_TRIP_K_ID;

	private static final String ROUTE_TRIP_JOIN = GTFSRouteTripStopDbHelper.T_TRIP + SqlUtils.INNER_JOIN + GTFSRouteTripStopDbHelper.T_ROUTE + " ON "
			+ GTFSRouteTripStopDbHelper.T_TRIP + "." + GTFSRouteTripStopDbHelper.T_TRIP_K_ROUTE_ID + "=" + GTFSRouteTripStopDbHelper.T_ROUTE + "."
			+ GTFSRouteTripStopDbHelper.T_ROUTE_K_ID;

	private static GTFSRouteTripStopDbHelper dbHelper;

	private static int currentDbVersion = -1;

	private static UriMatcher uriMatcher = null;

	/**
	 * Override if multiple {@link GTFSRouteTripStopProvider} implementations in same app.
	 */
	public static UriMatcher getURIMATCHER(Context context) {
		if (uriMatcher == null) {
			uriMatcher = getNewUriMatcher(getAUTHORITY(context));
		}
		return uriMatcher;
	}

	@Override
	public UriMatcher getURIMATCHER() {
		return getURIMATCHER(getContext());
	}

	public static UriMatcher getNewUriMatcher(String authority) {
		UriMatcher URI_MATCHER = AgencyProvider.getNewUriMatcher(authority);
		StatusProvider.append(URI_MATCHER, authority);
		POIProvider.append(URI_MATCHER, authority);
		ScheduleTimestampsProvider.append(URI_MATCHER, authority);
		//
		URI_MATCHER.addURI(authority, "route", ROUTES);
		URI_MATCHER.addURI(authority, "trip", TRIPS);
		URI_MATCHER.addURI(authority, "stop", STOPS);
		URI_MATCHER.addURI(authority, "route/trip/stop", ROUTES_TRIPS_STOPS);
		URI_MATCHER.addURI(authority, "route/trip/stop/*", ROUTES_TRIPS_STOPS_SEARCH);
		URI_MATCHER.addURI(authority, "route/trip", ROUTES_TRIPS);
		URI_MATCHER.addURI(authority, "trip/stop", TRIPS_STOPS);
		URI_MATCHER.addURI(authority, "route/logo", ROUTE_LOGO);
		return URI_MATCHER;
	}

	private static String authority = null;

	/**
	 * Override if multiple {@link GTFSRouteTripStopProvider} implementations in same app.
	 */
	public static String getAUTHORITY(Context context) {
		if (authority == null) {
			authority = context.getResources().getString(R.string.gtfs_rts_authority);
		}
		return authority;
	}

	private static Uri authorityUri = null;

	/**
	 * Override if multiple {@link BikeStationProvider} implementations in same app.
	 */
	public static Uri getAUTHORITYURI(Context context) {
		if (authorityUri == null) {
			authorityUri = UriUtils.newContentUri(getAUTHORITY(context));
		}
		return authorityUri;
	}

	private static int agencyType = -1;

	/**
	 * Override if multiple {@link GTFSRouteTripStopProvider} implementations in same app.
	 */
	public static int getAGENCYTYPE(Context context) {
		if (agencyType < 0) {
			agencyType = context.getResources().getInteger(R.integer.gtfs_rts_agency_type);
		}
		return agencyType;
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

	private GTFSRouteTripStopDbHelper getDBHelper(Context context) {
		if (dbHelper == null) {
			dbHelper = getNewDbHelper(context);
			currentDbVersion = getCurrentDbVersion();
		} else {
			try {
				if (currentDbVersion != getCurrentDbVersion()) {
					dbHelper.close();
					dbHelper = null;
					return getDBHelper(context);
				}
			} catch (Throwable t) {
				MTLog.d(this, t, "Can't check DB version!");
			}
		}
		return dbHelper;
	}

	@Override
	public SQLiteOpenHelper getDBHelper() {
		return getDBHelper(getContext());
	}

	@Override
	public Context getContentProviderContext() {
		return getContext();
	}

	public static final long SCHEDULE_MAX_VALIDITY_IN_MS = TimeUtils.ONE_DAY_IN_MS;

	public static final long SCHEDULE_VALIDITY_IN_MS = 6 * TimeUtils.ONE_HOUR_IN_MS;

	public static final long SCHEDULE_MIN_DURATION_BETWEEN_REFRESH_IN_MS = TimeUtils.ONE_HOUR_IN_MS;

	@Override
	public long getStatusValidityInMs() {
		return SCHEDULE_VALIDITY_IN_MS;
	}

	@Override
	public long getStatusMaxValidityInMs() {
		return SCHEDULE_MAX_VALIDITY_IN_MS;
	}

	@Override
	public long getMinDurationBetweenRefreshInMs() {
		return SCHEDULE_MIN_DURATION_BETWEEN_REFRESH_IN_MS;
	}

	private static final int PROVIDER_PRECISION_IN_MS = TimeUtils.ONE_MINUTE_IN_MS;


	@Override
	public POIStatus getNewStatus(StatusFilter filter) {
		if (!(filter instanceof Schedule.ScheduleStatusFilter)) {
			MTLog.w(this, "Can't find new schecule whithout schedule filter!");
			return null;
		}
		Schedule.ScheduleStatusFilter scheduleStatusFilter = (Schedule.ScheduleStatusFilter) filter;
		ArrayList<Schedule.Timestamp> allTimestamps = findTimestamps(scheduleStatusFilter);
		Schedule schedule = new Schedule(filter.getTargetUUID(), scheduleStatusFilter.getTimestampOrDefault(), getStatusMaxValidityInMs(),
				PROVIDER_PRECISION_IN_MS, scheduleStatusFilter.getRouteTripStop().decentOnly);
		schedule.setTimestampsAndSort(allTimestamps);
		return schedule;
	}

	private static final ThreadSafeDateFormatter DATE_FORMAT = new ThreadSafeDateFormatter("yyyyMMdd");

	private static final ThreadSafeDateFormatter TIME_FORMAT = new ThreadSafeDateFormatter("HHmmss");

	private ArrayList<Schedule.Timestamp> findTimestamps(Schedule.ScheduleStatusFilter filter) {
		ArrayList<Schedule.Timestamp> allTimestamps = new ArrayList<Schedule.Timestamp>();
		final RouteTripStop routeTripStop = filter.getRouteTripStop();
		final int maxDataRequests = filter.getMaxDataRequestsOrDefault();
		final int minUsefulResults = filter.getMinUsefulResultsOrDefault();
		final long minDurationCoveredInMs = filter.getMinUsefulDurationCoveredInMsOrDefault();
		final int lookBehindInMs = filter.getLookBehindInMsOrDefault();
		final long timestamp = filter.getTimestampOrDefault();
		final long minTimestampCovered = timestamp + minDurationCoveredInMs;
		Calendar now = TimeUtils.getNewCalendar(timestamp);
		if (lookBehindInMs > PROVIDER_PRECISION_IN_MS) {
			if (lookBehindInMs > 0) {
				now.add(Calendar.MILLISECOND, -lookBehindInMs);
			}
		} else {
			if (PROVIDER_PRECISION_IN_MS > 0) {
				now.add(Calendar.MILLISECOND, -PROVIDER_PRECISION_IN_MS);
			}
		}
		now.add(Calendar.DATE, -1); // starting yesterday
		HashSet<Schedule.Timestamp> dayTimestamps = null;
		String dayTime = null;
		String dayDate = null;
		int nbTimestamps = 0;
		int dataRequests = 0;
		while (dataRequests < maxDataRequests) {
			Date timeDate = now.getTime();
			dayDate = DATE_FORMAT.formatThreadSafe(timeDate);
			if (dataRequests == 0) { // IF yesterday DO
				dayTime = String.valueOf(Integer.valueOf(TIME_FORMAT.formatThreadSafe(timeDate)) + 240000); // look for trips started yesterday (with 240000+
			} else if (dataRequests == 1) { // ELSE IF today DO
				dayTime = TIME_FORMAT.formatThreadSafe(timeDate); // start now
			} else { // ELSE tomorrow or later DO
				dayTime = "000000"; // start at midnight
			}
			dayTimestamps = findScheduleList(routeTripStop.route.id, routeTripStop.trip.getId(), routeTripStop.stop.id, dayDate, dayTime);
			dataRequests++; // 1 more data request done
			allTimestamps.addAll(dayTimestamps);
			if (lookBehindInMs == 0) {
				nbTimestamps += dayTimestamps.size();
			} else {
				for (Schedule.Timestamp dayTimestamp : dayTimestamps) {
					if (dayTimestamp.t >= timestamp) {
						nbTimestamps++;
					}
				}
			}
			if (nbTimestamps >= minUsefulResults && now.getTimeInMillis() >= minTimestampCovered) {
				break;
			}
			now.add(Calendar.DATE, +1); // NEXT DAY
		}
		return allTimestamps;
	}

	@Override
	public ScheduleTimestamps getScheduleTimestamps(ScheduleTimestampsFilter filter) {
		ArrayList<Schedule.Timestamp> allTimestamps = new ArrayList<Schedule.Timestamp>();
		final RouteTripStop rts = filter.getRouteTripStop();
		final long startsAtInMs = filter.getStartsAtInMs();
		final long endsAtInMs = filter.getEndsAtInMs();
		Calendar startsAt = TimeUtils.getNewCalendar(startsAtInMs);
		startsAt.add(Calendar.DATE, -1); // starting yesterday
		HashSet<Schedule.Timestamp> dayTimestamps = null;
		String dayTime = null;
		String dayDate = null;
		int dataRequests = 0;
		while (startsAt.getTimeInMillis() < endsAtInMs) {
			Date timeDate = startsAt.getTime();
			dayDate = DATE_FORMAT.formatThreadSafe(timeDate);
			if (dataRequests == 0) { // IF yesterday DO
				dayTime = String.valueOf(Integer.valueOf(TIME_FORMAT.formatThreadSafe(timeDate)) + 240000);
				// look for trips started yesterday (with 240000+
			} else { // ELSE tomorrow or later DO
				dayTime = "000000"; // start at midnight
			}
			dayTimestamps = findScheduleList(rts.route.id, rts.trip.getId(), rts.stop.id, dayDate, dayTime);
			dataRequests++; // 1 more data request done
			for (Schedule.Timestamp t : dayTimestamps) {
				if (t.t >= startsAtInMs && t.t < endsAtInMs) {
					allTimestamps.add(t);
				}
			}
			startsAt.add(Calendar.DATE, +1); // NEXT DAY
		}
		final ScheduleTimestamps scheduleTimestamps = new ScheduleTimestamps(rts.getUUID(), startsAtInMs, endsAtInMs);
		scheduleTimestamps.setTimestampsAndSort(allTimestamps);
		return scheduleTimestamps;
	}

	private static final String RAW_FILE_FORMAT = "gtfs_schedule_stop_%s";

	private static final int GTFS_SCHEDULE_STOP_FILE_COL_SERVICE_IDX = 0;
	private static final int GTFS_SCHEDULE_STOP_FILE_COL_TRIP_IDX = 1;
	private static final int GTFS_SCHEDULE_STOP_FILE_COL_STOP_IDX = 2;
	private static final int GTFS_SCHEDULE_STOP_FILE_COL_DEPARTURE_IDX = 3;
	private static final int GTFS_SCHEDULE_STOP_FILE_COL_HEADSIGN_TYPE_IDX = 4;
	private static final int GTFS_SCHEDULE_STOP_FILE_COL_HEADSIGN_VALUE_IDX = 5;

	private HashSet<Schedule.Timestamp> findScheduleList(int routeId, int tripId, int stopId, String dateS, String timeS) {
		long timeI = Integer.parseInt(timeS);
		HashSet<Schedule.Timestamp> result = new HashSet<Schedule.Timestamp>();
		// 1st find date service(s) in DB
		HashSet<String> serviceIds = findServices(dateS);
		// 2nd read schedule file
		BufferedReader br = null;
		String line = null;
		String fileName = String.format(RAW_FILE_FORMAT, stopId);
		try {
			br = new BufferedReader(new InputStreamReader(getContext().getResources().openRawResource(
					getContext().getResources().getIdentifier(fileName, "raw", getContext().getPackageName())), "UTF8"), 8192);
			while ((line = br.readLine()) != null) {
				try {
					String[] lineItems = line.split(",");
					if (lineItems.length != 6) {
						MTLog.w(this, "Cannot parse schedule '%s'!", line);
						continue;
					}
					String lineServiceIdWithQuotes = lineItems[GTFS_SCHEDULE_STOP_FILE_COL_SERVICE_IDX];
					final String lineServiceId = lineServiceIdWithQuotes.substring(1, lineServiceIdWithQuotes.length() - 1);
					if (!serviceIds.contains(lineServiceId)) {
						continue;
					}
					final int lineTripId = Integer.parseInt(lineItems[GTFS_SCHEDULE_STOP_FILE_COL_TRIP_IDX]);
					if (tripId != lineTripId) {
						continue;
					}
					final int lineStopId = Integer.parseInt(lineItems[GTFS_SCHEDULE_STOP_FILE_COL_STOP_IDX]);
					if (stopId != lineStopId) {
						MTLog.w(this, "Wrong stop id '%s' while looking for stop id '%s'!", lineStopId, stopId);
						continue;
					}
					final int lineDeparture = Integer.parseInt(lineItems[GTFS_SCHEDULE_STOP_FILE_COL_DEPARTURE_IDX]);
					if (lineDeparture > timeI) {
						final Long tLong = convertToTimestamp(lineDeparture, dateS);
						if (tLong != null) {
							Schedule.Timestamp timestamp = new Schedule.Timestamp(tLong.longValue());
							final int headsignType = Integer.parseInt(lineItems[GTFS_SCHEDULE_STOP_FILE_COL_HEADSIGN_TYPE_IDX]);
							if (headsignType >= 0) {
								final String headsignValueWithQuotes = lineItems[GTFS_SCHEDULE_STOP_FILE_COL_HEADSIGN_VALUE_IDX];
								if (headsignValueWithQuotes.length() > 2) {
									final String headsignValue = headsignValueWithQuotes.substring(1, headsignValueWithQuotes.length() - 1);
									timestamp.setHeadsign(headsignType, headsignValue);
								}
							}
							result.add(timestamp);
						}
					}
				} catch (Exception e) {
					MTLog.w(this, e, "Cannot parse schedule '%s' (fileName: %s)!", line, fileName);
				}
			}
		} catch (Exception e) {
			MTLog.w(this, e, "ERROR while reading stop time from file! (fileName: %s, line: %s)", fileName, line);
		} finally {
			try {
				if (br != null) {
					br.close();
				}
			} catch (Exception e) {
				MTLog.w(this, e, "ERROR while closing the input stream!");
			}
		}
		return result;
	}

	private static final String[] PROJECTION_SERVICE_DATES = new String[] { ServiceDateColumns.T_SERVICE_DATES_K_SERVICE_ID };

	public HashSet<String> findServices(String dateS) {
		HashSet<String> serviceIds = new HashSet<String>();
		Cursor cursor = null;
		try {
			String where = new StringBuilder() //
					.append(ServiceDateColumns.T_SERVICE_DATES_K_DATE).append("=").append(dateS) //
					.toString();
			SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
			qb.setTables(GTFSRouteTripStopDbHelper.T_SERVICE_DATES);
			cursor = qb.query(getDBHelper().getReadableDatabase(), PROJECTION_SERVICE_DATES, where, null, null, null, null, null);
			if (cursor != null && cursor.getCount() > 0) {
				if (cursor.moveToFirst()) {
					do {
						final String serviceId = cursor.getString(0);
						if (!TextUtils.isEmpty(serviceId)) {
							serviceIds.add(serviceId);
						}
					} while (cursor.moveToNext());
				}
			}
		} catch (Throwable t) {
			MTLog.w(this, t, "Error!");
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return serviceIds;
	}

	private Long convertToTimestamp(int timeInt, String dateS) {
		try {
			final Date parsedDate = TO_TIMESTAMP_FORMAT.parseThreadSafe(dateS + String.format("%06d", timeInt));
			final long timestamp = parsedDate.getTime();
			return timestamp;
		} catch (Exception e) {
			MTLog.w(this, e, "Error while parsing time %s %s!", dateS, timeInt);
			return null;
		}
	}

	public static final ThreadSafeDateFormatter TO_TIMESTAMP_FORMAT = new ThreadSafeDateFormatter("yyyyMMdd" + "HHmmss");


	@Override
	public void cacheStatus(POIStatus newStatusToCache) {
		StatusProvider.cacheStatusS(getContext(), this, newStatusToCache);
	}

	@Override
	public POIStatus getCachedStatus(String targetUUID) {
		return StatusProvider.getCachedStatusS(this, targetUUID);
	}

	@Override
	public boolean purgeUselessCachedStatuses() {
		return StatusProvider.purgeUselessCachedStatuses(getContext(), this);
	}

	@Override
	public boolean deleteCachedStatus(int cachedStatusId) {
		return StatusProvider.deleteCachedStatus(getContext(), this, cachedStatusId);
	}

	@Override
	public String getStatusDbTableName() {
		return GTFSRouteTripStopDbHelper.T_ROUTE_TRIP_STOP_STATUS;
	}

	@Override
	public Uri getAuthorityUri() {
		return getAUTHORITYURI(getContext());
	}

	@Override
	public Cursor queryMT(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		try {
			Cursor cursor = super.queryMT(uri, projection, selection, selectionArgs, sortOrder);
			if (cursor != null) {
				return cursor;
			}
			cursor = POIProvider.queryS(this, uri, projection, selection, selectionArgs, sortOrder);
			if (cursor != null) {
				return cursor;
			}
			cursor = StatusProvider.queryS(this, uri, projection, selection, selectionArgs, sortOrder);
			if (cursor != null) {
				return cursor;
			}
			cursor = ScheduleTimestampsProvider.queryS(this, uri, projection, selection, selectionArgs, sortOrder);
			if (cursor != null) {
				return cursor;
			}
			SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
			String limit = null;
			switch (getURIMATCHER(getContext()).match(uri)) {
			case ROUTES:
				qb.setTables(GTFSRouteTripStopDbHelper.T_ROUTE);
				qb.setProjectionMap(ROUTE_PROJECTION_MAP);
				break;
			case TRIPS:
				qb.setTables(GTFSRouteTripStopDbHelper.T_TRIP);
				qb.setProjectionMap(TRIP_PROJECTION_MAP);
				break;
			case STOPS:
				qb.setTables(GTFSRouteTripStopDbHelper.T_STOP);
				qb.setProjectionMap(STOP_PROJECTION_MAP);
				break;
			case ROUTES_TRIPS_STOPS:
				qb.setTables(ROUTE_TRIP_TRIP_STOPS_STOP_JOIN);
				qb.setProjectionMap(ROUTE_TRIP_STOP_PROJECTION_MAP);
				break;
			case ROUTES_TRIPS_STOPS_SEARCH:
				qb.setTables(ROUTE_TRIP_TRIP_STOPS_STOP_JOIN);
				qb.setProjectionMap(ROUTE_TRIP_STOP_PROJECTION_MAP);
				appendRouteTripStopSearch(uri, qb);
				break;
			case ROUTES_TRIPS:
				qb.setTables(ROUTE_TRIP_JOIN);
				qb.setProjectionMap(ROUTE_TRIP_PROJECTION_MAP);
				break;
			case TRIPS_STOPS:
				qb.setTables(TRIP_TRIP_STOPS_STOP_JOIN);
				qb.setProjectionMap(TRIP_STOP_PROJECTION_MAP);
				break;
			case ROUTE_LOGO:
				return getRouteLogo();
			default:
				throw new IllegalArgumentException(String.format("Unknown URI (query): '%s'", uri));
			}
			if (TextUtils.isEmpty(sortOrder)) {
				sortOrder = getSortOrder(uri);
			}
			cursor = qb.query(getDBHelper().getReadableDatabase(), projection, selection, selectionArgs, null, null, sortOrder, limit);
			if (cursor != null) {
				cursor.setNotificationUri(getContext().getContentResolver(), uri);
			}
			return cursor;
		} catch (Throwable t) {
			MTLog.w(this, t, "Error while resolving query '%s'!", uri);
			return null;
		}
	}

	@Override
	public Cursor getSearchSuggest(String query) {
		return POIProvider.getDefaultSearchSuggest(query, this); // simple search suggest
	}

	@Override
	public String getSearchSuggestTable() {
		return GTFSRouteTripStopDbHelper.T_STOP; // simple search suggest
	}

	@Override
	public HashMap<String, String> getSearchSuggestProjectionMap() {
		return SIMPLE_SEARCH_SUGGEST_PROJECTION_MAP; // simple search suggest
	}

	@Override
	public Cursor getPOI(POIFilter poiFilter) {
		return getPOIFromDB(poiFilter);
	}

	public static final String[] PROJECTION_ROUTE_TRIP_STOP = new String[] { RouteTripStopColumns.T_ROUTE_K_ID, RouteTripStopColumns.T_ROUTE_K_SHORT_NAME,
			RouteTripStopColumns.T_ROUTE_K_LONG_NAME, RouteTripStopColumns.T_ROUTE_K_COLOR, RouteTripStopColumns.T_ROUTE_K_TEXT_COLOR,
			RouteTripStopColumns.T_TRIP_K_ID, RouteTripStopColumns.T_TRIP_K_HEADSIGN_TYPE, RouteTripStopColumns.T_TRIP_K_HEADSIGN_VALUE,
			RouteTripStopColumns.T_TRIP_K_ROUTE_ID, RouteTripStopColumns.T_TRIP_STOPS_K_STOP_SEQUENCE, RouteTripStopColumns.T_TRIP_STOPS_K_DECENT_ONLY,
			RouteTripStopColumns.T_STOP_K_ID, RouteTripStopColumns.T_STOP_K_CODE, RouteTripStopColumns.T_STOP_K_NAME, RouteTripStopColumns.T_STOP_K_LAT,
			RouteTripStopColumns.T_STOP_K_LNG };

	public static final String[] PROJECTION_ROUTE = new String[] { RouteColumns.T_ROUTE_K_ID, RouteColumns.T_ROUTE_K_SHORT_NAME,
			RouteColumns.T_ROUTE_K_LONG_NAME, RouteColumns.T_ROUTE_K_COLOR, RouteColumns.T_ROUTE_K_TEXT_COLOR };

	public static final String[] PROJECTION_TRIP = new String[] { TripColumns.T_TRIP_K_ID, TripColumns.T_TRIP_K_HEADSIGN_TYPE,
			TripColumns.T_TRIP_K_HEADSIGN_VALUE, TripColumns.T_TRIP_K_ROUTE_ID };

	public static final String[] PROJECTION_RTS_POI = ArrayUtils.addAll(POIProvider.PROJECTION_POI, PROJECTION_ROUTE_TRIP_STOP);

	private static final String[] SEARCHABLE_LIKE_COLUMNS = new String[] { //
	GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_NAME,//
			GTFSRouteTripStopDbHelper.T_ROUTE + "." + GTFSRouteTripStopDbHelper.T_ROUTE_K_LONG_NAME,//
	};
	private static final String[] SEARCHABLE_EQUAL_COLUMNS = new String[] { //
	GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_CODE, //
			GTFSRouteTripStopDbHelper.T_ROUTE + "." + GTFSRouteTripStopDbHelper.T_ROUTE_K_SHORT_NAME,//
	};
	@Override
	public Cursor getPOIFromDB(POIFilter poiFilter) {
		try {
			String selection = poiFilter.getSqlSelection(POIColumns.T_POI_K_UUID_META, POIColumns.T_POI_K_LAT, POIColumns.T_POI_K_LNG, SEARCHABLE_LIKE_COLUMNS,
					SEARCHABLE_EQUAL_COLUMNS);
			final boolean isDecentOnly = poiFilter.getExtraBoolean("decentOnly", false);
			if (isDecentOnly) {
				if (selection == null) {
					selection = StringUtils.EMPTY;
				} else if (selection.length() > 0) {
					selection += " AND ";
				}
				selection += RouteTripStopColumns.T_TRIP_STOPS_K_DECENT_ONLY + "!=1";
			}
			SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
			qb.setTables(ROUTE_TRIP_TRIP_STOPS_STOP_JOIN);
			HashMap<String, String> poiProjectionMap = getPOIProjectionMap();
			if (POIFilter.isSearchKeywords(poiFilter)) {
				final String searchSelectionScore = POIFilter.getSearchSelectionScore(poiFilter.getSearchKeywords(), SEARCHABLE_LIKE_COLUMNS,
						SEARCHABLE_EQUAL_COLUMNS);
				poiProjectionMap.put(POIColumns.T_POI_K_SCORE_META_OPT, searchSelectionScore + " AS " + POIColumns.T_POI_K_SCORE_META_OPT);
			}
			qb.setProjectionMap(poiProjectionMap);
			String[] poiProjection = getPOIProjection();
			if (POIFilter.isSearchKeywords(poiFilter)) {
				poiProjection = ArrayUtils.addAll(poiProjection, new String[] { POIColumns.T_POI_K_SCORE_META_OPT });
			}
			String groupBy = null;
			if (POIFilter.isSearchKeywords(poiFilter)) {
				groupBy = POIColumns.T_POI_K_UUID_META;
			}
			String sortOrder = poiFilter.getExtraString("sortOrder", null);
			if (POIFilter.isSearchKeywords(poiFilter)) {
				sortOrder = POIColumns.T_POI_K_SCORE_META_OPT + " DESC";
			}
			final String limit = "";
			Cursor cursor = qb.query(getDBHelper().getReadableDatabase(), poiProjection, selection, null, groupBy, null, sortOrder, limit);
			return cursor;
		} catch (Throwable t) {
			MTLog.w(TAG, t, "Error while loading POIs '%s'!", poiFilter);
			return null;
		}
	}

	@Override
	public String[] getPOIProjection() {
		return PROJECTION_RTS_POI;
	}

	private static HashMap<String, String> poiProjectionMap;

	@Override
	public HashMap<String, String> getPOIProjectionMap() {
		if (poiProjectionMap == null) {
			poiProjectionMap = getNewProjectionMap(getAUTHORITY(getContext()));
		}
		return poiProjectionMap;
	}

	private static HashMap<String, String> getNewProjectionMap(String authority) {
		HashMap<String, String> newMap = new HashMap<String, String>();
		newMap.put(POIColumns.T_POI_K_UUID_META, SqlUtils.concatenate("'" + POI.POIUtils.UID_SEPARATOR + "'", //
				"'" + authority + "'", //
				GTFSRouteTripStopDbHelper.T_ROUTE + "." + GTFSRouteTripStopDbHelper.T_ROUTE_K_ID,//
				GTFSRouteTripStopDbHelper.T_TRIP + "." + GTFSRouteTripStopDbHelper.T_TRIP_K_ID, //
				GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_ID //
		) + " AS " + POIColumns.T_POI_K_UUID_META);
		newMap.put(POIColumns.T_POI_K_ID, GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_ID + " AS " + POIColumns.T_POI_K_ID);
		newMap.put(POIColumns.T_POI_K_NAME, GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_NAME + " AS " + POIColumns.T_POI_K_NAME);
		newMap.put(POIColumns.T_POI_K_LAT, GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_LAT + " AS " + POIColumns.T_POI_K_LAT);
		newMap.put(POIColumns.T_POI_K_LNG, GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_LNG + " AS " + POIColumns.T_POI_K_LNG);
		newMap.put(POIColumns.T_POI_K_TYPE, POI.ITEM_VIEW_TYPE_ROUTE_TRIP_STOP + " AS " + POIColumns.T_POI_K_TYPE);
		newMap.put(POIColumns.T_POI_K_STATUS_TYPE, POI.ITEM_STATUS_TYPE_SCHEDULE + " AS " + POIColumns.T_POI_K_STATUS_TYPE);
		newMap.put(POIColumns.T_POI_K_ACTIONS_TYPE, POI.ITEM_ACTION_TYPE_ROUTE_TRIP_STOP + " AS " + POIColumns.T_POI_K_ACTIONS_TYPE);
		newMap.put(RouteTripStopColumns.T_STOP_K_ID, GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_ID + " AS "
				+ RouteTripStopColumns.T_STOP_K_ID);
		newMap.put(RouteTripStopColumns.T_STOP_K_CODE, GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_CODE + " AS "
				+ RouteTripStopColumns.T_STOP_K_CODE);
		newMap.put(RouteTripStopColumns.T_STOP_K_NAME, GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_NAME + " AS "
				+ RouteTripStopColumns.T_STOP_K_NAME);
		newMap.put(RouteTripStopColumns.T_STOP_K_LAT, GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_LAT + " AS "
				+ RouteTripStopColumns.T_STOP_K_LAT);
		newMap.put(RouteTripStopColumns.T_STOP_K_LNG, GTFSRouteTripStopDbHelper.T_STOP + "." + GTFSRouteTripStopDbHelper.T_STOP_K_LNG + " AS "
				+ RouteTripStopColumns.T_STOP_K_LNG);
		newMap.put(RouteTripStopColumns.T_TRIP_STOPS_K_STOP_SEQUENCE, GTFSRouteTripStopDbHelper.T_TRIP_STOPS + "."
				+ GTFSRouteTripStopDbHelper.T_TRIP_STOPS_K_STOP_SEQUENCE + " AS " + RouteTripStopColumns.T_TRIP_STOPS_K_STOP_SEQUENCE);
		newMap.put(RouteTripStopColumns.T_TRIP_STOPS_K_DECENT_ONLY, GTFSRouteTripStopDbHelper.T_TRIP_STOPS + "."
				+ GTFSRouteTripStopDbHelper.T_TRIP_STOPS_K_DECENT_ONLY + " AS " + RouteTripStopColumns.T_TRIP_STOPS_K_DECENT_ONLY);
		newMap.put(RouteTripStopColumns.T_TRIP_K_ID, GTFSRouteTripStopDbHelper.T_TRIP + "." + GTFSRouteTripStopDbHelper.T_TRIP_K_ID + " AS "
				+ RouteTripStopColumns.T_TRIP_K_ID);
		newMap.put(RouteTripStopColumns.T_TRIP_K_HEADSIGN_TYPE, GTFSRouteTripStopDbHelper.T_TRIP + "." + GTFSRouteTripStopDbHelper.T_TRIP_K_HEADSIGN_TYPE
				+ " AS " + RouteTripStopColumns.T_TRIP_K_HEADSIGN_TYPE);
		newMap.put(RouteTripStopColumns.T_TRIP_K_HEADSIGN_VALUE, GTFSRouteTripStopDbHelper.T_TRIP + "." + GTFSRouteTripStopDbHelper.T_TRIP_K_HEADSIGN_VALUE
				+ " AS " + RouteTripStopColumns.T_TRIP_K_HEADSIGN_VALUE);
		newMap.put(RouteTripStopColumns.T_TRIP_K_ROUTE_ID, GTFSRouteTripStopDbHelper.T_TRIP + "." + GTFSRouteTripStopDbHelper.T_TRIP_K_ROUTE_ID + " AS "
				+ RouteTripStopColumns.T_TRIP_K_ROUTE_ID);
		newMap.put(RouteTripStopColumns.T_ROUTE_K_ID, GTFSRouteTripStopDbHelper.T_ROUTE + "." + GTFSRouteTripStopDbHelper.T_ROUTE_K_ID + " AS "
				+ RouteTripStopColumns.T_ROUTE_K_ID);
		newMap.put(RouteTripStopColumns.T_ROUTE_K_SHORT_NAME, GTFSRouteTripStopDbHelper.T_ROUTE + "." + GTFSRouteTripStopDbHelper.T_ROUTE_K_SHORT_NAME + " AS "
				+ RouteTripStopColumns.T_ROUTE_K_SHORT_NAME);
		newMap.put(RouteTripStopColumns.T_ROUTE_K_LONG_NAME, GTFSRouteTripStopDbHelper.T_ROUTE + "." + GTFSRouteTripStopDbHelper.T_ROUTE_K_LONG_NAME + " AS "
				+ RouteTripStopColumns.T_ROUTE_K_LONG_NAME);
		newMap.put(RouteTripStopColumns.T_ROUTE_K_COLOR, GTFSRouteTripStopDbHelper.T_ROUTE + "." + GTFSRouteTripStopDbHelper.T_ROUTE_K_COLOR + " AS "
				+ RouteTripStopColumns.T_ROUTE_K_COLOR);
		newMap.put(RouteTripStopColumns.T_ROUTE_K_TEXT_COLOR, GTFSRouteTripStopDbHelper.T_ROUTE + "." + GTFSRouteTripStopDbHelper.T_ROUTE_K_TEXT_COLOR + " AS "
				+ RouteTripStopColumns.T_ROUTE_K_TEXT_COLOR);
		return newMap;
	}

	@Override
	public String getPOITable() {
		return null; // USING CUSTOM TABLE
	}


	private void appendRouteTripStopSearch(Uri uri, SQLiteQueryBuilder qb) {
		String search = uri.getLastPathSegment().toLowerCase(Locale.ENGLISH);
		if (!TextUtils.isEmpty(search)) {
			String[] keywords = search.split(ContentProviderConstants.SEARCH_SPLIT_ON);
			StringBuilder inWhere = new StringBuilder();
			for (String keyword : keywords) {
				if (inWhere.length() > 0) {
					inWhere.append(" AND ");
				}
				inWhere.append("(");
				inWhere.append(GTFSRouteTripStopDbHelper.T_STOP).append(".").append(GTFSRouteTripStopDbHelper.T_STOP_K_CODE).append(" LIKE '%").append(keyword)
						.append("%'");
				inWhere.append(" OR ");
				inWhere.append(GTFSRouteTripStopDbHelper.T_ROUTE).append(".").append(GTFSRouteTripStopDbHelper.T_ROUTE_K_SHORT_NAME).append(" LIKE '%")
						.append(keyword).append("%'");
				inWhere.append(" OR ");
				inWhere.append(GTFSRouteTripStopDbHelper.T_ROUTE).append(".").append(GTFSRouteTripStopDbHelper.T_ROUTE_K_LONG_NAME).append(" LIKE '%")
						.append(keyword).append("%'");
				inWhere.append(" OR ");
				inWhere.append(GTFSRouteTripStopDbHelper.T_STOP).append(".").append(GTFSRouteTripStopDbHelper.T_STOP_K_NAME).append(" LIKE '%").append(keyword)
						.append("%'");
				inWhere.append(")");
			}
			qb.appendWhere(inWhere);
		}
	}

	@Override
	public String getSortOrder(Uri uri) {
		String sortOrder = POIProvider.getSortOrderS(this, uri);
		if (sortOrder != null) {
			return sortOrder;
		}
		sortOrder = StatusProvider.getSortOrderS(this, uri);
		if (sortOrder != null) {
			return sortOrder;
		}
		switch (getURIMATCHER(getContext()).match(uri)) {
		case ROUTES:
			return ROUTE_SORT_ORDER;
		case TRIPS:
			return TRIP_SORT_ORDER;
		case STOPS:
			return STOP_SORT_ORDER;
		case ROUTES_TRIPS_STOPS:
		case ROUTES_TRIPS_STOPS_SEARCH:
			return ROUTE_TRIP_STOP_SORT_ORDER;
		case TRIPS_STOPS:
			return TRIP_STOP_SORT_ORDER;
		case ROUTES_TRIPS:
			return ROUTE_TRIP_SORT_ORDER;
		case ROUTE_LOGO:
			return null;
		default:
			return super.getSortOrder(uri);
		}
	}

	public static final String ROUTE_CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd." + Constants.MAIN_APP_PACKAGE_NAME + ".route";
	public static final String ROUTE_CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd." + Constants.MAIN_APP_PACKAGE_NAME + ".route";
	public static final String TRIP_CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd." + Constants.MAIN_APP_PACKAGE_NAME + ".trip";
	public static final String TRIP_CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd." + Constants.MAIN_APP_PACKAGE_NAME + ".trip";
	public static final String STOP_CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd." + Constants.MAIN_APP_PACKAGE_NAME + ".stop";
	public static final String STOP_CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd." + Constants.MAIN_APP_PACKAGE_NAME + ".stop";
	public static final String ROUTE_TRIP_STOP_CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd." + Constants.MAIN_APP_PACKAGE_NAME
			+ ".routetripstop";
	public static final String ROUTE_TRIP_STOP_CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd." + Constants.MAIN_APP_PACKAGE_NAME
			+ ".routetripstop";
	public static final String TRIP_STOP_CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd." + Constants.MAIN_APP_PACKAGE_NAME + ".tripstop";
	public static final String TRIP_STOP_CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd." + Constants.MAIN_APP_PACKAGE_NAME + ".tripstop";
	public static final String ROUTE_TRIP_CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd." + Constants.MAIN_APP_PACKAGE_NAME + ".routetrip";
	public static final String ROUTE_TRIP_CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd." + Constants.MAIN_APP_PACKAGE_NAME + ".routetrip";

	@Override
	public String getTypeMT(Uri uri) {
		String type = POIProvider.getTypeS(this, uri);
		if (type != null) {
			return type;
		}
		type = StatusProvider.getTypeS(this, uri);
		if (type != null) {
			return type;
		}
		switch (getURIMATCHER(getContext()).match(uri)) {
		case ROUTES:
			return ROUTE_CONTENT_TYPE;
		case TRIPS:
			return TRIP_CONTENT_TYPE;
		case STOPS:
			return STOP_CONTENT_TYPE;
		case ROUTES_TRIPS_STOPS:
		case ROUTES_TRIPS_STOPS_SEARCH:
			return ROUTE_TRIP_STOP_CONTENT_TYPE;
		case ROUTES_TRIPS:
			return ROUTE_TRIP_CONTENT_TYPE;
		case TRIPS_STOPS:
			return TRIP_STOP_CONTENT_TYPE;
		case ROUTE_LOGO:
			return null;
		default:
			return super.getTypeMT(uri);
		}
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

	@Override
	public boolean isAgencyDeployed() {
		return SqlUtils.isDbExist(getContext(), getDbName());
	}

	@Override
	public boolean isAgencySetupRequired() {
		boolean setupRequired = false;
		if (currentDbVersion > 0 && currentDbVersion != getCurrentDbVersion()) {
			setupRequired = true;
		} else if (!SqlUtils.isDbExist(getContext(), getDbName())) {
			setupRequired = true;
		} else if (SqlUtils.getCurrentDbVersion(getContext(), getDbName()) != getCurrentDbVersion()) {
			setupRequired = true;
		}
		return setupRequired;
	}

	@Override
	public UriMatcher getAgencyUriMatcher() {
		return getURIMATCHER(getContext());
	}

	@Override
	public int getAgencyType() {
		return getAGENCYTYPE(getContext());
	}

	@Override
	public int getStatusType() {
		return POI.ITEM_STATUS_TYPE_SCHEDULE;
	}

	@Override
	public int getAgencyVersion() {
		return getCurrentDbVersion();
	}

	/**
	 * Override if multiple {@link GTFSRouteTripStopProvider} implementations in same app.
	 */
	public String getDbName() {
		return GTFSRouteTripStopDbHelper.DB_NAME;
	}

	/**
	 * Override if multiple {@link GTFSRouteTripStopProvider} implementations in same app.
	 */
	@Override
	public int getAgencyLabelResId() {
		return R.string.gtfs_rts_label;
	}

	/**
	 * Override if multiple {@link GTFSRouteTripStopProvider} implementations in same app.
	 */
	@Override
	public String getAgencyColorString(Context context) {
		return context.getString(R.string.gtfs_rts_color);
	}

	/**
	 * Override if multiple {@link GTFSRouteTripStopProvider} implementations in same app.
	 */
	@Override
	public int getAgencyShortNameResId() {
		return R.string.gtfs_rts_short_name;
	}

	/**
	 * Override if multiple {@link GTFSRouteTripStopProvider} implementations in same app.
	 */
	@Override
	public Area getAgencyArea(Context context) {
		final String minLatS = context.getString(R.string.gtfs_rts_area_min_lat);
		if (TextUtils.isEmpty(minLatS)) {
			return null;
		}
		final double minLat = Double.parseDouble(minLatS);
		final String maxLatS = context.getString(R.string.gtfs_rts_area_max_lat);
		if (TextUtils.isEmpty(maxLatS)) {
			return null;
		}
		final double maxLat = Double.parseDouble(maxLatS);
		final String minLngS = context.getString(R.string.gtfs_rts_area_min_lng);
		if (TextUtils.isEmpty(minLngS)) {
			return null;
		}
		final double minLng = Double.parseDouble(minLngS);
		final String maxLngS = context.getString(R.string.gtfs_rts_area_max_lng);
		if (TextUtils.isEmpty(maxLngS)) {
			return null;
		}
		final double maxLng = Double.parseDouble(maxLngS);
		return new Area(minLat, maxLat, minLng, maxLng);
	}

	/**
	 * Override if multiple {@link GTFSRouteTripStopProvider} implementations in same app.
	 */
	public int getCurrentDbVersion() {
		return GTFSRouteTripStopDbHelper.getDbVersion(getContext());
	}

	/**
	 * Override if multiple {@link GTFSRouteTripStopProvider} implementations in same app.
	 */
	public GTFSRouteTripStopDbHelper getNewDbHelper(Context context) {
		return new GTFSRouteTripStopDbHelper(context.getApplicationContext());
	}

	private static String routeLogo = null;

	/**
	 * Override if multiple {@link GTFSRouteTripStopProvider} implementations in same app.
	 */
	private Cursor getRouteLogo() {
		if (routeLogo == null) {
			routeLogo = readRouteLogo();
		}
		if (routeLogo == null || routeLogo.length() == 0) {
			return null;
		}
		MatrixCursor matrixCursor = new MatrixCursor(new String[] { "routeLogo" });
		matrixCursor.addRow(new Object[] { routeLogo });
		return matrixCursor;
	}

	/**
	 * Override if multiple {@link GTFSRouteTripStopProvider} implementations in same app.
	 */
	private String readRouteLogo() {
		BufferedReader br = null;
		try {
			StringBuilder routeLogoSb = new StringBuilder();
			int file = R.raw.gtfs_rts_route_logo;
			String line;
			br = new BufferedReader(new InputStreamReader(getContext().getResources().openRawResource(file), "UTF8"), 8192);
			while ((line = br.readLine()) != null) {
				routeLogoSb.append(line);
			}
			return routeLogoSb.toString();
		} catch (Exception e) {
			MTLog.w(this, e, "Error while loading route logo!");
			return StringUtils.EMPTY; // empty string = done
		} finally {
			try {
				if (br != null) {
					br.close();
				}
			} catch (Exception e) {
				MTLog.w(this, e, "ERROR while closing the input stream!");
			}
		}
	}

	public static class RouteColumns {
		public static final String T_ROUTE_K_ID = BaseColumns._ID;
		public static final String T_ROUTE_K_SHORT_NAME = "short_name";
		public static final String T_ROUTE_K_LONG_NAME = "long_name";
		public static final String T_ROUTE_K_COLOR = "color";
		public static final String T_ROUTE_K_TEXT_COLOR = "text_color";
	}

	private static class RouteTripColumns {
		private static final String T_ROUTE = "route";
		public static final String T_ROUTE_K_ID = T_ROUTE + BaseColumns._ID;
		public static final String T_ROUTE_K_SHORT_NAME = T_ROUTE + "_" + "short_name";
		public static final String T_ROUTE_K_LONG_NAME = T_ROUTE + "_" + "long_name";
		public static final String T_ROUTE_K_COLOR = T_ROUTE + "_" + "color";
		public static final String T_ROUTE_K_TEXT_COLOR = T_ROUTE + "_" + "text_color";
		private static final String T_TRIP = "trip";
		public static final String T_TRIP_K_ID = T_TRIP + BaseColumns._ID;
		public static final String T_TRIP_K_HEADSIGN_TYPE = T_TRIP + "_" + "headsign_type";
		public static final String T_TRIP_K_HEADSIGN_VALUE = T_TRIP + "_" + "headsign_value";
		public static final String T_TRIP_K_ROUTE_ID = T_TRIP + "_" + "route_id";
	}

	public static class RouteTripStopColumns {
		private static final String T_ROUTE = "route";
		public static final String T_ROUTE_K_ID = T_ROUTE + BaseColumns._ID;
		public static final String T_ROUTE_K_SHORT_NAME = T_ROUTE + "_" + "short_name";
		public static final String T_ROUTE_K_LONG_NAME = T_ROUTE + "_" + "long_name";
		public static final String T_ROUTE_K_COLOR = T_ROUTE + "_" + "color";
		public static final String T_ROUTE_K_TEXT_COLOR = T_ROUTE + "_" + "text_color";
		private static final String T_TRIP = "trip";
		public static final String T_TRIP_K_ID = T_TRIP + BaseColumns._ID;
		public static final String T_TRIP_K_HEADSIGN_TYPE = T_TRIP + "_" + "headsign_type";
		public static final String T_TRIP_K_HEADSIGN_VALUE = T_TRIP + "_" + "headsign_value";
		public static final String T_TRIP_K_ROUTE_ID = T_TRIP + "_" + "route_id";
		private static final String T_STOP = "stop";
		public static final String T_STOP_K_ID = T_STOP + BaseColumns._ID;
		public static final String T_STOP_K_CODE = T_STOP + "_" + "code";
		public static final String T_STOP_K_NAME = T_STOP + "_" + "name";
		public static final String T_STOP_K_LAT = T_STOP + "_" + "lat";
		public static final String T_STOP_K_LNG = T_STOP + "_" + "lng";
		private static final String T_TRIP_STOPS = "trip_stops";
		public static final String T_TRIP_STOPS_K_STOP_SEQUENCE = T_TRIP_STOPS + "_" + "stop_sequence";
		public static final String T_TRIP_STOPS_K_DECENT_ONLY = T_TRIP_STOPS + "_" + "decent_only";
	}

	public static class StopColumns {
		public static final String T_STOP_K_ID = BaseColumns._ID;
		public static final String T_STOP_K_CODE = "code";
		public static final String T_STOP_K_NAME = "name";
		public static final String T_STOP_K_LAT = "lat";
		public static final String T_STOP_K_LNG = "lng";
	}

	public static class TripColumns {
		public static final String T_TRIP_K_ID = BaseColumns._ID;
		public static final String T_TRIP_K_HEADSIGN_TYPE = "headsign_type";
		public static final String T_TRIP_K_HEADSIGN_VALUE = "headsign_value";
		public static final String T_TRIP_K_ROUTE_ID = "route_id";
	}

	private static class TripStopColumns {
		private static final String T_TRIP = "trip";
		public static final String T_TRIP_K_ID = T_TRIP + BaseColumns._ID;
		public static final String T_TRIP_K_HEADSIGN_TYPE = T_TRIP + "_" + "headsign_type";
		public static final String T_TRIP_K_HEADSIGN_VALUE = T_TRIP + "_" + "headsign_value";
		public static final String T_TRIP_K_ROUTE_ID = T_TRIP + "_" + "route_id";
		private static final String T_STOP = "stop";
		public static final String T_STOP_K_ID = T_STOP + BaseColumns._ID;
		public static final String T_STOP_K_CODE = T_STOP + "_" + "code";
		public static final String T_STOP_K_NAME = T_STOP + "_" + "name";
		public static final String T_STOP_K_LAT = T_STOP + "_" + "lat";
		public static final String T_STOP_K_LNG = T_STOP + "_" + "lng";
		private static final String T_TRIP_STOPS = "trip_stops";
		public static final String T_TRIP_STOPS_K_STOP_SEQUENCE = T_TRIP_STOPS + "_" + "stop_sequence";
		public static final String T_TRIP_STOPS_K_DECENT_ONLY = T_TRIP_STOPS + "_" + "decent_only";
	}

}
