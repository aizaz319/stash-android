/*
 * Nextcloud Android client application
 *
 * @author Bartosz Przybylski
 * @author Chris Narkiewicz
 * Copyright (C) 2015 Bartosz Przybylski
 * Copyright (C) 2015 ownCloud Inc.
 * Copyright (C) 2016 Nextcloud.
 * Copyright (C) 2019 Chris Narkiewicz <hello@ezaquarii.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU AFFERO GENERAL PUBLIC LICENSE
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU AFFERO GENERAL PUBLIC LICENSE for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.client.onboarding;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.textfield.TextInputEditText;
import com.nextcloud.client.account.UserAccountManager;
import com.nextcloud.client.appinfo.AppInfo;
import com.nextcloud.client.di.Injectable;
import com.nextcloud.client.preferences.AppPreferences;
import com.owncloud.android.BuildConfig;
import com.owncloud.android.R;
import com.owncloud.android.authentication.AuthenticatorActivity;
import com.owncloud.android.features.FeatureItem;
import com.owncloud.android.ui.activity.BaseActivity;
import com.owncloud.android.ui.activity.FileDisplayActivity;
import com.owncloud.android.ui.adapter.FeaturesViewAdapter;
import com.owncloud.android.ui.whatsnew.ProgressIndicator;
import com.owncloud.android.utils.DisplayUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.viewpager.widget.ViewPager;

/**
 * Activity displaying general feature after a fresh install.
 */
public class FirstRunActivity extends BaseActivity implements ViewPager.OnPageChangeListener, Injectable {

    public static final String EXTRA_ALLOW_CLOSE = "ALLOW_CLOSE";
    public static final String EXTRA_EXIT = "EXIT";
    public static final int FIRST_RUN_RESULT_CODE = 199;
    ProgressDialog mProgressDialog;

    TextInputEditText nameEditText, surnameEditText, emailEditText, passwordEditText, cellPhoneCodeEditText,
        cellPhoneNumberEditText, defaultLocaleEditText, gift_codeEditText;

    private ProgressIndicator progressIndicator;

