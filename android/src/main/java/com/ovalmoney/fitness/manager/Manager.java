package com.ovalmoney.fitness.manager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInOptionsExtension;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessActivities;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.Session;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.request.SessionInsertRequest;
import com.google.android.gms.fitness.request.SessionReadRequest;
import com.google.android.gms.fitness.result.DataReadResponse;
import com.google.android.gms.fitness.result.SessionReadResponse;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.Task;
import com.ovalmoney.fitness.permission.Request;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import static com.ovalmoney.fitness.permission.Permissions.ACTIVITY;
import static com.ovalmoney.fitness.permission.Permissions.CALORIES;
import static com.ovalmoney.fitness.permission.Permissions.DISTANCES;
import static com.ovalmoney.fitness.permission.Permissions.STEPS;
import static com.ovalmoney.fitness.permission.Permissions.HEART_RATE;
import static com.ovalmoney.fitness.permission.Permissions.SLEEP_ANALYSIS;


import com.google.android.gms.fitness.SessionsClient;

public class Manager implements ActivityEventListener {

    private final static String TAG = "FitnessManager";

    private final static int GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 111;
    private final static int GOOGLE_PLAY_SERVICE_ERROR_DIALOG = 2404;

    private final static int GOOGLE_FIT_LOGIN_PERMISSIONS_REQUEST_CODE = 1001;
    private final static int GOOGLE_FIT_AUTO_PERMISSIONS_REQUEST_CODE = 1002;

