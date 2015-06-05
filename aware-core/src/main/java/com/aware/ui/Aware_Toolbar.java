package com.aware.ui;

import android.app.Application;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.support.v4.app.Fragment;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.aware.R;

/**
 * Created by denzil on 29/05/15.
 */
public class Aware_Toolbar extends Preference {

    public Aware_Toolbar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {

        parent.setPadding(0,0,0,0);

        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.aware_toolbar, parent, false);

        final Toolbar toolbar = (Toolbar) layout.findViewById(R.id.aware_toolbar);
        toolbar.setTitle(this.getTitle());
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PreferenceScreen pref = (PreferenceScreen) getPreferenceManager().findPreference(getKey());
                if ( pref != null && pref.getDialog() != null ) { //for the client sub-preference pages
                    pref.getDialog().dismiss();
                } else { //for plugins
                    //To nothing... TODO: find a way to go back in the navigation
                }
            }
        });
        return layout;
    }
}
