package org.bottiger.podcast.service;

import org.bottiger.podcast.R;
import org.bottiger.podcast.flavors.CrashReporter.VendorCrashReporter;
import org.bottiger.podcast.player.LegacyRemoteController;
import org.bottiger.podcast.player.PlayerHandler;
import org.bottiger.podcast.player.PlayerPhoneListener;
import org.bottiger.podcast.player.PlayerStateManager;
import org.bottiger.podcast.player.SoundWavesPlayer;
import org.bottiger.podcast.SoundWaves;
import org.bottiger.podcast.flavors.MediaCast.IMediaCast;
import org.bottiger.podcast.listeners.PlayerStatusData;
import org.bottiger.podcast.listeners.PlayerStatusObservable;
import org.bottiger.podcast.notification.NotificationPlayer;
import org.bottiger.podcast.player.exoplayer.ExoPlayerWrapper;
import org.bottiger.podcast.playlist.Playlist;
import org.bottiger.podcast.provider.FeedItem;
import org.bottiger.podcast.provider.IEpisode;
import org.bottiger.podcast.provider.ISubscription;
import org.bottiger.podcast.provider.Subscription;
import org.bottiger.podcast.receiver.HeadsetReceiver;
import org.bottiger.podcast.service.Downloader.SoundWavesDownloadManager;
import org.bottiger.podcast.service.jobservice.PodcastUpdater;
import org.bottiger.podcast.utils.PlaybackSpeed;
import org.bottiger.podcast.widgets.SoundWavesWidgetProvider;

import android.Manifest;
import android.app.NotificationManager;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.session.MediaSessionManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.annotation.IntDef;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresPermission;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaControllerCompat;
import android.telephony.PhoneStateListener;
import android.util.Log;

import com.google.android.exoplayer.ExoPlayer;
import com.squareup.otto.Subscribe;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URL;
import java.util.List;

import javax.annotation.Nullable;

/**
 * The service which handles the audio extended_player. This is responsible for playing
 * including controlling the playback. Play, pause, stop etc.
 * 
 * @author Arvid Böttiger
 */
