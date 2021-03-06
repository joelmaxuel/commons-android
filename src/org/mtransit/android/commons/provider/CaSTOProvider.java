package org.mtransit.android.commons.provider;

import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLHandshakeException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

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
import org.mtransit.android.commons.data.News;
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
import android.support.v4.util.ArrayMap;
import android.text.Html;
import android.text.TextUtils;

@SuppressLint("Registered")
public class CaSTOProvider extends MTContentProvider implements NewsProviderContract {

	private static final String LOG_TAG = CaSTOProvider.class.getSimpleName();

	@Override
	public String getLogTag() {
		return LOG_TAG;
	}

	public static UriMatcher getNewUriMatcher(String authority) {
		UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
		NewsProvider.append(URI_MATCHER, authority);
		return URI_MATCHER;
	}

	@Nullable
	private static UriMatcher uriMatcher = null;

	/**
	 * Override if multiple {@link CaSTOProvider} implementations in same app.
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
	 * Override if multiple {@link CaSTOProvider} implementations in same app.
	 */
	@NonNull
	private static String getAUTHORITY(@NonNull Context context) {
		if (authority == null) {
			authority = context.getResources().getString(R.string.ca_sto_authority);
		}
		return authority;
	}

	@Nullable
	private static Uri authorityUri = null;

	/**
	 * Override if multiple {@link CaSTOProvider} implementations in same app.
	 */
	@NonNull
	private static Uri getAUTHORITY_URI(@NonNull Context context) {
		if (authorityUri == null) {
			authorityUri = UriUtils.newContentUri(getAUTHORITY(context));
		}
		return authorityUri;
	}

	@Nullable
	private static String newsAuthorName = null;

	/**
	 * Override if multiple {@link CaSTOProvider} implementations in same app.
	 */
	@NonNull
	private static String getNEWS_AUTHOR_NAME(@NonNull Context context) {
		if (newsAuthorName == null) {
			newsAuthorName = context.getResources().getString(R.string.ca_sto_news_author_name);
		}
		return newsAuthorName;
	}

	@Nullable
	private static String newsColor = null;

	/**
	 * Override if multiple {@link CaSTOProvider} implementations in same app.
	 */
	@NonNull
	private static String getNEWS_COLOR(@NonNull Context context) {
		if (newsColor == null) {
			newsColor = context.getResources().getString(R.string.ca_sto_news_color);
		}
		return newsColor;
	}

	@Nullable
	private static String newsTargetAuthority = null;

	/**
	 * Override if multiple {@link CaSTOProvider} implementations in same app.
	 */
	@NonNull
	private static String getNEWS_TARGET_AUTHORITY(@NonNull Context context) {
		if (newsTargetAuthority == null) {
			newsTargetAuthority = context.getResources().getString(R.string.ca_sto_news_target_for_poi_authority);
		}
		return newsTargetAuthority;
	}

	/**
	 * Override if multiple {@link CaSTOProvider} implementations in same app.
	 */
	private static final String PREF_KEY_AGENCY_NEWS_LAST_UPDATE_MS = CaSTODbHelper.PREF_KEY_AGENCY_NEWS_LAST_UPDATE_MS;

	/**
	 * Override if multiple {@link CaSTOProvider} implementations in same app.
	 */
	private static final String PREF_KEY_AGENCY_NEWS_LAST_UPDATE_LANG = CaSTODbHelper.PREF_KEY_AGENCY_NEWS_LAST_UPDATE_LANG;

	private static final long NEWS_MAX_VALIDITY_IN_MS = Long.MAX_VALUE; // FOREVER
	private static final long NEWS_VALIDITY_IN_MS = TimeUnit.DAYS.toMillis(1L);
	private static final long NEWS_VALIDITY_IN_FOCUS_IN_MS = TimeUnit.HOURS.toMillis(1L);
	private static final long NEWS_MIN_DURATION_BETWEEN_REFRESH_IN_MS = TimeUnit.MINUTES.toMillis(30L);
	private static final long NEWS_MIN_DURATION_BETWEEN_REFRESH_IN_FOCUS_IN_MS = TimeUnit.MINUTES.toMillis(10L);

	@Override
	public long getMinDurationBetweenNewsRefreshInMs(boolean inFocus) {
		if (inFocus) {
			return NEWS_MIN_DURATION_BETWEEN_REFRESH_IN_FOCUS_IN_MS;
		}
		return NEWS_MIN_DURATION_BETWEEN_REFRESH_IN_MS;
	}

