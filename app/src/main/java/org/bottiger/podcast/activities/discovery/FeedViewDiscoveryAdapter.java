package org.bottiger.podcast.activities.discovery;

import android.app.Activity;
import android.database.Cursor;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.util.SortedList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import org.bottiger.podcast.R;
import org.bottiger.podcast.TopActivity;
import org.bottiger.podcast.activities.feedview.EpisodeViewHolder;
import org.bottiger.podcast.activities.feedview.FeedViewAdapter;
import org.bottiger.podcast.listeners.PlayerStatusObservable;
import org.bottiger.podcast.player.exoplayer.ExoPlayerWrapper;
import org.bottiger.podcast.provider.IEpisode;
import org.bottiger.podcast.provider.ISubscription;
import org.bottiger.podcast.provider.SlimImplementations.SlimEpisode;
import org.bottiger.podcast.views.PlayPauseImageView;

import java.util.ArrayList;

/**
 * Created by apl on 21-04-2015.
 */
public class FeedViewDiscoveryAdapter extends FeedViewAdapter {

    public FeedViewDiscoveryAdapter(@NonNull TopActivity activity, @NonNull ISubscription argSubscription) {
        super(activity, argSubscription);
    }

    @Override
    public EpisodeViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        EpisodeViewHolder viewHolder = super.onCreateViewHolder(viewGroup, i);

        //ViewGroup.LayoutParams params = viewHolder.mPlayPauseButton.getLayoutParams();
        //params.width = (int)mActivity.getResources().getDimension(R.dimen.playpause_button_size_small);
        //params.height = (int)mActivity.getResources().getDimension(R.dimen.playpause_button_size_small);

        //viewHolder.mPlayPauseButton.setLayoutParams(params);
        //viewHolder.itemView.invalidate();

        return viewHolder;

    }

    @Override
    protected IEpisode getItemForPosition(int argPosition) {
        return mSubscription.getEpisodes().get(argPosition);
    }

    @Override
    public void onBindViewHolder(EpisodeViewHolder episodeViewHolder, final int position) {
        super.onBindViewHolder(episodeViewHolder, position);
        int dataPosition = getDatasetPosition(position);
        final IEpisode argEpisode = getItemForPosition(dataPosition);

        episodeViewHolder.DisplayDescription = true;

        episodeViewHolder.mPlayPauseButton.setEpisode(argEpisode, PlayPauseImageView.DISCOVERY_FEEDVIEW);
        episodeViewHolder.mQueueButton.setEpisode(argEpisode, PlayPauseImageView.DISCOVERY_FEEDVIEW);
        episodeViewHolder.mDownloadButton.setVisibility(View.INVISIBLE);

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) episodeViewHolder.mQueueButton.getLayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            params.removeRule(RelativeLayout.BELOW);
        } else {
            params.addRule(RelativeLayout.BELOW, 0);
        }

        episodeViewHolder.mPlayPauseButton.setStatus(ExoPlayerWrapper.STATE_IDLE);

        getPalette(episodeViewHolder);
    }
}