    @Inject UserAccountManager userAccountManager;
    @Inject AppPreferences preferences;
    @Inject AppInfo appInfo;
    @Inject OnboardingService onboarding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        enableAccountHandling = false;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.first_run_activity);

        mProgressDialog = new ProgressDialog(FirstRunActivity.this);

        boolean isProviderOrOwnInstallationVisible = getResources().getBoolean(R.bool.show_provider_or_own_installation);

        setSlideshowSize(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);

        Button loginButton = findViewById(R.id.login);
        loginButton.setBackgroundColor(getResources().getColor(R.color.login_btn_tint));
        loginButton.setTextColor(getResources().getColor(R.color.primary));

        loginButton.setOnClickListener(v -> {
            if (getIntent().getBooleanExtra(EXTRA_ALLOW_CLOSE, false)) {
                Intent authenticatorActivityIntent = new Intent(this, AuthenticatorActivity.class);
                authenticatorActivityIntent.putExtra(AuthenticatorActivity.EXTRA_USE_PROVIDER_AS_WEBLOGIN, false);
                startActivityForResult(authenticatorActivityIntent, FIRST_RUN_RESULT_CODE);
            } else {
                finish();
            }
        });

        Button providerButton = findViewById(R.id.signup);
        providerButton.setBackgroundColor(getResources().getColor(R.color.primary));
        providerButton.setTextColor(getResources().getColor(R.color.login_text_color));
        providerButton.setVisibility(isProviderOrOwnInstallationVisible ? View.VISIBLE : View.GONE);
        providerButton.setOnClickListener(v -> {
            Intent authenticatorActivityIntent = new Intent(this, AuthenticatorActivity.class);
            authenticatorActivityIntent.putExtra(AuthenticatorActivity.EXTRA_USE_PROVIDER_AS_WEBLOGIN, true);

            if (getIntent().getBooleanExtra(EXTRA_ALLOW_CLOSE, false)) {
                startActivityForResult(authenticatorActivityIntent, FIRST_RUN_RESULT_CODE);
            } else {
                authenticatorActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(authenticatorActivityIntent);
            }
        });

        TextView sign_up = findViewById(R.id.sign_up);
        sign_up.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LayoutInflater li = LayoutInflater.from(FirstRunActivity.this);
                View view = li.inflate(R.layout.register_dialog, null);
                AlertDialog alertDialogBuilder = new AlertDialog.Builder(FirstRunActivity.this)
                    .setView(view).setCancelable(true).
                    setTitle("Sign Up").
                    setMessage("Please Enter all the information").
                    setPositiveButton("OK", null)
                    .setNegativeButton("Cancel", null)
                    .show();


                nameEditText = (TextInputEditText) view.findViewById(R.id.nameEditText);
                surnameEditText = (TextInputEditText) view.findViewById(R.id.surnameEditText);
                emailEditText = (TextInputEditText) view.findViewById(R.id.emailEditText);
                passwordEditText = (TextInputEditText) view.findViewById(R.id.passwordEditText);
                cellPhoneCodeEditText = (TextInputEditText) view.findViewById(R.id.cellPhoneCodeEditText);
                cellPhoneNumberEditText = view.findViewById(R.id.phoneNumberEditText);
                defaultLocaleEditText = view.findViewById(R.id.defaultLocaleEditText);
                gift_codeEditText = view.findViewById(R.id.giftCodeEditText);


                Button positiveButton = alertDialogBuilder.getButton(AlertDialog.BUTTON_POSITIVE);

                Button negativeButton = alertDialogBuilder.getButton(AlertDialog.BUTTON_NEGATIVE);

                positiveButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String nameStr = nameEditText.getText().toString();
                        String surnameStr = surnameEditText.getText().toString();
                        String emailStr = emailEditText.getText().toString();
                        String passwordStr = passwordEditText.getText().toString();
                        String phoneCodeStr = cellPhoneCodeEditText.getText().toString();
                        String phoneNumberStr = cellPhoneNumberEditText.getText().toString();
                        String defaultLocaleStr = defaultLocaleEditText.getText().toString();
                        String giftCodeStr = gift_codeEditText.getText().toString();

                        if ((nameStr.length() <= 0) && (surnameStr.length() <= 0) && (emailStr.length() <= 0)
                            && (passwordStr.length() <= 0) && (phoneCodeStr.length() <= 0)
                            && (giftCodeStr.length() <= 0) && (phoneNumberStr.length() <= 0)) {
                            nameEditText.setError("Please enter your name");
                            surnameEditText.setError("Please enter your surname");
                            emailEditText.setError("Please enter your email");
                            passwordEditText.setError("Please enter password");
                            cellPhoneCodeEditText.setError("Cell Phone Code is required");
                            cellPhoneNumberEditText.setError("Cell Phone Number is required");
                            gift_codeEditText.setError("Please enter gift code");


                        } else {
                            if (nameStr.length() <= 0) {
                                nameEditText.setError("Please enter your name");
                            }
                            if (surnameStr.length() <= 0) {
                                surnameEditText.setError("Please enter your surname");
                            }
                            if (emailStr.length() <= 0) {
                                emailEditText.setError("Please enter your email");
                            }
                            if (passwordStr.length() <= 0) {
                                passwordEditText.setError("Please enter password");
                            }
                            if (phoneCodeStr.length() <= 0) {
                                cellPhoneCodeEditText.setError("Please enter area code");
                            }
                            if (phoneNumberStr.length() <= 0) {
                                cellPhoneNumberEditText.setError("Please enter phone number");
                            }
                            if (giftCodeStr.length() <= 0) {
                                gift_codeEditText.setError("Please enter your gift code");
                            } else {
                                registerUser(nameStr, surnameStr, emailStr, passwordStr, phoneCodeStr,
                                             phoneNumberStr, giftCodeStr);
                            }
                        }
