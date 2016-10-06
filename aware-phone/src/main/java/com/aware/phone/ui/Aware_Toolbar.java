package com.aware.phone.ui;

import android.content.Context;
import android.content.Intent;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.aware.phone.Aware_Client;
import com.aware.phone.R;

/**
 * Created by denzil on 29/05/15.
 */
public class Aware_Toolbar extends Preference {

    private Context mContext;

    public Aware_Toolbar(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        super.onCreateView(parent);
        parent.setPadding(0, 0, 0, 0);

        View layout = LayoutInflater.from(getContext()).inflate(R.layout.aware_toolbar, parent, false);
        final Toolbar toolbar = (Toolbar) layout.findViewById(R.id.aware_toolbar);
        toolbar.setTitle(this.getTitle());
        toolbar.setNavigationIcon(ContextCompat.getDrawable(mContext, R.drawable.ic_arrow_back));
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    PreferenceScreen pref = (PreferenceScreen) getPreferenceManager().findPreference(getKey());
                    if (pref != null && pref.getDialog() != null) { //for the client sub-preference pages
                        pref.getDialog().dismiss();
                    }
                } catch (ClassCastException e) {}
            }
        });
        return layout;
    }
}
