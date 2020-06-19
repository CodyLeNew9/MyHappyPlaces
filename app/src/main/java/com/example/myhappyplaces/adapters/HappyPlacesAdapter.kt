package com.example.myhappyplaces.adapters

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.myhappyplaces.R
import com.example.myhappyplaces.activities.AddHappyPlaceActivity
import com.example.myhappyplaces.activities.MainActivity
import com.example.myhappyplaces.database.DatabaseHandler
import com.example.myhappyplaces.models.HappyPlaceModel
import kotlinx.android.synthetic.main.item_happy_place.view.*

private const val TAG = "HappyPlacesAdapterDebug"

open class HappyPlacesAdapter(private val context: Context, private var list: ArrayList<HappyPlaceModel>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    //Add a variable for onClickListener interface.)
    private var onClickListener: OnClickListener? = null

    /**
     * Inflates the item views which is designed in xml layout file
     *
     * create a new
     * {@link ViewHolder} and initializes some private fields to be used by RecyclerView.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return MyViewHolder(LayoutInflater.from(context).inflate(R.layout.item_happy_place, parent, false))
    }

    /**
     * Binds each item in the ArrayList to a view
     *
     * Called when RecyclerView needs a new {@link ViewHolder} of the given type to represent
     * an item.
     *
     * This new ViewHolder should be constructed with a new View that can represent the items
     * of the given type. You can either create a new View manually or inflate it from an XML
     * layout file.
     */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val model = list[position]

        Log.d(TAG, "onBindViewHolder Called")

        if (holder is MyViewHolder) {
            holder.itemView.iv_place_image.setImageURI(Uri.parse(model.image))
            holder.itemView.tvTitle.text = model.title
            holder.itemView.tvDescription.text = model.description

            //Finally add an onclickListener to the item.
            holder.itemView.setOnClickListener {
                if (onClickListener != null) {
                    onClickListener!!.onClick(position, model)
                }
            }
        }
    }

    /**
     * Gets the number of items in the list
     */
    override fun getItemCount(): Int {
        return list.size
    }

    //Create a function to edit the happy place details which is inserted earlier and pass the details through intent.)
    /**
     * A function to edit the added happy place detail and pass the existing details through intent.
     */
    fun notifyEditItem(activity: Activity, position: Int, requestCode: Int) {
        val intent = Intent(context, AddHappyPlaceActivity::class.java)
        intent.putExtra(MainActivity.EXTRA_PLACE_DETAILS, list[position])
        activity.startActivityForResult(intent, requestCode) // Activity is started with requestCode

        //notifyItemChanged(position) // Notify any registered observers that the item at position has changed.
    }

    //Create a function to delete the happy place details which is inserted earlier from the local storage.)
    //
    /**
     * A function to delete the added happy place detail from the local storage.
     */
    fun removeAt(position: Int) {

        Log.d(TAG, "Removing item ${list[position].title}")

        val dbHandler = DatabaseHandler(context)

        val isDeleted = dbHandler.deleteHappyPlace(list[position])

        if (isDeleted > 0) {

            list.removeAt(position)

            notifyItemRemoved(position)

            Toast.makeText(context, "Deleted Place", Toast.LENGTH_SHORT).show()

        } else {
            Toast.makeText(context, "Error", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Error removing item")
        }
    }

    //Create a function to bind the onclickListener)
    /**
     * A function to bind the onclickListener.
     */
    fun setOnClickListener(onClickListener: OnClickListener) {
        this.onClickListener = onClickListener
    }

    //Create an interface for onclickListener
    interface OnClickListener {
        fun onClick(position: Int, model: HappyPlaceModel)
    }

    /**
     * A ViewHolder describes an item view and metadata about its place within the RecyclerView.
     */
    private class MyViewHolder(view: View) : RecyclerView.ViewHolder(view)
}