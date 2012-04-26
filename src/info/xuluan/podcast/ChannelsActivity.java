package info.xuluan.podcast;


import info.xuluan.podcast.provider.Subscription;
import info.xuluan.podcast.provider.SubscriptionColumns;
import info.xuluan.podcast.utils.DialogMenu;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

public class ChannelsActivity extends PodcastBaseActivity {
	//private final int MENU_ADD = Menu.FIRST + 2;

	
	private final int MENU_ITEM_VIEW = Menu.FIRST + 9;
	private final int MENU_ITEM_DELETE = Menu.FIRST + 10;
	private final int MENU_ITEM_AUTO = Menu.FIRST + 11;
	private final int MENU_ITEM_REFRESH = Menu.FIRST + 12;
	

	private static final String[] PROJECTION = new String[] {
			SubscriptionColumns._ID, // 0
			SubscriptionColumns.TITLE, // 1
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.subs);
		setTitle(getResources().getString(R.string.title_channels));

		getListView().setOnCreateContextMenuListener(this);

		Intent intent = getIntent();
		intent.setData(SubscriptionColumns.URI);

		mPrevIntent = new Intent(this, SearchActivity.class);
		mNextIntent = new Intent(this, AllItemActivity.class);	
		
		TabsHelper.setChannelTabClickListeners(this, R.id.channel_bar_manage_button);

		startInit();

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.channels_activity, menu);
	    return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
/*		case R.id.add_channel:
			startActivity(new Intent(this, AddChannelActivity.class));
			return true;
		case R.id.backup_channels:
			startActivity(new Intent(this, BackupChannelsActivity.class));
			return true;
		case R.id.search_channels:
			startActivity(new Intent(this, SearchActivity.class));
			return true;*/
		}
		return super.onOptionsItemSelected(item);
	}

	public DialogMenu createDialogMenus(long id) {

		Subscription subs = Subscription.getSubbyId(getContentResolver(), 
				id);
		if (subs == null)
			return null;		
		
		DialogMenu dialog_menu = new DialogMenu();
		
		dialog_menu.setHeader(subs.title);
		
		dialog_menu.addMenu(MENU_ITEM_VIEW, 
				getResources().getString(R.string.menu_view));

		String auto;
		if(subs.auto_download==0){
			auto = getResources().getString(R.string.menu_auto_download);
		}else{
			auto = getResources().getString(R.string.menu_manual_download);
		}       
		dialog_menu.addMenu(MENU_ITEM_AUTO, auto);

		dialog_menu.addMenu(MENU_ITEM_REFRESH, 
				getResources().getString(R.string.menu_manual_update));
		
		dialog_menu.addMenu(MENU_ITEM_DELETE, 
				getResources().getString(R.string.unsubscribe));
		
		return dialog_menu;
	}	


	class SubsClickListener implements DialogInterface.OnClickListener {
		public DialogMenu mMenu;
		public long subs_id;
		public SubsClickListener(DialogMenu menu, long id)
		{
			mMenu = menu;
			subs_id = id;
		}
		
        public void onClick(DialogInterface dialog, int select) 
        {
    		switch (mMenu.getSelect(select)) {
    		case MENU_ITEM_REFRESH: {
        		Subscription subs = Subscription.getSubbyId(getContentResolver(),
        				subs_id);
        		if (subs != null) {
        			subs.lastUpdated = 0;
        			subs.update(getContentResolver());
        			ContentValues cv = new ContentValues();
        			cv.put(SubscriptionColumns.LAST_UPDATED, 0);
        			getContentResolver().update(SubscriptionColumns.URI, cv,
        					SubscriptionColumns._ID + "=" + subs.id, null);        			
        			
        			if(mServiceBinder!=null)
        				mServiceBinder.start_update();        	
					Toast.makeText(ChannelsActivity.this, "Start to update channel, please waiting a little",
							Toast.LENGTH_LONG).show();					
        		}
    		}     				
    		case MENU_ITEM_VIEW: {
    			Uri uri = ContentUris.withAppendedId(SubscriptionColumns.URI, subs_id);
    			startActivity(new Intent(Intent.ACTION_EDIT, uri));
    			return;
    		}  
	
    		case MENU_ITEM_DELETE: {

    			new AlertDialog.Builder(ChannelsActivity.this)
                .setTitle(R.string.unsubscribe_channel)
                .setPositiveButton(R.string.menu_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                		Subscription subs = Subscription.getSubbyId(getContentResolver(),
                				subs_id);
                		if (subs != null)
                			subs.delete(getContentResolver());
    						
            			dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.menu_cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                })
                .show();
    			return;
    		}
    		case MENU_ITEM_AUTO: {
    			Subscription subs = Subscription.getSubbyId(getContentResolver(),
    					subs_id);
    			if (subs == null)
    				return;			
    			subs.auto_download = 1 - subs.auto_download;
    			if(subs.auto_download==1){
					Toast.makeText(ChannelsActivity.this, R.string.auto_download_hint,
							Toast.LENGTH_LONG).show();	    				
    			}else{
    				Toast.makeText(ChannelsActivity.this, R.string.manual_download_hint,
							Toast.LENGTH_LONG).show();    				
    				
    			}
    			subs.update(getContentResolver());	
    			return ;
    		}
    		}    		

		}        	
       }	
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {

		DialogMenu dialog_menu = createDialogMenus(id);
		if( dialog_menu==null)
			return;
		
		 new AlertDialog.Builder(this)
         .setTitle(dialog_menu.getHeader())
         .setItems(dialog_menu.getItems(), new SubsClickListener(dialog_menu,id)).show();		
	}	

	@Override
	public void startInit() {

		mCursor = managedQuery(SubscriptionColumns.URI, PROJECTION, null, null,
				null);

		// Used to map notes entries from the database to views
		mAdapter = new SimpleCursorAdapter(this,
				android.R.layout.simple_list_item_1, mCursor,
				new String[] { SubscriptionColumns.TITLE },
				new int[] { android.R.id.text1 });
		setListAdapter(mAdapter);

		super.startInit();
	}
}