//                        else{
//
//                            registerUser(nameStr, surnameStr, emailStr, passwordStr, phoneCodeStr,
//                                         phoneNumberStr, defaultLocaleStr, giftCodeStr);
//                        }

                    }
                });


            }
        });

        TextView hostOwnServerTextView = findViewById(R.id.host_own_server);
        hostOwnServerTextView.setTextColor(getResources().getColor(R.color.login_text_color));
//        hostOwnServerTextView.setVisibility(isProviderOrOwnInstallationVisible ? View.VISIBLE : View.GONE);
        hostOwnServerTextView.setVisibility(View.GONE);

        if (!isProviderOrOwnInstallationVisible) {
            hostOwnServerTextView.setOnClickListener(v -> onHostYourOwnServerClick());
        }

        progressIndicator = findViewById(R.id.progressIndicator);
        ViewPager viewPager = findViewById(R.id.contentPanel);

        // Sometimes, accounts are not deleted when you uninstall the application so we'll do it now
        if (onboarding.isFirstRun()) {
            userAccountManager.removeAllAccounts();
        }

        FeaturesViewAdapter featuresViewAdapter = new FeaturesViewAdapter(getSupportFragmentManager(), getFirstRun());
        progressIndicator.setNumberOfSteps(featuresViewAdapter.getCount());
        viewPager.setAdapter(featuresViewAdapter);

        viewPager.addOnPageChangeListener(this);
    }

    private void registerUser(String nameStr, String surnameStr, String emailStr,
                              String passwordStr, String phoneCodeStr, String phoneNumberStr, String giftCodeStr) {
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
//        mProgressDialog.setMessage("Progressing...");
        mProgressDialog.setIndeterminate(false);
        mProgressDialog.setCancelable(false);
        mProgressDialog.setProgress(0);
        mProgressDialog.setProgressNumberFormat(null);
        mProgressDialog.setTitle("Please wait...");
        mProgressDialog.setMessage("Registering Information...");
        mProgressDialog.setProgressPercentFormat(null);
        mProgressDialog.show();
        String url = "https://api.plusclouds.com/v2/partners/teknosa/register";

        RequestQueue queue = Volley.newRequestQueue(FirstRunActivity.this);
        JSONObject postData = new JSONObject();
        try{
//            postData.put("name", nameStr);
            postData.put("name", nameStr);
            postData.put("surname", surnameStr);
            postData.put("email", emailStr);
            postData.put("password", passwordStr);
            postData.put("cell_phone_code", phoneCodeStr);
            postData.put("cell_phone_number", phoneNumberStr);
//                params.put("default_locale", defaultLocaleStr);
            postData.put("gift_code", giftCodeStr);
        } catch (JSONException e) {
            e.printStackTrace();
        }

//        Map<String, String> postParams = new HashMap<String, String>();
//        postParams.put("name", nameStr);
//        postParams.put("surname", surnameStr);
//        postParams.put("email", emailStr);
//        postParams.put("password", passwordStr);
//        postParams.put("cell_phone_code", phoneCodeStr);
//        postParams.put("cell_phone_number", phoneNumberStr);
////                params.put("default_locale", defaultLocaleStr);
//        postParams.put("gift_code", giftCodeStr);

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
            (Request.Method.POST,
             url,
             postData,
             new Response.Listener<JSONObject>() {
                 @Override
                 public void onResponse(JSONObject response) {

                     mProgressDialog.dismiss();

                     try {
                         JSONObject jObj = new JSONObject(String.valueOf(response));
                         String success = jObj.getString("result");
                         Log.e("value in success", String.valueOf(success));

                         Toast.makeText(FirstRunActivity.this, "success: " + success, Toast.LENGTH_LONG).show();

                         Toast.makeText(FirstRunActivity.this, "response true: " + response, Toast.LENGTH_SHORT).show();
                         Toast.makeText(FirstRunActivity.this, "Your Information Registered Successfully", Toast.LENGTH_SHORT).show();


                         Toast.makeText(FirstRunActivity.this, "Your Information Registered Successfully", Toast.LENGTH_SHORT).show();
                         Intent authenticatorActivityIntent = new Intent(FirstRunActivity.this, AuthenticatorActivity.class);
                         authenticatorActivityIntent.putExtra(AuthenticatorActivity.EXTRA_USE_PROVIDER_AS_WEBLOGIN, false);
                         startActivityForResult(authenticatorActivityIntent, FIRST_RUN_RESULT_CODE);

                     } catch (JSONException e) {

                         Log.i("myTag", e.toString());
                         Toast.makeText(FirstRunActivity.this, "Parsing error", Toast.LENGTH_SHORT).show();

                     }

//                         Toast.makeText(FirstRunActivity.this, "response true: " + response, Toast.LENGTH_SHORT).show();
//                         Toast.makeText(FirstRunActivity.this, "Your Information Registered Successfully", Toast.LENGTH_SHORT).show();

//                         Intent authenticatorActivityIntent = new Intent(FirstRunActivity.this, AuthenticatorActivity.class);
//                         authenticatorActivityIntent.putExtra(AuthenticatorActivity.EXTRA_USE_PROVIDER_AS_WEBLOGIN, false);
//                         startActivityForResult(authenticatorActivityIntent, FIRST_RUN_RESULT_CODE);

//                         AlertDialog.Builder builder = new AlertDialog.Builder(FirstRunActivity.this);
//                         builder.setTitle("Congratulation!");
//                         builder.setMessage("Your Information registered successfully...");
//
//
//                         AlertDialog alertDialog = builder.create();
//                         alertDialog.show();


                 }
             }
                , new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
//                    if (error == null || error.networkResponse == null) {
//                        return;
//                    }
                    String body;
                    final String statusCode = String.valueOf(error.networkResponse.statusCode);

                    String bodySuccessful = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                    if(bodySuccessful.contains("true")){

                        mProgressDialog.dismiss();
//                            Toast.makeText(FirstRunActivity.this, "response: " + statusCode, Toast.LENGTH_SHORT).show();
                        Toast.makeText(FirstRunActivity.this, "Your Information Registered Successfully", Toast.LENGTH_SHORT).show();
                        Intent authenticatorActivityIntent = new Intent(FirstRunActivity.this, AuthenticatorActivity.class);
                        authenticatorActivityIntent.putExtra(AuthenticatorActivity.EXTRA_USE_PROVIDER_AS_WEBLOGIN, false);
                        startActivityForResult(authenticatorActivityIntent, FIRST_RUN_RESULT_CODE);
                    }
//                    if(Integer.parseInt(statusCode) == 422){
//                        Toast.makeText(FirstRunActivity.this, "response: " + statusCode, Toast.LENGTH_SHORT).show();
                        mProgressDialog.dismiss();
                        body = new String(error.networkResponse.data, StandardCharsets.UTF_8);
//                          Toast.makeText(FirstRunActivity.this, "B: " + body, Toast.LENGTH_SHORT).show();
//                              mProgressDialog.setMessage(body);
                        String newString = body.replaceAll("[\\{\\}\\[\\]\"]", "");
                        String formattedStr = newString.replaceAll(",", "\n\n");
                        String splitStr = formattedStr.substring(102);

                        if(body.contains("true")){

                            mProgressDialog.dismiss();
    //                            Toast.makeText(FirstRunActivity.this, "response: " + statusCode, Toast.LENGTH_SHORT).show();
                            Toast.makeText(FirstRunActivity.this, "Your Information Registered Successfully", Toast.LENGTH_SHORT).show();
                            Intent authenticatorActivityIntent = new Intent(FirstRunActivity.this, AuthenticatorActivity.class);
                            authenticatorActivityIntent.putExtra(AuthenticatorActivity.EXTRA_USE_PROVIDER_AS_WEBLOGIN, false);
                            startActivityForResult(authenticatorActivityIntent, FIRST_RUN_RESULT_CODE);
                        }

                        else if(body.contains("B\\u00f6yle bir kod bulunamam\\u0131")){
                            AlertDialog.Builder builder = new AlertDialog.Builder(FirstRunActivity.this);
                            builder.setTitle("Following Error(s) Occurred! Please Check and Enter Correct Information");
                            builder.setMessage("Böyle bir kod bulunamamıştır. Lütfen kodunuzu kontrol edip tekrar giriniz.");


                            AlertDialog alertDialog = builder.create();
                            alertDialog.show();
                        }
                        else if(body.contains("Bu kod daha \\u00f6nceden kullan\\u0131lm\\u0131\\u015f")){
                            AlertDialog.Builder builder = new AlertDialog.Builder(FirstRunActivity.this);
                            builder.setTitle("Following Error(s) Occurred! Please Check and Enter Correct Information");
                            builder.setMessage("Bu kod daha önceden kullanılmış. Aynı kod ile tekrar satın alım yapamazsınız. " +
                                                   "Eğer bunun bir hata olduğunu düşünüyorsanız lütfen bizimle iletişime geçiniz.\"");
//                            builder.setMessage("This code has been used before. You can't buy again with the same code");

                            AlertDialog alertDialog = builder.create();
                            alertDialog.show();
                        }
                        else if(body.contains("Bu e-posta adresi ile daha \\u00f6nce hesap yarat\\u0131lm\\u0131\\u015ft\\u0131r")){
                            AlertDialog.Builder builder = new AlertDialog.Builder(FirstRunActivity.this);
                            builder.setTitle("Following Error(s) Occurred! Please Check and Enter Correct Information");
                            builder.setMessage("Bu e-posta adresi ile daha önce hesap yaratılmıştır. \nLütfen başka bir " +
                                                   "e-posta adresi deneyin veya mevcut e-posta adresiniz ile " +
                                                   "\"https://leo.plusclouds.com\" adresini\nziyaret ederek ilgili hesabı " +
                                                   "yine aynı satın alım kodu ile oluşturun.");
//                            builder.setMessage("This code has been used before. You can't buy again with the same code");

                            AlertDialog alertDialog = builder.create();
                            alertDialog.show();



//                            mProgressDialog.dismiss();
                            //                            Toast.makeText(FirstRunActivity.this, "response: " + statusCode, Toast.LENGTH_SHORT).show();
//                            Toast.makeText(FirstRunActivity.this, "Your Information Registered Successfully", Toast.LENGTH_SHORT).show();
//                            Intent authenticatorActivityIntent = new Intent(FirstRunActivity.this, AuthenticatorActivity.class);
//                            authenticatorActivityIntent.putExtra(AuthenticatorActivity.EXTRA_USE_PROVIDER_AS_WEBLOGIN, false);
//                            startActivityForResult(authenticatorActivityIntent, FIRST_RUN_RESULT_CODE);
//




                        }
                        else{
                            AlertDialog.Builder builder = new AlertDialog.Builder(FirstRunActivity.this);
                            builder.setTitle("Following Error(s) Occurred! Please Check and Enter Correct Information");
                            builder.setMessage(splitStr);


                            AlertDialog alertDialog = builder.create();
                            alertDialog.show();
                        }

//                    }
//                    else{
//                        mProgressDialog.dismiss();
//                        Toast.makeText(FirstRunActivity.this, "response: " + statusCode, Toast.LENGTH_SHORT).show();
//                        Toast.makeText(FirstRunActivity.this, "Your Information Registered Successfully", Toast.LENGTH_SHORT).show();
//                        Intent authenticatorActivityIntent = new Intent(FirstRunActivity.this, AuthenticatorActivity.class);
//                        authenticatorActivityIntent.putExtra(AuthenticatorActivity.EXTRA_USE_PROVIDER_AS_WEBLOGIN, false);
//                        startActivityForResult(authenticatorActivityIntent, FIRST_RUN_RESULT_CODE);
//                    }




                }
            }){
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                HashMap<String, String> headers = new HashMap<String, String>();
                headers.put("Content-Type", "application/json; charset=utf-8");
                return headers;
            }

