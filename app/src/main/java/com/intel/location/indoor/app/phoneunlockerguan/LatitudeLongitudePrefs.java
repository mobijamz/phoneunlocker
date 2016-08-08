package com.intel.location.indoor.app.phoneunlockerguan;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * LatitudeLongitudePreferences is a global preferences file that contains all of the variables for
 * LatitudeLongitudeActivity and LatitudeLongitudeService.
 *
 * Author: Grace Guan
 * Last Modified: July 2016
 */
public class LatitudeLongitudePrefs {
    private static LatitudeLongitudePrefs mInstance;
    private Context mContext;
    private SharedPreferences myPreferences;

    private LatitudeLongitudePrefs(){ }

    /**
     * @return the instance of the phone's preferences file, if it exists. Otherwise, create it.
     */
    public static LatitudeLongitudePrefs getInstance(){
        if (mInstance == null) { mInstance = new LatitudeLongitudePrefs(); }
        return mInstance;
    }

    /**
     * Initializes the preferences file for the Android device with the given context.s
     * @param ctxt the Activity context to create this global variables file in
     */
    public void Initialize(Context ctxt){
        mContext = ctxt;
        myPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    // METHODS TO SET AND GET PREFERENCES (String, boolean, int, double) FROM THE PREFERENCES FILE
    public void setString(String key, String value){
        SharedPreferences.Editor e = myPreferences.edit();
        e.putString(key, value);
        e.commit();
    }
    public String getString(String key, String value) {
        return myPreferences.getString(key, value);
    }
    public void setBoolean(String key, Boolean value) {
        SharedPreferences.Editor e = myPreferences.edit();
        e.putBoolean(key,value);
        e.commit();
    }
    public boolean getBoolean(String key, Boolean value) {
        return myPreferences.getBoolean(key, value);
    }
    public void setDouble(String key, double value){
        SharedPreferences.Editor e = myPreferences.edit();
        e.putLong(key, Double.doubleToLongBits(value));
        e.commit();
    }
    public double getDouble(String key, double defValue) {
        return Double.longBitsToDouble(myPreferences.getLong(key,Double.doubleToLongBits(defValue)));
    }
    public void setInt(String key, int value) {
        SharedPreferences.Editor e = myPreferences.edit();
        e.putInt(key, value);
        e.commit();
    }
    public int getInt(String key, int defValue) {
        return myPreferences.getInt(key, defValue);
    }

    // OTHER METHODS: contains, clear, removePref
    public boolean contains(String key){ return myPreferences.contains(key); }
    public void clear(){
        SharedPreferences.Editor e = myPreferences.edit();
        e.clear();
        e.commit();
    }
    public void removePref(String key){
        SharedPreferences.Editor e = myPreferences.edit();
        e.remove(key);
        e.commit();
    }
}
