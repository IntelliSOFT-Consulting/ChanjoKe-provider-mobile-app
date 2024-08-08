package com.intellisoft.chanjoke.fhir.external

import android.content.Context
import com.intellisoft.chanjoke.fhir.FhirApplication
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.ExternalAnswerValueSetResolver
import com.google.android.fhir.search.search
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.ValueSet

abstract class ValueSetResolver : ExternalAnswerValueSetResolver {

    companion object {
        private lateinit var fhirEngine: FhirEngine

        fun init(context: Context) {
            fhirEngine = FhirApplication.fhirEngine(context)
        }

        private suspend fun fetchValueSetFromDb(uri: String): List<Coding> {

            val valueSets = fhirEngine.search<ValueSet> {
                filter(ValueSet.URL, { value = uri }) }

            if (valueSets.isEmpty())
                return listOf(Coding().apply { display = "No referral facility available." })
            else {
                val valueSetList = ArrayList<Coding>()
                for (valueSet in valueSets) {
                    for (item in valueSet.expansion.contains) {
                        valueSetList.add(
                            Coding().apply {
                                system = item.system
                                code = item.code
                                display = item.display
                            }
                        )
                    }
                }
                return valueSetList
            }
        }
    }

    override suspend fun resolve(uri: String): List<Coding> {
        return fetchValueSetFromDb(uri)
    }
}
