package com.aware.phone.ui;

import android.content.Context;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.aware.phone.R;

/**
 * Created by denzil on 29/05/15.
 */
public class Aware_Toolbar extends Preference {

    public Aware_Toolbar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        super.onCreateView(parent);
        parent.setPadding(0, 0, 0, 0);

        View layout = LayoutInflater.from(getContext()).inflate(R.layout.aware_toolbar, parent, false);
        final Toolbar toolbar = (Toolbar) layout.findViewById(R.id.aware_toolbar);
        toolbar.setTitle(this.getTitle());
        toolbar.inflateMenu(R.menu.aware_menu);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PreferenceScreen pref = (PreferenceScreen) getPreferenceManager().findPreference(getKey());
                if (pref != null && pref.getDialog() != null) { //for the client sub-preference pages
                    pref.getDialog().dismiss();
                } else { //for plugins
                    //To nothing... TODO: find a way to go back in the navigation
                }
            }
        });

        return layout;
    }
}
