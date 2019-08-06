package com.aware.utils;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.NetworkErrorException;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import androidx.annotation.Nullable;

/**
 * Created by denzil on 18/07/2017.
 * This service allows the creation of an AWARE type of account
 */
public class Aware_Accounts extends Service {

    private Aware_Account mAccount;

    @Override
    public void onCreate() {
        super.onCreate();
        mAccount = new Aware_Account(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mAccount.getIBinder();
    }

    public static class Aware_Account extends AbstractAccountAuthenticator {
        /**
         * Variables for Sync Adapter and AWARE accounts' support
         */
        public static final String AWARE_ACCOUNT_TYPE = "com.awareframework";
        public static final String AWARE_ACCOUNT = "awareframework";

        public Aware_Account(Context context) {
            super(context);
        }

        @Override
        public Bundle editProperties(AccountAuthenticatorResponse accountAuthenticatorResponse, String s) {
            return null;
        }

        @Override
        public Bundle addAccount(AccountAuthenticatorResponse accountAuthenticatorResponse, String s, String s1, String[] strings, Bundle bundle) throws NetworkErrorException {
            return null;
        }

        @Override
        public Bundle confirmCredentials(AccountAuthenticatorResponse accountAuthenticatorResponse, Account account, Bundle bundle) throws NetworkErrorException {
            return null;
        }

        @Override
        public Bundle getAuthToken(AccountAuthenticatorResponse accountAuthenticatorResponse, Account account, String s, Bundle bundle) throws NetworkErrorException {
            return null;
        }

        @Override
        public String getAuthTokenLabel(String s) {
            return null;
        }

        @Override
        public Bundle updateCredentials(AccountAuthenticatorResponse accountAuthenticatorResponse, Account account, String s, Bundle bundle) throws NetworkErrorException {
            return null;
        }

        @Override
        public Bundle hasFeatures(AccountAuthenticatorResponse accountAuthenticatorResponse, Account account, String[] strings) throws NetworkErrorException {
            return null;
        }
    }
}
