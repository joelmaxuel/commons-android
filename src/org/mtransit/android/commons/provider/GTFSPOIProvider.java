package org.mtransit.android.commons.provider;

import org.mtransit.android.commons.ArrayUtils;
import org.mtransit.android.commons.MTLog;
import org.mtransit.android.commons.R;
import org.mtransit.android.commons.SqlUtils;
import org.mtransit.android.commons.StringUtils;
import org.mtransit.android.commons.data.POI;

import android.app.SearchManager;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.v4.util.ArrayMap;

public class GTFSPOIProvider implements MTLog.Loggable {

	private static final String TAG = GTFSPOIProvider.class.getSimpleName();

	@Override
	public String getLogTag() {
		return TAG;
	}

	public static void append(UriMatcher uriMatcher, String authority) {
		POIProvider.append(uriMatcher, authority);
	}

	private static int agencyTypeId = -1;

	/**
	 * Override if multiple {@link GTFSProvider} implementations in same app.
	 */
	public static int getAGENCY_TYPE_ID(Context context) {
		if (agencyTypeId < 0) {
			agencyTypeId = context.getResources().getInteger(R.integer.gtfs_rts_agency_type);
		}
		return agencyTypeId;
	}

	public static Cursor queryS(GTFSProvider provider, Uri uri, String selection) {
		return POIProvider.queryS(provider, uri, selection);
	}

	public static String getSortOrderS(GTFSProvider provider, Uri uri) {
		return POIProvider.getSortOrderS(provider, uri);
	}

	public static String getTypeS(GTFSProvider provider, Uri uri) {
		return POIProvider.getTypeS(provider, uri);
	}

	public static Cursor getSearchSuggest(GTFSProvider provider, String query) {
		return POIProvider.getDefaultSearchSuggest(query, provider); // simple search suggest
	}

	public static String getSearchSuggestTable(GTFSProvider provider) {
		return GTFSProviderDbHelper.T_STOP; // simple search suggest
	}

	// @formatter:off
	private static final ArrayMap<String, String> SIMPLE_SEARCH_SUGGEST_PROJECTION_MAP = SqlUtils.ProjectionMapBuilder.getNew()
			.appendTableColumn(GTFSProviderDbHelper.T_STOP, GTFSProviderDbHelper.T_STOP_K_NAME, SearchManager.SUGGEST_COLUMN_TEXT_1) //
			.build();
	// @formatter:on

	public static ArrayMap<String, String> getSearchSuggestProjectionMap(GTFSProvider provider) {
		return SIMPLE_SEARCH_SUGGEST_PROJECTION_MAP; // simple search suggest
	}

	public static Cursor getPOI(GTFSProvider provider, POIProviderContract.Filter poiFilter) {
		return provider.getPOIFromDB(poiFilter);
	}

	private static final String[] SEARCHABLE_LIKE_COLUMNS = new String[] { //
			SqlUtils.getTableColumn(GTFSProviderDbHelper.T_STOP, GTFSProviderDbHelper.T_STOP_K_NAME),//
			SqlUtils.getTableColumn(GTFSProviderDbHelper.T_ROUTE, GTFSProviderDbHelper.T_ROUTE_K_LONG_NAME),//
	};
	private static final String[] SEARCHABLE_EQUAL_COLUMNS = new String[] { //
			SqlUtils.getTableColumn(GTFSProviderDbHelper.T_STOP, GTFSProviderDbHelper.T_STOP_K_CODE), //
			SqlUtils.getTableColumn(GTFSProviderDbHelper.T_ROUTE, GTFSProviderDbHelper.T_ROUTE_K_SHORT_NAME),//
	};

