package org.bottiger.podcast.listeners;


import java.util.WeakHashMap;

import org.bottiger.podcast.MainActivity;
import org.bottiger.podcast.notification.NotificationPlayer;
import org.bottiger.podcast.provider.FeedItem;
import org.bottiger.podcast.provider.IEpisode;
import org.bottiger.podcast.service.PlayerService;

import android.app.Activity;
import android.content.ContentResolver;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;

public class PlayerStatusObservable {

    /**
     * How often the UI should refresh
     */
    private static long REFRESH_INTERVAL = 100; // 16 ms => 60 fps

    private static PlayerService sPlayerService;

    private static WeakHashMap<PlayerStatusObserver, Boolean> mListeners = new WeakHashMap<PlayerStatusObserver, Boolean>();
	private static Activity mActivity;
    private static STATUS mStatus;

	public enum STATUS {
		PLAYING(0), PAUSED(1), STOPPED(2);

        private int value;
        private STATUS(int value){
            this.value = value;
        }
	}

    private static STATUS statusFromInt(int i) {
        return STATUS.values()[i];
    }

    private static PlayerService getsPlayerService() {
        if (sPlayerService == null)
            sPlayerService = MainActivity.sBoundPlayerService;

        return sPlayerService;
    }

    private static Handler sHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message inputMessage) {
            // Gets the task from the incoming Message object.
            //PhotoTask photoTask = (PhotoTask) inputMessage.obj;

            // Gets the ImageView for this task
            STATUS status = statusFromInt(inputMessage.what);
            switch (status) {
                // The decoding is done
                case PLAYING:
                    PlayerService ps = getsPlayerService();

                    if (ps != null) {
                        updateProgress(ps);

                        int currentStatus = ps.isPlaying() || ps.getPlayer().isCasting() ? STATUS.PLAYING.value : STATUS.STOPPED.value;
                        inputMessage = sHandler.obtainMessage(currentStatus);
                        sHandler.sendMessageDelayed(inputMessage, REFRESH_INTERVAL);
                    }
                    break;
                default:

                //super.handleMessage(inputMessage);
            }
        }
    };

    private static void startProgressUpdate() {
        sHandler.removeMessages(STATUS.PLAYING.value);
        Message msg = sHandler.obtainMessage(STATUS.PLAYING.value);
        sHandler.sendMessage(msg);
    }


    /**
	 * Set the activity to update the action bar in
	 * 
	 * @param activity
	 */
	public static void setActivity(Activity activity) {
		mActivity = activity;
	}

    public static void registerListener(PlayerStatusObserver listener) {
        mListeners.put(listener, true);
    }

    public static boolean unregisterListener(PlayerStatusObserver listener) {
        return mListeners.remove(listener);
    }

    public static STATUS getStatus() {
        return mStatus;
    }

    public static void updateProgress(@NonNull PlayerService argPlayerService) {
        IEpisode currentItem = argPlayerService.getCurrentItem();
        if (currentItem != null && currentItem instanceof FeedItem) {
            FeedItem feedItem = (FeedItem)currentItem;
            int offset = argPlayerService.getPlayer().getCurrentPosition();
            offset = argPlayerService.getPlayer().getCurrentPosition();
            updateEpisodeOffset(argPlayerService.getContentResolver(),
                    feedItem,
                    offset);

            updateProgress(argPlayerService, feedItem);
        }
    }

    public static void updateProgress(@NonNull PlayerService argPlayerService, @NonNull IEpisode argEpisode) {
        for (PlayerStatusObserver listener : mListeners.keySet()) {
            if (argEpisode.equals(listener.getEpisode())) {
                //listener.setProgressMs(argPlayerService.position());
                listener.setProgressMs(argEpisode.getOffset());
            }
        }
    }

    private static long lastUpdate = System.currentTimeMillis();
    public static void updateEpisodeOffset(@NonNull ContentResolver argContentResolver,
                                           @NonNull FeedItem argEpisode,
                                           int argOffset) {
        long now = System.currentTimeMillis();

        // more than a second ago
        if (now - lastUpdate > 1000) {
            argEpisode.setPosition(argContentResolver, argOffset);
            lastUpdate = now;
        }
    }

    static NotificationPlayer np;

	/**
	 * Update the icons so they match the current status of the extended_player
	 */
	public static void updateStatus(STATUS status) {
        mStatus = status;

		// Update action bar
		if (mActivity != null) {
			mActivity.invalidateOptionsMenu();
		}
		
		// Update notification
		PlayerService ps = MainActivity.sBoundPlayerService;
		if (ps != null) {
			IEpisode currentItem = ps.getCurrentItem();
			if (currentItem != null) {

                for (PlayerStatusObserver listener : mListeners.keySet()) {
                    listener.onStateChange(EpisodeStatus.generateEpisodeStatsus(currentItem, status, (int)ps.position()));
                }

                startProgressUpdate();

                if (np == null)
				    np = new NotificationPlayer(ps, currentItem);

                np.setmPlayerService(ps);
				np.setItem(currentItem);
				np.show(status == STATUS.PLAYING);
			}
		}
	}
}
