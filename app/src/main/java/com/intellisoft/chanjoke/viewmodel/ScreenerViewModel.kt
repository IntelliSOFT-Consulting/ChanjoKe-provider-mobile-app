package com.intellisoft.chanjoke.viewmodel


import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import com.intellisoft.chanjoke.detail.ui.main.UpdateFragment
import com.intellisoft.chanjoke.fhir.FhirApplication
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.mapping.ResourceMapper
import com.google.common.collect.MapDifference.ValueDifference
import com.intellisoft.chanjoke.fhir.data.FormatterClass
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.hl7.fhir.r4.model.AdverseEvent
import org.hl7.fhir.r4.model.AllergyIntolerance
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Condition
import org.hl7.fhir.r4.model.DateTimeType
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Narrative
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Resource

/** ViewModel for screener questionnaire screen {@link ScreenerEncounterFragment}. */
class ScreenerViewModel(application: Application, private val state: SavedStateHandle) :
    AndroidViewModel(application) {
    val questionnaire: String
        get() = getQuestionnaireJson()

    val isResourcesSaved = MutableLiveData<Boolean>()

    private val questionnaireResource: Questionnaire
        get() = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
            .parseResource(questionnaire) as Questionnaire

    private var questionnaireJson: String? = null
    private var fhirEngine: FhirEngine = FhirApplication.fhirEngine(application.applicationContext)

    /**
     * Saves screener encounter questionnaire response into the application database.
     *
     * @param questionnaireResponse screener encounter questionnaire response
     */
    fun saveScreenerEncounter(questionnaireResponse: QuestionnaireResponse, patientId: String) {
        viewModelScope.launch {
            val bundle = ResourceMapper.extract(questionnaireResource, questionnaireResponse)
            val subjectReference = Reference("Patient/$patientId")
            val encounterId = generateUuid()
            if (isRequiredFieldMissing(bundle)) {
                isResourcesSaved.value = false
                return@launch
            }
            saveResources(bundle, subjectReference, encounterId)
            isResourcesSaved.value = true
        }
    }

    private suspend fun saveResources(
        bundle: Bundle,
        subjectReference: Reference,
        encounterId: String,
    ) {
        val encounterReference = Reference("Encounter/$encounterId")
        bundle.entry.forEach {
            when (val resource = it.resource) {
                is Observation -> {
                    if (resource.hasCode()) {
                        resource.id = generateUuid()
                        resource.subject = subjectReference
                        resource.encounter = encounterReference
                        saveResourceToDatabase(resource)
                    }
                }

                is Condition -> {
                    if (resource.hasCode()) {
                        resource.id = generateUuid()
                        resource.subject = subjectReference
                        resource.encounter = encounterReference
                        saveResourceToDatabase(resource)
                    }
                }

                is Encounter -> {
                    resource.subject = subjectReference
                    resource.id = encounterId
                    saveResourceToDatabase(resource)
                }
            }
        }
    }

    private fun isRequiredFieldMissing(bundle: Bundle): Boolean {
        bundle.entry.forEach {
            val resource = it.resource
            when (resource) {
                is Observation -> {
                    if (resource.hasValueQuantity() && !resource.valueQuantity.hasValueElement()) {
                        return true
                    }
                }
                // TODO check other resources inputs
            }
        }
        return false
    }

    private suspend fun saveResourceToDatabase(resource: Resource) {
        fhirEngine.create(resource)
    }

    private fun getQuestionnaireJson(): String {
        questionnaireJson?.let {
            return it!!
        }
        questionnaireJson = readFileFromAssets(state[UpdateFragment.QUESTIONNAIRE_FILE_PATH_KEY]!!)
        return questionnaireJson!!
    }

    private fun readFileFromAssets(filename: String): String {
        return getApplication<Application>().assets.open(filename).bufferedReader().use {
            it.readText()
        }
    }

    private fun generateUuid(): String {
        return UUID.randomUUID().toString()
    }

    fun updateObservation(it: Observation) = runBlocking {
        updateObservationData(it)

    }

    private suspend fun updateObservationData(it: Observation) {
        fhirEngine.update(it)

    }

    fun addAeFi(
        context: Context,
        patientId: String,
        encounterId: String,
        observations: MutableList<Observation>
    ) {
        viewModelScope.launch {
            val age = FormatterClass().getSharedPref(
                "current_age", getApplication<Application>().applicationContext
            )

            createAdverseEvent(context, encounterId, patientId, age.toString())

            val subjectReference = Reference("Patient/$patientId")
            val resource = Encounter()
            resource.subject = subjectReference
            resource.id = encounterId


            val facility = FormatterClass().getSharedPref("practitionerFacility", context)
            if (facility != null) {

                val locationReference = Reference(facility)
                resource.locationFirstRep.location = locationReference

            }
            saveResourceToDatabase(resource)
            observations.forEach {
                saveResourceToDatabase(it)
            }
            isResourcesSaved.value = true
        }

    }

    private suspend fun createAdverseEvent(
        context: Context, encounterId: String, patientId: String, age: String
    ) {

        val encounterReference = Reference("Encounter/$encounterId")
        val patientReference = Reference("Patient/$patientId")

        val adv = AdverseEvent()
        adv.id = generateUuid()
        adv.encounter = encounterReference
        adv.subject = patientReference
        val fhirPractitionerId = FormatterClass().getSharedPref("fhirPractitionerId", context)
        if (fhirPractitionerId != null) {
            val recorderReference = Reference("Practitioner/$fhirPractitionerId")
            adv.recorder = recorderReference
        }
        val facility = FormatterClass().getSharedPref("practitionerFacility", context)
        if (facility != null) {
            val locationReference = Reference(facility)

            locationReference.display =
                FormatterClass().getSharedPref("practitionerFacilityName", context)
            adv.location = locationReference
        }
        val coding = Coding()
        coding.code = age
        coding.system = age
        coding.display = age
        val codeableConcept = CodeableConcept()
        codeableConcept.addCoding(coding)
        codeableConcept.text = age
        adv.event = codeableConcept
        /**
         * TODO: Add more details for the allergy intolerance
         */
        saveResourceToDatabase(adv)

    }

    private suspend fun createAdverseEffects(encounterId: String, patientId: String, age: String) {

        val encounterReference = Reference("Encounter/$encounterId")
        val patientReference = Reference("Patient/$patientId")

        val allergyIntolerance = AllergyIntolerance()
        allergyIntolerance.id = generateUuid()
        allergyIntolerance.encounter = encounterReference
        allergyIntolerance.patient = patientReference
        allergyIntolerance.noteFirstRep.text = age

        /**
         * TODO: Add more details for the allergy intolerance
         */
        saveResourceToDatabase(allergyIntolerance)

    }

    private companion object {
        const val ASTHMA = "161527007"
        const val LUNG_DISEASE = "13645005"
        const val DEPRESSION = "35489007"
        const val DIABETES = "161445009"
        const val HYPER_TENSION = "161501007"
        const val HEART_DISEASE = "56265001"
        const val HIGH_BLOOD_LIPIDS = "161450003"

        const val FEVER = "386661006"
        const val SHORTNESS_BREATH = "13645005"
        const val COUGH = "49727002"
        const val LOSS_OF_SMELL = "44169009"

        const val SPO2 = "59408-5"

        private val comorbidities: Set<String> = setOf(
            ASTHMA,
            LUNG_DISEASE,
            DEPRESSION,
            DIABETES,
            HYPER_TENSION,
            HEART_DISEASE,
            HIGH_BLOOD_LIPIDS,
        )
        private val symptoms: Set<String> = setOf(FEVER, SHORTNESS_BREATH, COUGH, LOSS_OF_SMELL)
    }
}