	public static Cursor getPOIFromDB(GTFSProvider provider, POIProviderContract.Filter poiFilter) {
		try {
			if (poiFilter == null) {
				return null;
			}
			String selection = poiFilter.getSqlSelection(POIProviderContract.Columns.T_POI_K_UUID_META, POIProviderContract.Columns.T_POI_K_LAT,
					POIProviderContract.Columns.T_POI_K_LNG, SEARCHABLE_LIKE_COLUMNS, SEARCHABLE_EQUAL_COLUMNS);
			boolean isDescentOnly = poiFilter.getExtraBoolean(GTFSProviderContract.POI_FILTER_EXTRA_DESCENT_ONLY, false);
			if (isDescentOnly) {
				if (selection == null) {
					selection = StringUtils.EMPTY;
				} else if (selection.length() > 0) {
					selection += SqlUtils.AND;
				}
				selection += SqlUtils.getWhereBooleanNotTrue(GTFSProviderContract.RouteTripStopColumns.T_TRIP_STOPS_K_DESCENT_ONLY);
			}
			SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
			qb.setTables(GTFSRTSProvider.ROUTE_TRIP_TRIP_STOPS_STOP_JOIN);
			ArrayMap<String, String> poiProjectionMap = provider.getPOIProjectionMap();
			if (POIProviderContract.Filter.isSearchKeywords(poiFilter)) {
				SqlUtils.appendProjection(poiProjectionMap,
						POIProviderContract.Filter.getSearchSelectionScore(poiFilter.getSearchKeywords(), SEARCHABLE_LIKE_COLUMNS, SEARCHABLE_EQUAL_COLUMNS),
						POIProviderContract.Columns.T_POI_K_SCORE_META_OPT);
			}
			qb.setProjectionMap(poiProjectionMap);

			String[] poiProjection = provider.getPOIProjection();
			if (POIProviderContract.Filter.isSearchKeywords(poiFilter)) {
				poiProjection = ArrayUtils.addAll(poiProjection, new String[] { POIProviderContract.Columns.T_POI_K_SCORE_META_OPT });
			}
			String groupBy = null;
			if (POIProviderContract.Filter.isSearchKeywords(poiFilter)) {
				groupBy = POIProviderContract.Columns.T_POI_K_UUID_META;
			}
			String sortOrder = poiFilter.getExtraString(POIProviderContract.POI_FILTER_EXTRA_SORT_ORDER, null);
			if (POIProviderContract.Filter.isSearchKeywords(poiFilter)) {
				sortOrder = SqlUtils.getSortOrderDescending(POIProviderContract.Columns.T_POI_K_SCORE_META_OPT);
			}
			return qb.query(provider.getDBHelper().getReadableDatabase(), poiProjection, selection, null, groupBy, null, sortOrder, null);
		} catch (Exception e) {
			MTLog.w(TAG, e, "Error while loading POIs '%s'!", poiFilter);
			return null;
		}
	}

	public static String[] getPOIProjection(GTFSProvider provider) {
		return GTFSProviderContract.PROJECTION_RTS_POI;
	}

	private static ArrayMap<String, String> poiProjectionMap;

	public static ArrayMap<String, String> getPOIProjectionMap(GTFSProvider provider) {
		if (poiProjectionMap == null) {
			poiProjectionMap = getNewProjectionMap(GTFSProvider.getAUTHORITY(provider.getContext()), getAGENCY_TYPE_ID(provider.getContext()));
		}
		return poiProjectionMap;
	}

