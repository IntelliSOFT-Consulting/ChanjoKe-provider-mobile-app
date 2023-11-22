package com.intellisoft.chanjoke.detail.ui.main

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.intellisoft.chanjoke.MainActivity
import com.intellisoft.chanjoke.R
import com.intellisoft.chanjoke.fhir.data.DbAppointmentDetails
import com.intellisoft.chanjoke.fhir.data.DbVaccineData
import com.intellisoft.chanjoke.fhir.data.FormatterClass
import com.intellisoft.chanjoke.fhir.data.NavigationDetails
import com.intellisoft.chanjoke.vaccine.BottomSheetFragment

class AppointmentAdapter(
    private var entryList: ArrayList<DbAppointmentDetails>,
    private val context: Context
) : RecyclerView.Adapter<AppointmentAdapter.Pager2ViewHolder>() {

    inner class Pager2ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener {

        val tvAppointment: TextView = itemView.findViewById(R.id.tvAppointment)
        val tvDateScheduled: TextView = itemView.findViewById(R.id.tvDateScheduled)
        val tvDoseNumber: TextView = itemView.findViewById(R.id.tvDoseNumber)
        val btnAdministerVaccine: TextView = itemView.findViewById(R.id.btnAdministerVaccine)

        init {
            itemView.setOnClickListener(this)
            btnAdministerVaccine.setOnClickListener {

                val formatterClass = FormatterClass()
                val patientId = FormatterClass().getSharedPref("patientId", context)


                formatterClass.saveSharedPref(
                    "questionnaireJson",
                    "contraindications.json",
                    context)

                formatterClass.saveSharedPref(
                    "vaccinationFlow",
                    "createVaccineDetails",
                    context
                )

//                val bottomSheetFragment = BottomSheetFragment()
//                bottomSheetFragment.show(childFragmentManager, bottomSheetFragment.tag)

            }

        }

        override fun onClick(p0: View) {

            val pos = adapterPosition

        }


    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): Pager2ViewHolder {
        return Pager2ViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.appointments,
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: Pager2ViewHolder, position: Int) {


        val targetDisease = entryList[position].targetDisease
        val dateScheduled = entryList[position].dateScheduled
        val doseNumber = entryList[position].doseNumber

        holder.tvAppointment.text = targetDisease
        holder.tvDateScheduled.text = dateScheduled
        holder.tvDoseNumber.text = doseNumber


    }

    override fun getItemCount(): Int {
        return entryList.size
    }

}