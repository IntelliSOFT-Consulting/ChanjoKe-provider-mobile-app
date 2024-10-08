package com.intellisoft.chanjoke.detail.ui.main.aefis

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.fhir.FhirEngine
import com.google.gson.Gson
import com.intellisoft.chanjoke.R
import com.intellisoft.chanjoke.databinding.FragmentAefisBinding
import com.intellisoft.chanjoke.detail.PatientDetailActivity
import com.intellisoft.chanjoke.detail.ui.main.adapters.VaccineAefiAdapter
import com.intellisoft.chanjoke.fhir.FhirApplication
import com.intellisoft.chanjoke.fhir.data.AdministeredDetails
import com.intellisoft.chanjoke.fhir.data.AllergicReaction
import com.intellisoft.chanjoke.fhir.data.DbVaccineData
import com.intellisoft.chanjoke.fhir.data.FormatterClass
import com.intellisoft.chanjoke.vaccine.validations.ImmunizationHandler
import com.intellisoft.chanjoke.viewmodel.PatientDetailsViewModel
import com.intellisoft.chanjoke.viewmodel.PatientDetailsViewModelFactory
import timber.log.Timber

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [AefisFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class AefisFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    private lateinit var binding: FragmentAefisBinding
    private lateinit var patientDetailsViewModel: PatientDetailsViewModel
    private lateinit var patientId: String
    private lateinit var fhirEngine: FhirEngine
    private val formatterClass = FormatterClass()
    private lateinit var layoutManager: RecyclerView.LayoutManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentAefisBinding.inflate(layoutInflater)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.apply {
            title = FormatterClass().getSharedPref("title", requireContext())
                ?: getString(R.string.administer_vaccine)
            setDisplayHomeAsUpEnabled(true)
        }
        fhirEngine = FhirApplication.fhirEngine(requireContext())

        patientId = formatterClass.getSharedPref("patientId", requireContext()).toString()

        layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.VERTICAL,
            false
        )
        binding.aefiParentList.layoutManager = layoutManager
        binding.aefiParentList.setHasFixedSize(true)

        patientDetailsViewModel = ViewModelProvider(
            this,
            PatientDetailsViewModelFactory(
                requireContext().applicationContext as Application,
                fhirEngine,
                patientId
            )
        )[PatientDetailsViewModel::class.java]
        setHasOptionsMenu(true)
        onBackPressed()
        pullVaccinesWithAefis()

        binding.btnAdd.apply {
            setOnClickListener {
                if (!activePatient()) {
                    Toast.makeText(
                        requireContext(),
                        "Patient is deceased",
                        Toast.LENGTH_LONG
                    ).show()

                    return@setOnClickListener
                }

                if (hasReceivedImmunizationAtGivenAge()) {
                    val patientId = FormatterClass().getSharedPref("patientId", context)

                    FormatterClass().saveSharedPref(
                        "questionnaireJson",
                        "adverse_effects.json", context
                    )
                    FormatterClass().saveSharedPref(
                        "title",
                        "AEFI", context
                    )

                    FormatterClass().saveSharedPref("vaccinationFlow", "addAefi", context)
                    FormatterClass().deleteSharedPref("updated_aefi_data", context)

                    FormatterClass().deleteSharedPref(
                        "aefi_data", requireContext()
                    )
                    startActivity(Intent(requireContext(), AddAefiActivity::class.java))
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Please administer vaccine first",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun activePatient(): Boolean {
        try {
            val patient = patientDetailsViewModel.getPatientInfo()

            return patient.isAlive
        } catch (e: Exception) {
            return false
        }

    }


    override fun onResume() {
        super.onResume()
        try {
            pullVaccinesWithAefis()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hasReceivedImmunizationAtGivenAge(): Boolean {
        val age = formatterClass.getSharedPref("current_age", requireContext())
        var hasReceived = false
        if (age != null) {
            val vaccineList = retrieveAdministered(age)
            if (vaccineList.isNotEmpty()) {
                // save for further processing

                FormatterClass().saveSharedPref(
                    "vaccine_administration_data",
                    Gson().toJson(vaccineList),
                    requireContext()
                )
                hasReceived = true
            }
        } else {
            hasReceived = false
        }
        return hasReceived
    }

    private fun retrieveAdministered(status: String): ArrayList<AdministeredDetails> {
        val expandableListDetail = ImmunizationHandler().generateDbVaccineSchedule()
        val administeredVaccines = patientDetailsViewModel.getAllImmunizationDetails()
        val alreadyAdministered = ArrayList<AdministeredDetails>()

        try {
            if (status == "At Birth") {

                alreadyAdministered.clear()
                val basic = expandableListDetail.entries.firstOrNull { it.key == "0" }?.value
                basic?.forEach { q ->
                    val administered = administeredVaccines.find { it.vaccineName == q.vaccineName }
                    if (administered != null) {
                        alreadyAdministered.add(
                            AdministeredDetails(
                                vaccineCode = q.vaccineCode,
                                vaccineName = q.vaccineName,
                                vaccineDate = administered.recorded.toString()
                            )
                        )
                    }
                }

            } else {
                val weeks: Int = when {
                    status.endsWith("weeks") -> status.replace(" weeks", "").toIntOrNull() ?: 0
                    status.endsWith("months") -> (status.replace(" months", "")
                        .toDouble() / 0.230137).toInt()

                    status.endsWith("years") -> (status.replace(" years", "")
                        .toDouble() / 0.019).toInt()

                    else -> 0
                }

                val basic = expandableListDetail.entries.firstOrNull { it.key == "$weeks" }?.value
                alreadyAdministered.clear()
                basic?.forEach { q ->
                    val administered = administeredVaccines.find { it.vaccineName == q.vaccineName }
                    if (administered != null) {
                        alreadyAdministered.add(
                            AdministeredDetails(
                                vaccineCode = q.vaccineCode,
                                vaccineName = q.vaccineName,
                                vaccineDate = q.vaccineName
                            )
                        )
                    }
                }

            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return alreadyAdministered
    }

    private fun pullVaccinesWithAefis() {
        val vaccineList = patientDetailsViewModel.getVaccineListWithAefis()

        val groupedByStatus = vaccineList.groupBy { it.status }

        val allergicReactions = groupedByStatus
            .toList()
            .sortedWith(compareBy { entry ->
                val status = entry.first
                FormatterClass().orderedDurations().indexOf(status)
            })
            .map { (status, vaccines) ->
                val reactions = ArrayList<DbVaccineData>(vaccines)

                AllergicReaction(
                    status,
                    getVaccinesAtGivenAge(status),
                    reactions = reactions
                )
            }

        val vaccineAdapter =
            VaccineAefiAdapter(
                patientDetailsViewModel,
                allergicReactions,
                requireContext()
            )

        binding.aefiParentList.adapter = vaccineAdapter
    }

    private fun getVaccinesAtGivenAge(status: String): String {
        val expandableListDetail = ImmunizationHandler().generateDbVaccineSchedule()

        var administeredVaccines = patientDetailsViewModel.getAllImmunizationDetails()

        val alreadyAdministered = ArrayList<String>()

        try {
            if (status == "At Birth") {
                var commaSeparatedString = ""
                alreadyAdministered.clear()
                val basic = expandableListDetail.entries.firstOrNull { it.key == "0" }?.value
                basic?.forEach { q ->
                    val administered = administeredVaccines.find { it.vaccineName == q.vaccineName }
                    if (administered != null) {
                        commaSeparatedString += "${q.vaccineName},"
                        alreadyAdministered.add(q.vaccineName)
                    }
                }

                return commaSeparatedString

            } else {
                val weeks: Int = when {
                    status.endsWith("weeks") -> status.replace(" weeks", "").toIntOrNull() ?: 0
                    status.endsWith("months") -> (status.replace(" months", "")
                        .toDouble() / 0.230137).toInt()

                    status.endsWith("years") -> (status.replace(" years", "")
                        .toDouble() / 0.019).toInt()

                    else -> 0
                }

                var commaSeparatedString = ""
                val basic = expandableListDetail.entries.firstOrNull { it.key == "$weeks" }?.value
                alreadyAdministered.clear()
                basic?.forEach { q ->
                    val administered = administeredVaccines.find { it.vaccineName == q.vaccineName }
                    if (administered != null) {
                        commaSeparatedString += "${q.vaccineName},"
                        alreadyAdministered.add(q.vaccineName)
                    }
                }

                return commaSeparatedString
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ""

    }

    private fun onBackPressed() {
        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner) {
            navigateBackToProfile()
        }
    }

    private fun navigateBackToProfile() {
        val patientId = FormatterClass().getSharedPref("patientId", requireContext())
        val intent = Intent(context, PatientDetailActivity::class.java)
        intent.putExtra("patientId", patientId)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        requireContext().startActivity(intent)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                navigateBackToProfile()

                true
            }

            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment AefisFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            AefisFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}