public class PlayerService extends MediaBrowserServiceCompat implements
		AudioManager.OnAudioFocusChangeListener {

    private static final String TAG = "PlayerService";

    public static final String ACTION_PLAY = "action_play";
    public static final String ACTION_PAUSE = "action_pause";
    public static final String ACTION_REWIND = "action_rewind";
    public static final String ACTION_FAST_FORWARD = "action_fast_foward";
    public static final String ACTION_NEXT = "action_next";
    public static final String ACTION_PREVIOUS = "action_previous";
    public static final String ACTION_STOP = "action_stop";

	public static final String ACTION_REFRESH_FEEDS = "action_refresh_feeds";

	/** Which action to perform when a track ends */
	@Retention(RetentionPolicy.SOURCE)
	@IntDef({NONE, NEW_TRACK, NEXT_IN_PLAYLIST})
	public @interface NextTrack {}
	public static final int NONE = 1;
	public static final int NEW_TRACK = 2;
	public static final int NEXT_IN_PLAYLIST = 3;

	private static @PlayerService.NextTrack int nextTrack = NEXT_IN_PLAYLIST;

	@NonNull private SoundWavesPlayer mPlayer;
    private MediaControllerCompat mController;
    private PlayerStateManager mPlayerStateManager;

    // Google Cast
    private IMediaCast mMediaCast;


    private NotificationManager mNotificationManager;
    @Nullable private NotificationPlayer mNotificationPlayer;
    @Nullable private MediaSessionManager mMediaSessionManager;

	// AudioManager
	private AudioManager mAudioManager;
	private ComponentName mControllerComponentName;

	private IEpisode mItem = null;
    private boolean mResumePlayback = false;

    private final String LOCK_NAME = "SoundWavesWifiLock";
    WifiManager.WifiLock wifiLock;

    private PlayerHandler mPlayerHandler;

	/**
	 * Phone state listener. Will pause the playback when the phone is ringing
	 * and continue it afterwards
	 */
	private PhoneStateListener mPhoneStateListener;

	public void startAndFadeIn() {
        mPlayerHandler.sendEmptyMessageDelayed(PlayerHandler.FADEIN, 10);
	}

	public void FaceOutAndStop(int argDelayMs) {
		mPlayerHandler.sendEmptyMessageDelayed(PlayerHandler.FADEOUT, argDelayMs);
	}

    public SoundWavesPlayer getPlayer() {
        return mPlayer;
    }
	private static PlayerService sInstance = null;

	@Nullable
	public static PlayerService getInstance() {
		return sInstance;
	}

	@RequiresPermission(Manifest.permission.READ_PHONE_STATE)
	@Override
	public void onCreate() {
		super.onCreate();
        Log.d(TAG, "PlayerService started");

		sInstance = this;

        wifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE))
                .createWifiLock(WifiManager.WIFI_MODE_FULL, LOCK_NAME);

		SoundWaves.getBus().register(SoundWaves.getAppContext(this).getPlaylist());
		SoundWaves.getBus().register(this);

        mPlayerHandler = new PlayerHandler(this);

		mPlayerStateManager = new PlayerStateManager(this);
		try {
			mController = new MediaControllerCompat(getApplicationContext(), mPlayerStateManager.getToken()); // .fromToken( mSession.getSessionToken() );
		} catch (RemoteException e) {
			e.printStackTrace();
		}

		setSessionToken(mPlayerStateManager.getToken());
		
		mPlayer = SoundWaves.getAppContext(this).getPlayer();
		mPlayer.setHandler(mPlayerHandler);
		mPlayer.addListener(new ExoPlayerWrapper.Listener() {
			@Override
			public void onStateChanged(boolean playWhenReady, @ExoPlayerWrapper.PlayerState int playbackState) {
				if (playbackState == ExoPlayerWrapper.STATE_READY) {
					IEpisode episode = getCurrentItem();
					if (episode != null) {
						long duration = mPlayer.getDuration();
						if (duration != ExoPlayer.UNKNOWN_TIME && episode.setDuration(duration)) {
							SoundWaves.getAppContext(PlayerService.this).getLibraryInstance().updateEpisode(episode);
						}
					}
				}
			}

			@Override
			public void onError(Exception e) {

			}

			@Override
			public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {

			}
		});

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mMediaSessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        }

		mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		mPhoneStateListener = new PlayerPhoneListener(this);

		this.mControllerComponentName = new ComponentName(this,
				HeadsetReceiver.class);
		this.mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }

	@Override
	public void onAudioFocusChange(int focusChange) {
		if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            if (isPlaying()) {
                // Pause playback
                pause();
                mResumePlayback = true;
            }
		} else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
			// Resume playback
			if (mResumePlayback) {
				start();
				mResumePlayback = false;
			}
		} else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
			mAudioManager.abandonAudioFocus(this);
			// Stop playback
			stop();
		}
	}

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(TAG, "onStartCommand");
		MediaButtonReceiver.handleIntent(mPlayerStateManager.getSession(), intent);
        return START_STICKY;
    }

	@Override
	public void onDestroy() {
		sInstance = null;
		SoundWaves.getBus().unregister(SoundWaves.getAppContext(this).getPlaylist());
		SoundWaves.getBus().unregister(this);
        super.onDestroy();
		if (mPlayer != null) {
			mPlayer.release();
		}
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
	}

	@android.support.annotation.Nullable
	@Override
	public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @android.support.annotation.Nullable Bundle rootHints) {
		return new BrowserRoot(getString(R.string.app_name), null);
	}

	@Override
	public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
		result.sendResult(null);
	}

	/**
	 * Hide the notification
	 */
    public void dis_notifyStatus() {

        if (mNotificationManager == null)
			mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		mNotificationManager.cancel(NotificationPlayer.NOTIFICATION_PLAYER_ID);

		if (mNotificationPlayer != null)
			mNotificationPlayer.hide();
	}

	/**
	 * Display a notification with the current podcast
	 */
    public void notifyStatus(@android.support.annotation.Nullable IEpisode argItem) {

		SoundWavesWidgetProvider.updateAllWidgets(this);

		if (argItem != null) {
			if (mNotificationPlayer == null)
				try {
					mNotificationPlayer = new NotificationPlayer(this, argItem);
				} catch (RemoteException e) {
					e.printStackTrace();
				}

			mItem = argItem;
			mNotificationPlayer.setPlayerService(this);
			mNotificationPlayer.show(argItem);
		}
    }

	public void playNext() {
        IEpisode item = getCurrentItem();
        IEpisode nextItem = SoundWaves.getAppContext(this).getPlaylist().getNext();

		if (item != null && item instanceof FeedItem) {
            ((FeedItem)item).trackEnded(getContentResolver());
        }

        if (nextItem == null) {
            stop();
            return;
        }

		play(nextItem.getUrl().toString());
		SoundWaves.getAppContext(this).getPlaylist().removeItem(0);
		SoundWaves.getAppContext(this).getPlaylist().notifyPlaylistChanged();
	}

	public void play() {
		if (mPlayer == null)
			return;

		if (mPlayer.isPlaying())
			return;

        if (mItem == null)
            mItem = SoundWaves.getAppContext(this).getPlaylist().getItem(0);

        if (mItem == null)
            return;

		play(mItem.getUrl().toString());
	}

	public void play(String argEpisodeURL) {

		// Pause the current episode in order to save the current state
		if (mPlayer.isPlaying())
			mPlayer.pause();

		if (mItem != null) {
			if ((mItem.getURL().equals(argEpisodeURL)) && mPlayer.isInitialized()) {
				if (!mPlayer.isPlaying()) {
					start();
				}
				return;
			}

			if (mPlayer.isPlaying()) {
                mItem.setOffset(getContentResolver(), mPlayer.position());
				stop();
			}
		}

		IEpisode oldItem = mItem;

		mItem = SoundWaves.getAppContext(this).getLibraryInstance().getEpisode(argEpisodeURL);

		if (mItem == null)
			return;


		// Removed the current top episode from the playlist if it has been started
		if (oldItem != null && !oldItem.equals(mItem)) {
			if (oldItem instanceof FeedItem) {
				FeedItem oldFeedItem = (FeedItem)oldItem;
				if (oldFeedItem.getPriority() > 0) {
					oldFeedItem.setPriority(0);
					int pos = SoundWaves.getAppContext(this).getPlaylist().getPosition(oldItem);
					if (pos >= 0) {
						SoundWaves.getAppContext(this).getPlaylist().removeItem(pos);
					}
				}

				oldFeedItem.removePriority();
			}
		}

        boolean isFeedItem = false;
        if (mItem instanceof FeedItem) {
            isFeedItem = true;
        }

        final FeedItem feedItem = isFeedItem ? (FeedItem)mItem : null;

		String dataSource = null;
		String dataSourceUrl = mItem.getUrl().toString();
		try {
			if (mItem.isDownloaded() && feedItem != null) {
				String path = feedItem.getAbsolutePath();
				File file = new File(path);
				if (file.exists()) {
					dataSource = path;
				}
			}
		} catch (IOException ignored) {
		} finally {
			if (dataSource == null) {
				dataSource = dataSourceUrl;
			}
		}

		int offset = mItem.getOffset() < 0 ? 0 : (int) mItem.getOffset();

		SoundWaves.getAppContext(this).getPlaylist().setAsFrist(mItem);

		ISubscription subscription = mItem.getSubscription(this);
		float speed = PlaybackSpeed.DEFAULT;

		float globalSpeed = PlaybackSpeed.globalPlaybackSpeed(this);
		if (globalSpeed != PlaybackSpeed.UNDEFINED) {
			speed = globalSpeed;
		}

		if (subscription != null && subscription instanceof Subscription) {
			speed = ((Subscription) subscription).getPlaybackSpeed();
		}

		mPlayer.setPlaybackSpeed(speed);

		mPlayer.setDataSourceAsync(dataSource, offset);

        IEpisode item = getCurrentItem();
        if (item != null) {
			updateMetadata(item);
        }

        if (feedItem != null) {
            if (feedItem.priority != 1)
                feedItem.setPriority(null, getApplication());
			SoundWaves.getAppContext(this).getLibraryInstance().updateEpisode(feedItem);
        }
	}

	private void updateMetadata(IEpisode item) {
		notifyStatus(item);
		mPlayerStateManager.updateMedia(item);
	}

    /**
     *
     * @return True of the songs start to play
     */
	@MainThread
	public boolean toggle(@NonNull IEpisode argEpisode) {


        if (mPlayer.isPlaying()) {
            IEpisode item = getCurrentItem();
            if (argEpisode.equals(item)) {
                pause();
                return false;
            }
        }

		URL url = argEpisode.getUrl();

		if (url == null) {
			ISubscription subscription = argEpisode.getSubscription(this);
			if (subscription != null)
				VendorCrashReporter.report("Malform episode", "subscription: " + subscription.toString());

			Log.wtf(TAG, "Malform episode");
			return false;
		}

        play(url.toString());
        return true;
	}

	public void toggle() {
		IEpisode episode = SoundWaves.getAppContext(this).getPlaylist().getItem(0);

		if (episode == null)
			return;

		toggle(episode);
	}

	private void start() {
		if (!mPlayer.isPlaying()) {
			if (mItem == null)
				mItem = SoundWaves.getAppContext(this).getPlaylist().getItem(0);

			if (mItem == null)
				return;

            takeWakelock(mPlayer.isSteaming());
			SoundWaves.getAppContext(this).getPlaylist().setAsFrist(mItem);
			mPlayer.start();

			notifyStatus(mItem);
		}
	}

	public void pause() {
		if (!mPlayer.isPlaying()) {
			return;
		}

		if ((mItem != null)) {
            if (mItem instanceof FeedItem) {
                (mItem).setOffset(getContentResolver(), mPlayer.position());
            }
		}

		//dis_notifyStatus();

		mPlayer.pause();
        releaseWakelock();
	}

	public void stop() {
		pause();
		mPlayer.stop();
		mItem = null;
		dis_notifyStatus();
	}

	public void halt() {
        stopForeground(true);
        dis_notifyStatus();
		mPlayer.stop();
	}

	public boolean isInitialized() {
		return mPlayer.isInitialized();
	}

	public static boolean isPlaying() {

		if (sInstance == null)
			return false;

		SoundWavesPlayer player = sInstance.getPlayer();

        if (!player.isInitialized())
            return false;

		return player.isPlaying();
	}

	public long seek(long offset) {
		offset = offset < 0 ? 0 : offset;

		mItem.setOffset(getContentResolver(), offset);
		return mPlayer.seek(offset);

	}

	@Subscribe
	public void onPlayerStateChange(PlayerStatusData argPlayerStatus) {
		if (argPlayerStatus == null)
			return;
        if (argPlayerStatus.status == PlayerStatusObservable.STOPPED) {
            dis_notifyStatus();
        } else {
            notifyStatus(mItem);
        }
	}

	public long position() {
		return mPlayer.position();
	}

	public long duration() {
		return mPlayer.duration();
	}

	@Nullable
	public static IEpisode getCurrentItem() {
		PlayerService ps = PlayerService.getInstance();

		if (ps == null)
			return null;

		return ps.getCurrentItemInternal();
	}

	@Nullable
	private IEpisode getCurrentItemInternal() {
		return mItem;
	}

	/**
	 * @return The ID of the next episode in the playlist
	 */
    @Nullable
	public IEpisode getNextId() {
		IEpisode next = SoundWaves.getAppContext(this).getPlaylist().nextEpisode();
		if (next != null)
			return next;
		return null;
	}

	/**
	 * Returns whether the next episode to be played should come from the
	 * playlist, or somewhere else
	 * 
	 * @return The type of episode to be played next
	 */
	public static @NextTrack int getNextTrack() {
		return nextTrack;
	}

    /**
     * Set wakelocks
     */
    public void takeWakelock(boolean isSteaming) {
        if (wifiLock.isHeld())
            return;

        ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        if (mNotificationPlayer != null) {
			mNotificationPlayer.show(isPlaying(), mItem);
        }

        mPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);

        if (isSteaming && mWifi.isConnected()) {
            wifiLock.acquire();
        }
    }

    public void setMediaCast(IMediaCast mMediaCast) {
        this.mMediaCast = mMediaCast;
        mMediaCast.registerStateChangedListener(getPlayer());
    }

    /**
     * Remove wakelocks
     */
    public void releaseWakelock() {
        //stopForeground(true);
        //mPlayer.release();

        if (wifiLock.isHeld())
            wifiLock.release();
    }

	@Deprecated
    public Playlist getPlaylist() {
        return SoundWaves.getAppContext(this).getPlaylist();
    }

	public PlayerStateManager getPlayerStateManager() {
		return mPlayerStateManager;
	}

}