	private static ArrayMap<String, String> getNewProjectionMap(String authority, int dataSourceTypeId) {
		// @formatter:off
		SqlUtils.ProjectionMapBuilder sb = SqlUtils.ProjectionMapBuilder.getNew() //
				.appendValue(SqlUtils.concatenate( //
						SqlUtils.escapeString(POI.POIUtils.UID_SEPARATOR), //
						SqlUtils.escapeString(authority), //
						SqlUtils.getTableColumn(GTFSProviderDbHelper.T_ROUTE, GTFSProviderDbHelper.T_ROUTE_K_ID),//
						SqlUtils.getTableColumn(GTFSProviderDbHelper.T_TRIP, GTFSProviderDbHelper.T_TRIP_K_ID), //
						SqlUtils.getTableColumn(GTFSProviderDbHelper.T_STOP, GTFSProviderDbHelper.T_STOP_K_ID) //
						), POIProviderContract.Columns.T_POI_K_UUID_META)
				.appendValue(dataSourceTypeId, POIProviderContract.Columns.T_POI_K_DST_ID_META) //
				.appendTableColumn(GTFSProviderDbHelper.T_STOP, GTFSProviderDbHelper.T_STOP_K_ID, POIProviderContract.Columns.T_POI_K_ID) //
				.appendTableColumn(GTFSProviderDbHelper.T_STOP, GTFSProviderDbHelper.T_STOP_K_NAME, POIProviderContract.Columns.T_POI_K_NAME) //
				.appendTableColumn(GTFSProviderDbHelper.T_STOP, GTFSProviderDbHelper.T_STOP_K_LAT, POIProviderContract.Columns.T_POI_K_LAT) //
				.appendTableColumn(GTFSProviderDbHelper.T_STOP, GTFSProviderDbHelper.T_STOP_K_LNG, POIProviderContract.Columns.T_POI_K_LNG) //
				.appendValue(POI.ITEM_VIEW_TYPE_ROUTE_TRIP_STOP, POIProviderContract.Columns.T_POI_K_TYPE) //
				.appendValue(POI.ITEM_STATUS_TYPE_SCHEDULE, POIProviderContract.Columns.T_POI_K_STATUS_TYPE) //
				.appendValue(POI.ITEM_ACTION_TYPE_ROUTE_TRIP_STOP, POIProviderContract.Columns.T_POI_K_ACTIONS_TYPE) //
				//
				.appendTableColumn(GTFSProviderDbHelper.T_STOP, GTFSProviderDbHelper.T_STOP_K_ID, GTFSProviderContract.RouteTripStopColumns.T_STOP_K_ID) //
				.appendTableColumn(GTFSProviderDbHelper.T_STOP, GTFSProviderDbHelper.T_STOP_K_CODE, GTFSProviderContract.RouteTripStopColumns.T_STOP_K_CODE);
		sb //
				.appendTableColumn(GTFSProviderDbHelper.T_STOP, GTFSProviderDbHelper.T_STOP_K_NAME, GTFSProviderContract.RouteTripStopColumns.T_STOP_K_NAME) //
				.appendTableColumn(GTFSProviderDbHelper.T_STOP, GTFSProviderDbHelper.T_STOP_K_LAT, GTFSProviderContract.RouteTripStopColumns.T_STOP_K_LAT) //
				.appendTableColumn(GTFSProviderDbHelper.T_STOP, GTFSProviderDbHelper.T_STOP_K_LNG, GTFSProviderContract.RouteTripStopColumns.T_STOP_K_LNG) //
				//
				.appendTableColumn(GTFSProviderDbHelper.T_TRIP_STOPS, GTFSProviderDbHelper.T_TRIP_STOPS_K_STOP_SEQUENCE, GTFSProviderContract.RouteTripStopColumns.T_TRIP_STOPS_K_STOP_SEQUENCE) //
				.appendTableColumn(GTFSProviderDbHelper.T_TRIP_STOPS, GTFSProviderDbHelper.T_TRIP_STOPS_K_DESCENT_ONLY, GTFSProviderContract.RouteTripStopColumns.T_TRIP_STOPS_K_DESCENT_ONLY) //
				//
				.appendTableColumn(GTFSProviderDbHelper.T_TRIP, GTFSProviderDbHelper.T_TRIP_K_ID, GTFSProviderContract.RouteTripStopColumns.T_TRIP_K_ID) //
				.appendTableColumn(GTFSProviderDbHelper.T_TRIP, GTFSProviderDbHelper.T_TRIP_K_HEADSIGN_TYPE, GTFSProviderContract.RouteTripStopColumns.T_TRIP_K_HEADSIGN_TYPE) //
				.appendTableColumn(GTFSProviderDbHelper.T_TRIP, GTFSProviderDbHelper.T_TRIP_K_HEADSIGN_VALUE, GTFSProviderContract.RouteTripStopColumns.T_TRIP_K_HEADSIGN_VALUE) //
				.appendTableColumn(GTFSProviderDbHelper.T_TRIP, GTFSProviderDbHelper.T_TRIP_K_ROUTE_ID, GTFSProviderContract.RouteTripStopColumns.T_TRIP_K_ROUTE_ID) //
				//
				.appendTableColumn(GTFSProviderDbHelper.T_ROUTE, GTFSProviderDbHelper.T_ROUTE_K_ID, GTFSProviderContract.RouteTripStopColumns.T_ROUTE_K_ID) //
				.appendTableColumn(GTFSProviderDbHelper.T_ROUTE, GTFSProviderDbHelper.T_ROUTE_K_SHORT_NAME, GTFSProviderContract.RouteTripStopColumns.T_ROUTE_K_SHORT_NAME) //
				.appendTableColumn(GTFSProviderDbHelper.T_ROUTE, GTFSProviderDbHelper.T_ROUTE_K_LONG_NAME, GTFSProviderContract.RouteTripStopColumns.T_ROUTE_K_LONG_NAME) //
				.appendTableColumn(GTFSProviderDbHelper.T_ROUTE, GTFSProviderDbHelper.T_ROUTE_K_COLOR, GTFSProviderContract.RouteTripStopColumns.T_ROUTE_K_COLOR) //
		;
		return sb.build();
		// @formatter:on
	}

	public static String getPOITable(GTFSProvider provider) {
		return null; // USING CUSTOM TABLE
	}

	private static final long POI_MAX_VALIDITY_IN_MS = Long.MAX_VALUE;

	public static long getPOIMaxValidityInMs(GTFSProvider provider) {
		return POI_MAX_VALIDITY_IN_MS;
	}

	private static final long POI_VALIDITY_IN_MS = Long.MAX_VALUE;

	public static long getPOIValidityInMs(GTFSProvider provider) {
		return POI_VALIDITY_IN_MS;
	}
}