	@Override
	public long getNewsMaxValidityInMs() {
		return NEWS_MAX_VALIDITY_IN_MS;
	}

	@Override
	public long getNewsValidityInMs(boolean inFocus) {
		if (inFocus) {
			return NEWS_VALIDITY_IN_FOCUS_IN_MS;
		}
		return NEWS_VALIDITY_IN_MS;
	}

	@Override
	public boolean purgeUselessCachedNews() {
		return NewsProvider.purgeUselessCachedNews(this);
	}

	@Override
	public boolean deleteCachedNews(Integer serviceUpdateId) {
		return NewsProvider.deleteCachedNews(this, serviceUpdateId);
	}

	private static final String AGENCY_SOURCE_ID = "sto_ca_inforeseauxml";

	private static final String AGENCY_SOURCE_LABEL = "sto.ca";

	private int deleteAllAgencyNewsData() {
		int affectedRows = 0;
		try {
			String selection = SqlUtils.getWhereEqualsString(NewsProviderContract.Columns.T_NEWS_K_SOURCE_ID, AGENCY_SOURCE_ID);
			affectedRows = getDBHelper().getWritableDatabase().delete(getNewsDbTableName(), selection, null);
		} catch (Exception e) {
			MTLog.w(this, e, "Error while deleting all agency news data!");
		}
		return affectedRows;
	}

	@Override
	public void cacheNews(ArrayList<News> newNews) {
		NewsProvider.cacheNewsS(this, newNews);
	}

	@Override
	public ArrayList<News> getCachedNews(NewsProviderContract.Filter newsFilter) {
		if (newsFilter == null) {
			MTLog.w(this, "getCachedNews() > skip (no news filter)");
			return null;
		}
		ArrayList<News> cachedNews = NewsProvider.getCachedNewsS(this, newsFilter);
		return cachedNews;
	}

	@Override
	public Cursor getNewsFromDB(NewsProviderContract.Filter newsFilter) {
		return NewsProvider.getDefaultNewsFromDB(newsFilter, this);
	}

	@Nullable
	private static Collection<String> languages = null;

	@NonNull
	@Override
	public Collection<String> getNewsLanguages() {
		if (languages == null) {
			languages = new HashSet<>();
			if (isLanguageFrench()) {
				languages.add(Locale.FRENCH.getLanguage());
			} else {
				languages.add(Locale.ENGLISH.getLanguage());
			}
		}
		return languages;
	}

	private static boolean isLanguageFrench() {
		return LocaleUtils.isFR();
	}

	@Override
	public ArrayList<News> getNewNews(NewsProviderContract.Filter newsFilter) {
		if (newsFilter == null) {
			MTLog.w(this, "getNewNews() > no new service update (filter null)");
			return null;
		}
		updateAgencyNewsDataIfRequired(newsFilter.isInFocusOrDefault());
		ArrayList<News> cachedNews = getCachedNews(newsFilter);
		return cachedNews;
	}

	private void updateAgencyNewsDataIfRequired(boolean inFocus) {
		long lastUpdateInMs = PreferenceUtils.getPrefLcl(getContext(), PREF_KEY_AGENCY_NEWS_LAST_UPDATE_MS, 0L);
		String lastUpdateLang = PreferenceUtils.getPrefLcl(getContext(), PREF_KEY_AGENCY_NEWS_LAST_UPDATE_LANG, StringUtils.EMPTY);
		long minUpdateMs = Math.min(getNewsMaxValidityInMs(), getNewsValidityInMs(inFocus));
		long nowInMs = TimeUtils.currentTimeMillis();
		if (lastUpdateInMs + minUpdateMs > nowInMs && LocaleUtils.getDefaultLanguage().equals(lastUpdateLang)) {
			return;
		}
		updateAgencyNewsDataIfRequiredSync(lastUpdateInMs, lastUpdateLang, inFocus);
	}

