package com.intel.location.indoor.app.phoneunlockerguan;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.location.Location;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
//import android.util.Log;
import android.widget.Toast;

import com.intel.location.indoor.datatype.rest.Floor;
import com.intel.location.indoor.datatype.rest.Room;
//import com.intel.location.indoor.sdk.core.IndoorLocationController;
import com.intel.location.indoor.sdk.core.IndoorLocationServiceListener;
import com.intel.location.indoor.sdk.core.IndoorPedometricLocationController;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * LatitudeLongitudeService allows LatitudeLongitudeActivity to run in the background. Uses a
 * Particle Filter to ensure that data do not jump around from place to place.
 *
 * Author: Grace Guan
 * Last Modified: August 2016
 */
public class LatitudeLongitudeService extends Service implements IndoorLocationServiceListener {
    // Implement the particle filter.
    LatitudeLongitudeParticleFilter filter;
    LatitudeLongitudeParticle robot;
    Location[] landmarks;
    final int NUM_PARTICLES = 100;
    final int WORLD_WIDTH = 90 , WORLD_HEIGHT = 180;

    // For Debugging
    // ArrayList<Double> printArrayLatitude;
    //ArrayList<String> printArrayProbabilities;
    //ArrayList<Double> printArrayLongitude;
    //int printCount;

    double lat, lon; // User's current latitude and longitude
    String currentSemanticLoc; // User's current semantic location
    String trLoc1, trLoc2, trLoc3; // Trusted locations' names
    double trLocLat1, trLocLat2, trLocLat3; // Trusted locations' latitudes
    double trLocLon1, trLocLon2, trLocLon3; // Trusted locations' longitudes
    double trRad1, trRad2, trRad3; // Trusted locations' unlock zone radii
    boolean semantic; // Whether to see location in semantic form or not
    boolean lock; // Whether lock is enabled
    boolean group1Expand, group2Expand, group3Expand; // Whether each group is expanded
    double lastUpdated; //Date at which Current Location was last updated
    List<Location> past20Locs;

    //IndoorLocationController mLocationController = null;
    IndoorPedometricLocationController pLocationController = null;
    // We are using the PedometricLocationController for updates with the filter

    ServiceReceiver serviceReceiver;
    LocalBroadcastManager serviceBroadcaster;
    Intent serviceIntent;
    static final String BROADCAST_ACTION = "com.intel.location.indoor.app.indoorlocationsimple";
    static final String LATITUDELONGITUDE_ACTION = "LATITUDELONGITUDE_ACTION";

    public LatitudeLongitudeService() { }

    @Override
    public IBinder onBind(Intent serviceIntent) { return null; }

    @Override
    /**
     * Creates the service. Creates receivers to communicate with the activity and starts the
     * location controller.
     */
    public void onCreate() {
        super.onCreate();
        //printArrayLatitude = new ArrayList<>();
        //printArrayLongitude = new ArrayList<>();
        //printArrayProbabilities = new ArrayList<>();
        //printCount = 0;

        // Set up the paramters for the Filter
        Location landmark1 = new Location("");
        Location landmark2 = new Location("");
        Location landmark3 = new Location("");
        Location landmark4 = new Location("");
        Location landmark5 = new Location("");
        Location landmark6 = new Location("");
        landmark1.setLatitude(20);
        landmark1.setLongitude(-140);
        landmark2.setLatitude(20);
        landmark2.setLongitude(-135);
        landmark3.setLatitude(40);
        landmark3.setLongitude(-140);
        landmark4.setLatitude(40);
        landmark4.setLongitude(-135);
        landmark5.setLatitude(60);
        landmark5.setLongitude(-140);
        landmark6.setLatitude(60);
        landmark6.setLongitude(-135);
        landmarks = new Location[]{landmark1,landmark2,landmark3,
                landmark4,landmark5,landmark6};

        filter = new LatitudeLongitudeParticleFilter(NUM_PARTICLES, landmarks, WORLD_WIDTH, WORLD_HEIGHT);
        filter.setNoise(0.05f, 0.05f, 4f);
        robot = new LatitudeLongitudeParticle(landmarks, WORLD_WIDTH, WORLD_HEIGHT);


        //Register BroadcastReceiver to receive events from the activity
        serviceReceiver = new ServiceReceiver();
        IntentFilter serviceIntentFilter = new IntentFilter();
        serviceIntentFilter.addAction(LatitudeLongitudeService.LATITUDELONGITUDE_ACTION);
        registerReceiver(serviceReceiver, serviceIntentFilter);

        //Create a Broadcaster to send events to the activity
        serviceIntent = new Intent(BROADCAST_ACTION);
        serviceBroadcaster = LocalBroadcastManager.getInstance(this);

        Toast.makeText(this, "Phone Unlocker started!", Toast.LENGTH_LONG).show();

        pLocationController = new IndoorPedometricLocationController(this);
        pLocationController.setApplicationKey("13052eafd6684f40ba13efe63e24ac91");
        pLocationController.requestLocationUpdates(LatitudeLongitudeService.this);

        //Use the Pedometric Location Controller
        //mLocationController = new IndoorLocationController(this);
        //mLocationController.setApplicationKey("13052eafd6684f40ba13efe63e24ac91");
        //mLocationController.requestLocationUpdates(LatitudeLongitudeService.this);

        showLockNotification();
        loadFirstLocation();
    }