    private final static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault());
    private GoogleSignInOptions goolgeSignInOptions;
    private FitnessOptions fitnessOptions;

    private Promise promise;

    private static boolean isGooglePlayServicesAvailable(final Activity activity) {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int status = googleApiAvailability.isGooglePlayServicesAvailable(activity);
        if (status == ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED) {
            // Alert Dialog and prompt user to update App
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.gms")));
        } else if(status != ConnectionResult.SUCCESS) {
            if(googleApiAvailability.isUserResolvableError(status)) {
                googleApiAvailability.getErrorDialog(activity, status, GOOGLE_PLAY_SERVICE_ERROR_DIALOG).show();
            }
            return false;
        }
        return true;
    }

    private static TimeUnit getInterval(String customInterval) {
        if(customInterval.equals("minute")) {
            return TimeUnit.MINUTES;
        }
        if(customInterval.equals("hour")) {
            return TimeUnit.HOURS;
        }
        return TimeUnit.DAYS;
    }

    protected FitnessOptions.Builder addPermissionToFitnessOptions(final FitnessOptions.Builder fitnessOptions,
                                                                   final ArrayList<Request> permissions){
        if (permissions == null || permissions.size() == 0) {
            return fitnessOptions;
        }
        int length = permissions.size();
        for(int i = 0; i < length; i++){
            Request currentRequest = permissions.get(i);
            switch(currentRequest.permissionKind){
                case STEPS:
                    fitnessOptions
                            .addDataType(DataType.TYPE_STEP_COUNT_DELTA, currentRequest.permissionAccess)
                            .addDataType(DataType.TYPE_STEP_COUNT_CUMULATIVE, currentRequest.permissionAccess);
                    break;
                case DISTANCES:
                    fitnessOptions.addDataType(DataType.TYPE_DISTANCE_DELTA, currentRequest.permissionAccess);
                    break;
                case CALORIES:
                    fitnessOptions.addDataType(DataType.TYPE_CALORIES_EXPENDED, currentRequest.permissionAccess);
                    break;
                case ACTIVITY:
                    fitnessOptions.addDataType(DataType.TYPE_ACTIVITY_SEGMENT, currentRequest.permissionAccess);
                    break;
                case HEART_RATE:
                    fitnessOptions.addDataType(DataType.TYPE_HEART_RATE_BPM, currentRequest.permissionAccess);
                    break;
                case SLEEP_ANALYSIS:
                    fitnessOptions.addDataType(DataType.TYPE_SLEEP_SEGMENT, currentRequest.permissionAccess);
                default:
                    break;
            }
        }

        return fitnessOptions;
    }

    public void updateFitnessOptions(final ArrayList<Request> permissions) {
        fitnessOptions = addPermissionToFitnessOptions(FitnessOptions.builder(), permissions).build();
        goolgeSignInOptions = new GoogleSignInOptions.Builder().requestScopes(
                                                fitnessOptions.getImpliedScopes().get(0),
                                                fitnessOptions.getImpliedScopes().remove(0)).build();
    }

    /**
     * check the authorization of permissions
     *
     * @param activity
     * @return
     */

    public boolean isAuthorized(final Activity activity, final ArrayList<Request> permissions){
        if(isGooglePlayServicesAvailable(activity)) {
            final FitnessOptions fitnessOptions = addPermissionToFitnessOptions(FitnessOptions.builder(), permissions)
                    .build();
            return GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(activity), fitnessOptions);
        }
        return false;
    }

    private boolean isAuthorized(final Activity activity, GoogleSignInAccount account){
        if(isGooglePlayServicesAvailable(activity)) {
            boolean authorized = GoogleSignIn.hasPermissions(account, fitnessOptions);
            return authorized;
        }
        return false;
    }

    public GoogleSignInAccount getGoogleAccount(Activity activity, final GoogleSignInOptionsExtension fitnessOptions) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(activity.getApplicationContext());
        if (account == null || !GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            account = GoogleSignIn.getAccountForExtension(activity.getApplicationContext(), fitnessOptions);
        }
        return account;
    }

    /**
     * start the sign in screen
     *
     * @param activity
     */
    public void signInToGoogleFit(@NonNull Activity activity, Promise promise) {
        this.promise = promise;
        activity.startActivityForResult(GoogleSignIn.getClient(activity.getApplicationContext(),
                goolgeSignInOptions).getSignInIntent(), GOOGLE_FIT_LOGIN_PERMISSIONS_REQUEST_CODE);
    }

    private void requestGoogleFitPermission(@NonNull Activity currentActivity,
                                            GoogleSignInAccount account, int requestCode,
                                            Promise promise) {
        try {
//            this.promise = promise;
//            FitnessOptions fitnessOptions = addPermissionToFitnessOptions(FitnessOptions.builder(), permissions)
//                    .build();
            GoogleSignIn.requestPermissions(
                    currentActivity,
                    requestCode, // GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
                    account, // GoogleSignIn.getLastSignedInAccount(currentActivity.getApplicationContext()),
                    fitnessOptions);
        }catch(Exception e){
            Log.e(getClass().getName(), e.getMessage());
            promise.reject(e);
        }
    }

    private void checkFitAuthorized(Activity activity, GoogleSignInAccount account, int requestCode, Promise promise) {
        if (isAuthorized(activity, account)) {
            // do whatever you need here
            //accessGoogleFit();
            promise.resolve(true);
        } else { //request the permission from google
            requestGoogleFitPermission(activity, account, requestCode, promise);
        }
    }

    public GoogleSignInAccount getSignInAccount(Activity activity, Intent data) {
        GoogleSignInAccount account = null;
        Task<GoogleSignInAccount> signinTask = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            account = signinTask.getResult(Exception.class);
        } catch (Exception e) {
            Log.w(TAG, "signInResult:failed code=" + e.getMessage());
            e.printStackTrace();
        }
        return account;
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode == GOOGLE_FIT_LOGIN_PERMISSIONS_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                Log.e(TAG, "Permission granted");
                checkFitAuthorized(activity,
                        getSignInAccount(activity, data),
                        GOOGLE_FIT_AUTO_PERMISSIONS_REQUEST_CODE,
                        this.promise);
                // do something
            } else {
                Log.e(TAG, "Result code: " + resultCode);
                // do error
                this.promise.resolve(false);
            }
        } else if (resultCode == GOOGLE_FIT_AUTO_PERMISSIONS_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                Log.e(TAG, "Permission granted");
                this.promise.resolve(true);
            } else {
                Log.e(TAG, "Result code: " + resultCode);
                // do error
                this.promise.resolve(false);
            }
        } else {
            this.promise.resolve(false);
        }