	private synchronized void updateAgencyNewsDataIfRequiredSync(long lastUpdateInMs, String lastUpdateLang, boolean inFocus) {
		if (PreferenceUtils.getPrefLcl(getContext(), PREF_KEY_AGENCY_NEWS_LAST_UPDATE_MS, 0L) > lastUpdateInMs
				&& LocaleUtils.getDefaultLanguage().equals(lastUpdateLang)) {
			return; // too late, another thread already updated
		}
		long nowInMs = TimeUtils.currentTimeMillis();
		boolean deleteAllRequired = false;
		if (lastUpdateInMs + getNewsMaxValidityInMs() < nowInMs || !LocaleUtils.getDefaultLanguage().equals(lastUpdateLang)) {
			deleteAllRequired = true; // too old to display
		}
		long minUpdateMs = Math.min(getNewsMaxValidityInMs(), getNewsValidityInMs(inFocus));
		if (deleteAllRequired || lastUpdateInMs + minUpdateMs < nowInMs) {
			updateAllAgencyNewsDataFromWWW(deleteAllRequired); // try to update
		}
	}

	private void updateAllAgencyNewsDataFromWWW(boolean deleteAllRequired) {
		boolean deleteAllDone = false;
		if (deleteAllRequired) {
			deleteAllAgencyNewsData();
			deleteAllDone = true;
		}
		ArrayList<News> newNews = loadAgencyNewsDataFromWWW();
		if (newNews != null) { // empty is OK
			long nowInMs = TimeUtils.currentTimeMillis();
			if (!deleteAllDone) {
				deleteAllAgencyNewsData();
			}
			cacheNews(newNews);
			PreferenceUtils.savePrefLcl(getContext(), PREF_KEY_AGENCY_NEWS_LAST_UPDATE_MS, nowInMs, true); // sync
			PreferenceUtils.savePrefLcl(getContext(), PREF_KEY_AGENCY_NEWS_LAST_UPDATE_LANG, LocaleUtils.getDefaultLanguage(), true); // sync
		} // else keep whatever we have until max validity reached
	}

	private static final String AGENCY_NEWS_URL_FR = "http://www.sto.ca/index.php?id=inforeseauxml&L=fr";
	private static final String AGENCY_NEWS_URL_EN = "http://www.sto.ca/index.php?id=inforeseauxml&L=en";

	private ArrayList<News> loadAgencyNewsDataFromWWW() {
		try {
			ArrayList<News> newNews = new ArrayList<>();
			String urlString;
			if (isLanguageFrench()) {
				urlString = AGENCY_NEWS_URL_FR;
			} else {
				urlString = AGENCY_NEWS_URL_EN;
			}
			ArrayList<News> feedNews = loadAgencyNewsDataFromWWW(urlString);
			if (feedNews != null) {
				newNews.addAll(feedNews);
			}
			return newNews;
		} catch (Exception e) {
			MTLog.e(LOG_TAG, e, "INTERNAL ERROR: Unknown Exception");
			return null;
		}
	}

	private static final String AUTHOR_URL_EN = "http://m.sto.ca/en/system-info";
	private static final String AUTHOR_URL_FR = "http://m.sto.ca/fr/info-reseau/";

	private static final String PRIVATE_FILE_NAME = "rss.xml";

	private static final String ENCODING = FileUtils.UTF_8;

