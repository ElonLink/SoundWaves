package org.bottiger.podcast.playlist;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.bottiger.podcast.ApplicationConfiguration;
import org.bottiger.podcast.PlaylistFragment;
import org.bottiger.podcast.R;
import org.bottiger.podcast.SoundWaves;
import org.bottiger.podcast.adapters.PlaylistAdapter;
import org.bottiger.podcast.adapters.decoration.OnDragStateChangedListener;
import org.bottiger.podcast.flavors.CrashReporter.VendorCrashReporter;
import org.bottiger.podcast.model.Library;
import org.bottiger.podcast.model.events.EpisodeChanged;
import org.bottiger.podcast.playlist.filters.SubscriptionFilter;
import org.bottiger.podcast.provider.DatabaseHelper;
import org.bottiger.podcast.provider.FeedItem;
import org.bottiger.podcast.provider.IEpisode;
import org.bottiger.podcast.provider.ISubscription;
import org.bottiger.podcast.provider.ItemColumns;
import org.bottiger.podcast.provider.PodcastOpenHelper;
import org.bottiger.podcast.provider.Subscription;
import org.bottiger.podcast.provider.SubscriptionColumns;
import org.bottiger.podcast.service.PlayerService;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.IntDef;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.widget.CursorAdapter;
import android.support.v7.util.SortedList;
import android.util.Log;

import com.google.api.client.util.DateTime;
import com.squareup.otto.Subscribe;

import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;