//        if (resultCode == Activity.RESULT_OK && requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
//            promise.resolve(true);
//        }
//        if (resultCode == Activity.RESULT_CANCELED && requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
//            promise.resolve(false);
//        }
    }


    public void logout(@NonNull Activity currentActivity, final Promise promise) {
        final GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build();
        GoogleSignIn.getClient(currentActivity, gso)
            .revokeAccess()
            .addOnCanceledListener(new OnCanceledListener() {
            @Override
            public void onCanceled() {
                promise.resolve(false);
            }
            })
            .addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                promise.resolve(true);
            }
            })
            .addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                promise.reject(e);
            }
            })
            .addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                promise.reject(e);
            }
        });
    }

    public void disconnect(@NonNull Activity currentActivity, final Promise promise) {
        Fitness.getConfigClient(
            currentActivity,
            GoogleSignIn.getLastSignedInAccount(currentActivity.getApplicationContext())
            )
          .disableFit()
          .addOnCanceledListener(new OnCanceledListener() {
            @Override
            public void onCanceled() {
              promise.resolve(false);
            }
          })
          .addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
              promise.resolve(true);
            }
          })
          .addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
              promise.reject(e);
            }
          });
    }

    @Override
    public void onNewIntent(Intent intent) { }

    public void subscribeToSteps(Context context, final Promise promise){
        final GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        if(account == null){
            promise.resolve(false);
            return;
        }
        Fitness.getRecordingClient(context, account)
                .subscribe(DataType.TYPE_STEP_COUNT_DELTA)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        promise.resolve(true);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        promise.resolve(false);
                    }
                });
    }

    public void getSteps(Context context, double startDate, double endDate, String customInterval, final Promise promise){
        DataSource ESTIMATED_STEP_DELTAS = new DataSource.Builder()
                .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .setType(DataSource.TYPE_DERIVED)
                .setStreamName("estimated_steps")
                .setAppPackageName("com.google.android.gms")
                .build();

        TimeUnit interval = getInterval(customInterval);

        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(ESTIMATED_STEP_DELTAS,    DataType.AGGREGATE_STEP_COUNT_DELTA)
                .bucketByTime(1, interval)
                .setTimeRange((long) startDate, (long) endDate, TimeUnit.MILLISECONDS)
                .build();

        assert false;
        Fitness.getHistoryClient(context, GoogleSignIn.getLastSignedInAccount(context))
                .readData(readRequest)
                .addOnSuccessListener(new OnSuccessListener<DataReadResponse>() {
                    @Override
                    public void onSuccess(DataReadResponse dataReadResponse) {
                        if (dataReadResponse.getBuckets().size() > 0) {
                            WritableArray steps = Arguments.createArray();
                            for (Bucket bucket : dataReadResponse.getBuckets()) {
                                List<DataSet> dataSets = bucket.getDataSets();
                                for (DataSet dataSet : dataSets) {
                                    processStep(dataSet, steps);
                                }
                            }
                            promise.resolve(steps);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        promise.reject(e);
                    }
                })
                .addOnCompleteListener(new OnCompleteListener<DataReadResponse>() {
                    @Override
                    public void onComplete(@NonNull Task<DataReadResponse> task) {
                    }
                });
    }

    public void getDistances(Context context, double startDate, double endDate, String customInterval, final Promise promise) {
        TimeUnit interval = getInterval(customInterval);

        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_DISTANCE_DELTA, DataType.AGGREGATE_DISTANCE_DELTA)
                .bucketByTime(1, interval)
                .setTimeRange((long) startDate, (long) endDate, TimeUnit.MILLISECONDS)
                .build();

        Fitness.getHistoryClient(context, GoogleSignIn.getLastSignedInAccount(context))
                .readData(readRequest)
                .addOnSuccessListener(new OnSuccessListener<DataReadResponse>() {
                    @Override
                    public void onSuccess(DataReadResponse dataReadResponse) {
                        if (dataReadResponse.getBuckets().size() > 0) {
                            WritableArray distances = Arguments.createArray();
                            for (Bucket bucket : dataReadResponse.getBuckets()) {
                                List<DataSet> dataSets = bucket.getDataSets();
                                for (DataSet dataSet : dataSets) {
                                    processDistance(dataSet, distances);
                                }
                            }
                            promise.resolve(distances);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        promise.reject(e);
                    }
                })
                .addOnCompleteListener(new OnCompleteListener<DataReadResponse>() {
                    @Override
                    public void onComplete(@NonNull Task<DataReadResponse> task) {
                    }
                });
    }

    public void getCalories(Context context, double startDate, double endDate, String customInterval, final Promise promise) {
        TimeUnit interval = getInterval(customInterval);

        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_CALORIES_EXPENDED, DataType.AGGREGATE_CALORIES_EXPENDED)
                .bucketByTime(1, interval)
                .setTimeRange((long) startDate, (long) endDate, TimeUnit.MILLISECONDS)
                .build();

        Fitness.getHistoryClient(context, GoogleSignIn.getLastSignedInAccount(context))
                .readData(readRequest)
                .addOnSuccessListener(new OnSuccessListener<DataReadResponse>() {
                    @Override
                    public void onSuccess(DataReadResponse dataReadResponse) {
                        if (dataReadResponse.getBuckets().size() > 0) {
                            WritableArray calories = Arguments.createArray();
                            for (Bucket bucket : dataReadResponse.getBuckets()) {
                                List<DataSet> dataSets = bucket.getDataSets();
                                for (DataSet dataSet : dataSets) {
                                    processDistance(dataSet, calories);
                                }
                            }
                            promise.resolve(calories);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        promise.reject(e);
                    }
                })
                .addOnCompleteListener(new OnCompleteListener<DataReadResponse>() {
                    @Override
                    public void onComplete(@NonNull Task<DataReadResponse> task) {
                    }
                });
    }

    public void getHeartRate(Context context, double startDate, double endDate, String customInterval,final Promise promise) {
        TimeUnit interval = getInterval(customInterval);

        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_HEART_RATE_BPM, DataType.AGGREGATE_HEART_RATE_SUMMARY)
                .bucketByTime(1, interval)
                .setTimeRange((long) startDate, (long) endDate, TimeUnit.MILLISECONDS)
                .build();

        Fitness.getHistoryClient(context, GoogleSignIn.getLastSignedInAccount(context))
                .readData(readRequest)
                .addOnSuccessListener(new OnSuccessListener<DataReadResponse>() {
                    @Override
                    public void onSuccess(DataReadResponse dataReadResponse) {
                        if (dataReadResponse.getBuckets().size() > 0) {
                            WritableArray heartRates = Arguments.createArray();
                            for (Bucket bucket : dataReadResponse.getBuckets()) {
                                List<DataSet> dataSets = bucket.getDataSets();
                                for (DataSet dataSet : dataSets) {
                                    processHeartRate(dataSet, heartRates);
                                }
                            }
                            promise.resolve(heartRates);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        promise.reject(e);
                    }
                })
                .addOnCompleteListener(new OnCompleteListener<DataReadResponse>() {
                    @Override
                    public void onComplete(@NonNull Task<DataReadResponse> task) {
                    }
                });
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void getSleepAnalysis(Activity activity, double startDate, double endDate, final Promise promise) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N){
            promise.reject(String.valueOf(FitnessError.ERROR_METHOD_NOT_AVAILABLE), "Method not available");
            return;
        }
        SessionReadRequest request = new SessionReadRequest.Builder()
                .readSessionsFromAllApps()
                .includeSleepSessions()
                .read(DataType.TYPE_SLEEP_SEGMENT)
                .setTimeInterval((long) startDate, (long) endDate, TimeUnit.MILLISECONDS)
                .build();
//        System.out.println("SessionReadRequest");
        GoogleSignInOptionsExtension fitnessOptions = FitnessOptions.builder()
                                                        .addDataType(DataType.TYPE_SLEEP_SEGMENT, FitnessOptions.ACCESS_READ)
                                                        .build();
        final GoogleSignInAccount gsa = getGoogleAccount(activity, fitnessOptions); //GoogleSignIn.getAccountForExtension(activity.getApplicationContext(), fitnessOptions);
//        System.out.println("SessionReadRequest email:" + gsa.getEmail());
        Fitness.getSessionsClient(activity, gsa)
                .readSession(request)
                .addOnSuccessListener(new OnSuccessListener<SessionReadResponse>() {
                    @Override
                    public void onSuccess(SessionReadResponse response) {
                        List<Session> sleepSessions = response.getSessions()
                                .stream()
                                .filter(s -> s.getActivity().equals(FitnessActivities.SLEEP))
                                .collect(Collectors.toList());

                        WritableArray sleepSample = Arguments.createArray();
                        for (Session session : sleepSessions) {
                            WritableMap sleepData = Arguments.createMap();
                            System.out.println("SessionReadRequest onsuccess : " + dateFormat.format(session.getStartTime(TimeUnit.MILLISECONDS)));
                            sleepData.putString("uid", gsa.getId());
                            sleepData.putString("email", gsa.getEmail());
                            sleepData.putString("addedBy", session.getAppPackageName());
                            sleepData.putString("startDate", dateFormat.format(session.getStartTime(TimeUnit.MILLISECONDS)));
                            sleepData.putString("endDate", dateFormat.format(session.getEndTime(TimeUnit.MILLISECONDS)));

                            // If the sleep session has finer granularity sub-components, extract them:
                            List<DataSet> dataSets = response.getDataSet(session);
                            WritableArray granularity = Arguments.createArray();
                            for (DataSet dataSet : dataSets) {
                                processDataSet(dataSet, granularity);
                            }
                            sleepData.putArray("granularity", granularity);

                            sleepSample.pushMap(sleepData);
                        }
                        promise.resolve(sleepSample);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        promise.reject(e);
                    }
                });
    }


    private void processStep(DataSet dataSet, WritableArray map) {

        WritableMap stepMap = Arguments.createMap();

        for (DataPoint dp : dataSet.getDataPoints()) {
            for(Field field : dp.getDataType().getFields()) {
                stepMap.putString("startDate", dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
                stepMap.putString("endDate", dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
                stepMap.putDouble("quantity", dp.getValue(field).asInt());
                map.pushMap(stepMap);
            }
        }
    }

    private void processDistance(DataSet dataSet, WritableArray map) {

        WritableMap distanceMap = Arguments.createMap();

        for (DataPoint dp : dataSet.getDataPoints()) {
            for(Field field : dp.getDataType().getFields()) {
                distanceMap.putString("startDate", dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
                distanceMap.putString("endDate", dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
                distanceMap.putDouble("quantity", dp.getValue(field).asFloat());
                map.pushMap(distanceMap);
            }
        }
    }

    private void processCalories(DataSet dataSet, WritableArray map) {

        WritableMap caloryMap = Arguments.createMap();

        for (DataPoint dp : dataSet.getDataPoints()) {
            for(Field field : dp.getDataType().getFields()) {
                caloryMap.putString("startDate", dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
                caloryMap.putString("endDate", dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
                caloryMap.putDouble("quantity", dp.getValue(field).asFloat());
                map.pushMap(caloryMap);
            }
        }
    }

    private void processHeartRate(DataSet dataSet, WritableArray map) {

        WritableMap heartRateMap = Arguments.createMap();

        for (DataPoint dp : dataSet.getDataPoints()) {
            for(Field field : dp.getDataType().getFields()) {
                heartRateMap.putString("startDate", dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
                heartRateMap.putString("endDate", dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
                heartRateMap.putDouble("quantity", dp.getValue(field).asFloat());
                map.pushMap(heartRateMap);
            }
        }
    }

    private void processDataSet(DataSet dataSet, WritableArray granularity) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        dateFormat.setTimeZone(TimeZone.getDefault());
        for (DataPoint dp : dataSet.getDataPoints()) {
            WritableMap sleepStage = Arguments.createMap();
            sleepStage.putInt("sleepStage", dp.getValue(Field.FIELD_SLEEP_SEGMENT_TYPE).asInt());
            sleepStage.putString("startDate", dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
            sleepStage.putString("endDate", dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
            granularity.pushMap(sleepStage);
        }
    }

    public void uploadSleepData(Activity activity, double startDate, double endDate, Promise promise) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N){
            promise.reject(String.valueOf(FitnessError.ERROR_METHOD_NOT_AVAILABLE), "Method not available");
            return;
        }

        GoogleSignInOptionsExtension fitnessOptions = FitnessOptions.builder()
                .accessSleepSessions(FitnessOptions.ACCESS_WRITE)
                .build();

        // Create the sleep session
        Session session = new Session.Builder()
                .setName("session")
                .setIdentifier("identifier")
                .setStartTime((long) startDate, TimeUnit.MILLISECONDS)
                .setEndTime((long) endDate, TimeUnit.MILLISECONDS)
                .setActivity(FitnessActivities.SLEEP)
                .build();

        // Build the request to insert the session.
        SessionInsertRequest request = new SessionInsertRequest.Builder()
                .setSession(session)
                .build();

        final GoogleSignInAccount gsa = GoogleSignIn.getAccountForExtension(activity, fitnessOptions);

        // Insert the session into Fit platform
        Fitness.getSessionsClient(activity, gsa)
                .insertSession(request)
                .addOnSuccessListener(activity, new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        promise.resolve("Upload successfully.");
                    }
                })
                .addOnFailureListener(activity, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        promise.reject(e);
                    }
                });
    }

}