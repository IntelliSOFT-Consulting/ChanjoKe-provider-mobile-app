package com.intellisoft.chanjoke.detail.ui.main

import android.app.Application
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.fhir.FhirEngine
import com.intellisoft.chanjoke.MainActivity
import com.intellisoft.chanjoke.databinding.FragmentVaccinesBinding
import com.intellisoft.chanjoke.fhir.FhirApplication
import com.intellisoft.chanjoke.fhir.data.FormatterClass
import com.intellisoft.chanjoke.fhir.data.NavigationDetails
import com.intellisoft.chanjoke.vaccine.BottomSheetFragment
import com.intellisoft.chanjoke.viewmodel.PatientDetailsViewModel
import com.intellisoft.chanjoke.viewmodel.PatientDetailsViewModelFactory
import timber.log.Timber


/**
 * A placeholder fragment containing a simple view.
 */
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

class VaccinesFragment : Fragment() {

    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    private lateinit var binding: FragmentVaccinesBinding
    private lateinit var patientDetailsViewModel: PatientDetailsViewModel
    private lateinit var patientId: String
    private lateinit var fhirEngine: FhirEngine
    private val formatterClass = FormatterClass()
    private lateinit var layoutManager: RecyclerView.LayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        binding = FragmentVaccinesBinding.inflate(inflater, container, false)

        fhirEngine = FhirApplication.fhirEngine(requireContext())

        patientId = formatterClass.getSharedPref("patientId", requireContext()).toString()

        layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.VERTICAL,
            false
        )
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.setHasFixedSize(true)

        patientDetailsViewModel = ViewModelProvider(this,
           PatientDetailsViewModelFactory(requireContext().applicationContext as Application,fhirEngine, patientId)
        )[PatientDetailsViewModel::class.java]

        binding.administerVaccine.setOnClickListener {

            formatterClass.saveSharedPref(
                "questionnaireJson",
                "contraindications.json",
                requireContext())

            formatterClass.saveSharedPref(
                "vaccinationFlow",
                "createVaccineDetails",
                requireContext()
            )

            val bottomSheetFragment = BottomSheetFragment()
            bottomSheetFragment.show(childFragmentManager, bottomSheetFragment.tag)

        }

        binding.btnUpdateHistory.setOnClickListener {
            createDialog()

        }

        getVaccinations()
        return binding.root
    }

    private fun getVaccinations() {

        val encounterList = patientDetailsViewModel.getEncounterList()

        println(encounterList)

        val vaccineAdapter = VaccineAdapter(encounterList,requireContext())
        binding.recyclerView.adapter = vaccineAdapter
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment AppointmentsFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            AppointmentsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }

    private fun createDialog() {

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Info")
        builder.setMessage("Select Record to Update")
        builder.setPositiveButton("Vaccine Details") { _: DialogInterface, i: Int ->

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

            val intent = Intent(context, MainActivity::class.java)
            intent.putExtra("functionToCall", NavigationDetails.ADMINISTER_VACCINE.name)
            intent.putExtra("patientId", patientId)
            startActivity(intent)

        }
        builder.setNegativeButton("Client Record") { dialogInterface: DialogInterface, i: Int ->

            formatterClass.saveSharedPref(
                "questionnaireJson",
                "update_history.json",
                requireContext()
            )

            val intent = Intent(context, MainActivity::class.java)
            intent.putExtra("functionToCall", NavigationDetails.ADMINISTER_VACCINE.name)
            intent.putExtra("patientId", patientId)
            startActivity(intent)


        }

        val dialog: AlertDialog = builder.create()
        dialog.show()

    }

}