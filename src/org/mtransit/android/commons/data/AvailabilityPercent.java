package org.mtransit.android.commons.data;

import org.json.JSONException;
import org.json.JSONObject;
import org.mtransit.android.commons.MTLog;

import android.database.Cursor;

public class AvailabilityPercent extends POIStatus implements MTLog.Loggable {

	private static final String TAG = AvailabilityPercent.class.getSimpleName();

	@Override
	public String getLogTag() {
		return TAG;
	}

	// the higher the status int value is, the more important it is
	private static final int STATUS_OK = 0;
	private static final int STATUS_LOCKED = 1;
	private static final int STATUS_NOT_PUBLIC = 99;
	private static final int STATUS_NOT_INSTALLED = 100;

	private int value1;
	private int value2;

	private String value1EmptyRes;
	private String value1QuantityRes;
	private int value1Color;
	private int value1ColorBg;

	private String value2EmptyRes;
	private String value2QuantityRes;
	private int value2Color;
	private int value2ColorBg;

	private int statusMsgId = STATUS_OK;

	public AvailabilityPercent(POIStatus status) {
		this(status.getId(), status.getTargetUUID(), status.getLastUpdateInMs(), status.getMaxValidityInMs());
	}

	public AvailabilityPercent(String targetUUID, long lastUpdateMs, long maxValidityInMs) {
		this(null, targetUUID, lastUpdateMs, maxValidityInMs);
	}

	public AvailabilityPercent(Integer id, String targetUUID, long lastUpdateMs, long maxValidityInMs) {
		super(id, targetUUID, POI.ITEM_STATUS_TYPE_AVAILABILITY_PERCENT, lastUpdateMs, maxValidityInMs);
	}

	@Override
	public boolean isUseful() {
		return super.isUseful();
	}

	public boolean hasValueStricklyLowerThan(int underThisValue) {
		return this.value1 < underThisValue || this.value2 < underThisValue;
	}

	public int getLowerValue() {
		return this.value1 < this.value2 ? this.value1 : this.value2;
	}

	public void setValue1(int value1) {
		this.value1 = value1;
	}

	public int getValue1() {
		return value1;
	}

	public void setValue1EmptyRes(String value1EmptyRes) {
		this.value1EmptyRes = value1EmptyRes;
	}

	public String getValue1EmptyRes() {
		return value1EmptyRes;
	}

	public void setValue1QuantityRes(String value1QuantityRes) {
		this.value1QuantityRes = value1QuantityRes;
	}

	public String getValue1QuantityRes() {
		return value1QuantityRes;
	}

	public void setValue1Color(int value1Color) {
		this.value1Color = value1Color;
	}

	public int getValue1Color() {
		return value1Color;
	}

	public void setValue1ColorBg(int value1ColorBg) {
		this.value1ColorBg = value1ColorBg;
	}

	public int getValue1ColorBg() {
		return value1ColorBg;
	}

	public void setValue2(int value2) {
		this.value2 = value2;
	}

	public int getValue2() {
		return value2;
	}

	public void setValue2EmptyRes(String value2EmptyRes) {
		this.value2EmptyRes = value2EmptyRes;
	}

	public String getValue2EmptyRes() {
		return value2EmptyRes;
	}

	public void setValue2QuantityRes(String value2QuantityRes) {
		this.value2QuantityRes = value2QuantityRes;
	}

	public String getValue2QuantityRes() {
		return value2QuantityRes;
	}

	public void setValue2Color(int value2Color) {
		this.value2Color = value2Color;
	}

	public int getValue2Color() {
		return value2Color;
	}

	public void setValue2ColorBg(int value2ColorBg) {
		this.value2ColorBg = value2ColorBg;
	}

	public int getValue2ColorBg() {
		return value2ColorBg;
	}

	public void setStatusInstalled(boolean installed) {
		if (!installed) {
			incStatusId(STATUS_NOT_INSTALLED);
		}
	}

	public void setStatusPublic(boolean isPublic) {
		if (!isPublic) {
			incStatusId(STATUS_NOT_PUBLIC);
		}
	}

	public void setStatusLocked(boolean locked) {
		if (locked) {
			incStatusId(STATUS_LOCKED);
		}
	}

	public void incStatusId(int statusId) {
		if (this.statusMsgId < statusId) {
			this.statusMsgId = statusId;
		}
	}

	public int getStatusMsgId() {
		return statusMsgId;
	}

	public static AvailabilityPercent fromCursor(Cursor cursor) {
		final POIStatus status = POIStatus.fromCursor(cursor);
		final String extrasJSONString = POIStatus.getExtrasFromCursor(cursor);
		return fromExtraJSONString(status, extrasJSONString);
	}

	private static AvailabilityPercent fromExtraJSONString(POIStatus status, String extrasJSONString) {
		try {
			return fromExtraJSON(status, new JSONObject(extrasJSONString));
		} catch (JSONException jsone) {
			MTLog.w(TAG, jsone, "Error while retreiving extras information from cursor.");
			return null;
		}
	}

	private static AvailabilityPercent fromExtraJSON(POIStatus status, JSONObject extrasJSON) {
		try {
			AvailabilityPercent availabilityPercent = new AvailabilityPercent(status);
			availabilityPercent.statusMsgId = extrasJSON.getInt("statusMsgId");
			availabilityPercent.value1 = extrasJSON.getInt("value1");
			availabilityPercent.value1EmptyRes = extrasJSON.getString("value1EmptyRes");
			availabilityPercent.value1QuantityRes = extrasJSON.getString("value1QuantityRes");
			availabilityPercent.value1Color = extrasJSON.getInt("value1Color");
			availabilityPercent.value1ColorBg = extrasJSON.getInt("value1ColorBg");
			availabilityPercent.value2 = extrasJSON.getInt("value2");
			availabilityPercent.value2EmptyRes = extrasJSON.getString("value2EmptyRes");
			availabilityPercent.value2QuantityRes = extrasJSON.getString("value2QuantityRes");
			availabilityPercent.value2Color = extrasJSON.getInt("value2Color");
			availabilityPercent.value2ColorBg = extrasJSON.getInt("value2ColorBg");
			return availabilityPercent;
		} catch (JSONException jsone) {
			MTLog.w(TAG, jsone, "Error while retreiving extras information from cursor.");
			return null;
		}
	}

	@Override
	public JSONObject getExtrasJSON() {
		try {
			JSONObject json = new JSONObject();
			json.put("statusMsgId", this.statusMsgId);
			json.put("value1", this.value1);
			json.put("value1EmptyRes", this.value1EmptyRes);
			json.put("value1QuantityRes", this.value1QuantityRes);
			json.put("value1Color", this.value1Color);
			json.put("value1ColorBg", this.value1ColorBg);
			json.put("value2", this.value2);
			json.put("value2EmptyRes", this.value2EmptyRes);
			json.put("value2QuantityRes", this.value2QuantityRes);
			json.put("value2Color", this.value2Color);
			json.put("value2ColorBg", this.value2ColorBg);
			return json;
		} catch (Exception e) {
			MTLog.w(TAG, e, "Error while converting object '%s' to JSON!", this);
			return null; // no partial result
		}
	}

}