//            @Nullable
//            @Override
//            protected Map<String, String> getParams() throws AuthFailureError {
//                Map<String, String> params = new HashMap<String, String>();
//                params.put("name", nameStr);
//                params.put("surname", surnameStr);
//                params.put("email", emailStr);
//                params.put("password", passwordStr);
//                params.put("cell_phone_code", phoneCodeStr);
//                params.put("cell_phone_number", phoneNumberStr);
////                params.put("default_locale", defaultLocaleStr);
//                params.put("gift_code", giftCodeStr);
//
//                return params;
//            }
        };

        /*
        StringRequest request = new StringRequest(Request.Method.POST, url,
                                                  new Response.Listener<String>() {

                                                      @Override
                                                      public void onResponse(String response) {
                                                          try {
                                                              JSONObject respObj = new JSONObject(response);
                                                              String nameStr = respObj.getString("name");
                                                              String surnameStr = respObj.getString("surname");
                                                              String emailStr = respObj.getString("email");
                                                              String passwordStr = respObj.getString("password");
                                                              String cellPhoneCodeStr = respObj.getString("cell_phone_code");
                                                              String cellPhoneNumberStr = respObj.getString("cell_phone_number");
                                                              String defaultLocaleStr = respObj.getString("default_locale");
                                                              String giftCodeStr = respObj.getString("gift_code");
                                                              mProgressDialog.dismiss();
                                                              Toast.makeText(FirstRunActivity.this, "Your Information Registered Successfully", Toast.LENGTH_SHORT).show();

                                                          } catch (JSONException e) {
                                                              e.printStackTrace();
                                                              mProgressDialog.dismiss();
                                                          }
                                                      }
                                                  },
                                                  new Response.ErrorListener() {

                                                      @Override
                                                      public void onErrorResponse(VolleyError error) {
                                                          if (error == null || error.networkResponse == null) {
                                                              return;
                                                          }
                                                          String body;
                                                          final String statusCode = String.valueOf(error.networkResponse.statusCode);
                                                          if (Integer.parseInt(statusCode) == 200) {
                                                              mProgressDialog.dismiss();
                                                              Toast.makeText(FirstRunActivity.this, "response: " + statusCode, Toast.LENGTH_SHORT).show();
                                                              Toast.makeText(FirstRunActivity.this, "Your Information Registered Successfully", Toast.LENGTH_SHORT).show();

                                                              AlertDialog.Builder builder = new AlertDialog.Builder(FirstRunActivity.this);
                                                              builder.setTitle("Congratulation!");
                                                              builder.setMessage("Your Information registered successfully...");


                                                              AlertDialog alertDialog = builder.create();
                                                              alertDialog.show();

                                                          } else {
                                                              Toast.makeText(FirstRunActivity.this, "response: " + statusCode, Toast.LENGTH_SHORT).show();
                                                              mProgressDialog.dismiss();
                                                              body = new String(error.networkResponse.data, StandardCharsets.UTF_8);
//                          Toast.makeText(FirstRunActivity.this, "B: " + body, Toast.LENGTH_SHORT).show();
//                              mProgressDialog.setMessage(body);
                                                              String newString = body.replaceAll("[\\{\\}\\[\\]\"]", "");
                                                              String formattedStr = newString.replaceAll(",", "\n\n");
                                                              String splitStr = formattedStr.substring(102);
                                                              AlertDialog.Builder builder = new AlertDialog.Builder(FirstRunActivity.this);
                                                              builder.setTitle("Following Error(s) Occurred! Please Check and Enter Correct Information");
                                                              builder.setMessage(splitStr);


                                                              AlertDialog alertDialog = builder.create();
                                                              alertDialog.show();
                                                          }
//
                                                      }
                                                  }) {
            @NonNull
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> params = new HashMap<String, String>();
                params.put("name", nameStr);
                params.put("surname", surnameStr);
                params.put("email", emailStr);
                params.put("password", passwordStr);
                params.put("cell_phone_code", phoneCodeStr);
                params.put("cell_phone_number", phoneNumberStr);
//                params.put("default_locale", defaultLocaleStr);
                params.put("gift_code", giftCodeStr);

                return params;
            }
        };

        queue.add(request);
        */
        queue.add(jsonObjectRequest);
    }

    private void setSlideshowSize(boolean isLandscape) {
        boolean isProviderOrOwnInstallationVisible = getResources().getBoolean(R.bool.show_provider_or_own_installation);
        LinearLayout buttonLayout = findViewById(R.id.buttonLayout);
        LinearLayout.LayoutParams layoutParams;

        buttonLayout.setOrientation(isLandscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        LinearLayout bottomLayout = findViewById(R.id.bottomLayout);
        if (isProviderOrOwnInstallationVisible) {
            layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                         ViewGroup.LayoutParams.WRAP_CONTENT);
        } else {
            layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                         DisplayUtils.convertDpToPixel(isLandscape ? 100f : 150f, this));
        }

        bottomLayout.setLayoutParams(layoutParams);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setSlideshowSize(true);
        } else {
            setSlideshowSize(false);
        }
    }

    @Override
    public void onBackPressed() {
        onFinish();

        if (getIntent().getBooleanExtra(EXTRA_ALLOW_CLOSE, false)) {
            super.onBackPressed();
        } else {
            Intent intent = new Intent(getApplicationContext(), AuthenticatorActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra(EXTRA_EXIT, true);
            startActivity(intent);
            finish();
        }
    }

    private void onFinish() {
        preferences.setLastSeenVersionCode(BuildConfig.VERSION_CODE);
    }

    @Override
    protected void onStop() {
        onFinish();

        super.onStop();
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        // unused but to be implemented due to abstract parent
    }

    @Override
    public void onPageSelected(int position) {
        progressIndicator.animateToStep(position + 1);
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        // unused but to be implemented due to abstract parent
    }

    public void onHostYourOwnServerClick() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.url_server_install)));
        DisplayUtils.startIntentIfAppAvailable(intent, this, R.string.no_browser_available);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (FIRST_RUN_RESULT_CODE == requestCode && RESULT_OK == resultCode) {

            String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
            Account account = userAccountManager.getAccountByName(accountName);


            if (account == null) {
                DisplayUtils.showSnackMessage(this, R.string.account_creation_failed);
                return;
            }

            userAccountManager.setCurrentOwnCloudAccount(account.name);

            Intent i = new Intent(this, FileDisplayActivity.class);
            i.setAction(FileDisplayActivity.RESTART);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);

            finish();
        }
    }


    public static FeatureItem[] getFirstRun() {
        return new FeatureItem[]{
            new FeatureItem(R.drawable.logo, R.string.first_run_1_text, R.string.empty, true, false),
            new FeatureItem(R.drawable.first_run_files, R.string.first_run_2_text, R.string.empty, true, false),
            new FeatureItem(R.drawable.first_run_groupware, R.string.first_run_3_text, R.string.empty, true, false),
            new FeatureItem(R.drawable.first_run_talk, R.string.first_run_4_text, R.string.empty, true, false)};
    }
}
