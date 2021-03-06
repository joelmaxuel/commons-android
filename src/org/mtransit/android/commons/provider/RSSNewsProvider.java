package org.mtransit.android.commons.provider;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocketFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.mtransit.android.commons.ArrayUtils;
import org.mtransit.android.commons.CollectionUtils;
import org.mtransit.android.commons.FileUtils;
import org.mtransit.android.commons.HtmlUtils;
import org.mtransit.android.commons.LocaleUtils;
import org.mtransit.android.commons.MTLog;
import org.mtransit.android.commons.PreferenceUtils;
import org.mtransit.android.commons.R;
import org.mtransit.android.commons.SecurityUtils;
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
import android.content.Context;
import android.content.UriMatcher;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Html;
import android.text.TextUtils;

@SuppressLint("Registered")
public class RSSNewsProvider extends NewsProvider {

	private static final String LOG_TAG = RSSNewsProvider.class.getSimpleName();

	@Override
	public String getLogTag() {
		return LOG_TAG;
	}

	/**
	 * Override if multiple {@link RSSNewsProvider} implementations in same app.
	 */
	private static final String PREF_KEY_AGENCY_LAST_UPDATE_MS = RSSNewsDbHelper.PREF_KEY_AGENCY_LAST_UPDATE_MS;

	/**
	 * Override if multiple {@link RSSNewsProvider} implementations in same app.
	 */
	private static final String PREF_KEY_AGENCY_LAST_UPDATE_LANG = RSSNewsDbHelper.PREF_KEY_AGENCY_LAST_UPDATE_LANG;

	@Nullable
	private static UriMatcher uriMatcher = null;

	/**
	 * Override if multiple {@link RSSNewsProvider} implementations in same app.
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
	 * Override if multiple {@link RSSNewsProvider} implementations in same app.
	 */
	@NonNull
	private static String getAUTHORITY(@NonNull Context context) {
		if (authority == null) {
			authority = context.getResources().getString(R.string.rss_authority);
		}
		return authority;
	}

	@Nullable
	private static Uri authorityUri = null;

	/**
	 * Override if multiple {@link RSSNewsProvider} implementations in same app.
	 */
	@NonNull
	private static Uri getAUTHORITY_URI(@NonNull Context context) {
		if (authorityUri == null) {
			authorityUri = UriUtils.newContentUri(getAUTHORITY(context));
		}
		return authorityUri;
	}

	@Nullable
	private static Boolean copyToFileInsteadOfStreaming = null;

	/**
	 * Override if multiple {@link RSSNewsProvider} implementations in same app.
	 */
	private static boolean isCOPY_TO_FILE_INSTEAD_OF_STREAMING(@NonNull Context context) {
		if (copyToFileInsteadOfStreaming == null) {
			copyToFileInsteadOfStreaming = context.getResources().getBoolean(R.bool.rss_copy_to_file_instead_of_streaming);
		}
		return copyToFileInsteadOfStreaming;
	}

	@Nullable
	private static Boolean useCustomSSLCertificate = null;

	/**
	 * Override if multiple {@link RSSNewsProvider} implementations in same app.
	 */
	private static boolean isUSE_CUSTOM_SSL_CERTIFICATE(@NonNull Context context) {
		if (useCustomSSLCertificate == null) {
			useCustomSSLCertificate = context.getResources().getBoolean(R.bool.rss_use_custom_ssl_certificate);
		}
		return useCustomSSLCertificate;
	}

	@Nullable
	private static String encoding = null;

	/**
	 * Override if multiple {@link RSSNewsProvider} implementations in same app.
	 */
	@NonNull
	private static String getENCODING(@NonNull Context context) {
		if (encoding == null) {
			encoding = context.getResources().getString(R.string.rss_encoding);
		}
		return encoding;
	}

	@Nullable
	private static java.util.List<String> fileCleaningRegex = null;

	/**
	 * Override if multiple {@link RSSNewsProvider} implementations in same app.
	 */
	@NonNull
	private static java.util.List<String> getFILE_CLEANING_REGEX(@NonNull Context context) {
		if (fileCleaningRegex == null) {
			fileCleaningRegex = Arrays.asList(context.getResources().getStringArray(R.array.rrs_file_cleaning_regex));
		}
		return fileCleaningRegex;
	}

	@Nullable
	private static java.util.List<String> fileCleaningReplacement = null;

