package com.intellisoft.chanjoke.detail.ui.main.referrals

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.Navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.intellisoft.chanjoke.MainActivity
import com.intellisoft.chanjoke.R
import com.intellisoft.chanjoke.fhir.data.DbTempData
import com.intellisoft.chanjoke.fhir.data.FormatterClass
import com.intellisoft.chanjoke.fhir.data.NavigationDetails
import com.intellisoft.chanjoke.fhir.data.ServiceRequestPatient
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale

class ReferralParentAdapter(
    private var entryList: List<ServiceRequestPatient>,
    private val context: Context
) : RecyclerView.Adapter<ReferralParentAdapter.Pager2ViewHolder>() {

    inner class Pager2ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener {

        val name: TextView = itemView.findViewById(R.id.name)
        val idNumber: TextView = itemView.findViewById(R.id.idNumber)
        val tvPhoneNumber: TextView = itemView.findViewById(R.id.tvPhoneNumber)
        val btnView: Button = itemView.findViewById(R.id.btnView)

        val viewPhoneNumber: TextView = itemView.findViewById(R.id.viewPhoneNumber)
        val viewId: TextView = itemView.findViewById(R.id.viewId)
        val viewName: TextView = itemView.findViewById(R.id.viewName)

        init {
            btnView.setOnClickListener(this)

        }

        override fun onClick(p0: View) {

            val logicalId = entryList[position].logicalId
            val patientId = entryList[position].patientId
            val patientName = entryList[position].patientName
            val dob = entryList[position].dob
            val gender = entryList[position].gender

            //Save to shared pref
            val sharedPreferences: SharedPreferences =
                context.getSharedPreferences(context.getString(R.string.referralData),
                    AppCompatActivity.MODE_PRIVATE
                )
            val editor = sharedPreferences.edit()

            editor.putString("serviceRequestId",logicalId)
            editor.putString("patientId",patientId)
            editor.putString("patientName",patientName)
            editor.apply()

            val age = try {
                FormatterClass().getFormattedAge(
                    dob,
                    btnView.context.resources,
                    context
                )
            } catch (e: Exception) {
                ""
            }

            // temporarily store details
            val temp = DbTempData(
                name = patientName,
                dob = dob,
                gender = gender,
                age = age,
            )
            FormatterClass().saveSharedPref(
                "temp_data",
                Gson().toJson(temp),
                context
            )
            FormatterClass().saveSharedPref("patientId", patientId, context)


            findNavController(p0).navigate(R.id.referralsFragment)

        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): Pager2ViewHolder {
        return Pager2ViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.patient_list_item_view,
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: Pager2ViewHolder, position: Int) {
        val payload = entryList[position]
        val patientName = entryList[position].patientName
        val patientPhone = entryList[position].patientPhone
        val patientNational = entryList[position].patientNational

        holder.name.text = patientName
        holder.idNumber.text = patientNational
        holder.tvPhoneNumber.text = patientPhone

        holder.viewPhoneNumber.text = "Phone Number"
        holder.viewId.text = "Identification No"
        holder.viewName.text = "Name"


    }

    override fun getItemCount(): Int {
        return entryList.size
    }
}