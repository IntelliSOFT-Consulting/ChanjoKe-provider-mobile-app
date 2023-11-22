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
import com.intellisoft.chanjoke.fhir.data.DbVaccineData
import com.intellisoft.chanjoke.fhir.data.FormatterClass
import com.intellisoft.chanjoke.fhir.data.NavigationDetails

class VaccineAdapter(
    private var entryList: ArrayList<DbVaccineData>,
    private val context: Context
) : RecyclerView.Adapter<VaccineAdapter.Pager2ViewHolder>() {

    inner class Pager2ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener {

        val logicalId: TextView = itemView.findViewById(R.id.logicalId)
        val tvVaccineName: TextView = itemView.findViewById(R.id.tvVaccineName)
        val btnAddAefi: Button = itemView.findViewById(R.id.btnAddAefi)
        val tvDateAdministered: TextView = itemView.findViewById(R.id.tvDateAdministered)
        val vaccineDosage: TextView = itemView.findViewById(R.id.vaccineDosage)

        init {
            itemView.setOnClickListener(this)
            btnAddAefi.setOnClickListener {

                val patientId = FormatterClass().getSharedPref("patientId", context)

                FormatterClass().saveSharedPref(
                    "questionnaireJson",
                    "adverse_effects.json", context
                )

                FormatterClass().saveSharedPref("vaccinationFlow", "addAefi", context)
                FormatterClass().saveSharedPref(
                    "encounter_logical_id", logicalId.text.toString(), context
                )
               val intent = Intent(context, MainActivity::class.java)
                intent.putExtra("functionToCall", NavigationDetails.ADMINISTER_VACCINE.name)
                intent.putExtra("patientId", patientId)
                context.startActivity(intent)

            }
            tvVaccineName.apply {
                setOnClickListener {
                    FormatterClass().saveSharedPref("current_immunization", logicalId.text.toString(), context)

                    val patientId = FormatterClass().getSharedPref("patientId", context)
                    FormatterClass().saveSharedPref(
                        "encounter_type", "aefi", context
                    )
                    FormatterClass().saveSharedPref(
                        "vaccine_name", tvVaccineName.text.toString(), context
                    )
                    FormatterClass().saveSharedPref(
                        "vaccine_date", tvDateAdministered.text.toString(), context
                    )
                    FormatterClass().saveSharedPref(
                        "vaccine_dose", vaccineDosage.text.toString(), context
                    )
                    val intent = Intent(context, MainActivity::class.java)
                    intent.putExtra("functionToCall", NavigationDetails.LIST_VACCINE_DETAILS.name)
                    intent.putExtra("patientId", patientId)
                    context.startActivity(intent)
                }
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
                R.layout.administered_vaccines,
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: Pager2ViewHolder, position: Int) {


        val vaccineName = entryList[position].vaccineName
        val vaccineDosage = entryList[position].vaccineDosage
        val logicalId = entryList[position].logicalId
        val dateAdministered = entryList[position].dateAdministered

        holder.tvVaccineName.text = "Vaccine: $vaccineName"
        holder.logicalId.text = logicalId
        holder.vaccineDosage.text = vaccineDosage
        holder.tvDateAdministered.text = dateAdministered


    }

    override fun getItemCount(): Int {
        return entryList.size
    }

}