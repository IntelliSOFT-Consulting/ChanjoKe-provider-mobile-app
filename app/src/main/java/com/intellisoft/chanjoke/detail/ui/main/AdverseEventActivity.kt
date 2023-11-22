package com.intellisoft.chanjoke.detail.ui.main

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.lifecycle.ReportFragment.Companion.reportFragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.logicalId
import com.intellisoft.chanjoke.R
import com.intellisoft.chanjoke.databinding.ActivityAdverseEventBinding
import com.intellisoft.chanjoke.fhir.FhirApplication
import com.intellisoft.chanjoke.fhir.data.FormatterClass
import com.intellisoft.chanjoke.viewmodel.PatientDetailsViewModel
import com.intellisoft.chanjoke.viewmodel.PatientDetailsViewModelFactory
import timber.log.Timber

class AdverseEventActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdverseEventBinding
    private lateinit var fhirEngine: FhirEngine
    private lateinit var patientDetailsViewModel: PatientDetailsViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdverseEventBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
            title = ""

        }
        val patientId = FormatterClass().getSharedPref("patientId", this@AdverseEventActivity)
        val encounterId =
            FormatterClass().getSharedPref("encounter_logical", this@AdverseEventActivity)
        fhirEngine = FhirApplication.fhirEngine(this)
        patientDetailsViewModel =
            ViewModelProvider(
                this,
                PatientDetailsViewModelFactory(
                    this.application,
                    fhirEngine,
                    patientId.toString()
                ),
            ).get(PatientDetailsViewModel::class.java)
        binding.apply {
            typeOfAEFITextView.text = extractAefiData(
                patientId.toString(),
                encounterId.toString(),
                "882-22"
            )
            // Brief details on AEFI
            briefDetailsTextView.text = extractAefiData(
                patientId.toString(),
                encounterId.toString(),
               "833-22"
            )
            // Onset of Event
            onsetOfEventTextView.text = extractAefiData(
                patientId.toString(),
                encounterId.toString(),
               "833-23"
            )
            // Past Medical History
            pastMedicalHistoryTextView.text = extractAefiData(
                patientId.toString(),
                encounterId.toString(),
            "833-21"
            )
            // Reaction Severity
            reactionSeverityTextView.text = extractAefiData(
                patientId.toString(),
                encounterId.toString(),
            "880-11"
            )
            // Action Taken
            actionTakenTextView.text = extractAefiData(
                patientId.toString(),
                encounterId.toString(),
              "888-1"
            )
            // AEFI Outcome
            aefiOutcomeTextView.text = extractAefiData(
                patientId.toString(),
                encounterId.toString(),
             "808-11"
            )
        }

    }

    private fun extractAefiData(patientId: String, encounterId: String, code: String): String {
        var text = "\t"
        /***
         * TODO: extract Observation by code
         */
       val  data=patientDetailsViewModel.getObservationByCode(patientId, encounterId, code)
        if (data!=null){
            text="\t $data"
        }

        return text
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return super.onSupportNavigateUp()
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }
}