	/**
	 * Override if multiple {@link RSSNewsProvider} implementations in same app.
	 */
	@NonNull
	private static java.util.List<String> getFILE_CLEANING_REPLACEMENT(@NonNull Context context) {
		if (fileCleaningReplacement == null) {
			fileCleaningReplacement = Arrays.asList(context.getResources().getStringArray(R.array.rrs_file_cleaning_replacement));
		}
		return fileCleaningReplacement;
	}

	@Nullable
	private static java.util.List<String> feeds = null;

	/**
	 * Override if multiple {@link RSSNewsProvider} implementations in same app.
	 */
	@NonNull
	private static java.util.List<String> getFEEDS(@NonNull Context context) {
		if (feeds == null) {
			feeds = Arrays.asList(context.getResources().getStringArray(R.array.rss_feeds));
		}
		return feeds;
	}

	@Nullable
	private static java.util.List<String> feedsAuthorName = null;

	/**
	 * Override if multiple {@link RSSNewsProvider} implementations in same app.
	 */
	@NonNull
	private static java.util.List<String> getFEEDS_AUTHOR_NAME(@NonNull Context context) {
		if (feedsAuthorName == null) {
			feedsAuthorName = Arrays.asList(context.getResources().getStringArray(R.array.rss_feeds_author_name));
		}
		return feedsAuthorName;
	}

	@Nullable
	private static java.util.List<String> feedsAuthorUrl = null;

	/**
	 * Override if multiple {@link RSSNewsProvider} implementations in same app.
	 */
	@NonNull
	private static java.util.List<String> getFEEDS_AUTHOR_URL(@NonNull Context context) {
		if (feedsAuthorUrl == null) {
			feedsAuthorUrl = Arrays.asList(context.getResources().getStringArray(R.array.rss_feeds_author_url));
		}
		return feedsAuthorUrl;
	}

	@Nullable
	private static java.util.List<String> feedsLabel = null;

	/**
	 * Override if multiple {@link RSSNewsProvider} implementations in same app.
	 */
	@NonNull
	private static java.util.List<String> getFEEDS_LABEL(@NonNull Context context) {
		if (feedsLabel == null) {
			feedsLabel = Arrays.asList(context.getResources().getStringArray(R.array.rss_feeds_label));
		}
		return feedsLabel;
	}

	@Nullable
	private static java.util.List<String> feedsLang = null;

	/**
	 * Override if multiple {@link RSSNewsProvider} implementations in same app.
	 */
	@NonNull
	private static java.util.List<String> getFEEDS_LANG(@NonNull Context context) {
		if (feedsLang == null) {
			feedsLang = Arrays.asList(context.getResources().getStringArray(R.array.rss_feeds_lang));
		}
		return feedsLang;
	}

	@Nullable
	private static java.util.List<String> feedsColors = null;

	/**
	 * Override if multiple {@link RSSNewsProvider} implementations in same app.
	 */
	@NonNull
	private static java.util.List<String> getFEEDS_COLORS(@NonNull Context context) {
		if (feedsColors == null) {
			feedsColors = Arrays.asList(context.getResources().getStringArray(R.array.rss_feeds_colors));
		}
		return feedsColors;
	}

	@Nullable
	private static java.util.List<String> feedsTargets = null;

	/**
	 * Override if multiple {@link RSSNewsProvider} implementations in same app.
	 */
	@NonNull
	private static java.util.List<String> getFEEDS_TARGETS(@NonNull Context context) {
		if (feedsTargets == null) {
			feedsTargets = Arrays.asList(context.getResources().getStringArray(R.array.rss_feeds_target));
		}
		return feedsTargets;
	}

	@Nullable
	private static java.util.List<Integer> feedsSeverity = null;

	/**
	 * Override if multiple {@link RSSNewsProvider} implementations in same app.
	 */
	@NonNull
	private static java.util.List<Integer> getFEEDS_SEVERITY(@NonNull Context context) {
		if (feedsSeverity == null) {
			feedsSeverity = ArrayUtils.asIntegerList(context.getResources().getIntArray(R.array.rss_feeds_severity));
		}
		return feedsSeverity;
	}

	@Nullable
	private static java.util.List<Long> feedsNoteworthy = null;

	/**
	 * Override if multiple {@link RSSNewsProvider} implementations in same app.
	 */
	@NonNull
	private static java.util.List<Long> getFEEDS_NOTEWORTHY(@NonNull Context context) {
		if (feedsNoteworthy == null) {
			feedsNoteworthy = ArrayUtils.asLongList(context.getResources().getStringArray(R.array.rss_feeds_noteworthy));
		}
		return feedsNoteworthy;
	}

	@Nullable
	private static java.util.List<Boolean> feedsIgnoreGUID = null;

