/*******************************************************************************
 * Copyright (C) 2005-2014 Alfresco Software Limited.
 *
 * This file is part of Alfresco Mobile for Android.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.alfresco.mobile.android.application.activity;

import java.io.File;
import java.util.List;

import org.alfresco.mobile.android.application.R;
import org.alfresco.mobile.android.application.configuration.ConfigurationConstant;
import org.alfresco.mobile.android.application.fragments.FragmentDisplayer;
import org.alfresco.mobile.android.application.fragments.account.AccountOAuthFragment;
import org.alfresco.mobile.android.application.fragments.builder.AlfrescoFragmentBuilder;
import org.alfresco.mobile.android.application.fragments.builder.FragmentBuilderFactory;
import org.alfresco.mobile.android.application.fragments.fileexplorer.FileExplorerFragment;
import org.alfresco.mobile.android.application.fragments.node.browser.DocumentFolderBrowserFragment;
import org.alfresco.mobile.android.application.fragments.node.favorite.FavoritesFragment;
import org.alfresco.mobile.android.application.fragments.node.upload.UploadFormFragment;
import org.alfresco.mobile.android.application.fragments.preferences.PasscodePreferences;
import org.alfresco.mobile.android.application.fragments.sync.SyncFragment;
import org.alfresco.mobile.android.application.security.PassCodeActivity;
import org.alfresco.mobile.android.async.node.favorite.FavoriteNodesRequest;
import org.alfresco.mobile.android.async.session.LoadSessionCallBack.LoadAccountCompletedEvent;
import org.alfresco.mobile.android.async.session.RequestSessionEvent;
import org.alfresco.mobile.android.platform.intent.PrivateIntent;
import org.alfresco.mobile.android.ui.ListingModeFragment;

import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.squareup.otto.Subscribe;

/**
 * Activity responsible to manage public intent from 3rd party application. This
 * activity is "open" to public Intent.
 * 
 * @author Jean Marie Pascal
 */
public class PublicDispatcherActivity extends BaseActivity
{
    private static final String TAG = PublicDispatcherActivity.class.getName();

    /** Define the type of importFolder. */
    private int uploadFolder;

    /** Define the local file to upload */
    private List<File> uploadFiles;

    protected long requestedAccountId = -1;

    // ///////////////////////////////////////////////////////////////////////////
    // LIFECYCLE
    // ///////////////////////////////////////////////////////////////////////////
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        displayAsDialogActivity();
        setContentView(R.layout.app_left_panel);

        String action = getIntent().getAction();
        if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action))
                && getFragment(UploadFormFragment.TAG) == null)
        {
            FragmentDisplayer.with(this).load(new UploadFormFragment()).back(false).into(FragmentDisplayer.PANEL_LEFT);
            return;
        }

        if (PrivateIntent.ACTION_SYNCHRO_DISPLAY.equals(action))
        {
            SyncFragment.with(this).mode(SyncFragment.MODE_PROGRESS).display();
            return;
        }

        if (PrivateIntent.ACTION_PICK_FILE.equals(action))
        {
            File f = Environment.getExternalStorageDirectory();
            if (getIntent().hasExtra(PrivateIntent.EXTRA_FOLDER))
            {
                f = (File) getIntent().getExtras().getSerializable(PrivateIntent.EXTRA_FOLDER);
                FileExplorerFragment.with(this).file(f).mode(ListingModeFragment.MODE_PICK).isShortCut(true).menuId(1)
                        .display();
            }
        }
    }

    @Override
    protected void onStart()
    {
        getActionBar().setDisplayHomeAsUpEnabled(true);
        super.onStart();
        PassCodeActivity.requestUserPasscode(this);
        activateCheckPasscode = PasscodePreferences.hasPasscodeEnable(this);
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if (!activateCheckPasscode)
        {
            PasscodePreferences.updateLastActivityDisplay(this);
        }
    }

    @Override
    protected void onStop()
    {
        if (receiver != null)
        {
            broadcastManager.unregisterReceiver(receiver);
            receiver = null;
        }
        super.onStop();
    }

    // ///////////////////////////////////////////////////////////////////////////
    // UI Public Method
    // ///////////////////////////////////////////////////////////////////////////
    public void doCancel(View v)
    {
        finish();
    }

    public void validateAction(View v)
    {
        ((DocumentFolderBrowserFragment) getFragment(DocumentFolderBrowserFragment.TAG)).createFiles(uploadFiles);
    }

    // ///////////////////////////////////////////////////////////////////////////
    // MENU
    // ///////////////////////////////////////////////////////////////////////////
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        super.onCreateOptionsMenu(menu);

        if (isVisible(DocumentFolderBrowserFragment.TAG))
        {
            ((DocumentFolderBrowserFragment) getFragment(DocumentFolderBrowserFragment.TAG)).getMenu(menu);
            return true;
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case android.R.id.home:
                if (getIntent() != null && PrivateIntent.ACTION_PICK_FILE.equals(getIntent().getAction()))
                {
                    finish();
                }
                else
                {
                    Intent i = new Intent(this, MainActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(i);
                    finish();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // ///////////////////////////////////////////////////////////////////////////
    // UTILS
    // ///////////////////////////////////////////////////////////////////////////
    public void setUploadFolder(int uploadFolderType)
    {
        this.uploadFolder = uploadFolderType;
    }

    public void setUploadFile(List<File> localFile)
    {
        this.uploadFiles = localFile;
    }

    // ////////////////////////////////////////////////////////
    // EVENTS RECEIVER
    // ///////////////////////////////////////////////////////
    @Subscribe
    public void onAccountLoaded(LoadAccountCompletedEvent event)
    {
        // If the session is available, display the view associated
        // (repository, sites, downloads, favorites).
        if (event == null || event.account == null) { return; }
        if (requestedAccountId != -1 && requestedAccountId != event.account.getId()) { return; }
        requestedAccountId = -1;

        setProgressBarIndeterminateVisibility(false);

        // Remove OAuthFragment if one
        if (getFragment(AccountOAuthFragment.TAG) != null)
        {
            getFragmentManager().popBackStack(AccountOAuthFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }

        removeWaitingDialog();

        // Upload process : Display the view where the user wants to upload
        // files.
        if (getCurrentSession() == null) { return; }
        String type = null;
        Bundle b = null;
        switch (uploadFolder)
        {
            case R.string.menu_browse_sites:
                type = ConfigurationConstant.KEY_SITES;
                // BrowserSitesFragment.with(this).display();
                break;
            case R.string.menu_browse_root:
                type = ConfigurationConstant.KEY_REPOSITORY;
                // DocumentFolderBrowserFragment.with(this).folder(getCurrentSession().getRootFolder()).display();
                break;
            case R.string.menu_favorites_folder:
                FavoritesFragment.with(this).setMode(FavoriteNodesRequest.MODE_FOLDERS).display();
                return;
            default:
                break;
        }

        if (type != null)
        {
            AlfrescoFragmentBuilder viewConfig = FragmentBuilderFactory.createViewConfig(this, type, null);
            if (viewConfig == null) { return; }
            viewConfig.display();
        }
    }

    @Subscribe
    public void onSessionRequested(RequestSessionEvent event)
    {
        requestedAccountId = event.accountToLoad.getId();
        currentAccount = event.accountToLoad;
        displayWaitingDialog();
    }
}
