package com.aware.utils;

import android.content.Context;
import android.view.View;

/**
 * Interface that needs to be implemented to display a contextual card within AWARE
 * Also implement an empty constructor e.g., public ContextCard(){};
 * Created by denzil on 28/08/14.
 */
public interface IContextCard {
    /**
     * Return inflated XML layout with data to be displayed
     * @param context
     * @return View
     */
    View getContextCard(Context context);
}
