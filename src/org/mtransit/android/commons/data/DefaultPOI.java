package org.mtransit.android.commons.data;

import org.json.JSONException;
import org.json.JSONObject;
import org.mtransit.android.commons.MTLog;
import org.mtransit.android.commons.provider.POIProvider.POIColumns;

import android.content.ContentValues;
import android.database.Cursor;

public class DefaultPOI implements POI {

	private static final String TAG = DefaultPOI.class.getSimpleName();

	@Override
	public String getLogTag() {
		return TAG;
	}

	private String authority;
	private int id;
	private String name;
	private double lat;
	private double lng;
	private int type = POI.ITEM_VIEW_TYPE_BASIC_POI;
	private int statusType = -1;
	private int actionsType = -1; // mandatory 2014-10-04 (ALPHA)

	public DefaultPOI(String authority, int type, int statusType, int actionsType) {
		this.authority = authority;
		this.type = type;
		this.statusType = statusType;
		this.actionsType = actionsType;
	}

	@Override
	public String toString() {
		return new StringBuilder(DefaultPOI.class.getSimpleName()).append('[') //
				.append("authority:").append(authority) //
				.append(',') //
				.append("id:").append(id) //
				.append(',') //
				.append("name:").append(name) //
				.append(',') //
				.append("type:").append(type) //
				.append(',') //
				.append("statusType:").append(statusType) //
				.append(',') //
				.append("actionsType:").append(actionsType) //
				.append(']').toString();
	}

	@Override
	public int getType() {
		return this.type;
	}

	@Override
	public void setType(int type) {
		this.type = type;
	}

	@Override
	public String getUUID() {
		return POI.POIUtils.getUUID(this.authority, getId());
	}

	@Override
	public int compareToAlpha(Context contextOrNull, POI another) {
		if (another == null) {
			return +1;
		}
		return this.getName().compareTo(another.getName());
	}

	@Override
	public String getAuthority() {
		return authority;
	}

	@Override
	public void setAuthority(String authority) {
		this.authority = authority;
	}

	@Override
	public void setId(int id) {
		this.id = id;
	}

	@Override
	public int getId() {
		return id;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public void setLat(Double lat) {
		this.lat = lat;
	}

	@Override
	public void setLng(Double lng) {
		this.lng = lng;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public Double getLat() {
		return lat;
	}

	@Override
	public Double getLng() {
		return lng;
	}

	@Override
	public boolean hasLocation() {
		return true;
	}


	@Override
	public int getStatusType() {
		return this.statusType;
	}

	@Override
	public void setStatusType(int statusType) {
		this.statusType = statusType;
	}


	@Override
	public int getActionsType() {
		return this.actionsType;
	}

	@Override
	public void setActionsType(int actionsType) {
		this.actionsType = actionsType;
	}


	@Override
	public ContentValues toContentValues() {
		final ContentValues values = new ContentValues();
		values.put(POIColumns.T_POI_K_ID, getId());
		values.put(POIColumns.T_POI_K_NAME, getName());
		values.put(POIColumns.T_POI_K_LAT, getLat());
		values.put(POIColumns.T_POI_K_LNG, getLng());
		values.put(POIColumns.T_POI_K_TYPE, getType());
		values.put(POIColumns.T_POI_K_STATUS_TYPE, getStatusType());
		values.put(POIColumns.T_POI_K_ACTIONS_TYPE, getActionsType());
		return values;
	}

	@Override
	public POI fromCursor(Cursor c, String authority) {
		return fromCursorStatic(c, authority);
	}

	public static DefaultPOI fromCursorStatic(Cursor c, String authority) {
		final DefaultPOI defaultPOI = new DefaultPOI(authority, POI.ITEM_VIEW_TYPE_BASIC_POI, -1, -1);
		fromCursor(c, defaultPOI);
		return defaultPOI;
	}

	public static void fromCursor(Cursor c, DefaultPOI defaultPOI) {
		defaultPOI.id = c.getInt(c.getColumnIndexOrThrow(POIColumns.T_POI_K_ID));
		defaultPOI.name = c.getString(c.getColumnIndexOrThrow(POIColumns.T_POI_K_NAME));
		defaultPOI.lat = c.getDouble(c.getColumnIndexOrThrow(POIColumns.T_POI_K_LAT));
		defaultPOI.lng = c.getDouble(c.getColumnIndexOrThrow(POIColumns.T_POI_K_LNG));
		defaultPOI.type = c.getInt(c.getColumnIndexOrThrow(POIColumns.T_POI_K_TYPE));
		defaultPOI.statusType = c.getInt(c.getColumnIndexOrThrow(POIColumns.T_POI_K_STATUS_TYPE));
		final int actionsTypeColumnIdx = c.getColumnIndex(POIColumns.T_POI_K_ACTIONS_TYPE);
		if (actionsTypeColumnIdx > 0) {
			defaultPOI.actionsType = c.getInt(actionsTypeColumnIdx);
		} else {
			defaultPOI.actionsType = -1;
		}
	}

	public static int getTypeFromCursor(Cursor c) {
		try {
			return c.getInt(c.getColumnIndexOrThrow(POIColumns.T_POI_K_TYPE));
		} catch (Exception e) {
			MTLog.w(TAG, e, "Error while retreiving POI type!");
			return POI.ITEM_VIEW_TYPE_BASIC_POI; // default
		}
	}

	@Override
	public POI fromJSON(JSONObject json) {
		try {
			final DefaultPOI defaultPOI = new DefaultPOI(authority, POI.ITEM_VIEW_TYPE_BASIC_POI, -1, -1);
			fromJSON(json, defaultPOI);
			return defaultPOI;
		} catch (JSONException jsone) {
			MTLog.w(TAG, jsone, "Error while parsing JSON '%s'!", json);
			return null;
		}
	}

	public static void fromJSON(JSONObject json, POI defaultPOI) throws JSONException {
		defaultPOI.setId(json.getInt("id"));
		defaultPOI.setName(json.getString("name"));
		defaultPOI.setLat(json.getDouble("lat"));
		defaultPOI.setLng(json.getDouble("lng"));
		defaultPOI.setType(json.getInt("type"));
		defaultPOI.setStatusType(json.getInt("statusType"));
		defaultPOI.setActionsType(json.optInt("actionsType", -1));
	}

	@Override
	public JSONObject toJSON() {
		// MTLog.v(TAG, "toJSON(%s)", routeTripStop);
		try {
			JSONObject json = new JSONObject();
			toJSON(this, json);
			return json;
		} catch (JSONException jsone) {
			MTLog.w(this, jsone, "Error while converting to JSON (%s)!", this);
			return null;
		}
	}

	public static final void toJSON(POI defaultPOI, JSONObject json) throws JSONException {
		json.put("authority", defaultPOI.getAuthority()) //
				.put("id", defaultPOI.getId()) //
				.put("name", defaultPOI.getName()) //
				.put("lat", defaultPOI.getLat()) //
				.put("lng", defaultPOI.getLng()) //
				.put("type", defaultPOI.getType()) //
				.put("statusType", defaultPOI.getStatusType()) //
				.put("actionsType", defaultPOI.getActionsType() //
				);
	}

}