	private ArrayList<News> loadAgencyNewsDataFromWWW(String urlString) {
		try {
			URL url = new URL(urlString);
			URLConnection urlc = url.openConnection();
			HttpURLConnection httpUrlConnection = (HttpURLConnection) urlc;
			switch (httpUrlConnection.getResponseCode()) {
			case HttpURLConnection.HTTP_OK:
				long newLastUpdateInMs = TimeUtils.currentTimeMillis();
				SAXParserFactory spf = SAXParserFactory.newInstance();
				SAXParser sp = spf.newSAXParser();
				XMLReader xr = sp.getXMLReader();
				String authority = getAUTHORITY(getContext());
				int severity = getContext().getResources().getInteger(R.integer.news_provider_severity_info_related_poi);
				long noteworthyInMs = Long.parseLong(getContext().getResources().getString(R.string.news_provider_noteworthy_long_term));
				long maxValidityInMs = getNewsMaxValidityInMs();
				String target = getNEWS_TARGET_AUTHORITY(getContext());
				String color = getNEWS_COLOR(getContext());
				String authorName = getNEWS_AUTHOR_NAME(getContext());
				String authorUrl = isLanguageFrench() ? AUTHOR_URL_FR : AUTHOR_URL_EN;
				String label = AGENCY_SOURCE_LABEL;
				String language = isLanguageFrench() ? Locale.FRENCH.getLanguage() : Locale.ENGLISH.getLanguage();
				InfoReseauRSSDataHandler handler = new InfoReseauRSSDataHandler(this, authority, severity, noteworthyInMs, newLastUpdateInMs, maxValidityInMs,
						target, color, authorName, authorUrl, label, language);
				xr.setContentHandler(handler);
				FileUtils.copyToPrivateFile(getContext(), PRIVATE_FILE_NAME, urlc.getInputStream(), ENCODING);
				xr.parse(new InputSource(getContext().openFileInput(PRIVATE_FILE_NAME)));
				return handler.getNews();
			default:
				MTLog.w(this, "ERROR: HTTP URL-Connection Response Code %s (Message: %s)", httpUrlConnection.getResponseCode(),
						httpUrlConnection.getResponseMessage());
				return null;
			}
		} catch (SSLHandshakeException sslhe) {
			MTLog.w(this, sslhe, "SSL error!");
			return null;
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
	public String getNewsDbTableName() {
		return CaSTODbHelper.T_INFO_RESEAU_NEWS;
	}

	@Override
	public String[] getNewsProjection() {
		return NewsProviderContract.PROJECTION_NEWS;
	}

	@Nullable
	private static ArrayMap<String, String> newsProjectionMap = null;

	@NonNull
	@Override
	public ArrayMap<String, String> getNewsProjectionMap() {
		if (newsProjectionMap == null) {
			newsProjectionMap = NewsProvider.getNewNewsProjectionMap(getAUTHORITY(getContext()));
		}
		return newsProjectionMap;
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
	private CaSTODbHelper dbHelper;

	private static int currentDbVersion = -1;

	@NonNull
	private CaSTODbHelper getDBHelper(Context context) {
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
	 * Override if multiple {@link CaSTOProvider} implementations in same app.
	 */
	public int getCurrentDbVersion() {
		return CaSTODbHelper.getDbVersion(getContext());
	}

	/**
	 * Override if multiple {@link CaSTOProvider} implementations in same app.
	 */
	public CaSTODbHelper getNewDbHelper(@NonNull Context context) {
		return new CaSTODbHelper(context.getApplicationContext());
	}

	@NonNull
	@Override
	public UriMatcher getURI_MATCHER() {
		return getURIMATCHER(getContext());
	}

	@Override
	public Uri getAuthorityUri() {
		return getAUTHORITY_URI(getContext());
	}

	@Override
	public String getAuthority() {
		return getAUTHORITY(getContext());
	}

	@NonNull
	@Override
	public SQLiteOpenHelper getDBHelper() {
		return getDBHelper(getContext());
	}

	@Override
	public Cursor queryMT(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		Cursor cursor = NewsProvider.queryS(this, uri, selection);
		if (cursor != null) {
			return cursor;
		}
		throw new IllegalArgumentException(String.format("Unknown URI (query): '%s'", uri));
	}

	@Override
	public String getTypeMT(@NonNull Uri uri) {
		String type = NewsProvider.getTypeS(this, uri);
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

	private static class InfoReseauRSSDataHandler extends MTDefaultHandler {

		private static final String LOG_TAG = CaSTOProvider.LOG_TAG + ">" + InfoReseauRSSDataHandler.class.getSimpleName();

		@Override
		public String getLogTag() {
			return LOG_TAG;
		}

		private static final String NOUVELLES = "nouvelles";
		private static final String NOUVELLE = "nouvelle";
		private static final String TITRE = "titre";
		private static final String DATE = "date";
		private static final String HEURE = "heure";
		private static final String LIEN = "lien";
		private static final String RESUME = "resume";
		private static final String CONTENU = "contenu";

		private String currentLocalName = NOUVELLES;
		private boolean currentNouvelle = false;
		private StringBuilder currentTitreSb = new StringBuilder();
		private StringBuilder currentDateSb = new StringBuilder();
		private StringBuilder currentHeureSb = new StringBuilder();
		private StringBuilder currentLienSb = new StringBuilder();
		private StringBuilder currentResumeSb = new StringBuilder();
		private StringBuilder currentContenuSb = new StringBuilder();

		private ArrayList<News> news = new ArrayList<>();

		private String authority;
		private int severity;
		private long noteworthyInMs;
		private long lastUpdateInMs;
		private long maxValidityInMs;
		private String target;
		private String color;
		private String authorName;
		private String authorUrl;
		private String label;
		private String language;

		private CaSTOProvider provider;

		public InfoReseauRSSDataHandler(CaSTOProvider provider, String authority, int severity, long noteworthyInMs, long lastUpdateInMs, long maxValidityInMs,
				String target, String color, String authorName, String authorUrl, String label, String language) {
			this.provider = provider;
			this.authority = authority;
			this.severity = severity;
			this.noteworthyInMs = noteworthyInMs;
			this.lastUpdateInMs = lastUpdateInMs;
			this.maxValidityInMs = maxValidityInMs;
			this.target = target;
			this.color = color;
			this.authorName = authorName;
			this.authorUrl = authorUrl;
			this.label = label;
			this.language = language;
		}

		public ArrayList<News> getNews() {
			return this.news;
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
			super.startElement(uri, localName, qName, attributes);
			this.currentLocalName = localName;
			if (NOUVELLE.equals(this.currentLocalName)) {
				this.currentNouvelle = true;
				this.currentTitreSb.setLength(0); // reset
				this.currentDateSb.setLength(0); // reset
				this.currentHeureSb.setLength(0); // reset
				this.currentLienSb.setLength(0); // reset
				this.currentResumeSb.setLength(0); // reset
				this.currentContenuSb.setLength(0); // reset
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
				if (this.currentNouvelle) {
					if (TITRE.equals(this.currentLocalName)) {
						this.currentTitreSb.append(string);
					} else if (DATE.equals(this.currentLocalName)) {
						this.currentDateSb.append(string);
					} else if (HEURE.equals(this.currentLocalName)) {
						this.currentHeureSb.append(string);
					} else if (LIEN.equals(this.currentLocalName)) {
						this.currentLienSb.append(string);
					} else if (RESUME.equals(this.currentLocalName)) {
						this.currentResumeSb.append(string);
					} else if (CONTENU.equals(this.currentLocalName)) {
						this.currentContenuSb.append(string);
					} else {
						MTLog.w(this, "characters() > Unexpected item element '%s'", this.currentLocalName);
					}
				} else {
					MTLog.w(this, "characters() > Unexpected element '%s'", this.currentLocalName);
				}
			} catch (Exception e) {
				MTLog.w(this, e, "Error while parsing '%s' value '%s, %s, %s'!", this.currentLocalName, ch, start, length);
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException {
			super.endElement(uri, localName, qName);
			try {
				if (NOUVELLE.equals(localName)) {
					processNouvelle();
					this.currentNouvelle = false;
				}
			} catch (Exception e) {
				MTLog.w(this, e, "Error while parsing '%s' end element!", this.currentLocalName);
			}
		}

		private static final String COLON = ": ";

		private void processNouvelle() throws ParseException {
			Long pubDateInMs = getPublicationDateInMs();
			if (pubDateInMs == null) {
				MTLog.w(this, "processNouvelle() > skip (no date)");
				return;
			}
			String uuid = AGENCY_SOURCE_ID + pubDateInMs;
			String title = this.currentTitreSb.toString().trim();
			String resume = this.currentResumeSb.toString().trim();
			String contenu = this.currentContenuSb.toString().trim();
			String link = this.currentLienSb.toString().trim();
			StringBuilder textSb = new StringBuilder();
			StringBuilder textHTMLSb = new StringBuilder();
			if (!TextUtils.isEmpty(title)) {
				textSb.append(title);
				textHTMLSb.append(HtmlUtils.applyBold(title));
			}
			if (!TextUtils.isEmpty(resume)) {
				if (textSb.length() > 0) {
					textSb.append(COLON);
				}
				textSb.append(Html.fromHtml(resume));
				if (textHTMLSb.length() > 0) {
					textHTMLSb.append(HtmlUtils.BR);
				}
				textHTMLSb.append(resume);
			}
			if (!TextUtils.isEmpty(contenu)) {
				if (!contenu.equals(resume)) {
					if (textSb.length() > 0) {
						textSb.append(COLON);
					}
					textSb.append(Html.fromHtml(contenu));
					if (textHTMLSb.length() > 0) {
						textHTMLSb.append(HtmlUtils.BR);
					}
					textHTMLSb.append(contenu);
				}
			}
			if (!TextUtils.isEmpty(link)) {
				if (textHTMLSb.length() > 0) {
					textHTMLSb.append(HtmlUtils.BR).append(HtmlUtils.BR);
				}
				textHTMLSb.append(HtmlUtils.linkify(link));
			}
			if (textSb.length() == 0 || textHTMLSb.length() == 0) {
				MTLog.w(this, "processItem() > skip (no text)");
				return;
			}
			News newNews = new News(null, this.authority, uuid, this.severity, this.noteworthyInMs, this.lastUpdateInMs, this.maxValidityInMs, pubDateInMs,
					this.target, this.color, this.authorName, null, null, this.authorUrl, //
					StringUtils.oneLineOneSpace(textSb.toString()), //
					textHTMLSb.toString(), //
					link, this.language, AGENCY_SOURCE_ID, this.label);
			this.news.add(newNews);
		}

		private static final String DATE_HEURE_FORMAT_FR_PATTERN = "yyyy-MM-dd HH:mm";
		private static final String DATE_HEURE_FORMAT_EN_PATTERN = "MM-dd-yyyy HH:mm";
		private static final String TIME_ZONE = "America/Montreal";
		private static ThreadSafeDateFormatter dateHeureFormat;

		public static ThreadSafeDateFormatter getDateHeureFormat(Context context) {
			if (dateHeureFormat == null) {
				dateHeureFormat = new ThreadSafeDateFormatter(isLanguageFrench() ? DATE_HEURE_FORMAT_FR_PATTERN : DATE_HEURE_FORMAT_EN_PATTERN);
				dateHeureFormat.setTimeZone(TimeZone.getTimeZone(TIME_ZONE));
			}
			return dateHeureFormat;
		}

		private Long getPublicationDateInMs() throws ParseException {
			if (!TextUtils.isEmpty(this.currentDateSb) && !TextUtils.isEmpty(this.currentHeureSb)) {
				return getDateHeureFormat(this.provider.getContext()).parseThreadSafe(
						this.currentDateSb.toString().trim() + " " + this.currentHeureSb.toString().trim()).getTime();
			}
			return null;
		}
	}

	public static class CaSTODbHelper extends MTSQLiteOpenHelper {

		private static final String LOG_TAG = CaSTODbHelper.class.getSimpleName();

		@Override
		public String getLogTag() {
			return LOG_TAG;
		}

		/**
		 * Override if multiple {@link CaSTODbHelper} implementations in same app.
		 */
		protected static final String PREF_KEY_AGENCY_NEWS_LAST_UPDATE_MS = "pCaSTONewsLastUpdate";

		/**
		 * Override if multiple {@link CaSTODbHelper} implementations in same app.
		 */
		protected static final String PREF_KEY_AGENCY_NEWS_LAST_UPDATE_LANG = "pCaSTONewsLastUpdateLang";

		/**
		 * Override if multiple {@link CaSTODbHelper} implementations in same app.
		 */
		protected static final String DB_NAME = "casto.db";

		public static final String T_INFO_RESEAU_NEWS = NewsProvider.NewsDbHelper.T_NEWS;

		private static final String T_INFO_RESEAU_NEWS_SQL_CREATE = NewsProvider.NewsDbHelper.getSqlCreateBuilder(T_INFO_RESEAU_NEWS).build();

		private static final String T_INFO_RESEAU_NEWS_SQL_DROP = SqlUtils.getSQLDropIfExistsQuery(T_INFO_RESEAU_NEWS);

		private static int dbVersion = -1;

		/**
		 * Override if multiple {@link CaSTODbHelper} in same app.
		 */
		public static int getDbVersion(@NonNull Context context) {
			if (dbVersion < 0) {
				dbVersion = context.getResources().getInteger(R.integer.ca_sto_db_version);
			}
			return dbVersion;
		}

		private Context context;

		public CaSTODbHelper(@NonNull Context context) {
			super(context, DB_NAME, null, getDbVersion(context));
			this.context = context;
		}

		@Override
		public void onCreateMT(@NonNull SQLiteDatabase db) {
			initAllDbTables(db);
		}

		@Override
		public void onUpgradeMT(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
			db.execSQL(T_INFO_RESEAU_NEWS_SQL_DROP);
			PreferenceUtils.savePrefLcl(this.context, PREF_KEY_AGENCY_NEWS_LAST_UPDATE_MS, 0L, true);
			initAllDbTables(db);
		}

		public boolean isDbExist(@NonNull Context context) {
			return SqlUtils.isDbExist(context, DB_NAME);
		}

		private void initAllDbTables(@NonNull SQLiteDatabase db) {
			db.execSQL(T_INFO_RESEAU_NEWS_SQL_CREATE);
		}
	}
}
