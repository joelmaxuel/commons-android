package org.mtransit.android.commons.provider;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.mtransit.android.commons.LocationUtils.Area;
import org.mtransit.android.commons.MTLog;
import org.mtransit.android.commons.PackageManagerUtils;
import org.mtransit.android.commons.R;
import org.mtransit.android.commons.SqlUtils;
import org.mtransit.android.commons.UriUtils;
import org.mtransit.android.commons.WordUtils;
import org.mtransit.android.commons.data.BikeStation;
import org.mtransit.android.commons.data.BikeStationAvailabilityPercent;
import org.mtransit.android.commons.data.POI;
import org.mtransit.android.commons.data.POIStatus;

import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.text.TextUtils;

public abstract class BikeStationProvider extends AgencyProvider implements POIProviderContract, StatusProviderContract {

	private static final String TAG = BikeStationProvider.class.getSimpleName();

	@Override
	public String getLogTag() {
		return TAG;
	}

	private static final long BIKE_STATION_MAX_VALIDITY_IN_MS = 1 * 7 * 24 * 60 * 60 * 1000; // 1 week
	private static final long BIKE_STATION_VALIDITY_IN_MS = 1 * 24 * 60 * 60 * 1000; // 1 day

	private static final long BIKE_STATION_STATUS_MAX_VALIDITY_IN_MS = 30 * 60 * 1000; // 30 minutes
	private static final long BIKE_STATION_STATUS_VALIDITY_IN_MS = 5 * 60 * 1000; // 5 minutes
	private static final long BIKE_STATION_STATUS_MIN_DURATION_BETWEEN_REFRESH_IN_MS = 1 * 60 * 1000; // 1 minute

	private static final int AGENCY_TYPE = 100;

	public static final HashMap<String, String> POI_PROJECTION_MAP;
	static {
		POI_PROJECTION_MAP = POIProvider.POI_PROJECTION_MAP;
	}

	private static BikeStationDbHelper dbHelper;

	private static int currentDbVersion = -1;

	private static UriMatcher uriMatcher = null;

	/**
	 * Override if multiple {@link BikeStationProvider} implementations in same app.
	 */
	public static UriMatcher getURIMATCHER(Context context) {
		if (uriMatcher == null) {
			uriMatcher = getNewUriMatcher(getAUTHORITY(context));
		}
		return uriMatcher;
	}

	private static String authority = null;

