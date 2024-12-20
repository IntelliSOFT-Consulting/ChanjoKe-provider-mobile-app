package com.intellisoft.chanjoke

import android.app.Application
import android.app.DatePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.DatePicker
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.fhir.FhirEngine
import com.intellisoft.chanjoke.databinding.FragmentUpdateVaccineHistoryBinding
import com.intellisoft.chanjoke.detail.ui.main.adapters.UpdateVaccineHistoryAdapter
import com.intellisoft.chanjoke.fhir.FhirApplication
import com.intellisoft.chanjoke.fhir.data.DbCarePlan
import com.intellisoft.chanjoke.fhir.data.DbRecommendationDetails
import com.intellisoft.chanjoke.fhir.data.DbVaccineData
import com.intellisoft.chanjoke.fhir.data.DbVaccineHistory
import com.intellisoft.chanjoke.fhir.data.FormatterClass
import com.intellisoft.chanjoke.utils.BlurBackgroundDialog
import com.intellisoft.chanjoke.vaccine.AdministerVaccineViewModel
import com.intellisoft.chanjoke.vaccine.campaign.CampaignAdapter
import com.intellisoft.chanjoke.vaccine.validations.ImmunizationHandler
import com.intellisoft.chanjoke.vaccine.validations.RoutineVaccine
import com.intellisoft.chanjoke.viewmodel.PatientDetailsViewModel
import com.intellisoft.chanjoke.viewmodel.PatientDetailsViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Immunization
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

class UpdateVaccineHistoryFragment : Fragment() {

    private lateinit var binding: FragmentUpdateVaccineHistoryBinding
    private lateinit var patientDetailsViewModel: PatientDetailsViewModel
    private lateinit var patientId: String
    private lateinit var fhirEngine: FhirEngine
    private val formatterClass = FormatterClass()
    private var patientYears: String? = null
    private var patientWeeks: String? = null
    private lateinit var sharedPreferences: SharedPreferences
    private val immunizationHandler = ImmunizationHandler()

    private var vaccineType: String = ""
    private var lastDose: String = ""
    private var vaccinePlace: String = ""
    private val administerVaccineViewModel: AdministerVaccineViewModel by viewModels()
    private var recommendationList = ArrayList<DbRecommendationDetails>()
    private var administeredList = ArrayList<DbVaccineData>()
    private var administeredVaccineList = ArrayList<String>()
    private val today = LocalDate.now()
    private val formatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy")
    private lateinit var layoutManager: RecyclerView.LayoutManager
    private val vaccineHistory = ArrayList<DbVaccineHistory>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // Inflate the layout for this fragment
        binding = FragmentUpdateVaccineHistoryBinding.inflate(layoutInflater)

