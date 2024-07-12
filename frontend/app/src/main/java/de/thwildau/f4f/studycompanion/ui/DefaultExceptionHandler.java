package de.thwildau.f4f.studycompanion.ui;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ApplicationErrorReport;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;

import de.thwildau.f4f.studycompanion.BuildConfig;
import de.thwildau.f4f.studycompanion.backend.BackendIO;


/***
 * Default Exception Handler class
 * taken from Andrious Solutions @ https://stackoverflow.com/a/46591885/5106474 with modifications
 * used for logging App Crashes.
 * License: ("Provided below is the error handler I personally use. Take a copy and do what you will with it.")
 */
public class DefaultExceptionHandler implements
        java.lang.Thread.UncaughtExceptionHandler {


    private DefaultExceptionHandler(Activity activity) {

        mPackageName = getPackageName(activity);
    }


    public static DefaultExceptionHandler getINSTANCE(Activity activity) {

        if (mErrorHandler == null) {

            mErrorHandler = new DefaultExceptionHandler(activity);
        }

        return mErrorHandler;
    }


    private static String getPackageName(Context pContext) {

        String packageName = "";

        try {

            ActivityManager activityManager = (ActivityManager) pContext
                    .getSystemService(Context.ACTIVITY_SERVICE);

            if (Build.VERSION.SDK_INT > 20) {

                packageName = activityManager.getRunningAppProcesses().get(0).processName;
            } else {

                // <uses-permission android:name="android.permission.GET_TASKS" />
                packageName = activityManager.getRunningTasks(1).get(0).topActivity
                        .getPackageName();
            }

            // There is a limit to the tag length of 23.
            packageName = packageName
                    .substring(0, packageName.length() > 22 ? 22 : packageName.length());

        } catch (Exception ex) {
        }

        if (packageName.isEmpty()) {
            packageName = pContext.getPackageName();
        }

        return packageName;
    }


    public static void toCatch(Activity activity) {

        Thread.setDefaultUncaughtExceptionHandler(getINSTANCE(activity));
    }


    // Return the last error message
    public static String getErrorMessage() {

        return mErrorMessage;
    }


    // Return the last crash information
    public static ApplicationErrorReport.CrashInfo crashInfo() {
        return mCrashInfo;
    }


    @NonNull
    private static String errorMsg(Throwable exception, String exceptError) {

        if (!exceptError.contains("error")) {

            mReportBuilder.append(reportError(exception));
        }

        if (!exceptError.contains("deviceinfo")) {

            mReportBuilder.append(reportDeviceInfo());
        }

        if (!exceptError.contains("firmware")) {

            mReportBuilder.append(reportVersion());
        }

        if (!exceptError.contains("callstack")) {

            mReportBuilder.append(reportCallStack(exception));
        }

        return mReportBuilder.toString();
    }


    private static String reportError(Throwable exception) {

        mCrashInfo = new ApplicationErrorReport.CrashInfo(exception);

        if (mCrashInfo.exceptionMessage == null) {

            mErrorMessage = "<unknown error>";
        } else {

            mErrorMessage = mCrashInfo.exceptionMessage
                    .replace(": " + mCrashInfo.exceptionClassName, "");
        }

        String throwFile = mCrashInfo.throwFileName == null ? "<unknown file>"
                : mCrashInfo.throwFileName;

        return
                "\n\tError Message: \"" + mErrorMessage +"\""
                + ", Exception Class: " + mCrashInfo.exceptionClassName
                + "\n\t  in file: " + throwFile + ":" + mCrashInfo.throwLineNumber
                + ", Method: " + mCrashInfo.throwMethodName + "()";
    }


    private static String reportCallStack(Throwable exception) {

        StringWriter stackTrace = new StringWriter();

        exception.printStackTrace(new PrintWriter(stackTrace));

        String callStack = stackTrace.toString();

        return "\n\tCALLSTACK: "+ callStack.replace("\n", "<br/>");
    }


    private static String reportDeviceInfo() {

        return  "\n\tDevice Brand: "
                + Build.BRAND
                + ", Device: "
                + Build.DEVICE
                + ", Model: "
                + Build.MODEL
                + ", Id: "
                + Build.ID
                + ", Product: "
                + Build.PRODUCT;
    }


    private static String reportVersion() {

        return  "\n\tApp Version: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + "). " + BuildConfig.BUILD_TYPE + " build.";
    }

    @Override
    public void uncaughtException(Thread thread, Throwable exception) {

        // Don't re-enter -- avoid infinite loops if crash-reporting crashes.
        if (mCrashing) return;

        mCrashing = true;

        String errorMsg = catchException(thread, exception);

        BackendIO.serverLog(Log.ERROR, "!UNEXPECTED APP CRASH!", errorMsg);
        try {
            // wait some time for sending log message
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        defaultExceptionHandler(thread, exception);
    }


    public String catchException(Thread thread, Throwable exception) {

        String errorMsg = "!!!UNEXPECTED APP CRASH!!!";

        try {

            errorMsg += errorMsg(exception, "");

        } catch (Exception ex) {

            Log.e(mPackageName, ex.getMessage());
        }

        return errorMsg;
    }


    public static void defaultExceptionHandler(Thread thread, Throwable exception) {

        try {

            // Execute the old handler.
            if (mOldHandler != null) {

                mOldHandler.uncaughtException(thread, exception);
            }

        } catch (Exception ex) {

            Log.e(mPackageName, ex.getMessage());
        }
    }


    public void onDestroy() {

        mErrorHandler = null;
    }

    // Prevents infinite loops.
    private static volatile boolean mCrashing = false;

    private static final StringBuilder mReportBuilder = new StringBuilder();

    private static final Thread.UncaughtExceptionHandler mOldHandler = Thread
            .getDefaultUncaughtExceptionHandler();

    private static DefaultExceptionHandler mErrorHandler;

    private static String mPackageName;

    private static ApplicationErrorReport.CrashInfo mCrashInfo;

    private static String mErrorMessage = "";
}