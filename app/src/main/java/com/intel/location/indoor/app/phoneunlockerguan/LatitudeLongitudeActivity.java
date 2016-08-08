package com.intel.location.indoor.app.phoneunlockerguan;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.ToggleButton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This application saves three trusted locations and has the ability to check whether you are
 * inside each of them. This application has the ability to unlock your phone when you are within
 * a X (user-set) foot radius around each trusted location.
 *
 * LatitudeLongitudeActivity is a phone activity that extends a normal activity. This class also
 * must have a Service which implements IndoorLocationServiceListener to be able to get location
 * updates through WiFi.
 *
 * Author: Grace Guan
 * Last Modified: August 2016
 */
public class LatitudeLongitudeActivity extends Activity {
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
    boolean truth1, truth2, truth3; // Whether we are in each trusted locations 1, 2, and 3

    ExpandableListAdapter listAdapter;
    ExpandableListView expListView;
    List<String> listDataHeader;
    HashMap<String, List<String>> listDataChild;

    ActivityReceiver activityReceiver;
    LocalBroadcastManager activityBroadcaster;
    Intent activityIntent;
    static final String BROADCAST_ACTION = "com.intel.location.indoor.app.indoorlocationsimple";
    static final String LATITUDELONGITUDE_ACTION = "LATITUDELONGITUDE_ACTION";

