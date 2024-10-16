package com.intellisoft.chanjoke.utils

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.DatePicker
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.button.MaterialButton
import com.intellisoft.chanjoke.R
import com.intellisoft.chanjoke.detail.PatientDetailActivity
import com.intellisoft.chanjoke.fhir.data.FormatterClass
import com.intellisoft.chanjoke.fhir.data.NavigationDetails
import com.intellisoft.chanjoke.vaccine.stock_management.VaccineStockManagement
import com.intellisoft.chanjoke.viewmodel.PatientDetailsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class BlurBackgroundDialog(
    private val fragment: Fragment,
    context: Context,
    private var patientDetailsViewModel: PatientDetailsViewModel? = null
) : Dialog(context) {



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.layout_blur_background)


        // Set window attributes to cover the entire screen
        window?.apply {
            attributes?.width = WindowManager.LayoutParams.MATCH_PARENT
            attributes?.height = WindowManager.LayoutParams.MATCH_PARENT

            // Make the dialog cover the status bar and navigation bar
            setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )

            setBackgroundDrawableResource(android.R.color.transparent) // Set a transparent background
        }
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        window?.setBackgroundDrawableResource(R.color.colorPrimary)

        val appointmentDate = findViewById<TextView>(R.id.etAppointmentDate)
        val linearVaccination = findViewById<LinearLayout>(R.id.linearVaccination)
        val tvAppointmentNo = findViewById<TextView>(R.id.tvAppointmentNo)
        val closeMaterialButton = findViewById<MaterialButton>(R.id.closeMaterialButton)


        var valueText = when (FormatterClass().getSharedPref("vaccinationFlow", context)) {
            "addAefi" -> {
                "The AEFI details have been recorded successfully."
            }

            "createVaccineDetails" -> {
                "The vaccine details have been recorded successfully."
            }

            "updateVaccineDetails" -> {
                "Client details updated successfully!"
            }

            NavigationDetails.NOT_ADMINISTER_VACCINE.name -> {
                "The vaccine(s) was not administered!"
            }

            "hiv_status" -> {
                "Client HIV status has been updated successfully"
            }

            NavigationDetails.ADMINISTER_VACCINE.name -> {
                val formatterClass = FormatterClass()
                val dueDate = formatterClass.getSharedPref("immunizationNextDate", context)
                val appointmentNo = formatterClass.getSharedPref("appointmentSize", context)

                //Make More text
                linearVaccination.visibility = View.VISIBLE

                tvAppointmentNo.text = appointmentNo
                appointmentDate.text = dueDate

                formatterClass.deleteSharedPref("dueDate", context)
                formatterClass.deleteSharedPref("appointmentNo", context)

                "Vaccine has been administered successfully!"
            }

            NavigationDetails.UPDATE_VACCINE_DETAILS.name -> {
                "Vaccine has been updated successfully!"
            }
            NavigationDetails.REFERRALS.name -> {
                "Vaccine has been administered successfully!"
            }

            else -> {
                "Record has been captured successfully!"
            }
        }
        if (FormatterClass().getSharedPref("isRegistration", context) == "true") {
            valueText = "The client has been registered successfully"
        }

        findViewById<TextView>(R.id.info_textview).apply {
            text = valueText
        }

        appointmentDate.setOnClickListener {
            showDatePickerDialog(context, appointmentDate, tvAppointmentNo, closeMaterialButton)
        }

        closeMaterialButton.setOnClickListener {
            dismiss()
            val patientId = FormatterClass().getSharedPref("patientId", context)

            val isRegistration = FormatterClass().getSharedPref("isRegistration", context)
            if (isRegistration != null) {
                if (isRegistration == "true") {
//                    val intent = Intent(context, PatientDetailActivity::class.java)
//                    intent.putExtra("patientId", patientId)
//                    context.startActivity(intent)
                    FormatterClass().deleteSharedPref("isRegistration", context)
                    NavHostFragment.findNavController(fragment).navigateUp()
                }
            } else {
                val vaccinationFlow =
                    FormatterClass().getSharedPref("isVaccineAdministered", context)

                if (vaccinationFlow == "stockManagement") {
                    val intent = Intent(context, VaccineStockManagement::class.java)
                    intent.putExtra("functionToCall", NavigationDetails.ADMINISTER_VACCINE.name)
                    intent.putExtra("patientId", patientId)
                    context.startActivity(intent)
                    FormatterClass().deleteSharedPref("isVaccineAdministered", context)
                }else if (vaccinationFlow == NavigationDetails.REFERRALS.name){
                    fragment.view?.let { it1 ->
                        Navigation.findNavController(it1).navigate(R.id.activeReferralsFragment)
                    }
                } else {
                    val intent = Intent(context, PatientDetailActivity::class.java)
                    intent.putExtra("patientId", patientId)
                    context.startActivity(intent)
                }

            }

            FormatterClass().deleteSharedPref("isVaccineAdministered", context)
            FormatterClass().deleteSharedPref("vaccinationFlow", context)
        }


    }

    private fun showDatePickerDialog(
        context: Context,
        appointmentDate: TextView,
        tvAppointmentNo: TextView,
        closeMaterialButton: MaterialButton
    ) {

        closeMaterialButton.text = "Save"

        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val maxCalendar = Calendar.getInstance()
//        maxCalendar.add(Calendar.DAY_OF_MONTH, -7)

        val datePickerDialog = DatePickerDialog(
            context,
            { _: DatePicker, selectedYear: Int, selectedMonth: Int, selectedDay: Int ->
                // Handle the selected date (e.g., update the TextView)
                val formattedDate = "$selectedDay-${selectedMonth + 1}-$selectedYear"
                appointmentDate.text = formattedDate

                Toast.makeText(context,
                    "Please wait as we calculate the number of appointments",
                    Toast.LENGTH_SHORT).show()

                getDateAppointments(context, formattedDate, tvAppointmentNo)

            },
            year,
            month,
            day
        )
        datePickerDialog.datePicker.minDate = System.currentTimeMillis() // Set the limit for the last date

        // Show the DatePickerDialog
        datePickerDialog.show()
    }

    private fun getDateAppointments(
        context: Context,
        formattedDate: String,
        tvAppointmentNo: TextView
    ) {

        CoroutineScope(Dispatchers.IO).launch {
            val recommendationList = patientDetailsViewModel?.getRecommendationDate(formattedDate) ?: emptyList()
            val appointmentList = patientDetailsViewModel?.getAppointmentList() ?: emptyList()

            val appointmentSize = appointmentList.size + recommendationList.size
            CoroutineScope(Dispatchers.Main).launch {
                tvAppointmentNo.text = "$appointmentSize"

            }

        }


    }
}