        // Inflate the layout for this fragment
        return binding.root


    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up onBackPressedCallback to navigate back to Fragment 2 when in Fragment 3
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    requireActivity().supportFragmentManager.popBackStack()
                }
            })

        binding.btnBack.setOnClickListener {
            //Implement onBackPressedCallback
            requireActivity().supportFragmentManager.popBackStack()
        }


        fhirEngine = FhirApplication.fhirEngine(requireContext())

        sharedPreferences = requireContext()
            .getSharedPreferences(
                getString(R.string.vaccineList),
                Context.MODE_PRIVATE
            )

        patientId = formatterClass.getSharedPref("patientId", requireContext()).toString()


        patientDetailsViewModel = ViewModelProvider(
            this,
            PatientDetailsViewModelFactory(
                requireContext().applicationContext as Application,
                fhirEngine,
                patientId
            )
        )[PatientDetailsViewModel::class.java]

        recommendationList = patientDetailsViewModel.recommendationList(null)

        administeredList = patientDetailsViewModel.getVaccineList()
        administeredVaccineList = administeredList.map { it.vaccineName } as ArrayList<String>

        layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.VERTICAL,
            false
        )
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.setHasFixedSize(true)

        binding.btnAdd.setOnClickListener {
            val lastDoseDate = binding.tvDatePicker.text.trim().toString()
            val batchNumber = binding.etBatchNumber.text

            if (
                vaccineType != "" &&
                lastDose != "" &&
                vaccinePlace != "" &&
                lastDoseDate != "Date of last dose *"
            ) {

                val vaccineDetail = immunizationHandler
                    .getVaccineDetailsByBasicVaccineName(lastDose)
                val doseNumber = vaccineDetail?.doseNumber
                val vaccineName = vaccineDetail?.vaccineName

                val batchNumbers = if (!batchNumber.isNullOrBlank()) {
                    batchNumber.toString()
                } else {
                    ""
                }

                CoroutineScope(Dispatchers.IO).launch {
                    val dbVaccineHistory = DbVaccineHistory(
                        vaccineName,
                        vaccineType,
                        doseNumber,
                        vaccinePlace,
                        lastDoseDate,
                        batchNumbers
                    )
                    // Check if the entry already exists in the list based on relevant fields
                    val exists = vaccineHistory.any {
                        it.vaccineName == dbVaccineHistory.vaccineName
                    }

                    // Add only if it doesn't exist
                    if (!exists) {
                        vaccineHistory.add(dbVaccineHistory)
                    }else{
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(requireContext(),
                                "$vaccineName already exists in the list.", Toast.LENGTH_SHORT).show()
                        }

                    }

                    populateList(vaccineHistory)
                }

            } else {
                if (vaccineType == "") {
                    binding.vaccineSpinner.requestFocus()
                    Toast.makeText(
                        requireContext(),
                        "Select the type of vaccine",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                if (lastDose == "") {
                    binding.lastDose.requestFocus()
                    Toast.makeText(
                        requireContext(),
                        "Select the type of last vaccine dose",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                if (vaccinePlace == "") {
                    binding.vaccinationPlace.requestFocus()
                    Toast.makeText(
                        requireContext(),
                        "Select the place of vaccination",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }


                if (lastDoseDate == "Date of last dose *") {
                    binding.tvDatePicker.requestFocus()
                    Toast.makeText(
                        requireContext(),
                        "Select the Date of last Dose",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
            }

        }

        binding.nextSubmit.setOnClickListener {

            if (vaccineHistory.isNotEmpty()) {

                val resultList = ArrayList<String>()

                vaccineHistory.forEach {
                    val vaccineName = it.vaccineName
                    val date = it.lastDoseDate

                    if (vaccineName != null) {
                        resultList.add(vaccineName)

                        administerVaccineViewModel.createManualImmunizationResource(
                            resultList,
                            formatterClass.generateUuid(),
                            patientId,
                            requireContext(),
                            date,
                            Immunization.ImmunizationStatus.COMPLETED,
                            patientDetailsViewModel
                        )

                        val blurBackgroundDialog = BlurBackgroundDialog(this, requireContext())
                        blurBackgroundDialog.show()
                    }
                }

            } else {
                Toast.makeText(
                    requireContext(),
                    "Select vaccines to update.",
                    Toast.LENGTH_SHORT
                ).show()

            }
        }

        patientWeeks = formatterClass.getSharedPref("patientWeeks", requireContext())
        patientYears = formatterClass.getSharedPref("patientYears", requireContext())

        binding.tvDatePicker.setOnClickListener { showDatePickerDialog() }

        CoroutineScope(Dispatchers.IO).launch {

            CoroutineScope(Dispatchers.Main).launch {
                createVaccinationPlace()
                createVaccineType()
            }

        }

    }

    private fun populateList(dbVaccineHistoryList: ArrayList<DbVaccineHistory>) {
        CoroutineScope(Dispatchers.Main).launch {
            val vaccineAdapter = UpdateVaccineHistoryAdapter(
                dbVaccineHistoryList,
                requireContext()
            )
            binding.recyclerView.adapter = vaccineAdapter
        }
    }

    private fun createVaccineType() {

        //Check if years < 6 years
        val patientWeeksInt = patientWeeks?.toIntOrNull()
        if (patientWeeksInt != null && patientWeeksInt < 260) {

            /**
             * 1.) Get the target disease
             * 2.) Get the vaccines under the target disease
             */
            //get the vaccine not vaccinated
            val routineKeyList = sharedPreferences.getString("routineList", null)
            val expandableListTitle = routineKeyList!!.split(",").toList()
//            Get the administered list
            val administeredList = patientDetailsViewModel.getVaccineList()
            val newVaccineNameList = ArrayList<String>()

            val filteredWeeksList = expandableListTitle.filter { it.toInt() < patientWeeksInt }
            filteredWeeksList.forEach { keyValue ->
                val weekNo = formatterClass.getVaccineScheduleValue(keyValue)
                val weekNoList = sharedPreferences.getStringSet(weekNo, null)
                weekNoList?.toList()?.forEach { vaccineToCheck ->
                    val exists = administeredList.any { it.vaccineName == vaccineToCheck }
                    if (!exists) {
                        newVaccineNameList.add(vaccineToCheck)
                    }
                }
            }


            val newVaccineList = ArrayList<String>()
            val routineVaccineList = ArrayList<RoutineVaccine>()
            val routineTargetDiseases = immunizationHandler.getRoutineTargetDiseases()

            routineTargetDiseases.forEach { routineVaccine ->

                val vaccineList = routineVaccine.vaccineList

                val newBasicVaccineList =
                    vaccineList.filter { basicVaccine -> newVaccineNameList.contains(basicVaccine.vaccineName) }

                routineVaccine.vaccineList = newBasicVaccineList

                if (newBasicVaccineList.isNotEmpty())
                    routineVaccineList.add(routineVaccine)
                if (newBasicVaccineList.isNotEmpty()) {
                    newVaccineList.add(routineVaccine.targetDisease)
                }

            }

            // Create an ArrayAdapter using the dataList and a default spinner layout
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                newVaccineList
            )

            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            binding.vaccineSpinner.adapter = adapter

            binding.vaccineSpinner.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        // Handle the selection
                        val selectedItem = parent.getItemAtPosition(position) as String
                        vaccineType = selectedItem
                        createLastDose(selectedItem)
                        // Perform your actions based on the selected item
                    }

                    override fun onNothingSelected(parent: AdapterView<*>) {
                        // Handle the case when nothing is selected
                    }
                }

        }

    }

    private fun createLastDose(targetDisease: String) {

        /**
         * Get the dose number from the vaccine name
         */
        val vaccineList = ArrayList<String>()
        val routineBasicVaccine =
            immunizationHandler.getRoutineVaccineDetailsBySeriesTargetName(targetDisease)
        if (routineBasicVaccine is RoutineVaccine) {

            //Get the missed vaccine list
            val routineVaccineList = routineBasicVaccine.vaccineList

            routineVaccineList.forEach { basicVaccine ->
                val vaccineName = basicVaccine.vaccineName
                if (!administeredVaccineList.contains(vaccineName)) {
                    val recommendedVaccine =
                        recommendationList.find { it.vaccineName == vaccineName }
                    if (recommendedVaccine != null) {
                        val recommendedDate =
                            LocalDate.parse(recommendedVaccine.latestDate, formatter)
                        if (recommendedDate.isBefore(today)) {
                            vaccineList.add(recommendedVaccine.vaccineName)
                        }
                    }
                }

            }
        }


        // Create an ArrayAdapter using the dataList and a default spinner layout
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            vaccineList
        )

        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        // Apply the adapter to the spinner
        binding.lastDose.adapter = adapter

        /** Temp assignment**/
//        lastDose = "Polio"

        binding.lastDose.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                // Handle the selection
                val selectedItem = parent.getItemAtPosition(position) as String
                lastDose = selectedItem
                // Perform your actions based on the selected item
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Handle the case when nothing is selected
            }
        }
    }

    private fun createVaccinationPlace() {

        // Create an ArrayAdapter using the string array and a default spinner layout
        val adapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.vaccine_place,
            android.R.layout.simple_spinner_item
        )
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        // Apply the adapter to the spinner
        binding.vaccinationPlace.adapter = adapter

        binding.vaccinationPlace.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    // Handle the selection
                    val selectedItem = parent.getItemAtPosition(position) as String
                    vaccinePlace = selectedItem
                    // Perform your actions based on the selected item
                }

                override fun onNothingSelected(parent: AdapterView<*>) {
                    // Handle the case when nothing is selected
                }
            }


    }


    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val maxCalendar = Calendar.getInstance()
//        maxCalendar.add(Calendar.DAY_OF_MONTH, -7)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _: DatePicker, selectedYear: Int, selectedMonth: Int, selectedDay: Int ->
                // Handle the selected date (e.g., update the TextView)
                val formattedDate = "$selectedDay/${selectedMonth + 1}/$selectedYear"
                binding.tvDatePicker.text = formattedDate
            },
            year,
            month,
            day
        )
        datePickerDialog.datePicker.maxDate =
            System.currentTimeMillis() // Set the limit for the last date

        // Show the DatePickerDialog
        datePickerDialog.show()
    }

}