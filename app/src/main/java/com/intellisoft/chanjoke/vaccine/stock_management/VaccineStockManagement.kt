package com.intellisoft.chanjoke.vaccine.stock_management

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.intellisoft.chanjoke.MainActivity
import com.intellisoft.chanjoke.R
import com.intellisoft.chanjoke.detail.PatientDetailActivity
import com.intellisoft.chanjoke.fhir.data.DbVaccineStockDetails
import com.intellisoft.chanjoke.fhir.data.FormatterClass
import com.intellisoft.chanjoke.fhir.data.NavigationDetails
import com.intellisoft.chanjoke.vaccine.validations.VaccinationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Arrays
import java.util.Locale

class VaccineStockManagement : AppCompatActivity() {
    private lateinit var layoutManager: RecyclerView.LayoutManager
    private lateinit var recyclerView:RecyclerView
    private var patientId = ""
    private var targetDisease = ""
    private var formatterClass = FormatterClass()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vaccine_stock_management)

        patientId = formatterClass.getSharedPref("patientId", this).toString()
        targetDisease = formatterClass.getSharedPref("targetDisease", this).toString()

        recyclerView = findViewById(R.id.recyclerView)
        layoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.VERTICAL,
            false
        )
        recyclerView.layoutManager = layoutManager
        recyclerView.setHasFixedSize(true)

        findViewById<Button>(R.id.btnNext).setOnClickListener {

            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("functionToCall", NavigationDetails.ADMINISTER_VACCINE.name)
            intent.putExtra("patientId", patientId)
            startActivity(intent)
        }
        findViewById<Button>(R.id.btnCancel).setOnClickListener {

//            val intent = Intent(this, PatientDetailActivity::class.java)
//            intent.putExtra("patientId", patientId)
//            startActivity(intent)
        }

        getStockManagement()

    }

    private fun getStockManagement() {

        CoroutineScope(Dispatchers.IO).launch {
            val vaccinationManager = VaccinationManager()

            val vaccineDetails = vaccinationManager.getVaccineDetails(targetDisease)
            Log.e("******", "****** $vaccineDetails")

            if (vaccineDetails != null) {

                /**
                 * TODO: Add all these tto shared Preference
                 */

                val stockList = formatterClass.generateStockValue(vaccineDetails, this@VaccineStockManagement)
                val dbVaccineStockDetailsList= ArrayList<DbVaccineStockDetails>()
                for(i in stockList){
                    val dbVaccineStockDetails = DbVaccineStockDetails(i.value, i.name)
                    dbVaccineStockDetailsList.add(dbVaccineStockDetails)
                }
                val vaccineStockAdapter = VaccineStockAdapter(dbVaccineStockDetailsList, this@VaccineStockManagement)
                CoroutineScope(Dispatchers.Main).launch { recyclerView.adapter = vaccineStockAdapter }

            } else {
                val intent = Intent(this@VaccineStockManagement, PatientDetailActivity::class.java)
                startActivity(intent)
            }
        }



    }

}