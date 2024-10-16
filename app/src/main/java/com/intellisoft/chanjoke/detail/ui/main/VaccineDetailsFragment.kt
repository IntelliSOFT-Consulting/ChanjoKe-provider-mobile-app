package com.intellisoft.chanjoke.detail.ui.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import com.google.android.fhir.FhirEngine
import com.intellisoft.chanjoke.R
import com.intellisoft.chanjoke.databinding.FragmentVaccineDetailsBinding
import com.intellisoft.chanjoke.detail.PatientDetailActivity
import com.intellisoft.chanjoke.detail.ui.main.contraindications.ContrasActivity
import com.intellisoft.chanjoke.fhir.FhirApplication
import com.intellisoft.chanjoke.fhir.data.FormatterClass
import com.intellisoft.chanjoke.fhir.data.Reasons
import com.intellisoft.chanjoke.vaccine.validations.ImmunizationHandler
import com.intellisoft.chanjoke.viewmodel.PatientDetailsViewModel
import com.intellisoft.chanjoke.viewmodel.PatientDetailsViewModelFactory
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter


class VaccineDetailsFragment : Fragment() {
    private lateinit var binding: FragmentVaccineDetailsBinding
    private lateinit var fhirEngine: FhirEngine
    private lateinit var patientDetailsViewModel: PatientDetailsViewModel
    private val immunizationHandler = ImmunizationHandler()
    private var patientId = ""
    private val formatterClass = FormatterClass()

    /**/
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentVaccineDetailsBinding.inflate(inflater, container, false)
        fhirEngine = FhirApplication.fhirEngine(requireContext())
        patientId = FormatterClass().getSharedPref("patientId", requireContext()).toString()

        patientDetailsViewModel =
            ViewModelProvider(
                this,
                PatientDetailsViewModelFactory(
                    requireActivity().application,
                    fhirEngine,
                    patientId.toString()
                ),
            ).get(PatientDetailsViewModel::class.java)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onBackPressed()
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.apply {
            title = "Vaccines"
            setDisplayHomeAsUpEnabled(true)
        }

        getVaccineDetails()