    /**
     * ServiceReceiver is specific to this LatitudeLongitudeService.
     */
    private class ServiceReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            int datapassed = arg1.getIntExtra("LOCKPHONE", 0);
            // Show the lock notification if data = 0
            if (datapassed == 0) { LatitudeLongitudeService.this.showLockNotification(); }
            // Show the trust notification if data = 1
            else if (datapassed == 1) { LatitudeLongitudeService.this.showTrustNotification(); }
        }
    }

    /**
     * Creates a fake location for loading, then breaks for one second before refreshing the
     * display. Initiates other instance variables as well.
     */
    private void loadFirstLocation( ) {
        getVariables();
        past20Locs = new ArrayList< >();
        currentSemanticLoc = "";
        Location targetLocation = new Location("");
        targetLocation.setLatitude(0.01);
        targetLocation.setLongitude(0.01);
        onLocationChanged(targetLocation);
        try { Thread.sleep(1000); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        setVariables();
        Intent serviceIntent = new Intent();
        serviceIntent.setAction(LATITUDELONGITUDE_ACTION);
        serviceIntent.putExtra("LOCKPHONE", 2);
        sendBroadcast(serviceIntent);
    }

    /**
     * Displays a notification on whether the phone will lock or unlock automatically.
     */
    public void showLockNotification(){
        getVariables();
        String contentText = "Your phone will not unlock automatically.";
        if (! lock && inTrustedLocation()) { contentText = "Your phone will unlock automatically.";}
        // define sound URI, the sound to be played when there's a notification
        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        // serviceIntent triggered, you can add other serviceIntent for other actions
        Intent serviceIntent = new Intent(LatitudeLongitudeService.this, LatitudeLongitudeActivity.class);
        PendingIntent pIntent = PendingIntent.getActivity(LatitudeLongitudeService.this, 0, serviceIntent, 0);
        // builds the notification
        Notification mNotification = new Notification.Builder(this)
                .setContentTitle("Phone Unlocker Is Running")
                .setContentText(contentText)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pIntent)
                .setSound(soundUri)
                .build();
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        mNotification.flags |= Notification.FLAG_AUTO_CANCEL;

        notificationManager.notify("Lock",0, mNotification);
    }

    /**
     * Dismisses all notifications, to be called through onDestroy.
     */
    public void dismissAllNotifications(){
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.cancel("Lock",0);
        notificationManager.cancel("Trust",0);
    }

    /**
     * Displays a notification on whether the phone's current location should be trusted.
     */
    public void showTrustNotification(){
        getVariables();
        String contentText = "You are not in a trusted location.";
        if (inTrustedLocation()) { contentText = "You are in a trusted location."; }
        // define sound URI, the sound to be played when there's a notification
        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        // serviceIntent triggered, you can add other serviceIntent for other actions
        Intent serviceIntent = new Intent(LatitudeLongitudeService.this, LatitudeLongitudeActivity.class);
        PendingIntent pIntent = PendingIntent.getActivity(LatitudeLongitudeService.this, 0, serviceIntent, 0);
        // build the notification
        Notification mNotification = new Notification.Builder(this)
                .setContentTitle("Phone Unlocker Update")
                .setContentText(contentText)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pIntent)
                .setSound(soundUri)
                .build();

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        mNotification.flags |= Notification.FLAG_AUTO_CANCEL;

        notificationManager.notify("Trust",0, mNotification);
    }

    @Override
    public int onStartCommand(Intent serviceIntent, int flags, int startId) {
        return super.onStartCommand(serviceIntent, flags, startId);
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        setVariables();
        dismissAllNotifications();
        //mLocationController.removeUpdates(this);
        pLocationController.removeUpdates(this);
        Toast.makeText(this, "Phone Unlocker Stopped!", Toast.LENGTH_LONG).show();
    }

    @Override
    /**
     * Upon an Location Controller-detected location change, updates the display and the lock/trust
     * notifications. Calls the activity to leave the phone unlocked or not, depending on whether
     * the location (after checking to be valid) is within the radius (checked to be trusted).
     * @param location the new current location passed in by the location controller.
     */
    public void onLocationChanged(Location location) {

        if (location != null) {
            getVariables();
            if (location.getLatitude() != 0.01)  {
                //printArrayLatitude.add(location.getExtras().getDouble("x"));
                //printArrayLongitude.add(location.getExtras().getDouble("y"));
                //String printlat = "PRINTLAT" + printCount;
                //String printlon = "PRINTLON" + printCount;
                //Log.d(printlat,printArrayLatitude.toString());
                //Log.d(printlon,printArrayLongitude.toString());

                float x = (float) location.getLatitude();
                float y = (float) location.getLongitude();
                try {
                    robot.set(x, y, 0, 0);
                    /**float[] Z = robot.sense();

                     // Display distances from the current location to the landmarks
                    String s = "[";
                    for (float f : Z) {
                        s += f + ", ";
                    }
                    s += "]";
                    Toast.makeText(LatitudeLongitudeService.this,
                            s, Toast.LENGTH_SHORT).show();**/
                    filter.resample(robot.sense());
                } catch (Exception ex) {
                    Toast.makeText(LatitudeLongitudeService.this,
                            "Exception", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            if (isLocationValid(location)) {
                if (location.getLatitude() != 0.01) {
                    currentSemanticLoc = location.getExtras().getString("semantic");
                }
                lat = location.getLatitude();
                lon = location.getLongitude();
                setVariables();
                if (inTrustedLocation() && !lock) {
                    Intent serviceIntent = new Intent();
                    serviceIntent.setAction(LATITUDELONGITUDE_ACTION);
                    serviceIntent.putExtra("LOCKPHONE", 0);
                    sendBroadcast(serviceIntent);
                } else {
                    Intent serviceIntent = new Intent();
                    serviceIntent.setAction(LATITUDELONGITUDE_ACTION);
                    serviceIntent.putExtra("LOCKPHONE", 1);
                    sendBroadcast(serviceIntent);
                }
                showLockNotification();
                showTrustNotification();

            }
            setVariables();
            Intent serviceIntent = new Intent();
            serviceIntent.setAction(LATITUDELONGITUDE_ACTION);
            serviceIntent.putExtra("LOCKPHONE", 2);
            sendBroadcast(serviceIntent);
        }
        //printCount++;
    }


    /**
     * Checks whether a location is valid, and returns true if the location indeed is valid. A
     * location can be defined as valid if the distance between it and the average of the past 20
     * locations divided by the time it has taken to travel to that location is less than maximum
     * human running speed (Usain Bolt 28 mph = 12.5 m/s).
     * @param location the location to check whether it's valid
     * @return true if the location is "valid" (defined above)
     *
     * EDIT with particle filtering... if the location has a probbility of >10^-9, it will be valid
     */
    private boolean isLocationValid(Location location) {
        LatitudeLongitudeParticle bestParticle = filter.getBestParticle();
        double probability = bestParticle.measurementProb(bestParticle.sense());

        //printArrayProbabilities.add(Double.toString(probability));
        //String printprob = "PRINTPROBABILITY" + printCount;
        //Log.d(printprob,printArrayProbabilities.toString());
        if (probability > 0.0000000001) {
            Toast.makeText(LatitudeLongitudeService.this, "Probability is " + Double.toString(probability), Toast.LENGTH_SHORT).show();
            return true;
        }

        getVariables();
        long now = System.currentTimeMillis();
        double timeDifference = TimeUnit.MILLISECONDS.toSeconds(now - (long) lastUpdated);
        int numLocs = past20Locs.size();
        if (numLocs <= 2) {
            if (location.getLatitude() != 0.01) { past20Locs.add(location); }
            return true;
        }
        double totalLat = 0;
        double totalLon = 0;
        for (int i = 0; i < numLocs; i++) {
            Location current = past20Locs.get(i);
            totalLat = totalLat + current.getLatitude();
            totalLon = totalLon + current.getLongitude();
        }
        double avgLat = totalLat / numLocs;
        double avgLon = totalLon / numLocs;
        Location average = new Location("Average");
        average.setLongitude(avgLon);
        average.setLatitude(avgLat);
        float distanceDifference = location.distanceTo(average);
        if (Math.abs(distanceDifference) / timeDifference > 12.5) { return false; }
        past20Locs.add(location);
        if(numLocs >= 20) {
            past20Locs.remove(0);
        }
        lastUpdated = now;
        return true;
    }

    /**
     * Checks whether the current position is a trusted location within the circular radius of the
     * given location. Uses the location's distanceTo method to check.
     * @return true if the current location is within the radius of one of the trusted locations.
     */
    private boolean inTrustedLocation() {
        getVariables();
        Location current = new Location("");
        current.setLatitude(lat);
        current.setLongitude(lon);
        if (trLocLat1 != 0 && trLocLon1 != 0) { // Check the first location
            Location trusted1 = new Location("1");
            trusted1.setLatitude(trLocLat1);
            trusted1.setLongitude(trLocLon1);
            float difference = current.distanceTo(trusted1);
            if (Math.abs(difference) <= trRad1) {
                return true;
            }
        }
        if (trLocLat2 != 0 && trLocLon2 != 0) { // Check the second location
            Location trusted2 = new Location("2");
            trusted2.setLatitude(trLocLat2);
            trusted2.setLongitude(trLocLon2);
            float difference = current.distanceTo(trusted2);
            if (Math.abs(difference) <= trRad2) {
                return true;
            }
        }
        if (trLocLat3 != 0 && trLocLon3 != 0) {
            Location trusted3 = new Location("3");
            trusted3.setLatitude(trLocLat3);
            trusted3.setLongitude(trLocLon3);
            float difference = current.distanceTo(trusted3);
            if (Math.abs(difference) <= trRad3) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the state's current variables to the global variable class given in the java class
     * LatitudeLongitudePrefs. This essentially backs the variables up since the preferences file
     * is written into the Android.
     */
    private void setVariables() {
        LatitudeLongitudePrefs.getInstance().setDouble("lat", lat);
        LatitudeLongitudePrefs.getInstance().setDouble("lon", lon);
        LatitudeLongitudePrefs.getInstance().setString("currentSemanticLoc", currentSemanticLoc);
        LatitudeLongitudePrefs.getInstance().setString("trLoc1", trLoc1);
        LatitudeLongitudePrefs.getInstance().setString("trLoc2", trLoc2);
        LatitudeLongitudePrefs.getInstance().setString("trLoc3", trLoc3);
        LatitudeLongitudePrefs.getInstance().setDouble("trLocLat1", trLocLat1);
        LatitudeLongitudePrefs.getInstance().setDouble("trLocLat2", trLocLat2);
        LatitudeLongitudePrefs.getInstance().setDouble("trLocLat3", trLocLat3);
        LatitudeLongitudePrefs.getInstance().setDouble("trLocLon1", trLocLon1);
        LatitudeLongitudePrefs.getInstance().setDouble("trLocLon2", trLocLon2);
        LatitudeLongitudePrefs.getInstance().setDouble("trLocLon3", trLocLon3);
        LatitudeLongitudePrefs.getInstance().setDouble("trRad1", trRad1);
        LatitudeLongitudePrefs.getInstance().setDouble("trRad2", trRad2);
        LatitudeLongitudePrefs.getInstance().setDouble("trRad3", trRad3);
        LatitudeLongitudePrefs.getInstance().setBoolean("semantic", semantic);
        LatitudeLongitudePrefs.getInstance().setBoolean("lock", lock);
        LatitudeLongitudePrefs.getInstance().setBoolean("group1Expand", group1Expand);
        LatitudeLongitudePrefs.getInstance().setBoolean("group2Expand", group2Expand);
        LatitudeLongitudePrefs.getInstance().setBoolean("group3Expand", group3Expand);
        LatitudeLongitudePrefs.getInstance().setDouble("lastUpdated", lastUpdated);
    }

    /**
     * Gets the variable values from the global variable class given in the java class
     * LatitudeLongitudePrefs and sets them to this state's instance variables. Initiates the values
     * if it is the first time the app is running.
     */
    private void getVariables() {
        if (! LatitudeLongitudePrefs.getInstance().contains("lat")) {
            LatitudeLongitudePrefs.getInstance().setDouble("lat", 0.0);
            LatitudeLongitudePrefs.getInstance().setDouble("lon", 0.0);
            LatitudeLongitudePrefs.getInstance().setString("currentSemanticLoc", "");
            LatitudeLongitudePrefs.getInstance().setString("trLoc1", "Trusted Location 1");
            LatitudeLongitudePrefs.getInstance().setString("trLoc2", "Trusted Location 2");
            LatitudeLongitudePrefs.getInstance().setString("trLoc3", "Trusted Location 3");
            LatitudeLongitudePrefs.getInstance().setDouble("trLocLat1", 0.0);
            LatitudeLongitudePrefs.getInstance().setDouble("trLocLat2", 0.0);
            LatitudeLongitudePrefs.getInstance().setDouble("trLocLat3", 0.0);
            LatitudeLongitudePrefs.getInstance().setDouble("trLocLon1", 0.0);
            LatitudeLongitudePrefs.getInstance().setDouble("trLocLon2", 0.0);
            LatitudeLongitudePrefs.getInstance().setDouble("trLocLon3", 0.0);
            LatitudeLongitudePrefs.getInstance().setDouble("trRad1", 0.0);
            LatitudeLongitudePrefs.getInstance().setDouble("trRad2", 0.0);
            LatitudeLongitudePrefs.getInstance().setDouble("trRad3", 0.0);
            LatitudeLongitudePrefs.getInstance().setBoolean("semantic", false);
            LatitudeLongitudePrefs.getInstance().setBoolean("lock", true);
            LatitudeLongitudePrefs.getInstance().setBoolean("group1Expand", true);
            LatitudeLongitudePrefs.getInstance().setBoolean("group2Expand", false);
            LatitudeLongitudePrefs.getInstance().setBoolean("group3Expand", true);
            LatitudeLongitudePrefs.getInstance().setDouble("lastUpdated", (double) System.currentTimeMillis());
        }
        lat = LatitudeLongitudePrefs.getInstance().getDouble("lat", lat);
        lon = LatitudeLongitudePrefs.getInstance().getDouble("lon", lon);
        currentSemanticLoc = LatitudeLongitudePrefs.getInstance().getString("currentSemanticLoc", currentSemanticLoc);
        trLoc1 = LatitudeLongitudePrefs.getInstance().getString("trLoc1", trLoc1);
        trLoc2 = LatitudeLongitudePrefs.getInstance().getString("trLoc2", trLoc2);
        trLoc3 = LatitudeLongitudePrefs.getInstance().getString("trLoc3", trLoc3);
        trLocLat1 = LatitudeLongitudePrefs.getInstance().getDouble("trLocLat1", trLocLat1);
        trLocLat2 = LatitudeLongitudePrefs.getInstance().getDouble("trLocLat2", trLocLat2);
        trLocLat3 = LatitudeLongitudePrefs.getInstance().getDouble("trLocLat3", trLocLat3);
        trLocLon1 = LatitudeLongitudePrefs.getInstance().getDouble("trLocLon1", trLocLon1);
        trLocLon2 = LatitudeLongitudePrefs.getInstance().getDouble("trLocLon2", trLocLon2);
        trLocLon3 = LatitudeLongitudePrefs.getInstance().getDouble("trLocLon3", trLocLon3);
        trRad1 = LatitudeLongitudePrefs.getInstance().getDouble("trRad1", trRad1);
        trRad2 = LatitudeLongitudePrefs.getInstance().getDouble("trRad2", trRad2);
        trRad3 = LatitudeLongitudePrefs.getInstance().getDouble("trRad3", trRad3);
        semantic = LatitudeLongitudePrefs.getInstance().getBoolean("semantic", semantic);
        lock = LatitudeLongitudePrefs.getInstance().getBoolean("lock", lock);
        group1Expand = LatitudeLongitudePrefs.getInstance().getBoolean("group1Expand", group1Expand);
        group2Expand = LatitudeLongitudePrefs.getInstance().getBoolean("group2Expand", group2Expand);
        group3Expand = LatitudeLongitudePrefs.getInstance().getBoolean("group3Expand", group3Expand);
        lastUpdated = LatitudeLongitudePrefs.getInstance().getDouble("lastUpdated", lastUpdated);

    }

    // OVERRIDING LOCATIONCONTROLLER METHODS
    @Override
    public void onFloorReceived(Floor floor) {  }
    @Override
    public void onRoomsReceived(List<Room> rooms) {  }
    @Override
    public void onMapChanged(String name, Bitmap bitmap, double scale) {  }
    @Override
    public void onGeofenced(String locator, String message) {  }
}
