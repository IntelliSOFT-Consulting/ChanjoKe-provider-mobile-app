package com.intellisoft.chanjoke.detail.ui.main.appointments

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.fhir.FhirEngine
import com.google.android.material.button.MaterialButton
import com.intellisoft.chanjoke.MainActivity
import com.intellisoft.chanjoke.R
import com.intellisoft.chanjoke.databinding.FragmentAppointmentsBinding
import com.intellisoft.chanjoke.detail.PatientDetailActivity
import com.intellisoft.chanjoke.fhir.FhirApplication
import com.intellisoft.chanjoke.fhir.data.FormatterClass
import com.intellisoft.chanjoke.fhir.data.NavigationDetails
import com.intellisoft.chanjoke.viewmodel.PatientDetailsViewModel
import com.intellisoft.chanjoke.viewmodel.PatientDetailsViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppointmentsFragment : Fragment() {

    // TODO: Rename and change types of parameters

    private lateinit var binding: FragmentAppointmentsBinding
    private lateinit var patientDetailsViewModel: PatientDetailsViewModel
    private lateinit var patientId: String
    private lateinit var fhirEngine: FhirEngine
    private val formatterClass = FormatterClass()
    private lateinit var layoutManager: RecyclerView.LayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {

        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentAppointmentsBinding.inflate(layoutInflater)

        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)

        (requireActivity() as AppCompatActivity).supportActionBar?.apply {
            title = "Administer Vaccine"
//            setDisplayShowHomeEnabled(true)
//            setDisplayHomeAsUpEnabled(true)
        }

        fhirEngine = FhirApplication.fhirEngine(requireContext())

        patientId = formatterClass.getSharedPref("patientId", requireContext()).toString()
//        onBackPressed()

        layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.VERTICAL,
            false
        )
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.setHasFixedSize(true)

        patientDetailsViewModel = ViewModelProvider(
            this,
            PatientDetailsViewModelFactory(
                requireContext().applicationContext as Application,
                fhirEngine,
                patientId
            )
        )[PatientDetailsViewModel::class.java]

        binding.addAppointment.setOnClickListener {

            formatterClass.deleteSharedPref("addAppointment",requireContext())

            val intent = Intent(requireContext(), AddAppointment::class.java)
            startActivity(intent)

        }
        binding.btnBack.setOnClickListener { onBackPressed() }


        getAppointments()
    }
    private fun onBackPressed() {
        val intent = Intent(requireContext(), PatientDetailActivity::class.java)
        intent.putExtra("patientId", patientId)
        startActivity(intent)
    }

    private fun getAppointments() {

        CoroutineScope(Dispatchers.IO).launch {
            formatterClass.deleteSharedPref("appointmentId", requireContext())

            val appointmentList = patientDetailsViewModel.getAppointmentList()

            val vaccineAdapter = AppointmentAdapter(appointmentList, requireContext())
            CoroutineScope(Dispatchers.Main)
                .launch {
                    binding.recyclerView.adapter = vaccineAdapter
                }
        }


    }



    private fun createDialog() {

        val customDialogView =
            LayoutInflater.from(context).inflate(R.layout.custom_dialog_layout, null)

        // Find views within the custom layout
        val vaccineDetails: MaterialButton = customDialogView.findViewById(R.id.vaccineDetails)
        val clientDetails: MaterialButton = customDialogView.findViewById(R.id.clientDetails)
        val cancelButton: ImageButton = customDialogView.findViewById(R.id.cancel_button)

        // Set up the AlertDialog
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(customDialogView)
        // Set up any other UI interactions or logic here

        // Create and show the AlertDialog
        val customDialog = builder.create()
        customDialog.show()

        // Example: Set an onClickListener for the "Close" button
        vaccineDetails.setOnClickListener {
            formatterClass.saveSharedPref(
                "questionnaireJson",
                "update_history_specifics.json",
                requireContext()
            )
            formatterClass.saveSharedPref(
                "vaccinationFlow",
                "updateVaccineDetails",
                requireContext()
            )
            FormatterClass().saveSharedPref(
                "title",
                "Update Vaccine Details", requireContext()
            )
            val intent = Intent(context, MainActivity::class.java)
            intent.putExtra("functionToCall", NavigationDetails.ADMINISTER_VACCINE.name)
            intent.putExtra("patientId", patientId)
            startActivity(intent)
            customDialog.dismiss() // Close the dialog
        }
        clientDetails.setOnClickListener {
            formatterClass.saveSharedPref(
                "questionnaireJson",
                "update_history.json",
                requireContext()
            )
            FormatterClass().saveSharedPref(
                "title",
                "Update Client Details", requireContext()
            )
            val intent = Intent(context, MainActivity::class.java)
            intent.putExtra("functionToCall", NavigationDetails.ADMINISTER_VACCINE.name)
            intent.putExtra("patientId", patientId)
            startActivity(intent)
            customDialog.dismiss() // Close the dialog
        }

        // Example: Set a dismiss listener for additional actions when the dialog is dismissed
        customDialog.setOnDismissListener {
            // Additional actions when the dialog is dismissed
        }
        cancelButton.setOnClickListener {
            // Additional actions when the dialog is dismissed
            customDialog.dismiss()
        }


    }

}