        binding.apply {

            val vaccineCode =
                FormatterClass().getSharedPref("vaccineCode", requireContext())
            val basicVaccine = immunizationHandler.getVaccineDetailsByBasicVaccineCode(vaccineCode.toString())

            val contras = basicVaccine?.let { patientDetailsViewModel.loadContraindications(it.vaccineName, "") }

            btnNotAdminister.apply {
                setOnClickListener {

                    if (contras != null) {
                        if (contras.isNotEmpty()){
                            formatterClass.saveSharedPref("vaccineDetailsType",
                                Reasons.NOT_ADMINISTERED.name, requireContext())
                            startActivity(Intent(requireContext(), ContrasActivity::class.java))
                        }else {
                            Toast.makeText(requireContext(),
                                "There were no recorded data for this vaccine",
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            btnContraindication.apply {
                setOnClickListener {
                    if (contras != null) {
                        if (contras.isNotEmpty()){
                            formatterClass.saveSharedPref("vaccineDetailsType",
                                Reasons.CONTRAINDICATE.name, requireContext())
                            startActivity(Intent(requireContext(), ContrasActivity::class.java))
                        }else {
                            Toast.makeText(requireContext(),
                                "There were no recorded contraindication data for this vaccine",
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun getVaccineDetails() {
        val vaccineName = FormatterClass().getSharedPref("vaccineNameDetails", requireContext())
        if (vaccineName != null) {
            //Get Vaccine details
            binding.tvVaccineName.text = vaccineName
            (requireActivity() as AppCompatActivity).supportActionBar?.apply {
                title = vaccineName
            }

            val basicVaccine = immunizationHandler.getVaccineDetailsByBasicVaccineName(vaccineName)
            if (basicVaccine != null) {
                val vaccineCode = basicVaccine.vaccineCode
                FormatterClass().saveSharedPref("vaccineCode", vaccineCode, requireContext())
                val immunizationDetails =
                    patientDetailsViewModel.getImmunizationDataDetails(vaccineCode)

                if (immunizationDetails.isNotEmpty()) {

                    val logicalId = immunizationDetails[0].logicalId
                    val vaccineNameDetails = immunizationDetails[0].vaccineName
                    val dosesAdministered = immunizationDetails[0].dosesAdministered
                    val seriesDosesString = immunizationDetails[0].seriesDosesString
                    val series = immunizationDetails[0].series
                    val status = immunizationDetails[0].status

                    binding.apply {

                        tvVaccineDate.text = dosesAdministered
                        tvVaccineDose.text = seriesDosesString
                        tvDaysSince.text =
                            generateDaysSince(dosesAdministered, days = true, month = false)
                        tvMonthSince.text =
                            generateDaysSince(dosesAdministered, days = false, month = true)
                        tvYearsSince.text =
                            generateDaysSince(dosesAdministered, days = false, month = false)

                        /**
                         * TODO1: Update these with correct data
                         */

                        try {
                            val practitionerId = immunizationDetails[0].practioner?.replace("Practitioner/","")
                            val locationId = immunizationDetails[0].facility?.replace("Location/","")

                            val locationDetails = locationId?.let {
                                patientDetailsViewModel.getLocationName(
                                    it
                                )
                            }
                            tvFacility.text = locationDetails //Location


                            println("locationId $locationId")
                            println("locationDetails $locationDetails")

                            Log.e("---->","<-----")


                            val myPair = practitionerId?.let {
                                patientDetailsViewModel.getPractitionerName(
                                    it
                                )
                            }
                            val name = myPair?.first ?: ""
                            tvAdministrator.text = name //Practitioner

                            println(practitionerId)
                            println(myPair)
                            println(name)
                            Log.e("---->","<-----")

                        } catch (e: Exception) {
                            e.printStackTrace()
                        }


                        val patientDob =
                            FormatterClass().getSharedPref("patientDob", requireContext())
                        val age = generateAgeSince(patientDob, dosesAdministered)

                        if (series == seriesDosesString) tvCompleteSeries.text = "Yes" else "No"

                        tvAgeThen.text = age

                    }
                }

            }

        }

    }

    private fun onBackPressed() {
        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner) {
            exitPageSection()
        }
    }

    private fun generateAgeSince(dateString: String?, vaccineDate: String?): String {
        return try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val formatter2 = DateTimeFormatter.ofPattern("MMM dd yyyy")

            // Parse the string into a LocalDate
            val date1 = LocalDate.parse(dateString, formatter)
            val date2 = LocalDate.parse(vaccineDate, formatter2)
            // Calculate the period between the two dates
            val period = Period.between(date1, date2)
            when {
                period.years > 1 -> "${period.years} years ${period.months} months ${period.days} days"
                period.years == 1 -> "${period.years} year ${period.months} months ${period.days} days"
                period.months > 1 -> "${period.months} months ${period.days} days"
                else -> "${period.days} days"
            }

//            "${period.years} year(s) ${period.months} month(s) ${period.days} day(s)"

        } catch (e: Exception) {
            "0 day(s)"
        }
    }

    private fun generateDaysSince(
        dateString: String,
        days: Boolean,
        month: Boolean,
    ): String {

        // Define the date format
        try {
            val formatter = DateTimeFormatter.ofPattern("MMM dd yyyy")

            // Parse the string into a LocalDate
            val date1 = LocalDate.parse(dateString, formatter)
            val date2 = LocalDate.now()
            // Calculate the period between the two dates
            val period = Period.between(date1, date2)
            if (days) {
                return "${period.days} day(s)"
            }
            return if (month) {
                "${period.months} month(s)"
            } else {
                "${period.years} year(s)"
            }
        } catch (e: Exception) {
            return "0 day(s)"
        }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                exitPageSection()
                true
            }

            else -> false
        }
    }

    private fun exitPageSection() {
        formatterClass.deleteSharedPref("vaccineDetailsType", requireContext())

        val patientId = FormatterClass().getSharedPref("patientId", requireContext())
        val intent = Intent(context, PatientDetailActivity::class.java)
        intent.putExtra("patientId", patientId)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        requireContext().startActivity(intent)
    }

}