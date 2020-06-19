package com.example.myhappyplaces.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.myhappyplaces.R
import com.example.myhappyplaces.database.DatabaseHandler
import com.example.myhappyplaces.models.HappyPlaceModel
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.example.myhappyplaces.utils.GetAddressFromLatLng
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_add_happy_place.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "AddHappyPlaceActivityDebug"

class AddHappyPlaceActivity : AppCompatActivity(), View.OnClickListener {

    companion object {
        //Create a variable for GALLERY Selection which will be later used in the onActivityResult method.)
        private const val GALLERY = 1

        //Create a variable for CAMERA Selection which will be later used in the onActivityResult method.)
        private const val CAMERA = 2

        private const val IMAGE_DIRECTORY = "HappyPlacesImages"

        // A constant variable for place picker
        private const val PLACE_AUTOCOMPLETE_REQUEST_CODE = 3
    }

    private var saveImageToInternalStorage: Uri? = null
    private var mLatitude: Double = 0.0 // A variable which will hold the latitude value.
    private var mLongitude: Double = 0.0 // A variable which will hold the longitude value.

    private var mHappyPlaceDetails : HappyPlaceModel? = null

    /**
     * An variable to get an instance calendar using the default time zone and locale.
     */
    private var cal = Calendar.getInstance()