    @Override
    /**
     * Creates the activity. From scratch, this method sets up the saved instance state (if there is
     * one) as well as preferences. Then, this method checks if there is network connection. If
     * there is no connection, the application ends. Next, this method creates a location controller
     * that begins to request location updates. After, this method sets the content view to display
     * the XML. Lastly, this method defines all of the functionality of the buttons.
     * @param savedInstanceState - the saved values from the previous running of the program, if any
     */
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // Create the app

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); // Lock vertical orient

        // Initialize and set up the global variables Java file
        LatitudeLongitudePrefs.getInstance().Initialize(LatitudeLongitudeActivity.this);
        getVariables();

        //Create a Broadcaster to send events to service
        activityIntent = new Intent(BROADCAST_ACTION);
        activityBroadcaster = LocalBroadcastManager.getInstance(this);

        //Register BroadcastReceiver to receive events from service
        activityReceiver = new ActivityReceiver();
        IntentFilter activityIntentFilter = new IntentFilter();
        activityIntentFilter.addAction(LatitudeLongitudeService.LATITUDELONGITUDE_ACTION);
        registerReceiver(activityReceiver, activityIntentFilter);

        // Start the application as a service
        Intent serviceIntent = new Intent(LatitudeLongitudeActivity.this, LatitudeLongitudeService.class);
        startService(serviceIntent);

        // Check for network connection
        if (!isNetworkConnected()) {
            new AlertDialog.Builder(this)
                .setMessage("No network connection.")
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    finish();
                    }
                }).show();
            return;
        }

        // Set up the content view
        setContentView(R.layout.activity_latitude_longitude);
        setUpSetTrustedLocationButton(); // Set up the set trusted location button
        setUpToggleButtons(); // Set up the toggle buttons
        setUpExpListView(); // Set up the expanded list view
        refresh(); // Refresh the display
    } // end onCreate method

    /**
     * ActivityReceiver is specific to this LatitudeLongitudeActivity.
     */
    private class ActivityReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            int datapassed = arg1.getIntExtra("LOCKPHONE", 0);
            // hide the lock screen if datapassed = 0
            if (datapassed == 0) {
                LatitudeLongitudeActivity.this.getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            // show the lock screen if datapassed = 1
            else if (datapassed == 1) {
                LatitudeLongitudeActivity.this.getWindow().clearFlags(
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            // refresh the display if datapassed = 2
            else if (datapassed == 2) { LatitudeLongitudeActivity.this.refresh(); }
        }
    }

    /**
     * Refreshes the display. Updates the current location text, the trusted location text, and
     * writes whether the user is currently located in a trusted location. Also displays whether the
     * phone is set to unlock in said trusted location. Does so by refreshing the listview and
     * opening the display to what it was open to previously.
     */
    public void refresh( ) {
        getVariables();
        expListView = (ExpandableListView) findViewById(R.id.lvExp); // get the listview
        prepareListData(); // preparing list data
        listAdapter = new ExpandableListAdapter(this, listDataHeader, listDataChild);
        expListView.setAdapter(listAdapter); // reset list adapter with new data
        if (group1Expand) { expListView.expandGroup(0); } // re-expand data to previous openings
        if (group2Expand) { expListView.expandGroup(1); }
        if (group3Expand) { expListView.expandGroup(2); }
        setVariables();
        Intent activityIntent = new Intent();
        activityIntent.setAction(LATITUDELONGITUDE_ACTION);
        activityIntent.putExtra("LOCKPHONE", 0);
        sendBroadcast(activityIntent);
        Intent activityIntent2 = new Intent();
        activityIntent2.setAction(LATITUDELONGITUDE_ACTION);
        activityIntent2.putExtra("LOCKPHONE", 1);
        sendBroadcast(activityIntent2);
    }

    /**
     * Sets up the expandable list view. The view starts with all of the tabs open. On clicking the
     * group, it will have infinite scroll. On clicking each subgroup, they can expand and contract.
     * On clicking each child, only the second group will create alerts.
     */
    private void setUpExpListView( ) {
        getVariables();
        group1Expand = true;
        group2Expand = true;
        group3Expand = true;
        expListView = (ExpandableListView) findViewById(R.id.lvExp); // get the listview
        prepareListData(); // preparing list data
        listAdapter = new ExpandableListAdapter(this, listDataHeader, listDataChild);
        expListView.setAdapter(listAdapter); // setting list adapter
        // Listview Group click listener -- set infinite scroll
        expListView.setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {

            @Override
            public boolean onGroupClick(ExpandableListView parent, View v,
                                        int groupPosition, long id) {
            setListViewHeight(parent, groupPosition); // set infinite scroll
            return false;
            }
        });
        // Listview Group expanded listener -- check whether every group is expanded
        expListView.setOnGroupExpandListener(new ExpandableListView.OnGroupExpandListener() {

            @Override
            public void onGroupExpand(int groupPosition) {
            if (groupPosition == 0) { group1Expand = true; }
            else if (groupPosition == 1) { group2Expand = true; }
            else if (groupPosition == 2) { group3Expand = true; }
            setVariables();
            }
        });
        // Listview Group collapsed listener -- check whether every group is collapsed or not
        expListView.setOnGroupCollapseListener(new ExpandableListView.OnGroupCollapseListener() {

            @Override
            public void onGroupCollapse(int groupPosition) {
            if (groupPosition == 0) { group1Expand = false; }
            else if (groupPosition == 1) { group2Expand = false; }
            else if (groupPosition == 2) { group3Expand = false; }
            setVariables();
            }
        });
        // Listview on child click listener
        expListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {

            @Override
            public boolean onChildClick(ExpandableListView parent, View v,
                                        int groupPosition, int childPosition, long id) {
            if (groupPosition == 1) {
                final Context context = LatitudeLongitudeActivity.this; // define this activity for later
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
                String trustedLocationDisplay = "Location Not Yet Set";
                if (childPosition == 0) {
                    alertDialog.setTitle("Trusted Location 1");
                    if (trLocLat1 != 0 && trLocLon1 != 0) {
                        trustedLocationDisplay = "Name: " + trLoc1 + "\nLatitude: "
                                + Double.toString(LatitudeLongitudeMath.round(trLocLat1, 5))
                                + "\nLongitude: " + Double.toString(LatitudeLongitudeMath.round(trLocLon1, 5));
                        alertDialog.setPositiveButton("Remove Location",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        trLoc1 = "Trusted Location 1";
                                        trLocLat1 = 0.0;
                                        trLocLon1 = 0.0;
                                        trRad1 = 0.0;
                                        setVariables();
                                        refresh();
                                    }
                                });
                    }
                }
                else if (childPosition == 1) {
                    alertDialog.setTitle("Trusted Location 2");
                    if (trLocLat2 != 0 && trLocLon2 != 0) {
                        trustedLocationDisplay = "Name: " + trLoc2 + "\nLatitude: "
                                + Double.toString(LatitudeLongitudeMath.round(trLocLat2, 5))
                                + "\nLongitude: " + Double.toString(LatitudeLongitudeMath.round(trLocLon2, 5));
                        alertDialog.setPositiveButton("Remove Location",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        trLoc2 = "Trusted Location 2";
                                        trLocLat2 = 0.0;
                                        trLocLon2 = 0.0;
                                        trRad2 = 0.0;
                                        setVariables();
                                        refresh();
                                    }
                                });
                    }
                }
                else if (childPosition == 2) {
                    alertDialog.setTitle("Trusted Location 3");
                    if (trLocLat3 != 0 && trLocLon3 != 0) {
                        trustedLocationDisplay = "Name: " + trLoc3 + "\nLatitude: "
                                + Double.toString(LatitudeLongitudeMath.round(trLocLat3, 5))
                                + "\nLongitude: " + Double.toString(LatitudeLongitudeMath.round(trLocLon3, 5));
                        alertDialog.setPositiveButton("Remove Location",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        trLoc3 = "Trusted Location 3";
                                        trLocLat3 = 0.0;
                                        trLocLon3 = 0.0;
                                        trRad3 = 0.0;
                                        setVariables();
                                        refresh();
                                    }
                                });
                    }
                }
                refresh();
                alertDialog.setMessage(trustedLocationDisplay);
                // onCreate button onClick: Set the buttons
                alertDialog.setNegativeButton("Close",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
                alertDialog.show(); // display the alert dialog
            }
            return false;
            }
        });
        setVariables();
        refresh();
    }

    /**
     * Sets up the two toggle buttons to show semantic location and to lock the phone or not.
     */
    private void setUpToggleButtons( ) {
        getVariables();
        // onCreate: Make the toggle button to switch between semantic and longitude/latitude.
        // Semantic = off; Long/Lat = on

        ToggleButton toggle = (ToggleButton) findViewById(R.id.toggleButton);
        if (semantic) {
            toggle.setChecked(true);
            setVariables();
            refresh();
        }
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            semantic = isChecked;
            setVariables();
            refresh();
            }
        });
        // onCreate: Make the toggle button to switch between locking and not locking.
        // Lock = off; Unlock = on
        ToggleButton toggle2 = (ToggleButton) findViewById(R.id.toggleButton2);
        if (lock) {
            toggle2.setChecked(true);
            setVariables();
            refresh();
        }
        toggle2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            lock = isChecked;
            setVariables();
            refresh();
            }
        });
        setVariables();
    }

    /**
     * Sets the button to set up a trusted location. Has text fields, spinners, and number boxes for
     * the user to input name, radius, and which trusted location to replace or set. The function
     * breaks if a field is left empty.
     */
    private void setUpSetTrustedLocationButton( ) {
        getVariables();
        // onCreate: the first button allows you to set a trusted location
        Button b = (Button) findViewById(R.id.button);
        final Context context = LatitudeLongitudeActivity.this; // define this activity for later
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
                alertDialog.setTitle("Set Trusted Location");
                LinearLayout layout = new LinearLayout(context);
                layout.setOrientation(LinearLayout.VERTICAL);
                // onCreate button onClick: Create a Spinner called numbers for location number
                final Spinner numbers = new Spinner(context);
                String[] items = new String[]{"Trusted Location 1", "Trusted Location 2", "Trusted Location 3"};
                ArrayAdapter<String> adapter = new ArrayAdapter< >(context,
                        android.R.layout.simple_spinner_dropdown_item, items);
                numbers.setAdapter(adapter);
                layout.addView(numbers);
                // onCreate button onClick: Create a one-line EditText called titleBox for location
                // name. Input type is TEXT as in a usual text box.
                final EditText titleBox = new EditText(context);
                titleBox.setHint("Location Name");
                titleBox.setSingleLine();
                titleBox.setInputType(InputType.TYPE_CLASS_TEXT);
                titleBox.setImeOptions(EditorInfo.IME_ACTION_DONE);
                layout.addView(titleBox);
                // onCreate button onClick: Create a one-line EditText called radiusInput for the
                // radius of the location the user wants to set. Input type is NUMBER for positive
                // integers only.
                final EditText radiusInput = new EditText(context);
                radiusInput.setHint("Radius (m)");
                radiusInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                radiusInput.setSingleLine();
                layout.addView(radiusInput);
                // onCreate button onClick: Set the view for these 3 text fields
                alertDialog.setView(layout);
                // onCreate button onClick: Set the positive button
                alertDialog.setPositiveButton("Set Location",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                        String locName = titleBox.getText().toString();
                        String radName = radiusInput.getText().toString();
                        int num = Integer.valueOf(numbers.getSelectedItem().toString().substring(17,18));
                        if (locName.matches("")) {
                            Toast.makeText(context, "You did not enter a name.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (radName.matches("")) {
                            Toast.makeText(context, "You did not enter a radius.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        int radius = Integer.valueOf(radName);
                        setTrustedLocation(num, locName, radius);
                        refresh();
                        }
                    });
                // onCreate button onClick: Set the negative button
                alertDialog.setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
                alertDialog.show(); // display the alert dialog
            }
        });
        setVariables();
    }

    /**
     * Takes in the params trustedLocationNum, name, and trustedLocationRad, and sets them to the
     * respective trusted location's name and radius. Then, sets that trusted location's latitude
     * and longitude to the current latitude and longitude. Refreshes the display afterward.
     * @param trustedLocationNum = which trusted location (1, 2, 3) to set
     * @param name = the name of the trusted location
     * @param trustedLocationRad = the radius of the trusted location
     */
    private void setTrustedLocation(int trustedLocationNum, String name, int trustedLocationRad) {
        getVariables();
        if (lat == 0.01) {
            truth1 = false;
            truth2 = false;
            truth3 = false;
        }
        while (lat == 0.01) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        if (trustedLocationNum == 1) {
            trLoc1 = name;
            trLocLat1 = lat;
            trLocLon1 = lon;
            trRad1 = trustedLocationRad;
        } else if (trustedLocationNum == 2) {
            trLoc2 = name;
            trLocLat2 = lat;
            trLocLon2 = lon;
            trRad2 = trustedLocationRad;
        } else if (trustedLocationNum == 3) {
            trLoc3 = name;
            trLocLat3 = lat;
            trLocLon3 = lon;
            trRad3 = trustedLocationRad;
        }
        setVariables();
        refresh();
    }

    /**
     * Checks whether the current position is a trusted location within the square radius of the
     * given location. Uses the Location class' distanceTo method to check the distance in meters
     * between the two points.
     *
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
            truth1 = true;
            if (Math.abs(difference) <= trRad1) { return true; }
        }
        else { truth1 = false; }
        if (trLocLat2 != 0 && trLocLon2 != 0) { // Check the second location
            Location trusted2 = new Location("2");
            trusted2.setLatitude(trLocLat2);
            trusted2.setLongitude(trLocLon2);
            float difference = current.distanceTo(trusted2);
            truth2 = true;
            if (Math.abs(difference) <= trRad2) { return true; }
        }
        else { truth2 = false; }
        if (trLocLat3 != 0 && trLocLon3 != 0) {
            Location trusted3 = new Location("3");
            trusted3.setLatitude(trLocLat3);
            trusted3.setLongitude(trLocLon3);
            float difference = current.distanceTo(trusted3);
            truth3 = true;
            if (Math.abs(difference) <= trRad3) { return true; }
        }
        else { truth3 = false; }
        return false;
    }

    /**
    * Preparing the list data for the expanded list view.
    */
    private void prepareListData() {
        getVariables();
        listDataHeader = new ArrayList< >();
        listDataChild = new HashMap< >();

        // Adding child data
        listDataHeader.add("Current Location");
        listDataHeader.add("Trusted Locations");
        listDataHeader.add("Status");

        // Adding child data
        List<String> currentLocation = new ArrayList< >();

        String currentLocationDisp = "Current Semantic Location: " + currentSemanticLoc;
        if (! semantic) {
            currentLocationDisp = "Current Latitude: " + Double.toString(LatitudeLongitudeMath.round(lat,5))
                    + "\n" + "Current Longitude: " + Double.toString(LatitudeLongitudeMath.round(lon,5));
        }
        if (lat == 0.01 && lon == 0.01) {
            currentLocationDisp = "Loading Current Location...";
        }
        currentLocation.add(currentLocationDisp);

        List<String> trustedLocations = new ArrayList< >();

        String trustedLocation1 = "Trusted Location 1 not yet set.";
        String trustedLocation2 = "Trusted Location 2 not yet set.";
        String trustedLocation3 = "Trusted Location 3 not yet set.";
        if (trLocLat1 != 0 && trLocLon1 != 0) { trustedLocation1 = trLoc1; }
        if (trLocLat2 != 0 && trLocLon2 != 0) { trustedLocation2 = trLoc2; }
        if (trLocLat3 != 0 && trLocLon3 != 0) { trustedLocation3 = trLoc3; }
        trustedLocations.add(trustedLocation1);
        trustedLocations.add(trustedLocation2);
        trustedLocations.add(trustedLocation3);

        List<String> info = new ArrayList< >();

        String info1 = "You are not in a trusted location.";
        String info2 = "Your phone is not set to unlock in a trusted location.";
        if (inTrustedLocation()) {
            int truths = 0;
            if (truth1) { truths += 1; }
            if (truth2) { truths += 2; }
            if (truth3) { truths += 4; }
            switch (truths) {
                case 7: info1 = "You are in trusted locations: " + trLoc1 + ", " + trLoc2 + ", " + trLoc3 + ".";
                    break;
                case 6: info1 = "You are in trusted locations: " + trLoc2 + ", " + trLoc3 + ".";
                    break;
                case 5: info1 = "You are in trusted locations: " + trLoc1 + ", " + trLoc3 + ".";
                    break;
                case 4: info1 = "You are in trusted location: " + trLoc3 + ".";
                    break;
                case 3: info1 = "You are in trusted locations: " + trLoc1 + ", " + trLoc2 + ".";
                    break;
                case 2: info1 = "You are in trusted location: " + trLoc2 + ".";
                    break;
                case 1: info1 = "You are in trusted location: " + trLoc1 + ".";
                    break;
                default: info1 = "You are not in a trusted location.";
                    break;
            }
        }
        if (! lock) { info2 = "Your phone is set to unlock at a trusted location."; }
        info.add(info1);
        info.add(info2);

        listDataChild.put(listDataHeader.get(0), currentLocation); // Header, Child data
        listDataChild.put(listDataHeader.get(1), trustedLocations);
        listDataChild.put(listDataHeader.get(2), info);
        setVariables();
    }

    /**
     * Sets infinite scroll to the ExpandableListView.
     * Adapted from StackOverflow.
     * @param listView the listView to implement vertical scrolling on
     * @param group the expanded groups within the listView to scroll through
     */
    private void setListViewHeight(ExpandableListView listView, int group) {
        ExpandableListAdapter listAdapter = (ExpandableListAdapter) listView.getExpandableListAdapter();
        int totalHeight = 0;
        int desiredWidth = View.MeasureSpec.makeMeasureSpec(listView.getWidth(),
                View.MeasureSpec.EXACTLY);
        for (int i = 0; i < listAdapter.getGroupCount(); i++) {
            View groupItem = listAdapter.getGroupView(i, false, null, listView);
            groupItem.measure(desiredWidth, View.MeasureSpec.UNSPECIFIED);
            totalHeight += groupItem.getMeasuredHeight();
            if (((listView.isGroupExpanded(i)) && (i != group))
                    || ((!listView.isGroupExpanded(i)) && (i == group))) {
                for (int j = 0; j < listAdapter.getChildrenCount(i); j++) {
                    View listItem = listAdapter.getChildView(i, j, false, null,
                            listView);
                    listItem.measure(desiredWidth, View.MeasureSpec.UNSPECIFIED);
                    totalHeight += listItem.getMeasuredHeight();
                }
            }
        }
        ViewGroup.LayoutParams params = listView.getLayoutParams();
        int height = totalHeight
                + (listView.getDividerHeight() * (listAdapter.getGroupCount() - 1));
        if (height < 10)
            height = 200;
        params.height = height;
        listView.setLayoutParams(params);
        listView.requestLayout();
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

    // Overriding Android Methods
    @Override
    protected void onStop() {
        super.onStop();

        setVariables();
    }
    @Override
    protected void onPause() {
        super.onPause();
        setVariables();
    }
    @Override
    protected void onResume() {
        super.onResume();
        getVariables();
    }
    @Override
    protected void onRestart() {
        super.onRestart();
        getVariables();
    }
    @Override
    protected void onStart() {
        super.onStart();
        getVariables();
    }
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        setVariables();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        setVariables();
        //deviceManger.removeActiveAdmin(compName);
        stopService(new Intent(LatitudeLongitudeActivity.this,LatitudeLongitudeService.class));
        try {
            unregisterReceiver(activityReceiver);
        }
        catch (Exception e) {
            //do nothing
        }
    }
    //Creating Helper Methods
    private boolean isNetworkConnected() {
        boolean haveConnectedWifi = false;
        boolean haveConnectedMobile = false;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo[] netInfo = cm.getAllNetworkInfo();
        for (NetworkInfo ni : netInfo) {
            if (ni.getTypeName().equalsIgnoreCase("WIFI"))
                if (ni.isConnected())
                    haveConnectedWifi = true;
            if (ni.getTypeName().equalsIgnoreCase("MOBILE"))
                if (ni.isConnected())
                    haveConnectedMobile = true;
        }
        return haveConnectedWifi || haveConnectedMobile;
    }

}