	/**
	 * Override if multiple {@link RSSNewsProvider} implementations in same app.
	 */
	@NonNull
	private static java.util.List<Boolean> getFEEDS_IGNORE_GUID(@NonNull Context context) {
		if (feedsIgnoreGUID == null) {
			feedsIgnoreGUID = ArrayUtils.asBooleanList(context.getResources().getStringArray(R.array.rss_feeds_ignore_guid));
		}
		return feedsIgnoreGUID;
	}

	@Nullable
	private static java.util.List<Boolean> feedsIgnoreLink = null;

	/**
	 * Override if multiple {@link RSSNewsProvider} implementations in same app.
	 */
	@NonNull
	private static java.util.List<Boolean> getFEEDS_IGNORE_LINK(@NonNull Context context) {
		if (feedsIgnoreLink == null) {
			feedsIgnoreLink = ArrayUtils.asBooleanList(context.getResources().getStringArray(R.array.rss_feeds_ignore_link));
		}
		return feedsIgnoreLink;
	}

	@NonNull
	@Override
	public UriMatcher getURI_MATCHER() {
		return getURIMATCHER(getContext());
	}

	@Nullable
	private RSSNewsDbHelper dbHelper;
	private static int currentDbVersion = -1;

	@NonNull
	private RSSNewsDbHelper getDBHelper(@NonNull Context context) {
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
	 * Override if multiple {@link RSSNewsDbHelper} implementations in same app.
	 */
	@Override
	public int getCurrentDbVersion() {
		return RSSNewsDbHelper.getDbVersion(getContext());
	}

	/**
	 * Override if multiple {@link RSSNewsDbHelper} implementations in same app.
	 */
	@NonNull
	@Override
	public RSSNewsDbHelper getNewDbHelper(@NonNull Context context) {
		return new RSSNewsDbHelper(context.getApplicationContext());
	}

	@NonNull
	@Override
	public SQLiteOpenHelper getDBHelper() {
		return getDBHelper(getContext());
	}

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
	public boolean deleteCachedNews(Integer newsId) {
		return NewsProvider.deleteCachedNews(this, newsId);
	}

	private static final String AGENCY_SOURCE_ID = "rss";

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
	public String getAuthority() {
		return getAUTHORITY(getContext());
	}

	@Override
	public Uri getAuthorityUri() {
		return getAUTHORITY_URI(getContext());
	}

	@Override
	public void cacheNews(ArrayList<News> newNews) {
		NewsProvider.cacheNewsS(this, newNews);
	}

	@Override
	public ArrayList<News> getCachedNews(NewsProviderContract.Filter newsFilter) {
		if (newsFilter == null) {
			return null;
		}
		ArrayList<News> cachedNews = NewsProvider.getCachedNewsS(this, newsFilter);
		return cachedNews;
	}

	@Nullable
	private static Collection<String> languages = null;

	@NonNull
	@Override
	public Collection<String> getNewsLanguages() {
		if (languages == null) {
			languages = new HashSet<>();
			if (LocaleUtils.isFR()) {
				languages.add(Locale.FRENCH.getLanguage());
			} else {
				languages.add(Locale.ENGLISH.getLanguage());
			}
			languages.add(LocaleUtils.UNKNOWN);
		}
		return languages;
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
		long lastUpdateInMs = PreferenceUtils.getPrefLcl(getContext(), PREF_KEY_AGENCY_LAST_UPDATE_MS, 0L);
		String lastUpdateLang = PreferenceUtils.getPrefLcl(getContext(), PREF_KEY_AGENCY_LAST_UPDATE_LANG, StringUtils.EMPTY);
		long minUpdateMs = Math.min(getNewsMaxValidityInMs(), getNewsValidityInMs(inFocus));
		long nowInMs = TimeUtils.currentTimeMillis();
		if (lastUpdateInMs + minUpdateMs > nowInMs && LocaleUtils.getDefaultLanguage().equals(lastUpdateLang)) {
			return;
		}
		updateAgencyNewsDataIfRequiredSync(lastUpdateInMs, lastUpdateLang, inFocus);
	}

	private synchronized void updateAgencyNewsDataIfRequiredSync(long lastUpdateInMs, String lastUpdateLang, boolean inFocus) {
		if (PreferenceUtils.getPrefLcl(getContext(), PREF_KEY_AGENCY_LAST_UPDATE_MS, 0L) > lastUpdateInMs //
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
			PreferenceUtils.savePrefLcl(getContext(), PREF_KEY_AGENCY_LAST_UPDATE_MS, nowInMs, true); // sync
			PreferenceUtils.savePrefLcl(getContext(), PREF_KEY_AGENCY_LAST_UPDATE_LANG, LocaleUtils.getDefaultLanguage(), true); // sync
		} // else keep whatever we have until max validity reached
	}

	private ArrayList<News> loadAgencyNewsDataFromWWW() {
		try {
			ArrayList<News> newNews = new ArrayList<>();
			int i = 0;
			for (String urlString : getFEEDS(getContext())) {
				String language = getFEEDS_LANG(getContext()).get(i);
				if (!LocaleUtils.MULTIPLE.equals(language) && !LocaleUtils.UNKNOWN.equals(language) && !LocaleUtils.getDefaultLanguage().equals(language)) {
					i++;
					continue;
				}
				ArrayList<News> feedNews = loadAgencyNewsDataFromWWW(urlString, i++);
				if (feedNews != null) {
					newNews.addAll(filterNews(feedNews));
				}
			}
			return newNews;
		} catch (Exception e) {
			MTLog.e(LOG_TAG, e, "INTERNAL ERROR: Unknown Exception");
			return null;
		}
	}

	private static final long MIN_COVERAGE_DURATION_IN_MS = TimeUnit.DAYS.toMillis(100L); // PAST
	private static final long MAX_COVERAGE_DURATION_IN_MS = TimeUnit.HOURS.toMillis(12L); // FUTURE

	private static final int MIN_SIZE_IN_THE_PAST = 10;

	@NonNull
	private ArrayList<News> filterNews(@NonNull ArrayList<News> feedNews) {
		CollectionUtils.sort(feedNews, News.NEWS_COMPARATOR);
		int nbKeptInList = 0;
		long minCoverageDateInMs = TimeUtils.currentTimeMillis() - MIN_COVERAGE_DURATION_IN_MS;
		long maxCoverageDateInMs = TimeUtils.currentTimeMillis() + MAX_COVERAGE_DURATION_IN_MS;
		Iterator<News> it = feedNews.iterator();
		while (it.hasNext()) {
			News news = it.next();
			if (nbKeptInList > MIN_SIZE_IN_THE_PAST //
					&& news.getCreatedAtInMs() < minCoverageDateInMs) {
				it.remove(); // too old
			} else if (maxCoverageDateInMs < news.getCreatedAtInMs()) {
				it.remove(); // too far away in the future
			} else {
				nbKeptInList++;
			}
		}
		return feedNews;
	}

	private static final String PRIVATE_FILE_NAME = "rss.xml";

	@Nullable
	private ArrayList<News> loadAgencyNewsDataFromWWW(String urlString, int i) {
		Context context = getContext();
		if (context == null) {
			return null;
		}
		try {
			MTLog.i(this, "Loading from '%s'...", urlString);
			URL url = new URL(urlString);
			URLConnection urlc = url.openConnection();
			HttpURLConnection httpUrlConnection = (HttpURLConnection) urlc;
			if (isUSE_CUSTOM_SSL_CERTIFICATE(context)) {
				SSLSocketFactory sslSocketFactory = SecurityUtils.getSSLSocketFactory(context, R.raw.rss_custom_ssl_certificate);
				if (sslSocketFactory != null) {
					((HttpsURLConnection) httpUrlConnection).setSSLSocketFactory(sslSocketFactory);
				}
			}
			switch (httpUrlConnection.getResponseCode()) {
			case HttpURLConnection.HTTP_OK:
				long newLastUpdateInMs = TimeUtils.currentTimeMillis();
				SAXParserFactory spf = SAXParserFactory.newInstance();
				SAXParser sp = spf.newSAXParser();
				XMLReader xr = sp.getXMLReader();
				String authority = getAUTHORITY(context);
				int severity = getFEEDS_SEVERITY(context).get(i);
				long noteworthyInMs = getFEEDS_NOTEWORTHY(context).get(i);
				long maxValidityInMs = getNewsMaxValidityInMs();
				String target = getFEEDS_TARGETS(context).get(i);
				String color = getFEEDS_COLORS(context).get(i);
				String authorName = getFEEDS_AUTHOR_NAME(context).get(i);
				String authorUrl = getFEEDS_AUTHOR_URL(context).get(i);
				String label = getFEEDS_LABEL(context).get(i);
				String language = getFEEDS_LANG(context).get(i);
				boolean ignoreGUID = getFEEDS_IGNORE_GUID(context).get(i);
				boolean ignoreLink = getFEEDS_IGNORE_LINK(context).get(i);
				RSSDataHandler handler = new RSSDataHandler( //
						authority, severity, noteworthyInMs, newLastUpdateInMs, maxValidityInMs, target, color, authorName, authorUrl, label, language,
						ignoreGUID, ignoreLink);
				xr.setContentHandler(handler);
				if (isCOPY_TO_FILE_INSTEAD_OF_STREAMING(context)) { // fix leading space (invalid!) #BIXI #Montreal
					FileUtils.copyToPrivateFile(context, PRIVATE_FILE_NAME, urlc.getInputStream(), getENCODING(context));
					cleaningFile(context);
					xr.parse(new InputSource(context.openFileInput(PRIVATE_FILE_NAME)));
				} else {
					xr.parse(new InputSource(httpUrlConnection.getInputStream()));
				}
				return handler.getNews();
			default:
				MTLog.w(this, "ERROR: HTTP URL-Connection Response Code %s (Message: %s)", httpUrlConnection.getResponseCode(),
						httpUrlConnection.getResponseMessage());
				return null;
			}
		} catch (SSLHandshakeException sslhe) {
			MTLog.w(this, sslhe, "SSL error!");
			SecurityUtils.logCertPathValidatorException(sslhe);
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

	private void cleaningFile(@NonNull Context context) {
		try {
			java.util.List<String> fileCleaningRegex = getFILE_CLEANING_REGEX(context);
			int regexCount = fileCleaningRegex.size();
			java.util.List<String> fileCleaningReplacement = getFILE_CLEANING_REPLACEMENT(context);
			int replacementCount = fileCleaningReplacement.size();
			if (regexCount > 0 && replacementCount > 0) {
				if (regexCount != replacementCount) {
					MTLog.w(this, "Invalid number for file cleaning regex [%s] & replacement [%s] !", regexCount, replacementCount);
					return;
				}
				String fileContent = FileUtils.getString(context.openFileInput(PRIVATE_FILE_NAME));
				for (int i = 0; i < regexCount; i++) {
					String regex = fileCleaningRegex.get(i);
					if (TextUtils.isEmpty(regex)) {
						MTLog.w(this, "Invalid file cleaning regex! (%s)", regex);
						continue;
					}
					String replacement = fileCleaningReplacement.get(i);
					fileContent = fileContent.replaceAll(regex, replacement);
				}
				FileUtils.copyToPrivateFile(context, PRIVATE_FILE_NAME, fileContent);
			}
		} catch (Exception e) {
			MTLog.w(this, e, "Error while cleaning file!");
		}
	}

	private static class RSSDataHandler extends MTDefaultHandler {

		private static final String LOG_TAG = RSSNewsProvider.LOG_TAG + ">" + RSSDataHandler.class.getSimpleName();

		@Override
		public String getLogTag() {
			return LOG_TAG;
		}

		private static final String RSS = "rss";
		private static final String RDF = "RDF";
		private static final String CHANNEL = "channel";
		private static final String TITLE = "title";
		private static final String LINK = "link";
		private static final String LI = "li";
		private static final String ITEMS = "items";
		private static final String SEQ = "Seq";
		private static final String DESCRIPTION = "description";
		private static final String LAST_BUILD_DATE = "lastBuildDate";
		private static final String DOCS = "docs";
		private static final String GENERATOR = "generator";
		private static final String ITEM = "item";
		private static final String PUBLICATION_DATE = "pubDate";
		private static final String DATE = "date";
		private static final String UPDATED = "updated";
		private static final String COMMENTS = "comments";
		private static final String COMMENT_RSS = "commentRss";
		private static final String ENCODED = "encoded";
		private static final String GUID = "guid";
		private static final String GUID_IS_PERMA_LINK = "isPermaLink";
		private static final String CREATOR = "creator";
		private static final String CATEGORY = "category";
		private static final String LANGUAGE = "language";
		private static final String UPDATE_PERIOD = "updatePeriod";
		private static final String UPDATE_FREQUENCY = "updateFrequency";
		private static final String MANAGING_EDITOR = "managingEditor";
		private static final String WEB_MASTER = "webMaster";
		private static final String AUTHOR = "author";
		private static final String IMAGE = "image";
		private static final String URL = "url";
		private static final String WIDTH = "width";
		private static final String HEIGHT = "height";
		private static final String COPYRIGHT = "copyright";
		private static final String TTL = "ttl";

		private String currentLocalName = RSS;
		private boolean currentItem = false;
		@NonNull
		private StringBuilder currentPubDateSb = new StringBuilder();
		@NonNull
		private StringBuilder currentDateSb = new StringBuilder();
		@NonNull
		private StringBuilder currentUpdatedSb = new StringBuilder();
		@NonNull
		private StringBuilder currentTitleSb = new StringBuilder();
		@NonNull
		private StringBuilder currentLinkSb = new StringBuilder();
		@NonNull
		private StringBuilder currentDescriptionSb = new StringBuilder();
		@NonNull
		private StringBuilder currentGUIDSb = new StringBuilder();
		@Nullable
		private Boolean currentGUIDIsPermaLink = null;

		@NonNull
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
		private boolean ignoreGuid;
		private boolean ignoreLink;

		public RSSDataHandler(String authority, int severity, long noteworthyInMs, long lastUpdateInMs, long maxValidityInMs, String target, String color,
				String authorName, String authorUrl, String label, String language, boolean ignoreGuid, boolean ignoreLink) {
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
			this.ignoreGuid = ignoreGuid;
			this.ignoreLink = ignoreLink;
		}

		@NonNull
		public ArrayList<News> getNews() {
			return this.news;
		}

		private static final String FALSE = "false";

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
			super.startElement(uri, localName, qName, attributes);
			this.currentLocalName = localName;
			if (ITEM.equals(this.currentLocalName)) {
				this.currentItem = true;
				this.currentTitleSb.setLength(0); // reset
				this.currentPubDateSb.setLength(0); // reset
				this.currentDateSb.setLength(0); // reset
				this.currentUpdatedSb.setLength(0); // reset
				this.currentLinkSb.setLength(0); // reset
				this.currentDescriptionSb.setLength(0); // reset
				this.currentGUIDSb.setLength(0); // reset
				this.currentGUIDIsPermaLink = null; // reset
			} else if (GUID.equals(this.currentLocalName)) {
				this.currentGUIDIsPermaLink = !FALSE.equals(attributes.getValue(GUID_IS_PERMA_LINK)); // true = default
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
					} else if (PUBLICATION_DATE.equals(this.currentLocalName)) {
						this.currentPubDateSb.append(string);
					} else if (DATE.equals(this.currentLocalName)) {
						this.currentDateSb.append(string);
					} else if (UPDATED.equals(this.currentLocalName)) {
						this.currentUpdatedSb.append(string);
					} else if (LINK.equals(this.currentLocalName)) {
						if (!this.ignoreLink) {
							this.currentLinkSb.append(string);
						}
					} else if (DESCRIPTION.equals(this.currentLocalName)) {
						this.currentDescriptionSb.append(string);
					} else if (GUID.equals(this.currentLocalName)) {
						if (!this.ignoreGuid) {
							this.currentGUIDSb.append(string);
						}
					} else if (ITEM.equals(this.currentLocalName)) { // ignore
					} else if (COMMENTS.equals(this.currentLocalName)) { // ignore
					} else if (COMMENT_RSS.equals(this.currentLocalName)) { // ignore
					} else if (ENCODED.equals(this.currentLocalName)) { // ignore
					} else if (CATEGORY.equals(this.currentLocalName)) { // ignore
					} else if (CREATOR.equals(this.currentLocalName)) { // ignore
					} else if (AUTHOR.equals(this.currentLocalName)) { // ignore
					} else {
						MTLog.d(this, "characters() > Unexpected item element '%s'", this.currentLocalName);
					}
				} else if (RDF.equals(this.currentLocalName)) { // ignore
				} else if (TITLE.equals(this.currentLocalName)) { // ignore
				} else if (PUBLICATION_DATE.equals(this.currentLocalName)) { // ignore
				} else if (DATE.equals(this.currentLocalName)) { // ignore
				} else if (LINK.equals(this.currentLocalName)) { // ignore
				} else if (LI.equals(this.currentLocalName)) { // ignore
				} else if (ITEMS.equals(this.currentLocalName)) { // ignore
				} else if (SEQ.equals(this.currentLocalName)) { // ignore
				} else if (DESCRIPTION.equals(this.currentLocalName)) { // ignore
				} else if (RSS.equals(this.currentLocalName)) { // ignore
				} else if (CHANNEL.equals(this.currentLocalName)) { // ignore
				} else if (LAST_BUILD_DATE.equals(this.currentLocalName)) { // ignore
				} else if (DOCS.equals(this.currentLocalName)) { // ignore
				} else if (GENERATOR.equals(this.currentLocalName)) { // ignore
				} else if (COMMENTS.equals(this.currentLocalName)) { // ignore
				} else if (LANGUAGE.equals(this.currentLocalName)) { // ignore
				} else if (UPDATE_PERIOD.equals(this.currentLocalName)) { // ignore
				} else if (UPDATE_FREQUENCY.equals(this.currentLocalName)) { // ignore
				} else if (MANAGING_EDITOR.equals(this.currentLocalName)) { // ignore
				} else if (WEB_MASTER.equals(this.currentLocalName)) { // ignore
				} else if (IMAGE.equals(this.currentLocalName)) { // ignore
				} else if (URL.equals(this.currentLocalName)) { // ignore
				} else if (WIDTH.equals(this.currentLocalName)) { // ignore
				} else if (HEIGHT.equals(this.currentLocalName)) { // ignore
				} else if (GUID.equals(this.currentLocalName)) { // ignore
				} else if (COPYRIGHT.equals(this.currentLocalName)) { // ignore
				} else if (TTL.equals(this.currentLocalName)) { // ignore
				} else {
					MTLog.d(this, "characters() > Unexpected element '%s'", this.currentLocalName);
				}
			} catch (Exception e) {
				MTLog.w(this, e, "Error while parsing '%s' value '%s, %s, %s'!", this.currentLocalName, ch, start, length);
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException {
			super.endElement(uri, localName, qName);
			try {
				if (ITEM.equals(localName)) {
					processItem();
					this.currentItem = false;
				}
			} catch (Exception e) {
				MTLog.w(this, e, "Error while parsing '%s' end element!", this.currentLocalName);
			}
		}

		private static final String COLON = ": ";

		private void processItem() {
			Long pubDateInMs = getPublicationDateInMs();
			if (pubDateInMs == null) {
				return;
			}
			String uuid = getUUID(pubDateInMs);
			if (uuid == null) {
				return;
			}
			String title = this.currentTitleSb.toString().trim();
			String desc = this.currentDescriptionSb.toString().trim();
			String link = this.currentLinkSb.toString().trim();
			StringBuilder textSb = new StringBuilder();
			StringBuilder textHTMLSb = new StringBuilder();
			if (!TextUtils.isEmpty(title)) {
				textSb.append(title);
				textHTMLSb.append(HtmlUtils.applyBold(title));
			}
			if (!TextUtils.isEmpty(desc)) {
				if (textSb.length() > 0) {
					textSb.append(COLON);
				}
				textSb.append(Html.fromHtml(desc));
				if (textHTMLSb.length() > 0) {
					textHTMLSb.append(HtmlUtils.BR);
				}
				textHTMLSb.append(desc);
			}
			if (!TextUtils.isEmpty(link)) {
				if (textHTMLSb.length() > 0) {
					textHTMLSb.append(HtmlUtils.BR).append(HtmlUtils.BR);
				}
				textHTMLSb.append(HtmlUtils.linkify(link));
			}
			if (textSb.length() == 0 || textHTMLSb.length() == 0) {
				return;
			}
			this.news.add(new News(null, this.authority, uuid, this.severity, this.noteworthyInMs, this.lastUpdateInMs, this.maxValidityInMs, pubDateInMs,
					this.target, this.color, this.authorName, null, null, this.authorUrl, //
					StringUtils.oneLineOneSpace(textSb.toString()), //
					textHTMLSb.toString(), //
					link, this.language, AGENCY_SOURCE_ID, this.label));
		}

		private static final Pattern CONVERT_URL_TO_ID = Pattern.compile("[/.:]", Pattern.CASE_INSENSITIVE);
		private static final String CONVERT_URL_TO_ID_REPLACEMENT = "_";

		private String getUUID(Long pubDateInMs) {
			String guid = this.currentGUIDSb.toString().trim();
			if (guid.length() > 0) {
				if (this.currentGUIDIsPermaLink != null && !this.currentGUIDIsPermaLink) { // not URL
					return AGENCY_SOURCE_ID + guid;
				}
				guid = CONVERT_URL_TO_ID.matcher(guid).replaceAll(CONVERT_URL_TO_ID_REPLACEMENT);
				if (!TextUtils.isEmpty(guid)) {
					return AGENCY_SOURCE_ID + guid;
				}
			}
			String link = this.currentLinkSb.toString().trim();
			if (link.length() > 0) {
				link = CONVERT_URL_TO_ID.matcher(link).replaceAll(CONVERT_URL_TO_ID_REPLACEMENT);
				if (!TextUtils.isEmpty(link)) {
					return AGENCY_SOURCE_ID + link;
				}
			}
			if (pubDateInMs != null) {
				return AGENCY_SOURCE_ID + pubDateInMs;
			}
			MTLog.w(this, "getUUID() > can't find UUID! (GUID: %s, LINK: %s, DATE: %s)", this.currentGUIDSb, this.currentLinkSb, null);
			return null;
		}

		private static final ThreadSafeDateFormatter RSS_PUB_DATE_FORMATTER = new ThreadSafeDateFormatter("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH);
		private static final ThreadSafeDateFormatter ATOM_UPDATED_FORMATTER = new ThreadSafeDateFormatter("yyyy-MM-dd'T'HH:mm:ssZZZZZ", Locale.ENGLISH);
		private static final ThreadSafeDateFormatter DC_DATE_FORMATTER = new ThreadSafeDateFormatter("yyyy-MM-dd'T'HH:mm:ss.SSSZZZZZ", Locale.ENGLISH);

		@Nullable
		private Long getPublicationDateInMs() {
			try {
				if (this.currentPubDateSb.length() > 0) {
					return RSS_PUB_DATE_FORMATTER.parseThreadSafe(this.currentPubDateSb.toString().trim()).getTime();
				}
			} catch (Exception e) {
				MTLog.w(this, e, "Error while parsing pub date '%s'!!", this.currentPubDateSb);
			}
			try {
				if (this.currentUpdatedSb.length() > 0) {
					return ATOM_UPDATED_FORMATTER.parseThreadSafe(this.currentUpdatedSb.toString().trim()).getTime();
				}
			} catch (Exception e) {
				MTLog.w(this, e, "Error while parsing updated date '%s'!!", this.currentUpdatedSb);
			}

			try {
				if (this.currentDateSb.length() > 0) {
					return DC_DATE_FORMATTER.parseThreadSafe(this.currentDateSb.toString().trim()).getTime();
				}
			} catch (Exception e) {
				MTLog.w(this, e, "Error while parsing date '%s'!!", this.currentDateSb);
			}
			return null;
		}
	}

	private static class RSSNewsDbHelper extends NewsProvider.NewsDbHelper {

		private static final String LOG_TAG = RSSNewsDbHelper.class.getSimpleName();

		@Override
		public String getLogTag() {
			return LOG_TAG;
		}

		/**
		 * Override if multiple {@link RSSNewsDbHelper} implementations in same app.
		 */
		protected static final String DB_NAME = "rss.db";

		/**
		 * Override if multiple {@link RSSNewsDbHelper} implementations in same app.
		 */
		protected static final String PREF_KEY_AGENCY_LAST_UPDATE_MS = "pRSSNewsLastUpdate";

		/**
		 * Override if multiple {@link RSSNewsDbHelper} implementations in same app.
		 */
		protected static final String PREF_KEY_AGENCY_LAST_UPDATE_LANG = "pRSSNewsLastUpdateLang";

		public static final String T_RSS_NEWS = NewsProvider.NewsDbHelper.T_NEWS;

		private static final String T_RSS_NEWS_SQL_CREATE = NewsProvider.NewsDbHelper.getSqlCreateBuilder(T_RSS_NEWS).build();

		private static final String T_RSS_NEWS_SQL_DROP = SqlUtils.getSQLDropIfExistsQuery(T_RSS_NEWS);

		private static int dbVersion = -1;

		/**
		 * Override if multiple {@link RSSNewsDbHelper} in same app.
		 */
		public static int getDbVersion(@NonNull Context context) {
			if (dbVersion < 0) {
				dbVersion = context.getResources().getInteger(R.integer.rss_db_version);
			}
			return dbVersion;
		}

		private Context context;

		public RSSNewsDbHelper(@NonNull Context context) {
			this(context, DB_NAME, getDbVersion(context));
		}

		public RSSNewsDbHelper(@NonNull Context context, String dbName, int dbVersion) {
			super(context, dbName, dbVersion);
			this.context = context;
		}

		@Override
		public String getDbName() {
			return DB_NAME;
		}

		@Override
		public void onCreateMT(@NonNull SQLiteDatabase db) {
			initAllDbTables(db);
		}

		@Override
		public void onUpgradeMT(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
			db.execSQL(T_RSS_NEWS_SQL_DROP);
			PreferenceUtils.savePrefLcl(this.context, PREF_KEY_AGENCY_LAST_UPDATE_MS, 0L, true);
			PreferenceUtils.savePrefLcl(this.context, PREF_KEY_AGENCY_LAST_UPDATE_LANG, StringUtils.EMPTY, true);
			initAllDbTables(db);
		}

		private void initAllDbTables(@NonNull SQLiteDatabase db) {
			db.execSQL(T_RSS_NEWS_SQL_CREATE);
		}
	}
}