    /**
     * A variable for DatePickerDialog OnDateSetListener. I put this all in the varible to clean it up as i dont know why it needs to be in oncreate
     * The listener used to indicate the user has finished selecting a date. Which we will be initialize later on.
     */
    //Initializing the dateSetListener
    // https://www.tutorialkart.com/kotlin-android/android-datepicker-kotlin-example/
    private var dateSetListener = DatePickerDialog.OnDateSetListener { _, year, monthOfYear, dayOfMonth ->

            Log.d(TAG, "dateSetListener INIT") //this happens second

            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, monthOfYear)
            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)

            //Called a function as updateDateInView where after selecting a date from date picker is populated in the UI component.)
            updateDateInView()
        }

    private lateinit var mFusedLocationClient: FusedLocationProviderClient // A fused location client variable which is further user to get the user's current location

    /**
     * This function is auto created by Android when the Activity Class is created.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        //This call the parent constructor
        super.onCreate(savedInstanceState)

        // This is used to align the xml view to this class
        setContentView(R.layout.activity_add_happy_place)

        setSupportActionBar(toolbar_add_place) // Use the toolbar to set the action bar.
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // This is to use the home back button.

        // Setting the click event to the back button
        toolbar_add_place.setNavigationOnClickListener {
            onBackPressed()
        }

        //Initialize the places sdk if it is not initialized earlier.)
        if (!Places.isInitialized()) {
            Places.initialize(this@AddHappyPlaceActivity, resources.getString(R.string.google_maps_api_key))
        }

        //Assign the details to the variable of data model class which we have created above the details which we will receive through intent.)
        if (intent.hasExtra(MainActivity.EXTRA_PLACE_DETAILS)) {
            mHappyPlaceDetails = intent.getParcelableExtra(MainActivity.EXTRA_PLACE_DETAILS) as HappyPlaceModel

            //Filling the existing details to the UI components to edit.)
            supportActionBar?.title = "Edit Happy Place"

            et_title.setText(mHappyPlaceDetails!!.title)
            et_description.setText(mHappyPlaceDetails!!.description)
            et_date.setText(mHappyPlaceDetails!!.date)
            et_location.setText(mHappyPlaceDetails!!.location)
            mLatitude = mHappyPlaceDetails!!.latitude
            mLongitude = mHappyPlaceDetails!!.longitude

            saveImageToInternalStorage = Uri.parse(mHappyPlaceDetails!!.image)

            iv_place_image.setImageURI(saveImageToInternalStorage)

            btn_save.text = "UPDATE"

        } else {

            updateDateInView()
            supportActionBar?.title = "Add Happy Place"
        }

        //We have extended the onClickListener above and the override method as onClick added and here we are setting a listener to date edittext.)
        et_date.setOnClickListener(this)
        tv_add_image.setOnClickListener(this)
        btn_save.setOnClickListener(this)
        et_location.setOnClickListener(this)
        tv_select_current_location.setOnClickListener(this)
    }

    //This is a override method after extending the onclick listener interface.)
    override fun onClick(v: View?) {
        when (v!!.id) {
            //Launching the datepicker dialog on click of date edittext
            R.id.et_date -> {
                DatePickerDialog(
                    this@AddHappyPlaceActivity,
                    dateSetListener, // This is the variable which have created globally and initialized in setupUI method.
                    // set DatePickerDialog to point to today's date when it loads up
                    cal.get(Calendar.YEAR), // Here the cal instance is created globally and used everywhere in the class where it is required.
                    cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                ).show()

                Log.d(TAG, "Date picker dialog show") //this happens first
            }

            R.id.tv_add_image -> {
                val pictureDialog = AlertDialog.Builder(this)

                pictureDialog.setTitle("Select Action")

                val pictureDialogItems = arrayOf("Select photo from gallery", "Capture photo from camera")

                pictureDialog.setItems(pictureDialogItems) { _, which ->
                    when (which) {
                        // Here we have create the methods for image selection from GALLERY
                        0 -> choosePhotoFromGallery()
                        1 -> takePhotoFromCamera()
                    }
                }

                pictureDialog.show() //show dialog
            }

            R.id.btn_save -> {
                when {
                    et_title.text.isNullOrEmpty() -> { Toast.makeText(this, "Please enter title", Toast.LENGTH_SHORT).show() }
                    et_description.text.isNullOrEmpty() -> { Toast.makeText(this, "Please enter description", Toast.LENGTH_SHORT).show() }
                    et_location.text.isNullOrEmpty() -> { Toast.makeText(this, "Please select location", Toast.LENGTH_SHORT).show() }
                    saveImageToInternalStorage == null -> { Toast.makeText(this, "Please add image", Toast.LENGTH_SHORT).show() }

                    else -> {

                        // Assigning all the values to data model class.
                        val happyPlaceModel = HappyPlaceModel(

                            //Changing the id if it is for edit.)
                            if (mHappyPlaceDetails == null) 0 else mHappyPlaceDetails!!.id,

                            et_title.text.toString(),
                            saveImageToInternalStorage.toString(),
                            et_description.text.toString(),
                            et_date.text.toString(),
                            et_location.text.toString(),
                            mLatitude,
                            mLongitude
                        )

                        // Here we initialize the database handler class.
                        val dbHandler = DatabaseHandler(this)

                        //Call add or update details conditionally.)
                        if (mHappyPlaceDetails == null) {
                            val addHappyPlace = dbHandler.addHappyPlace(happyPlaceModel)

                            if (addHappyPlace > 0) {
                                setResult(Activity.RESULT_OK);
                                finish()//finishing activity
                            } else {
                                Toast.makeText(this, "Error Saving Location", Toast.LENGTH_SHORT).show()
                                setResult(Activity.RESULT_CANCELED);
                                finish()//finishing activity
                            }

                        } else {
                            val updateHappyPlace = dbHandler.updateHappyPlace(happyPlaceModel)

                            if (updateHappyPlace > 0) {
                                setResult(Activity.RESULT_OK);
                                finish()//finishing activity
                            } else {
                                Toast.makeText(this, "Error Saving Location", Toast.LENGTH_SHORT).show()
                                setResult(Activity.RESULT_CANCELED);
                                finish()//finishing activity
                            }
                        }
                    }
                }
            }

            R.id.et_location -> {
                try {
                    // These are the list of fields which we required is passed
                    val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)

                    // Start the autocomplete intent with a unique request code.
                    val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields).build(this@AddHappyPlaceActivity)

                    startActivityForResult(intent, PLACE_AUTOCOMPLETE_REQUEST_CODE)

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            R.id.tv_select_current_location -> {

                if (!isLocationEnabled()) {

                    Toast.makeText(this, "Your location provider is turned off. Please turn it on.", Toast.LENGTH_SHORT).show()

                    // This will redirect you to settings from where you need to turn on the location provider.
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)

                    startActivity(intent)

                } else {
                    // For Getting current location of user please have a look at below link for better understanding
                    // https://www.androdocs.com/kotlin/getting-current-location-latitude-longitude-in-android-using-kotlin.html
                    Dexter.withContext(this).withPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        .withListener(object : MultiplePermissionsListener {

                            override fun onPermissionsChecked(report: MultiplePermissionsReport?) {

                                //Log checking
                                Log.d(TAG, "Is Permission Permanently Denied? ${report!!.isAnyPermissionPermanentlyDenied}")

                                if (report.deniedPermissionResponses.isNotEmpty()) {
                                    Log.d(TAG, "How Many Permissions Denied? ${report.deniedPermissionResponses.size}")
                                }

                                if (report.grantedPermissionResponses.isNotEmpty()) {
                                    Log.d(TAG, "How Many Permissions Accepted? ${report.grantedPermissionResponses.size}")
                                }

                                when {
                                    report.areAllPermissionsGranted() -> { //if user selects accept
                                        requestNewLocationData()
                                    }
                                    report.isAnyPermissionPermanentlyDenied -> { //if user selects deny and dont ask again
                                        showRationalDialogForPermissions()
                                    }
                                    else -> { // if user selected deny
                                        Toast.makeText(this@AddHappyPlaceActivity, "Permission required for this feature", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }

                            override fun onPermissionRationaleShouldBeShown(permissions: MutableList<PermissionRequest>?, token: PermissionToken?) {
                                token?.continuePermissionRequest()// to make sure the permission dialog pops up even after deny
                            }
                        }).onSameThread().check()
                }
            }
        }
    }

    /**
     * A function to update the selected date in the UI with selected format.
     * This function is created because every time we don't need to add format which we have added here to show it in the UI.
     */
    private fun updateDateInView() {
        //Created a function as updateDateInView where after selecting a date from date picker is populated in the UI component.)
        val myFormat = "MM.dd.yyyy" // mention the format you need

        val sdf = SimpleDateFormat(myFormat, Locale.getDefault()) // A date format

        et_date.setText(sdf.format(cal.time).toString()) // A selected date using format which we have used is set to the UI.

        Log.d(TAG, "Updated date into field") //this happens third
    }

    /**
     * A method is used for image selection from GALLERY / PHOTOS of phone storage.
     */
    private fun choosePhotoFromGallery() {

        //Asking the permissions of Storage using DEXTER Library which we have added in gradle file.)
        Dexter.withContext(this).withPermissions(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
            .withListener(object : MultiplePermissionsListener {

                override fun onPermissionsChecked(report: MultiplePermissionsReport?) { //this is seconf

                    //Log checking
                    Log.d(TAG, "Is Permission Permanently Denied? ${report!!.isAnyPermissionPermanentlyDenied}")

                    if (report.deniedPermissionResponses.isNotEmpty()) {
                        Log.d(TAG, "How Many Permissions Denied? ${report.deniedPermissionResponses.size}")
                    }

                    if (report.grantedPermissionResponses.isNotEmpty()) {
                        Log.d(TAG, "How Many Permissions Accepted? ${report.grantedPermissionResponses.size}")
                    }

                    // Here after all the permission are granted launch the gallery to select and image.
                    when {
                        report.areAllPermissionsGranted() -> { //if user selects accept
                            //Adding an image selection code here from Gallery or phone storage.)
                            val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

                            startActivityForResult(galleryIntent, GALLERY)
                        }
                        report.isAnyPermissionPermanentlyDenied -> { //if user selects deny and dont ask again
                            showRationalDialogForPermissions()
                        }
                        else -> { // if user selected deny
                            Toast.makeText(
                                this@AddHappyPlaceActivity, "Permission required for this feature", Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                override fun onPermissionRationaleShouldBeShown(permissions: MutableList<PermissionRequest>?, token: PermissionToken?) { //this is first
                    token?.continuePermissionRequest()// to make sure the permission dialog pops up even after deny
                }
            }).onSameThread().check()
    }

    /**
     * A method is used  asking the permission for camera and storage and image capturing and selection from Camera.
     */
    private fun takePhotoFromCamera() {
        //Creating a method for image capturing and selecting from camera.)
        Dexter.withContext(this).withPermissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA)
            .withListener(object : MultiplePermissionsListener {

                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {

                    //Log checking
                    Log.d(TAG, "Is Permission Permanently Denied? ${report!!.isAnyPermissionPermanentlyDenied}")

                    if (report.deniedPermissionResponses.isNotEmpty()) {
                        Log.d(TAG, "How Many Permissions Denied? ${report.deniedPermissionResponses.size}")
                    }

                    if (report.grantedPermissionResponses.isNotEmpty()) {
                        Log.d(TAG, "How Many Permissions Accepted? ${report.grantedPermissionResponses.size}")
                    }

                    // Here after all the permission are granted launch the CAMERA to capture an image.
                    when {
                        report.areAllPermissionsGranted() -> { //if user selects accept
                            //Adding an image selection code here from Gallery or phone storage.)
                            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                            startActivityForResult(intent, CAMERA)
                        }
                        report.isAnyPermissionPermanentlyDenied -> { //if user selects deny and dont ask again
                            showRationalDialogForPermissions()
                        }
                        else -> { // if user selected deny
                            Toast.makeText(
                                this@AddHappyPlaceActivity, "Permission required for this feature", Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                override fun onPermissionRationaleShouldBeShown(permissions: MutableList<PermissionRequest>?, token: PermissionToken?) { //this is first
                    token?.continuePermissionRequest()// to make sure the permission dialog pops up even after deny
                }
            }).onSameThread().check()
    }

    /**
     * A function which is used to verify that the location or let's GPS is enable or not of the user's device.
     */
    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    //Create a method or let say function to request the current location. Using the fused location provider client.)
    /**
     * A function to request the current location. Using the fused location provider client.
     */
    @SuppressLint("MissingPermission")
    private fun requestNewLocationData() {

        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.interval = 0
        mLocationRequest.numUpdates = 1

        // Initialize the Fused location variable
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper())
    }

    //Create a location callback object of fused location provider client where we will get the current location details.)
    /**
     * A location callback object of fused location provider client where we will get the current location details.
     */
    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation = locationResult.lastLocation
            mLatitude = mLastLocation.latitude
            Log.d(TAG, "Current Latitude : $mLatitude")
            mLongitude = mLastLocation.longitude
            Log.d(TAG, "Current Longitude : $mLongitude")

            //Call the AsyncTask class fot getting an address from the latitude and longitude.)
            val addressTask = GetAddressFromLatLng(this@AddHappyPlaceActivity, mLatitude, mLongitude)

            addressTask.setAddressListener(object : GetAddressFromLatLng.AddressListener {

                override fun onAddressFound(address: String?) {
                    Log.d(TAG, "Address : $address")
                    et_location.setText(address) // Address is set to the edittext
                }

                override fun onError() {
                    Log.d(TAG, "Something is wrong with address...")
                }
            })

            Log.d(TAG, "Executing get address")
            addressTask.getAddress()
        }
    }

    /**
     * A function used to show the alert dialog when the permissions are denied and need to allow it from settings app info.
     */
    private fun showRationalDialogForPermissions() {
        //Creating a function which is used to show the alert dialog when the permissions are denied and need to allow it from settings app info.)
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton("GO TO SETTINGS") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    /**
     * Receive the result from a previous call to
     * {@link #startActivityForResult(Intent, int)}.  This follows the
     * related Activity API as described there in
     * {@link Activity#onActivityResult(int, int, Intent)}.
     *
     * @param requestCode The integer request code originally supplied to
     *                    startActivityForResult(), allowing you to identify who this
     *                    result came from.
     * @param resultCode The integer result code returned by the child activity
     *                   through its setResult().
     * @param data An Intent, which can return result data to the caller
     *               (various data can be attached to Intent "extras").
     */
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        Log.d(TAG, "onActivityResult")
        Log.d(TAG, "onResult[PLACE]: resultCode= $resultCode")

        //Receive the result of GALLERY and CAMERA.)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == GALLERY) {
                if (data != null) {
                    if (data.data != null) {

                        val contentURI = data.data!!

                        try {
                            // Here this is used to get an bitmap from URI (his way)
                            //@Suppress("DEPRECATION")
                            //val selectedImageBitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, contentURI)
                            //val saveImageToInternalStorage = saveImageToInternalStorage(selectedImageBitmap)
                            //Log.e("Saved Image : ", "Path :: $saveImageToInternalStorage")
                            //iv_place_image!!.setImageBitmap(selectedImageBitmap) // Set the selected image from GALLERY to imageView.

                            //I changed this to make it faster as his way too way to long to convert to bitmap
                            val file = getPath(contentURI)
                            val bitmap = BitmapFactory.decodeFile(file)
                            saveImageToInternalStorage =  saveImageToInternalStorage(bitmap)

                            Log.d(TAG, "Path :: $saveImageToInternalStorage")

                            iv_place_image!!.setImageBitmap(bitmap)

                        } catch (e: IOException) {
                            e.printStackTrace()
                            Toast.makeText(this@AddHappyPlaceActivity, "Failed to load image!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else if (requestCode == CAMERA) { //Camera result will be received here.
                if (data != null && data.extras != null) {

                    val thumbnail: Bitmap = data!!.extras!!.get("data") as Bitmap // Bitmap from camera
                    saveImageToInternalStorage = saveImageToInternalStorage(thumbnail)

                    Log.d(TAG, "Path :: $saveImageToInternalStorage")

                    iv_place_image!!.setImageBitmap(thumbnail) // Set to the imageView.
                }
            } else if (requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE) {

                val place= Autocomplete.getPlaceFromIntent(data!!)

                Log.d(TAG, "onActivityResult: place=${place}")

                et_location.setText(place.address)
                mLatitude = place.latLng!!.latitude
                mLongitude = place.latLng!!.longitude
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            Log.d(TAG, "Activity Cancelled")
        } else if (resultCode == AutocompleteActivity.RESULT_ERROR) {

            var status: Status? = Autocomplete.getStatusFromIntent(data!!)

            Log.d(TAG, "onResult[PLACE] error: ${status?.statusMessage}")
        }
    }

    /**
     * A function to save a copy of an image to internal storage for HappyPlaceApp to use.
     */
    private fun saveImageToInternalStorage(bitmap: Bitmap): Uri {

        // Get the context wrapper instance
        val wrapper = ContextWrapper(applicationContext)

        // Initializing a new file
        // The bellow line return a directory in internal storage
        /**
         * The Mode Private here is
         * File creation mode: the default mode, where the created file can only
         * be accessed by the calling application (or all applications sharing the
         * same user ID).
         */
        var file = wrapper.getDir(IMAGE_DIRECTORY, Context.MODE_PRIVATE)

        // Create a file to save the image
        file = File(file, "${UUID.randomUUID()}.jpg")

        try {
            // Get the file output stream
            val stream: OutputStream = FileOutputStream(file)

            // Compress bitmap
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)

            // Flush the stream
            stream.flush()

            // Close stream
            stream.close()

        } catch (e: IOException) { // Catch the exception
            e.printStackTrace()
        }

        // Return the saved image uri
        return Uri.parse(file.absolutePath)
    }

    /**
     * A function to get path of picture selected
     */
    private fun getPath(uri: Uri?): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = contentResolver.query(uri!!, projection, null, null, null) ?: return null
        val column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        cursor.moveToFirst()
        val s = cursor.getString(column_index)
        cursor.close()
        return s
    }
}