public class Playlist implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "Playlist";

    public static final boolean SHOW_LISTENED_DEFAULT = true;
    public static final boolean SHOW_ONLY_DOWNLOADED  = false;
    public static final boolean PLAY_NEXT_DEFAULT     = false;

	public static int MAX_SIZE = 20;

    private static final String mSortNew = "DESC";
    private static final String mSortOld = "ASC";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({DATE_NEW_FIRST, DATE_OLD_FIRST, NOT_SET})
    public @interface SortOrder {}
    public static final int DATE_NEW_FIRST = 0;
    public static final int DATE_OLD_FIRST = 1;
    public static final int NOT_SET = 2;

    private int mSortOrder = DATE_NEW_FIRST;

    private boolean mIsLoaded = false;

    private Context mContext;
    private Library mLibrary;

    private SubscriptionFilter mSubscriptionFilter;

	private ArrayList<IEpisode> mInternalPlaylist = new ArrayList<>();
	private SharedPreferences sharedPreferences;

	// Shared setting key/values
	private final String showListenedKey = ApplicationConfiguration.showListenedKey;
	private boolean showListenedVal = SHOW_LISTENED_DEFAULT;
    private boolean showOnlyDownloadedVal = SHOW_ONLY_DOWNLOADED;
	private String inputOrderKey = "inputOrder";
	private String defaultOrder = mSortNew;
	private String amountKey = "amountOfEpisodes";
	private int amountValue = 20;

    private rx.Subscription mRxSubscription;

	// http://stackoverflow.com/questions/1036754/difference-between-wait-and-sleep

	public Playlist(@NonNull Context argContext, int length) {
		this(argContext, length, false);
	}

	public Playlist(@NonNull Context argContext, int length, boolean isLocked) {
        setContext(argContext);
    }

	public Playlist(@NonNull Context argContext) {
		this(argContext, MAX_SIZE);
	}

    public void setContext(@NonNull Context argContext) {
        mLibrary = SoundWaves.getAppContext(argContext).getLibraryInstance();

        sharedPreferences = PreferenceManager
                   .getDefaultSharedPreferences(argContext);
        showListenedVal = sharedPreferences.getBoolean(showListenedKey, showListenedVal);

        String downloadKey = SoundWaves.getAppContext().getString(R.string.pref_only_downloaded_key);
        showOnlyDownloadedVal = sharedPreferences.getBoolean(downloadKey, SHOW_ONLY_DOWNLOADED);

        mSubscriptionFilter = new SubscriptionFilter(argContext);
        this.mContext = argContext;

        mRxSubscription = SoundWaves.getRxBus()
                .toObserverable()
                .onBackpressureDrop()
                .ofType(PlaylistData.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<PlaylistData>() {
                    @Override
                    public void call(PlaylistData argPlaylistData) {
                        onPlaylistChanged(argPlaylistData);
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        VendorCrashReporter.handleException(throwable);
                        Log.wtf(TAG, "Missing back pressure. Should not happen anymore :(");
                    }
                });
    }

    public void destroy() {
        if (mRxSubscription != null && !mRxSubscription.isUnsubscribed()) {
            mRxSubscription.unsubscribe();
        }
    }

	/**
	 * @return The playlist as a list of episodes
	 */
	public ArrayList<IEpisode> getPlaylist() {
		return mInternalPlaylist;
	}

	/**
	 * 
	 * @return the size of the playlist
	 */
	public int size() {
		return mInternalPlaylist.size();
	}

    public int defaultSize() {
        return amountValue;
    }

	/**
	 * 
	 * @param position
	 *            in the playlist (0-indexed)
	 * @return The episode at the given position
	 */
	public IEpisode getItem(int position) {
        if (position >= mInternalPlaylist.size())
            return null;

		return mInternalPlaylist.get(position);
	}

    public IEpisode getNext() {
        return getItem(1);
    }

    /**
     *
	 * @param episode
	 * @return The position of the episode
	 */
	public int getPosition(IEpisode episode) {
		return mInternalPlaylist.indexOf(episode);
	}

    public void setAsFrist(@NonNull IEpisode item) {

        if (mInternalPlaylist.isEmpty()) {
            mInternalPlaylist.add(0, item);
            notifyPlaylistChanged();
            return;
        }

        if (item.equals(mInternalPlaylist.get(0)))
            return;

        mInternalPlaylist.remove(item);
        mInternalPlaylist.add(0, item);
        notifyPlaylistChanged();
    }

	/**
	 * 
	 * @param position
	 * @param item
	 */
	public void setItem(int position, IEpisode item) {
		mInternalPlaylist.remove(item);

        int size = mInternalPlaylist.size();
		if (size > position) {
            mInternalPlaylist.add(position, item);
        } else if (size == position) {
			mInternalPlaylist.add(item);
		}
	}

    /**
     *
     * @param position
     */
    public void removeItem(int position, boolean argIsSilent) {
        if (position < 0) {
            VendorCrashReporter.report("Playlist remove", "Position must be greater or equal to zero"); // NoI18N
            return;
        }

        int size = mInternalPlaylist.size();
        if (size > position) {
            mInternalPlaylist.remove(position);
        }

        if (!argIsSilent)
            notifyPlaylistChanged();
    }

    /**
     *
     * @param position
     */
    public void removeItem(int position) {
        removeItem(position, false);
    }

	/**
	 * @return The next item in the playlist
	 */
	public IEpisode nextEpisode() {
		if (mInternalPlaylist.size() > 1) {
            return mInternalPlaylist.get(1);
        }
		return null;
	}

	/**
	 * 
	 * @param from
	 *            , old position in the playlist
	 * @param to
	 *            , the new position in the playlist
	 */
	public void move(int from, int to) {
        populatePlaylistIfEmpty();

        IEpisode fromItem = mInternalPlaylist.get(from);
        mInternalPlaylist.remove(from);
        mInternalPlaylist.add(to, fromItem);

        int min = from;
        int max = to;

        if (to < from) {
            min = to;
            max = from;
        }

        IEpisode precedingItem = to == 0 ? null : mInternalPlaylist.get(to-1);
        IEpisode movedItem = mInternalPlaylist.get(from);

        if (movedItem instanceof FeedItem && precedingItem instanceof FeedItem) {
            persist(mContext, (FeedItem) movedItem, (FeedItem) precedingItem, from, to);
        }
	}

    public void queue(@NonNull IEpisode argEpisode) {

        int currentPosition = -1;
        int lastPlaylistPosition = -1;

        for (int position = 0; position < mInternalPlaylist.size(); position++) {
            IEpisode item = mInternalPlaylist.get(position);

            // Find current position, if any
            if (argEpisode.equals(item)) {
                currentPosition = position;
            }


            // Find end of the queue
            if (item.getPriority() <= 0 && lastPlaylistPosition < 0) {
                lastPlaylistPosition = position;
            }
        }

        if (currentPosition < 0) {

            IEpisode preceedingItem = null;

            if (lastPlaylistPosition <= 0) {
                lastPlaylistPosition = mInternalPlaylist.size();
            } else {
                preceedingItem = mInternalPlaylist.get(lastPlaylistPosition-1);
            }

            if (preceedingItem == null) {
                argEpisode.setPriority(1);
            } else {
                argEpisode.setPriority(preceedingItem.getPriority() + 1);
            }

            SoundWaves.getAppContext(mContext).getLibraryInstance().updateEpisode(argEpisode);

            mInternalPlaylist.add(lastPlaylistPosition, argEpisode);

            for (int pos = lastPlaylistPosition; pos < mInternalPlaylist.size(); pos++) {
                IEpisode episode = mInternalPlaylist.get(pos);

                int priority = episode.getPriority();
                if (priority > 0) {
                    episode.setPriority(priority+1);
                    SoundWaves.getAppContext(mContext).getLibraryInstance().updateEpisode(episode);
                } else {
                    break;
                }
            }

            notifyPlaylistChanged();
            return;
        }

        if (lastPlaylistPosition >= 0) {
            move(currentPosition, lastPlaylistPosition);
            notifyPlaylistChanged();
            return;
        }

        mInternalPlaylist.add(0, argEpisode);

        notifyPlaylistChanged();

    }

	/**
	 * 
	 *
	 *            of episodes
	 * @return A SQL formatted string of the order
	 */
	public String getOrder() {

		String inputOrder = sharedPreferences.getString(inputOrderKey,
                defaultOrder);
		int amount = sharedPreferences.getInt(amountKey, amountValue);

		PlayerService playerService = PlayerService.getInstance();

		String playingFirst = "";
		if (playerService != null && playerService.getCurrentItem() != null && playerService.getCurrentItem() instanceof FeedItem) {
			playingFirst = "case " + ItemColumns.TABLE_NAME + "." + ItemColumns._ID + " when "
					+ + ((FeedItem)playerService.getCurrentItem()).getId()
					+ " then 1 else 2 end, ";
		}
		String prioritiesSecond = "case " + ItemColumns.TABLE_NAME + "." + ItemColumns.PRIORITY
				+ " when 0 then 1 else 2 end DESC, " + ItemColumns.TABLE_NAME + "." + ItemColumns.PRIORITY + " ASC, ";

        String deprecatedDateOrder = ItemColumns.TABLE_NAME + "." + ItemColumns.DATE + " " + inputOrder + " ";
        String dateOrder = ItemColumns.TABLE_NAME + "." + ItemColumns.PUB_DATE + " " + inputOrder + ", ";

		String order = playingFirst + prioritiesSecond + dateOrder + deprecatedDateOrder
				+ " LIMIT " + amount; // before:
		return order;
	}

	/**
	 * 
	 * @return A SQL formatted string of the where clause
	 */
	public String getWhere() {

        String where = "";

        // Ignore (or include) podcasts which have been toggled manually
        String filterSubscriptions = "";
        filterSubscriptions += " AND CASE WHEN (" + SubscriptionColumns.TABLE_NAME + "." + SubscriptionColumns.SETTINGS + ">0 & ((" +
                SubscriptionColumns.TABLE_NAME + "." + SubscriptionColumns.SETTINGS + " & " +
                Subscription.ADD_NEW_TO_PLAYLIST_SET + ") <> 0)) THEN (";
        filterSubscriptions += SubscriptionColumns.TABLE_NAME + "." + SubscriptionColumns.SETTINGS + " & " + Subscription.ADD_NEW_TO_PLAYLIST;
        filterSubscriptions += ") ELSE 1 END";

        where += "(";


        String ids = "";
        if (mSubscriptionFilter.getMode() == SubscriptionFilter.SHOW_SELECTED) {
            where += mSubscriptionFilter.toSQL();
        } else {
            where += ItemColumns.TABLE_NAME + "." + ItemColumns.SUBS_ID+ " ";
            // only find episodes from suscriptions which are not "unsubscribed"
            where += "IN (SELECT " + SubscriptionColumns.TABLE_NAME + "." + SubscriptionColumns._ID + " FROM "  +
                    SubscriptionColumns.TABLE_NAME + " WHERE (" + SubscriptionColumns.TABLE_NAME + "." + SubscriptionColumns.STATUS + "<>"
                    + Subscription.STATUS_UNSUBSCRIBED + " OR " + SubscriptionColumns.TABLE_NAME + "." + SubscriptionColumns.STATUS + " IS NULL)" +  filterSubscriptions + " )";
        }

        where += " )";

        where += " AND " + mSubscriptionFilter.toSQL();

        // show only downloaded
        if (showOnlyDownloadedVal) {
            where += " AND (" + ItemColumns.IS_DOWNLOADED + "==1)";
        }

        // skip 'removed' episodes
        where += " AND (" + ItemColumns.TABLE_NAME + "." + ItemColumns.PRIORITY + " >= 0)";

        // Manually queued episodes
        where += " OR (" + ItemColumns.TABLE_NAME + "." + ItemColumns.PRIORITY + " >= 0)";


		return where;
	}

	/**
	 * 
	 */
	public void resetPlaylist(CursorAdapter adapter) {
		// Update the database
		String currentTime = String.valueOf(System.currentTimeMillis());
		String updateLastUpdate = ", " + ItemColumns.LAST_UPDATE + "="
				+ currentTime + " ";

		// We remove the playlist position for all items in the playlist.
		String action = "UPDATE " + ItemColumns.TABLE_NAME + " SET ";
		String value = ItemColumns.PRIORITY + "=0" + updateLastUpdate;
		String where = "WHERE " + ItemColumns.PRIORITY + "<> 0";

		// Also update the timestamp of the top item in order to indicate to the
		// drivesyncer
		// Our data is up tp date.
		String where2 = " OR " + ItemColumns._ID + "==(select "
				+ ItemColumns._ID + " from " + ItemColumns.TABLE_NAME
				+ " order by " + ItemColumns.DATE + " desc limit 1)";

		String sql = action + value + where + where2;

		DatabaseHelper dbHelper = new DatabaseHelper();
		dbHelper.executeSQL(mContext, sql, adapter);
	}

	/**
	 * Populates the playlist up to a certain length if the playlist is empty
	 */
	public boolean populatePlaylistIfEmpty() {
		if (mInternalPlaylist.isEmpty()) {
            populatePlaylist();
            return true;
        }
        return false;
	}

	/**
	 * Populates the playlist up to a certain length
	 */
    public void populatePlaylist() {
        populatePlaylist(MAX_SIZE, false);
    }

	public void populatePlaylist(int length, boolean force) {
        if (mInternalPlaylist.size() >= length && !force) {
            return;
        }

        if (mContext == null) {
            Log.e("PlaylistState", "Context can not be null!");
            throw new IllegalStateException("Context can not be null");
        }

        int previousSize = mInternalPlaylist.size();

        SortedList<IEpisode> episodes = mLibrary.newEpisodeSortedList(new SortedList.Callback<IEpisode>() {
            @Override
            public int compare(IEpisode o1, IEpisode o2) {
                return comparePlaylistOrder(o1, o2);
            }

            @Override
            public void onInserted(int position, int count) {

            }

            @Override
            public void onRemoved(int position, int count) {

            }

            @Override
            public void onMoved(int fromPosition, int toPosition) {

            }

            @Override
            public void onChanged(int position, int count) {

            }

            @Override
            public boolean areContentsTheSame(IEpisode oldItem, IEpisode newItem) {
                return false;
            }

            @Override
            public boolean areItemsTheSame(IEpisode item1, IEpisode item2) {
                return false;
            }
        });

        mInternalPlaylist.clear();
        for (int i = 0; i < episodes.size(); i++) {

            IEpisode episode = episodes.get(i);

            boolean subscriptionIsShown = false;
            boolean downloadStateIsShown = false;
            boolean listenedStateIsShown = false;

            // Filter based on shown subscriptions
            if (episode instanceof FeedItem) {
                FeedItem item = (FeedItem)episode;
                subscriptionIsShown = mSubscriptionFilter.isShown(item.sub_id);
            }

            // Filter based on 'is listened'
            if (mSubscriptionFilter.showListened() || !episode.isMarkedAsListened()) {
                listenedStateIsShown = true;
            }

            // Filter based on donwload state
            if (!showOnlyDownloadedVal || episode.isDownloaded()) {
                downloadStateIsShown = true;
            }

            boolean highPriority = episode.getPriority() > 0;

            boolean doAdd = highPriority || (subscriptionIsShown && downloadStateIsShown && listenedStateIsShown);

            if (doAdd) {
                mInternalPlaylist.add(episode);
            }

            if (mInternalPlaylist.size() >= length)
                break;
        }

        int newSize = mInternalPlaylist.size();

        // prevent infinite loop because "populateIfEmpty" will be called again and again
        if (previousSize == 0 && newSize == 0)
            return;

        Handler mainHandler = new Handler(mContext.getMainLooper());

        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                notifyPlaylistChanged();
            } // This is your code
        };
        mainHandler.post(myRunnable);

	}

    /**
     * Write changes to the playlist back to the database.
     * @param context
     * @param from
     * @param to
     */
    public static void persist(final Context context,
                        final FeedItem movedItem, final FeedItem precedingItem, final int from, final int to) {
        new Thread(new Runnable() {
            public void run() {

                if (from != to) {
                    movedItem.setPriority(precedingItem, context);
                }
            }
        }).start();
    }

    public boolean contains(IEpisode argItem) {
        return mInternalPlaylist.contains(argItem);
    }

    public boolean isEmpty() {
        return mInternalPlaylist.isEmpty();
    }

    public IEpisode first() {
        if (mInternalPlaylist.size() <= 0) {
            throw new IllegalStateException("Playlist is empty"); // NoI18N
        }
        return mInternalPlaylist.get(0);
    }

    public void notifyDatabaseChanged() {
        populatePlaylist(amountValue, true);
        notifyPlaylistChanged();
    }

    @MainThread
    public void notifyPlaylistChanged() {
        Log.d(TAG, "notifyPlaylistChanged");
        SoundWaves.getRxBus().send(this);
    }

    void onPlaylistChanged(@NonNull PlaylistData argPlaylistData) {
        if (argPlaylistData.showListened != null) {
            setShowListened(argPlaylistData.showListened);
        }

        if (argPlaylistData.sortOrder != NOT_SET) {
            setSortOrder(argPlaylistData.sortOrder);
        }

        if (argPlaylistData.reset != null) {
            resetPlaylist(null);
            mInternalPlaylist.clear();
            populatePlaylist();
        }

        if (argPlaylistData.playlistChanged != null) {
            mInternalPlaylist.clear();
            populatePlaylist(MAX_SIZE, true);
        }

        if (argPlaylistData.onlyDownloaded != null) {
            showOnlyDownloaded(argPlaylistData.onlyDownloaded);
        }

        SoundWaves.getRxBus().send(Playlist.this);
    }

    @SuppressLint("CommitPrefEdits")
    public void showOnlyDownloaded(boolean argOnlyDownloaded) {
        boolean isChanged = showOnlyDownloadedVal != argOnlyDownloaded;
        showOnlyDownloadedVal = argOnlyDownloaded;

        if (isChanged) {
            String key = SoundWaves.getAppContext().getString(R.string.pref_only_downloaded_key);
            sharedPreferences.edit().putBoolean(key, argOnlyDownloaded).commit();
            notifyDatabaseChanged();
        }
    }

    @SuppressLint("CommitPrefEdits")
    public void setSortOrder(@SortOrder int argSortOrder) {
        boolean isChanged = mSortOrder != argSortOrder;
        mSortOrder = argSortOrder;

        if (isChanged) {
            String order = mSortOrder == DATE_NEW_FIRST ? mSortNew : mSortOld;
            sharedPreferences.edit().putString(inputOrderKey, order).commit();
            notifyDatabaseChanged();
        }
    }

    @SuppressLint("CommitPrefEdits")
    public void setShowListened(boolean argShowListened) {
        boolean isChanged = showListenedVal != argShowListened;
        showListenedVal = argShowListened;

        if (isChanged) {
            sharedPreferences.edit().putBoolean(showListenedKey, argShowListened).commit();
            notifyDatabaseChanged();
        }
    }

    public SubscriptionFilter getSubscriptionFilter() {
        return mSubscriptionFilter;
    }

    public void setIsLoaded(boolean argIsLoaded) {
        mIsLoaded = argIsLoaded;

        if (mIsLoaded) {
            notifyPlaylistChanged();
        }
    }

    public boolean isLoaded() {
        return mIsLoaded;
    }

    public void notifyFiltersChanged() {
        setIsLoaded(false);
        mLibrary.loadPlaylist(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

    }

    public static void refresh(@NonNull Context argContext) {
        // Notify the playlist about the change
        Handler mainHandler = new Handler(argContext.getMainLooper());

        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                PlaylistData pd = new PlaylistData();
                pd.playlistChanged = true;
                SoundWaves.getRxBus().send(pd);
            }
        };
        mainHandler.post(myRunnable);
    }

    public static void changePlaylistFilter(@NonNull Context argContext, @Nullable Playlist argPlaylist, @SubscriptionFilter.Mode int argMode) {
        if (argPlaylist == null)
            return;

        SubscriptionFilter filter = argPlaylist.getSubscriptionFilter();

        if (filter.getMode() == argMode)
            return;

        filter.setMode(argMode, argContext);
        argPlaylist.notifyDatabaseChanged();
    }

    private static int comparePlaylistOrder(@Nullable IEpisode argEpisode1,
                                            @Nullable IEpisode argEpisode2) {
        int E1_FIRST = 1;
        int E2_FIRST = -1;

        if (argEpisode1 == null)
            return E2_FIRST;

        if (argEpisode2 == null)
            return E1_FIRST;

        int p1 = argEpisode1.getPriority();
        int p2 = argEpisode2.getPriority();

        if (p1 != p2) {
            return p1 < p2 ? E1_FIRST : E2_FIRST;
        }

        Date dt1 = argEpisode1.getDateTime();

        if (dt1 == null)
            return E2_FIRST;

        Date dt2 = argEpisode2.getDateTime();

        if (dt2 == null)
            return E1_FIRST;

        return dt2.compareTo(dt1);
    }
}