	/**
	 * Override if multiple {@link BikeStationProvider} implementations in same app.
	 */
	public static String getAUTHORITY(Context context) {
		if (authority == null) {
			authority = context.getResources().getString(R.string.bike_station_authority);
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

	private static String dataUrl = null;

	/**
	 * Override if multiple {@link BikeStationProvider} implementations in same app.
	 */
	public static String getDATA_URL(Context context) {
		if (dataUrl == null) {
			dataUrl = context.getResources().getString(R.string.bike_station_data_url);
		}
		return dataUrl;
	}

	private static int value1Color = -1;

	/**
	 * Override if multiple {@link BikeStationProvider} implementations in same app.
	 */
	public static int getValue1Color(Context context) {
		if (value1Color < 0) {
			value1Color = context.getResources().getColor(R.color.bike_station_value1_color);
		}
		return value1Color;
	}

	private static int value1ColorBg = -1;

	/**
	 * Override if multiple {@link BikeStationProvider} implementations in same app.
	 */
	public static int getValue1ColorBg(Context context) {
		if (value1ColorBg < 0) {
			value1ColorBg = context.getResources().getColor(R.color.bike_station_value1_color_bg);
		}
		return value1ColorBg;
	}

	private static int value2Color = -1;

	/**
	 * Override if multiple {@link BikeStationProvider} implementations in same app.
	 */
	public static int getValue2Color(Context context) {
		if (value2Color < 0) {
			value2Color = context.getResources().getColor(R.color.bike_station_value2_color);
		}
		return value2Color;
	}

	private static int value2ColorBg = -1;

	/**
	 * Override if multiple {@link BikeStationProvider} implementations in same app.
	 */
	public static int getValue2ColorBg(Context context) {
		if (value2ColorBg < 0) {
			value2ColorBg = context.getResources().getColor(R.color.bike_station_value2_color_bg);
		}
		return value2ColorBg;
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

	private BikeStationDbHelper getDBHelper(Context context) {
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
	public Cursor getPOI(POIFilter poiFilter) {
		return getPOIBikeStations(poiFilter);
	}

	public abstract Cursor getPOIBikeStations(POIFilter poiFilter);

	@Override
	public Cursor getPOIFromDB(POIFilter poiFilter) {
		return POIProvider.getPOIFromDB(poiFilter, this);
	}

	@Override
	public POIStatus getNewStatus(StatusFilter filter) {
		if (!(filter instanceof AvailabilityPercentStatusFilter)) {
			return null;
		}
		AvailabilityPercentStatusFilter availabilityPercentStatusFilter = (AvailabilityPercentStatusFilter) filter;
		return getNewBikeStationStatus(availabilityPercentStatusFilter);
	}

	public abstract POIStatus getNewBikeStationStatus(AvailabilityPercentStatusFilter filter);

	@Override
	public POIStatus cacheStatus(POIStatus newStatusToCache) {
		return StatusProvider.cacheStatusS(getContext(), this, newStatusToCache);
	}

	@Override
	public POIStatus getCachedStatus(String targetUUID) {
		return StatusProvider.getCachedStatusS(getContext(), this, targetUUID);
	}

	@Override
	public boolean purgeUselessCachedStatuses() {
		return StatusProvider.purgeUselessCachedStatuses(getContext(), this);
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
			throw new IllegalArgumentException(String.format("Unknown URI (query): '%s'", uri));
		} catch (Throwable t) {
			MTLog.w(this, t, "Error while resolving query '%s'!", uri);
			return null;
		}
	}

	public abstract void updateBikeStationDataIfRequired();

	public abstract void updateBikeStationStatusDataIfRequired(String targetUUID);

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
		return super.getSortOrder(uri);
	}

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
		return super.getTypeMT(uri);
	}

	@Override
	public int deleteMT(Uri uri, String selection, String[] selectionArgs) {
		MTLog.w(this, "The delete method is not available.");
		return 0;
	}

	protected int deleteAllBikeStationData() {
		int affectedRows = 0;
		try {
			affectedRows = getDBHelper(getContext()).getWritableDatabase().delete(BikeStationDbHelper.T_BIKE_STATION, null, null);
		} catch (Throwable t) {
			MTLog.w(this, t, "Error while deleting all bike station data!");
		}
		return affectedRows;
	}

	protected int deleteAllBikeStationStatusData() {
		int affectedRows = 0;
		try {
			affectedRows = getDBHelper(getContext()).getWritableDatabase().delete(BikeStationDbHelper.T_BIKE_STATION_STATUS, null, null);
		} catch (Throwable t) {
			MTLog.w(this, t, "Error while deleting all bike station status data!");
		}
		return affectedRows;
	}

	@Override
	public String getStatusDbTableName() {
		return BikeStationDbHelper.T_BIKE_STATION_STATUS;
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

	protected synchronized int insertBikeStations(Collection<BikeStation> bikeStations) {
		int affectedRows = 0;
		SQLiteDatabase db = null;
		try {
			db = getDBHelper(getContext()).getWritableDatabase();
			db.beginTransaction(); // start the transaction
			if (bikeStations != null) {
				for (BikeStation bikeStation : bikeStations) {
					final long rowId = db.insert(BikeStationDbHelper.T_BIKE_STATION, POIDbHelper.T_POI_K_ID, bikeStation.toContentValues());
					if (rowId > 0) {
						affectedRows++;
					}
				}
			}
			db.setTransactionSuccessful(); // mark the transaction as successful
		} catch (Exception e) {
			MTLog.w(this, e, "ERROR while applying batch update to the database!");
		} finally {
			try {
				if (db != null) {
					db.endTransaction(); // end the transaction
					db.close();
				}
			} catch (Exception e) {
				MTLog.w(this, e, "ERROR while closing the new database!");
			}
		}
		return affectedRows;
	}

	protected synchronized int insertBikeStationStatus(Collection<BikeStationAvailabilityPercent> bikeStationsStatus) {
		int affectedRows = 0;
		SQLiteDatabase db = null;
		try {
			db = getDBHelper(getContext()).getWritableDatabase();
			db.beginTransaction(); // start the transaction
			if (bikeStationsStatus != null) {
				for (BikeStationAvailabilityPercent status : bikeStationsStatus) {
					final long rowId = db.insert(BikeStationDbHelper.T_BIKE_STATION_STATUS, StatusDbHelper.T_STATUS_K_ID, status.toContentValues());
					if (rowId > 0) {
						affectedRows++;
					}
				}
			}
			db.setTransactionSuccessful(); // mark the transaction as successful
		} catch (Exception e) {
			MTLog.w(this, e, "ERROR while applying batch update to the database!");
		} finally {
			try {
				if (db != null) {
					db.endTransaction(); // end the transaction
					db.close();
				}
			} catch (Exception e) {
				MTLog.w(this, e, "ERROR while closing the new database!");
			}
		}
		return affectedRows;
	}

	public static UriMatcher getNewUriMatcher(String authority) {
		UriMatcher URI_MATCHER = AgencyProvider.getNewUriMatcher(authority);
		StatusProvider.append(URI_MATCHER, authority);
		POIProvider.append(URI_MATCHER, authority);
		return URI_MATCHER;
	}

	@Override
	public boolean isAgencyDeployed() {
		return SqlUtils.isDbExist(getContext(), getDbName());
	}

	@Override
	public boolean isAgencySetupRequired() {
		boolean setupRequired = false;
		if (currentDbVersion > 0 && currentDbVersion != getCurrentDbVersion()) {
			// live update required => update
			setupRequired = true;
		} else if (!SqlUtils.isDbExist(getContext(), getDbName())) {
			// not deployed => initialization
			setupRequired = true;
		} else if (SqlUtils.getCurrentDbVersion(getContext(), getDbName()) != getCurrentDbVersion()) {
			// update required => update
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
		return AGENCY_TYPE;// getAGENCYTYPE(getContext());
	}

	@Override
	public int getStatusType() {
		return POI.ITEM_STATUS_TYPE_AVAILABILITY_PERCENT;
	}

	@Override
	public int getAgencyVersion() {
		return getCurrentDbVersion();
	}

	/**
	 * Override if multiple {@link RouteTripStopProvider} implementations in same app.
	 */
	@Override
	public int getAgencyLabelResId() {
		return R.string.bike_station_label;
	}

	/**
	 * Override if multiple {@link RouteTripStopProvider} implementations in same app.
	 */
	@Override
	public Area getAgencyArea(Context context) {
		final String minLatS = context.getString(R.string.bike_station_area_min_lat);
		if (TextUtils.isEmpty(minLatS)) {
			return null;
		}
		final double minLat = Double.parseDouble(minLatS);
		final String maxLatS = context.getString(R.string.bike_station_area_max_lat);
		if (TextUtils.isEmpty(maxLatS)) {
			return null;
		}
		final double maxLat = Double.parseDouble(maxLatS);
		final String minLngS = context.getString(R.string.bike_station_area_min_lng);
		if (TextUtils.isEmpty(minLngS)) {
			return null;
		}
		final double minLng = Double.parseDouble(minLngS);
		final String maxLngS = context.getString(R.string.bike_station_area_max_lng);
		if (TextUtils.isEmpty(maxLngS)) {
			return null;
		}
		final double maxLng = Double.parseDouble(maxLngS);
		return new Area(minLat, maxLat, minLng, maxLng);
	}

	/**
	 * Override if multiple {@link BikeStationProvider} implementations in same app.
	 */
	public String getDbName() {
		return BikeStationDbHelper.DB_NAME;
	}

	@Override
	public UriMatcher getURIMATCHER() {
		return getURIMATCHER(getContext());
	}

	/**
	 * Override if multiple {@link BikeStationProvider} implementations in same app.
	 */
	public int getCurrentDbVersion() {
		return BikeStationDbHelper.getDbVersion(getContext());
	}

	/**
	 * Override if multiple {@link BikeStationProvider} implementations in same app.
	 */
	public BikeStationDbHelper getNewDbHelper(Context context) {
		return new BikeStationDbHelper(context.getApplicationContext());
	}

	public long getBIKE_STATION_MAX_VALIDITY_IN_MS() {
		return BIKE_STATION_MAX_VALIDITY_IN_MS;
	}

	public long getBIKE_STATION_VALIDITY_IN_MS() {
		return BIKE_STATION_VALIDITY_IN_MS;
	}

	@Override
	public long getStatusMaxValidityInMs() {
		return BIKE_STATION_STATUS_MAX_VALIDITY_IN_MS;
	}

	@Override
	public long getStatusValidityInMs() {
		return BIKE_STATION_STATUS_VALIDITY_IN_MS;
	}

	@Override
	public long getMinDurationBetweenRefreshInMs() {
		return BIKE_STATION_STATUS_MIN_DURATION_BETWEEN_REFRESH_IN_MS;
	}

	@Override
	public Map<String, String> getPOIProjectionMap() {
		return POI_PROJECTION_MAP;
	}

	@Override
	public String getPOITable() {
		return BikeStationDbHelper.T_BIKE_STATION;
	}

	protected static final Pattern CLEAN_SLASHES = Pattern.compile("(\\w)[\\s]*[/][\\s]*(\\w)");
	protected static final String CLEAN_SLASHES_REPLACEMENT = "$1 / $2";

	private static final Pattern CLEAN_DOUBLE_SPACES = Pattern.compile("\\s+");
	private static final String CLEAN_DOUBLE_SPACES_REPLACEMENT = " ";

	protected static final String PARENTHESE1 = "\\(";
	protected static final String PARENTHESE2 = "\\)";

	private static final Pattern CLEAN_PARENTHESE1 = Pattern.compile("[" + PARENTHESE1 + "][\\s]*(\\w)");
	private static final String CLEAN_PARENTHESE1_REPLACEMENT = PARENTHESE1 + "$1";
	private static final Pattern CLEAN_PARENTHESE2 = Pattern.compile("(\\w)[\\s]*[" + PARENTHESE2 + "]");
	private static final String CLEAN_PARENTHESE2_REPLACEMENT = "$1" + PARENTHESE2;

	public static String cleanBikeStationName(String name) {
		if (name == null || name.length() == 0) {
			return name;
		}
		// clean "/" => " / "
		name = CLEAN_SLASHES.matcher(name).replaceAll(CLEAN_SLASHES_REPLACEMENT);
		// clean parentheses
		name = CLEAN_PARENTHESE1.matcher(name).replaceAll(CLEAN_PARENTHESE1_REPLACEMENT);
		name = CLEAN_PARENTHESE2.matcher(name).replaceAll(CLEAN_PARENTHESE2_REPLACEMENT);
		// remove double white-spaces
		name = CLEAN_DOUBLE_SPACES.matcher(name).replaceAll(CLEAN_DOUBLE_SPACES_REPLACEMENT);
		// cLean-Up tHe caPItalIsaTIon
		name = WordUtils.capitalize(name, new char[] { ' ', '-', '/', '\'', '(' });
		return name.trim();
	}
}